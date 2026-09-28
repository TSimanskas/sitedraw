# SiteDraw

The application lives in this folder. The [repository README](../../README.md) is the starting point for GitHub.

Construction document platform for project teams. Directors, project managers, and site workers can manage sites, upload PDF drawings, mark them up, and keep an audit trail.

## Stack

- Backend: Java 17, Spring Boot 4, Spring Security + JWT, JPA, Flyway
- Frontend: React 19, TypeScript, Vite, React Query, PDF.js, Fabric.js
- Data: PostgreSQL 16 (Docker) or H2 (local `dev` profile)
- Files: local folder in `dev`, MinIO in `docker` profile

## Run locally

From `Backend/platform`, starting the API also starts the Vite frontend in `dev` profile.

### IntelliJ

1. Open `Backend/platform` as the project.
2. Run `PlatformApplication`.

Backend: http://localhost:8080  
Frontend: http://localhost:5173

If Node.js is not on PATH, install Node and restart IntelliJ.

### Command line

```bash
./mvnw spring-boot:run
```

Or start them separately:

```bash
./mvnw spring-boot:run -Dapp.frontend.dev-server.enabled=false
cd frontend
npm install
npm run dev
```

Demo login (local `dev` profile only): `director@construction.local` / `Director123!`

Set `APP_JWT_SECRET` to a long random value before deploying. The H2 console is only open on the `dev` profile.

## Docker infrastructure

```bash
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=docker
```

This uses PostgreSQL on port 5432 and MinIO on 9000 (console 9001).

## Tests

```bash
./mvnw test
cd frontend && npm test
```

## What it covers

- Login, password change, director-managed users (edit / deactivate / reset password)
- Projects with role-based access and search
- PDF upload, new versions, delete, and in-browser viewing
- Drawing annotations, scale calibration, revision history, and audit events
