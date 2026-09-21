# Task Splitting

Node.js + Express + Prisma backend and React + TypeScript frontend development environment.

## Requirements

- Node.js 20+
- npm 10+

## Start development

```bash
npm install
npm run db:push
npm run dev
```

- Frontend: http://localhost:5173
- Backend: http://localhost:4000
- Health check: http://localhost:4000/api/health

The SQLite database is created at `server/prisma/dev.db` by `npm run db:push`.

## Useful commands

```bash
npm run typecheck
npm run build
npm run db:studio
```
