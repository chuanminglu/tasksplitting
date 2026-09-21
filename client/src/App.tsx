import { FormEvent, useEffect, useState } from 'react';

type Todo = { id: number; title: string; completed: boolean };

export default function App() {
  const [todos, setTodos] = useState<Todo[]>([]);
  const [title, setTitle] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch('/api/todos')
      .then((response) => response.json() as Promise<Todo[]>)
      .then(setTodos)
      .finally(() => setLoading(false));
  }, []);

  async function addTodo(event: FormEvent) {
    event.preventDefault();
    if (!title.trim()) return;
    const response = await fetch('/api/todos', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title }),
    });
    if (response.ok) {
      const todo = (await response.json()) as Todo;
      setTodos((current) => [todo, ...current]);
      setTitle('');
    }
  }

  return (
    <main className="shell">
      <p className="eyebrow">TASK SPLITTING</p>
      <h1>开发环境已就绪</h1>
      <p className="intro">React 前端正在通过 Vite 代理连接 Express + Prisma API。</p>
      <form onSubmit={addTodo} className="todo-form">
        <input value={title} onChange={(event) => setTitle(event.target.value)} placeholder="添加一个任务" />
        <button type="submit">添加</button>
      </form>
      <section className="todo-list" aria-live="polite">
        {loading ? <p className="muted">正在加载...</p> : null}
        {!loading && todos.length === 0 ? <p className="muted">还没有任务，添加第一项吧。</p> : null}
        {todos.map((todo) => <div className="todo" key={todo.id}><span>{todo.title}</span><small>待处理</small></div>)}
      </section>
    </main>
  );
}
