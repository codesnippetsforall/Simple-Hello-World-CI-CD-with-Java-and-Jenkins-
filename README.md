# Hello World with Java — GitHub CI/CD

Simple Maven Java HTTP app that serves `Hello, World!` in the browser, with GitHub Actions CI that builds, tests, and uploads the JAR on every push/PR to `main`.

## Prerequisites

- Java 21+
- Maven 3.9+

## Run locally

```bash
mvn clean package
java -jar target/helloworldwithjava-1.0.0.jar
```

Then open `http://localhost:8085/` in a browser. Expected page text:

```text
Hello, World!
```

On EC2, use `http://<ec2-public-ip>:8085/` and open port `8085` in the security group. Optional port: `java -jar target/helloworldwithjava-1.0.0.jar 9090`.

Run tests only:

```bash
mvn test
```

## CI/CD (GitHub Actions)

Workflow: [`.github/workflows/ci.yml`](.github/workflows/ci.yml)

On push or pull request to `main` / `master` it will:

1. Check out the repo
2. Install JDK 21 (Temurin)
3. Run `mvn clean verify`
4. Upload the built JAR as a workflow artifact

## Push to GitHub

```bash
git init
git add .
git commit -m "Initial Hello World Java app with GitHub Actions CI"
git branch -M main
git remote add origin https://github.com/<your-username>/<your-repo>.git
git push -u origin main
```

Then open the **Actions** tab on GitHub to watch the pipeline run.
