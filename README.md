# Hurray World with Java — Jenkins CI/CD

Simple **Java 21** Maven HTTP app that serves a greeting in the browser, with a **Jenkins Pipeline** that builds, tests, deploys (Docker), publishes Surefire reports, and sends email.

Repo example: [Simple-Hello-World-CI-CD-with-Java-and-Jenkins-](https://github.com/codesnippetsforall/Simple-Hello-World-CI-CD-with-Java-and-Jenkins-)

## What the app does

- Runs an embedded HTTP server on port **8085**
- `GET /` returns text like: `Hurray, World it is full of surprises !`
- Built as an executable JAR: `target/Hurrayworldwithjava-1.0.0.jar`

## Project layout

| Path | Purpose |
|---|---|
| `src/main/java/com/example/App.java` | HTTP server + `greet()` |
| `src/test/java/com/example/AppTest.java` | JUnit 5 tests (`@Order`) |
| `pom.xml` | Maven, Java 21, Surefire + Surefire Report |
| `JenkinsFile` | Jenkins Declarative Pipeline |
| `Dockerfile` | Custom Jenkins image (Docker CLI) |
| `docker-compose.yml` | Jenkins + Docker-in-Docker (DinD) |

## Prerequisites

**Local run**

- Java 21+
- Maven 3.9+

**Jenkins on Ubuntu/EC2**

- Docker + Docker Compose
- EC2 security group inbound: **22**, **8080** (Jenkins), **8085** (app)

## Run locally

```bash
mvn clean package
java -jar target/Hurrayworldwithjava-1.0.0.jar
```

Open: [http://localhost:8085/](http://localhost:8085/)

Optional port:

```bash
java -jar target/Hurrayworldwithjava-1.0.0.jar 9090
```

### Tests

```bash
mvn test
mvn surefire-report:report-only
```

- JUnit XML: `target/surefire-reports/`
- HTML report: `target/surefire-html/surefire-report.html`

## Jenkins setup (Docker Compose)

This project uses Jenkins in Docker with **Docker-in-Docker** so the pipeline can run `docker` commands.

```bash
# Build Jenkins image (Docker CLI included)
docker build -t my-jenkins .

# Start Jenkins + DinD
docker compose up -d
```

- Jenkins UI: `http://<EC2-PUBLIC-IP>:8080/`
- App (after deploy): `http://<EC2-PUBLIC-IP>:8085/`

`docker-compose.yml` publishes:

- `8080` → Jenkins
- `8085` → app (via DinD port publish)
- `2376` → Docker daemon (TLS)

Both services use `restart: unless-stopped` so containers come back after a reboot (see below).

Initial admin password:

```bash
docker exec -it my-jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

### Auto-start after EC2 reboot (restart policy)

This project uses **Option A**: Docker Compose `restart: unless-stopped` on `jenkins-docker` and `my-jenkins`.

On the EC2 host, enable Docker to start on boot, then apply Compose once:

```bash
sudo systemctl enable docker
sudo systemctl enable containerd

cd ~/install-jenkins-docker   # folder that has docker-compose.yml
docker compose up -d
docker compose ps
```

After an EC2 **stop/start** or reboot:

1. Docker starts automatically
2. `jenkins-docker` and `my-jenkins` restart because of `restart: unless-stopped`
3. Wait ~30–60 seconds, then open Jenkins

If Jenkins UI loads but builds fail with `lookup docker` / `tcp://docker:2376`, restart the stack once:

```bash
cd ~/install-jenkins-docker
docker compose up -d
docker compose restart my-jenkins
```

**Public IP:** a new public IP is assigned unless you use an Elastic IP. Update the GitHub webhook after each change:

```text
http://<NEW-PUBLIC-IP>:8080/github-webhook/
```

### Useful Jenkins plugins

- Docker Pipeline
- Pipeline: Stage View
- HTML Publisher
- JUnit
- GitHub (for push webhooks)
- Mailer (email notifications)

### Email (Mailer)

Configure **Manage Jenkins → System → E-mail Notification**:

- SMTP: `smtp.gmail.com`, port `587`, TLS
- SMTP auth with Gmail **App Password**
- System Admin e-mail: `Your Name <you@gmail.com>`
- Default user e-mail suffix: `@gmail.com`

## Jenkins CI/CD Pipeline (`JenkinsFile`)

Declarative Pipeline with `agent none` at the top. Each stage picks its own agent.

### Pipeline flow

```text
GitHub push (main)
       │
       ▼
┌──────────────┐    maven:3.9-eclipse-temurin-21-alpine
│ Build stage  │──► mvn clean package -DskipTests
└──────┬───────┘    stash JAR → app-jar
       │
       ▼
┌──────────────┐    same Maven Docker image
│ Test stage   │──► mvn test + surefire-report:report-only
└──────┬───────┘    junit + publishHTML + archive + stash email summary
       │
       ▼
┌──────────────┐    agent any (Jenkins host / DinD)
│ Deploy stage │──► free port 8085 → docker run Hurrayworld-app
└──────┬───────┘    health check: curl http://docker:8085/
       │
       ▼
┌──────────────┐    node('built-in')
│ Post always  │──► email HTML summary + links to reports
└──────────────┘
```

### Stage details

#### 1. Build stage

| Item | Detail |
|---|---|
| Agent | Docker `maven:3.9-eclipse-temurin-21-alpine` (`reuseNode true`) |
| Command | `mvn clean package -DskipTests` |
| Maven home fix | `export HOME="${WORKSPACE}"` so `.m2` is writable |
| Output | `target/Hurrayworldwithjava-1.0.0.jar` |
| Stash | `app-jar` (JAR passed to Deploy) |

#### 2. Test stage

| Item | Detail |
|---|---|
| Agent | Docker `maven:3.9-eclipse-temurin-21-alpine` (`reuseNode true`) |
| Commands | `mvn test` then `mvn surefire-report:report-only` |
| Framework | JUnit 5 (`@TestMethodOrder` / `@Order`) |
| JUnit publish | `target/surefire-reports/*.xml` → Jenkins **Test Result** |
| HTML report | `target/surefire-html/surefire-report.html` → **Surefire HTML Report** (HTML Publisher) |
| Email summary | `reports/email-report.html` (plain summary, no broken images in mail) |
| Artifacts | Surefire HTML folder + XML + email summary archived on the build |
| Stash | `test-reports` for the email post action |

#### 3. Deploy stage

| Item | Detail |
|---|---|
| Agent | `agent any` (needs Docker CLI against DinD) |
| Checkout | `skipDefaultCheckout(true)` — uses stashed JAR only |
| Unstash | `app-jar` |
| Port cleanup | Removes any container publishing **8085** (`docker ps --filter publish=8085`) |
| Run | `docker run -d --name Hurrayworld-app -p 8085:8085` with `eclipse-temurin:21-jre-alpine` |
| Health check | `curl -f --max-time 10 http://docker:8085/` |
| Browser URL | `http://<EC2-PUBLIC-IP>:8085/` |

#### 4. Post actions (always)

| Item | Detail |
|---|---|
| Node | `node('built-in')` (required because top-level `agent none`) |
| Unstash | `test-reports` |
| Email | Jenkins **Mailer** `mail` step (HTML body) |
| Links in email | Build URL, Test Result, Surefire HTML Report |

### Why `agent none` + per-stage agents?

- Build/Test need the **Maven Docker image**
- Deploy needs the **Jenkins/DinD Docker socket** (`docker run`)
- Mixing a global Docker agent with `agent any` caused shell launch failures; per-stage agents avoid that

### Create the Pipeline job

1. New Item → **Pipeline**
2. Pipeline → **Pipeline script from SCM** → Git  
   - Repository URL: your GitHub repo  
   - Branch: `*/main`  
   - Script path: `JenkinsFile`
3. Build Triggers → enable **GitHub hook trigger for GITScm polling**
4. Save

### Auto-build on push to `main`

1. Enable **GitHub hook trigger** on the job
2. GitHub repo → Settings → Webhooks → Add webhook:
   - **Payload URL:** `http://<EC2-PUBLIC-IP>:8080/github-webhook/`
   - **Content type:** `application/json`
   - **Events:** Just the **push** event
3. Push to `main` → Jenkins job starts automatically

### Where to see results in Jenkins

| Result | Where |
|---|---|
| Stage view | Job → Stage View / Pipeline steps |
| Unit tests | Build → **Test Result** |
| Styled Surefire report | Build → **Surefire HTML Report** |
| JAR / report files | Build → **Artifacts** |
| Live app | `http://<EC2-PUBLIC-IP>:8085/` |
| Email | Inbox after every build (success or failure) |

### Verify deploy

```text
http://<EC2-PUBLIC-IP>:8085/
```

If the browser cannot reach the app, confirm:

1. Security group allows **8085**
2. `jenkins-docker` has ports `"8085:8085"` in `docker-compose.yml`
3. Container is running: `docker exec -it my-jenkins docker ps`

### Free port 8085 manually (if needed)

```bash
docker exec -it my-jenkins docker ps --filter publish=8085
docker exec -it my-jenkins docker rm -f $(docker exec -it my-jenkins docker ps -aq --filter publish=8085)
```

The Deploy stage already removes any container publishing **8085** before starting a new one.

## Docker images used

| Image | Used for |
|---|---|
| `maven:3.9-eclipse-temurin-21-alpine` | Build & Test (JDK + Maven) |
| `eclipse-temurin:21-jre-alpine` | Run the JAR in Deploy |
| `docker:dind` | Docker-in-Docker for Jenkins |
| `my-jenkins` (local build) | Jenkins controller |

## Push to GitHub

```bash
git add .
git commit -m "Update app and Jenkins pipeline"
git push origin main
```

With the webhook configured, Jenkins builds automatically on push to `main`.
