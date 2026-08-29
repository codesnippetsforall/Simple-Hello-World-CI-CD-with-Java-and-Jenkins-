# Hurray World with Java — Jenkins CI/CD

Simple **Java 21** Maven HTTP app with **REST API** endpoints, a **Jenkins Pipeline** (build, OWASP dependency scan, test, manual approval, deploy), Surefire reports, and email notifications.

Repo: [Simple-Hello-World-CI-CD-with-Java-and-Jenkins-](https://github.com/codesnippetsforall/Simple-Hello-World-CI-CD-with-Java-and-Jenkins-)

**CI/CD in this repo:** **Jenkins only** — pipeline is defined in `JenkinsFile`. There is **no** `.github/workflows/` (no GitHub Actions YAML). GitHub is used as the **source repository** and **webhook trigger** for Jenkins on push to `main`.

## What the app does

- Runs an embedded HTTP server on port **8085** (plain Java `HttpServer`, no Spring)
- Serves a browser-friendly greeting and JSON **REST API** endpoints
- Built as an executable JAR: `target/Hurrayworldwithjava-1.0.0.jar`

### REST API endpoints

| Method | URL | Response |
|---|---|---|
| `GET` | `/` | Plain text — `Hurray, World it is full of surprises !` |
| `GET` | `/api/health` | JSON — `{"status":"UP","service":"Hurrayworldwithjava"}` |
| `GET` | `/api/greet` | JSON — default greet message |
| `GET` | `/api/greet?name=Java` | JSON — `{"message":"Hurray, Java!"}` |

**Try locally:**

```bash
curl http://localhost:8085/api/health
curl http://localhost:8085/api/greet?name=Java
```

## Project layout

| Path | Purpose |
|---|---|
| `src/main/java/com/example/App.java` | HTTP server, REST API, `greet()` |
| `src/test/java/com/example/AppTest.java` | JUnit 5 tests (`@Order`) for `/`, `/api/health`, `/api/greet` |
| `pom.xml` | Maven, Java 21, Surefire, **OWASP Dependency-Check** |
| `JenkinsFile` | Jenkins Declarative Pipeline (sole CI/CD config) |
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

### OWASP Dependency Scan (local)

Scans Maven dependencies for known CVEs (CVSS). Requires a free **NVD API key** — not related to the app REST API.

**1. Get key:** https://nvd.nist.gov/developers/request-an-api-key

**2. Run (PowerShell):**

```powershell
$env:NVD_API_KEY="your-nvd-api-key"
mvn -B dependency-check:check
```

Or:

```powershell
mvn -B org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7 -Dnvd.api.key=your-nvd-api-key
```

- **Report:** `target/dependency-check-report.html`
- **Fail threshold:** CVSS ≥ **7** (configured in `pom.xml`)
- **First run:** 5–15+ minutes (downloads ~380k+ NVD records)
- **Disk:** ~300 MB – 1 GB for the local NVD cache (under `.m2/repository/org/owasp/dependency-check-data/`)

Do **not** commit the NVD API key to Git.

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

Declarative Pipeline with `agent none` at the top. Each stage picks its own agent. Shared values live in `environment {}`; secrets use Jenkins **Credentials** via `credentials()`.

### Environment variables

Defined at the top of `JenkinsFile`:

| Variable | Value / source | Used for |
|---|---|---|
| `MAVEN_IMAGE` | `maven:3.9-eclipse-temurin-21-alpine` | Build & Test Docker agent |
| `RUNTIME_IMAGE` | `eclipse-temurin:21-jre-alpine` | Deploy runtime image |
| `APP_NAME` | `Hurrayworld-app` | Deployed container name |
| `APP_JAR` | `Hurrayworldwithjava-1.0.0.jar` | Built artifact name |
| `APP_PORT` | `8085` | App / publish port |
| `DOCKER_HOST_ALIAS` | `docker` | DinD hostname for health check |
| `STASH_APP_JAR` | `app-jar` | Stash name for the JAR |
| `STASH_TEST_REPORTS` | `test-reports` | Stash name for email summary |
| `EMAIL_TO` | `credentials('REPORT_TO_ADDRESS_EMAIL_ID')` | Email recipient (secret) |
| `NVD_API_KEY` | `credentials('NVD_API_KEY')` | NVD API key for OWASP scan (secret) |
| `REPORT_NAME` | `Surefire HTML Report` | HTML Publisher report title |

Change images, port, or app names in one place — the `environment {}` block.

### Secrets with `credentials()`

Do **not** hardcode the report email in Git. Store it in Jenkins and bind it in the pipeline:

```groovy
EMAIL_TO = credentials('REPORT_TO_ADDRESS_EMAIL_ID')
```

**Create the credential in Jenkins**

1. **Manage Jenkins → Credentials → System → Global credentials → Add Credentials**
2. Kind: **Secret text**
3. Secret: your email address (e.g. `you@gmail.com`)
4. ID: `REPORT_TO_ADDRESS_EMAIL_ID` (must match `JenkinsFile`)
5. Save

The Mailer `mail` step uses `to: env.EMAIL_TO`. The real address never appears in the repository.

**NVD API key (OWASP Dependency Scan)**

```groovy
NVD_API_KEY = credentials('NVD_API_KEY')
```

1. **Manage Jenkins → Credentials → Add → Secret text**
2. ID: `NVD_API_KEY`
3. Secret: your NVD API key from https://nvd.nist.gov/developers/request-an-api-key
4. Save

Used by the **Dependency Scan** stage and `pom.xml` (`<nvdApiKey>${env.NVD_API_KEY}</nvdApiKey>`).

### Pipeline flow

```text
GitHub push (main)
       │
       ▼
┌──────────────┐    ${MAVEN_IMAGE}
│ Build stage  │──► mvn clean package -DskipTests
└──────┬───────┘    stash JAR → ${STASH_APP_JAR}
       │
       ▼
┌──────────────────┐    ${MAVEN_IMAGE}
│ Dependency Scan  │──► OWASP dependency-check (CVSS ≥ 7 fails build)
└──────┬───────────┘    uses ${NVD_API_KEY}
       │
       ▼
┌──────────────┐    ${MAVEN_IMAGE}
│ Test stage   │──► mvn test + surefire-report:report-only
└──────┬───────┘    junit + publishHTML + archive + stash email summary
       │
       ▼
┌──────────────────────────┐
│ Approval to Deploy stage │──► input (manual Yes) · timeout 1 minute
└────────────┬─────────────┘
             │ approved
             ▼
┌──────────────┐    agent any (Jenkins host / DinD)
│ Deploy stage │──► free ${APP_PORT} → docker run ${APP_NAME}
└──────┬───────┘    curl / and /api/health
       │
       ▼
┌──────────────┐    node('built-in')
│ Post always  │──► email to ${EMAIL_TO} + HTML summary + report links
└──────────────┘
```

### Stage details

#### 1. Build stage

| Item | Detail |
|---|---|
| Agent | Docker `${MAVEN_IMAGE}` (`reuseNode true`) |
| Command | `mvn clean package -DskipTests` |
| Maven home fix | `export HOME="${WORKSPACE}"` so `.m2` is writable |
| Output | `target/${APP_JAR}` |
| Stash | `${STASH_APP_JAR}` (JAR passed to Deploy) |

#### 2. Dependency Scan stage

OWASP Dependency-Check scans **Maven dependencies** (e.g. JUnit) for known CVEs.

| Item | Detail |
|---|---|
| Agent | Docker `${MAVEN_IMAGE}` (`reuseNode true`) |
| Command | `mvn -B org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7` |
| Maven home fix | `export HOME="${WORKSPACE}"` |
| NVD key | `${NVD_API_KEY}` from Jenkins credentials (required) |
| Config | `pom.xml` — plugin `dependency-check-maven` 13.0.0, `failBuildOnCVSS=7` |
| Report | `target/dependency-check-report.html` |
| Fail | Build fails if any dependency has CVSS ≥ **7** |
| Duration | First run: 5–15+ min (NVD DB download); later runs faster if cache reused |
| Disk | ~300 MB – 1 GB NVD cache under workspace `.m2` |

**Note:** Adding REST API endpoints does not change OWASP results — it scans library JARs, not your HTTP routes.

#### 3. Test stage

| Item | Detail |
|---|---|
| Agent | Docker `${MAVEN_IMAGE}` (`reuseNode true`) |
| Commands | `mvn test` then `mvn surefire-report:report-only` |
| Framework | JUnit 5 (`@TestMethodOrder` / `@Order`) |
| JUnit publish | `target/surefire-reports/*.xml` → Jenkins **Test Result** |
| HTML report | `target/surefire-html/surefire-report.html` → **`${REPORT_NAME}`** (HTML Publisher) |
| Email summary | `reports/email-report.html` (plain summary, no broken images in mail) |
| Artifacts | Surefire HTML folder + XML + email summary archived on the build |
| Stash | `${STASH_TEST_REPORTS}` for the email post action |

#### 4. Approval to Deploy stage

Manual gate before production-like deploy.

| Item | Detail |
|---|---|
| Agent | `agent any` |
| Step | `input message: 'Approve to deploy?'` |
| OK button | `Yes, Proceed to Deploy stage !` |
| Timeout | **1 minute** — if no one approves, the stage (and deploy) fails/aborts |
| Effect | Deploy runs only after approval |

In the Jenkins UI, open the running build and click **Yes, Proceed to Deploy stage !** when prompted.

#### 5. Deploy stage

| Item | Detail |
|---|---|
| Agent | `agent any` (needs Docker CLI against DinD) |
| Checkout | `skipDefaultCheckout(true)` — uses stashed JAR only |
| Unstash | `${STASH_APP_JAR}` |
| Port cleanup | Removes any container publishing `${APP_PORT}` |
| Run | `docker run -d --name ${APP_NAME} -p ${APP_PORT}:${APP_PORT}` with `${RUNTIME_IMAGE}` |
| Health check | `curl /` and `curl /api/health` on `http://${DOCKER_HOST_ALIAS}:${APP_PORT}/` |
| Browser URL | `http://<EC2-PUBLIC-IP>:${APP_PORT}/` |
| API URL | `http://<EC2-PUBLIC-IP>:${APP_PORT}/api/health` |

#### 6. Post actions (always)

| Item | Detail |
|---|---|
| Node | `node('built-in')` (required because top-level `agent none`) |
| Unstash | `${STASH_TEST_REPORTS}` |
| Email | Jenkins **Mailer** `mail` step → `to: env.EMAIL_TO` (from credentials) |
| Links in email | Build URL, Test Result, Surefire HTML Report |

### Why `agent none` + per-stage agents?

- Build/Test need the **Maven Docker image**
- Deploy needs the **Jenkins/DinD Docker socket** (`docker run`)
- Approval waits for a human without holding a Maven container
- Mixing a global Docker agent with `agent any` caused shell launch failures; per-stage agents avoid that

### `reuseNode true` — same workspace across stages

In Build and Test:

```groovy
agent {
    docker {
        image "${MAVEN_IMAGE}"
        reuseNode true
    }
}
```

**What `reuseNode true` does**

- Runs the Docker agent on the **same Jenkins node** and **same workspace** as the job checkout
- Source code, `target/`, and `.m2` stay in one place (e.g. `.../learn-jenkins-with-hello-world`)

**Without `reuseNode true`**

- Each stage can get a **new workspace**, often named with a suffix:
  - `learn-jenkins-with-hello-world`
  - `learn-jenkins-with-hello-world@2`
  - `learn-jenkins-with-hello-world@3`
- That causes problems in a multi-stage build:
  - Build writes the JAR in workspace A, Test/Deploy look in workspace B → **file not found**
  - Maven cache / compiled classes are not shared → **slower, repeated downloads**
  - You must **stash/unstash** (or archive) everything between stages
  - Harder to debug (logs and files split across folders)

**In this pipeline**

| Stage | `reuseNode` | Why |
|---|---|---|
| Build / Dependency Scan / Test | `true` | Share checkout + `target/` + `.m2` (incl. NVD cache) |
| Approval / Deploy | `agent any` (no Docker agent) | Deploy still uses **stash** (`app-jar`) because it is a different agent type |

Use **`reuseNode true`** whenever a stage Docker agent should keep working in the job’s main workspace instead of creating another `@2` / `@3` copy.

### Create the Pipeline job

1. New Item → **Pipeline**
2. Pipeline → **Pipeline script from SCM** → Git  
   - Repository URL: your GitHub repo  
   - Branch: `*/main`  
   - Script path: `JenkinsFile`
3. Build Triggers → enable **GitHub hook trigger for GITScm polling**
4. Add credentials: `REPORT_TO_ADDRESS_EMAIL_ID` and `NVD_API_KEY` (see Secrets above)
5. Save

### Auto-build on push to `main`

1. Enable **GitHub hook trigger** on the job
2. GitHub repo → Settings → Webhooks → Add webhook:
   - **Payload URL:** `http://<EC2-PUBLIC-IP>:8080/github-webhook/`
   - **Content type:** `application/json`
   - **Events:** Just the **push** event
3. Push to `main` → Jenkins job starts automatically
4. After Test succeeds, **approve** the deploy within 1 minute

### Where to see results in Jenkins

| Result | Where |
|---|---|
| Stage view | Job → Stage View / Pipeline steps |
| Approval prompt | Running build → **input** / Proceed |
| OWASP report | Build log + `target/dependency-check-report.html` |
| Unit tests | Build → **Test Result** |
| Styled Surefire report | Build → **Surefire HTML Report** |
| JAR / report files | Build → **Artifacts** |
| Live app | `http://<EC2-PUBLIC-IP>:8085/` |
| API health | `http://<EC2-PUBLIC-IP>:8085/api/health` |
| Email | Inbox after every build (success or failure) |

### Verify deploy

```text
http://<EC2-PUBLIC-IP>:8085/
http://<EC2-PUBLIC-IP>:8085/api/health
http://<EC2-PUBLIC-IP>:8085/api/greet?name=Java
```

If the browser cannot reach the app, confirm:

1. Security group allows **8085**
2. `jenkins-docker` has ports `"8085:8085"` in `docker-compose.yml`
3. Container is running: `docker exec -it my-jenkins docker ps`
4. Deploy was **approved** (Approval stage did not time out)

### Free port 8085 manually (if needed)

```bash
docker exec -it my-jenkins docker ps --filter publish=8085
docker exec -it my-jenkins docker rm -f $(docker exec -it my-jenkins docker ps -aq --filter publish=8085)
```

The Deploy stage already removes any container publishing **8085** before starting a new one.

## Docker images used

| Image | Used for |
|---|---|
| `maven:3.9-eclipse-temurin-21-alpine` | Build & Test (JDK + Maven) — `${MAVEN_IMAGE}` |
| `eclipse-temurin:21-jre-alpine` | Run the JAR in Deploy — `${RUNTIME_IMAGE}` |
| `docker:dind` | Docker-in-Docker for Jenkins |
| `my-jenkins` (local build) | Jenkins controller |

## Push to GitHub

GitHub stores the code; **Jenkins** (not GitHub Actions) runs the pipeline when you push.

```bash
git add .
git commit -m "Update app and Jenkins pipeline"
git push origin main
```

With the webhook configured, Jenkins builds automatically on push to `main`. Approve Deploy when prompted.
