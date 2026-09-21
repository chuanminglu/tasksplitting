import 'dotenv/config';
import cors from 'cors';
import express from 'express';
import { PrismaClient } from '@prisma/client';

const app = express();
const prisma = new PrismaClient();
const port = Number(process.env.PORT ?? 4000);

app.use(cors());
app.use(express.json());

app.get('/api/health', (_request, response) => {
  response.json({ status: 'ok', service: 'tasksplitting-api' });
});

app.get('/api/todos', async (_request, response, next) => {
  try {
    const todos = await prisma.todo.findMany({ orderBy: { createdAt: 'desc' } });
    response.json(todos);
  } catch (error) {
    next(error);
  }
});

app.post('/api/todos', async (request, response, next) => {
  try {
    const title = typeof request.body?.title === 'string' ? request.body.title.trim() : '';
    if (!title) {
      response.status(400).json({ message: 'title is required' });
      return;
    }
    const todo = await prisma.todo.create({ data: { title } });
    response.status(201).json(todo);
  } catch (error) {
    next(error);
  }
});

app.use((error: unknown, _request: express.Request, response: express.Response, _next: express.NextFunction) => {
  console.error(error);
  response.status(500).json({ message: 'Internal server error' });
});

const server = app.listen(port, () => {
  console.log(`API listening on http://localhost:${port}`);
});

const shutdown = async () => {
  server.close();
  await prisma.$disconnect();
};

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
