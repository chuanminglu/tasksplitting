# Task Splitting

Spring Boot + SQLite backend and React + TypeScript frontend development environment.

## Requirements

- Java 17+
- Maven 3.9+
- npm 10+

## Start development

```bash
npm install
npm run dev
```

- Frontend: http://localhost:5173
- Backend: http://localhost:4000
- Health check: http://localhost:4000/api/health

The backend port (default `4000`) can be overridden with the `SERVER_PORT` environment variable (Spring Boot native relaxed binding, no extra configuration needed):

```bash
SERVER_PORT=4001 mvn -f server/pom.xml spring-boot:run
```

The existing SQLite database remains at `server/prisma/dev.db` and is used directly by Spring Boot.

## Useful commands

```bash
npm run typecheck
npm run build
```
