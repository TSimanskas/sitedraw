# SiteDraw

Construction document platform for project teams. Directors, project managers, and site workers can manage sites, upload PDF drawings, mark them up, and keep an audit trail.

[![CI](https://github.com/TSimanskas/sitedraw/actions/workflows/ci.yml/badge.svg)](https://github.com/TSimanskas/sitedraw/actions/workflows/ci.yml)

![Sign-in screen](docs/screenshots/login.png)

## What it does

- Sign in with JWT, change password, and (as director) manage users
- Projects with role-based access and search
- PDF upload, versioning, delete, and in-browser viewing
- Drawing annotations, scale calibration, revision history, and audit events

![Project workspace](docs/screenshots/projects.png)

![Project overview](docs/screenshots/project.png)

## Stack

- **API:** Java 17, Spring Boot 4, Spring Security, JWT, JPA, Flyway
- **UI:** React 19, TypeScript (strict), Vite, React Query, PDF.js, Fabric.js
- **Data:** PostgreSQL 16 (Docker) or H2 (`dev` profile)
- **Files:** local folder in `dev`, MinIO in `docker` profile

## Layout

```
Backend/platform/            Spring Boot API
Backend/platform/frontend/   React app (started with the API in dev)
docs/                        Screenshots and a sample drawing PDF
```

## Run locally

From `Backend/platform`:

```bash
./mvnw spring-boot:run
```

- API: http://localhost:8080
- UI: http://localhost:5173

The `dev` profile starts the Vite frontend next to the API and uses a local H2 file in `Backend/platform/data/`. Render uses `prod` (Neon Postgres from env vars). Tests use a separate in-memory H2, so `./mvnw test` does not touch your IntelliJ database. Node.js must be on your PATH.

If Flyway reports a checksum mismatch locally, stop the app and delete `Backend/platform/data/platform-db*.db` — that only resets local data. Do not edit V1–V5 after they have been applied on Render; add a new `V6__…sql` instead.

### One JAR (UI inside the API)

Local `dev` still uses Vite on port 5173. For a single process (and later for Railway/Render), build the React app into the Spring jar:

```bash
cd Backend/platform
./mvnw -Pfrontend package -DskipTests
java -jar target/platform-0.0.1-SNAPSHOT.jar
```

Then open http://localhost:8080 — the UI and `/api` come from the same origin (`VITE_API_URL` stays empty). Maven downloads Node 22 if needed. `./mvnw test` does not bundle the UI, so CI stays fast.

### Docker infrastructure

```bash
cd Backend/platform
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=docker
```

PostgreSQL is on 5432, MinIO on 9000 (console 9001).

### Local demo user

The `dev` profile seeds one director account so you can sign in without creating a user first. It is documented here only, not on the login screen:

`director@construction.local` / `Director123!`

Set `APP_JWT_SECRET` to a long random value before deploying. The H2 console is only open on the `dev` profile.

## Tests

```bash
cd Backend/platform
./mvnw test
cd frontend && npm test
```

GitHub Actions runs both suites on every push.

## Built as

A full-stack portfolio project: layered Spring services, JWT auth, RBAC, PDF markup, Flyway migrations, Docker Compose, and CI.
