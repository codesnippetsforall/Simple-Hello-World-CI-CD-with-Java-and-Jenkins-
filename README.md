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

Initial admin password:

```bash
docker exec -it my-jenkins cat /var/jenkins_home/secrets/initialAdminPassword
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

## Jenkins Pipeline (`JenkinsFile`)

| Stage | What it does |
|---|---|
| **Build** | `mvn clean package -DskipTests` in `maven:3.9-eclipse-temurin-21-alpine` |
| **Test** | `mvn test`, Surefire HTML, JUnit publish, HTML Publisher, stash email summary |
| **Deploy** | Free port **8085** if in use, run `Hurrayworld-app` container, health-check via `http://docker:8085/` |
| **Post** | Email build result + test summary (HTML) |

### Create the job

1. New Item → **Pipeline**
2. Pipeline → **Pipeline script from SCM** → Git
3. Branch: `*/main`
4. Script path: `JenkinsFile`
5. (Optional) Build Triggers → **GitHub hook trigger for GITScm polling**

### Auto-build on push to `main`

1. Enable **GitHub hook trigger** on the job
2. GitHub repo → Settings → Webhooks:
   - URL: `http://<EC2-PUBLIC-IP>:8080/github-webhook/`
   - Content type: `application/json`
   - Event: **push**

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
| `eclipse-temurin:21-jre-alpine` | Run the JAR |
| `docker:dind` | Docker-in-Docker for Jenkins |
| `my-jenkins` (local build) | Jenkins controller |

## Push to GitHub

```bash
git add .
git commit -m "Update app and Jenkins pipeline"
git push origin main
```

With the webhook configured, Jenkins builds automatically on push to `main`.
