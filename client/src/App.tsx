import { FormEvent, useEffect, useState } from 'react';

type Todo = { id: number; title: string; completed: boolean };

export default function App() {
  const [token, setToken] = useState<string | null>(null);

  if (!token) return <LoginForm onLogin={setToken} />;
  return <TodoBoard token={token} onLogout={() => setToken(null)} />;
}

function LoginForm({ onLogin }: { onLogin: (token: string) => void }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password, rememberMe }),
      });
      if (response.ok) {
        const data = (await response.json()) as { token: string };
        onLogin(data.token);
      } else {
        const data = (await response.json().catch(() => null)) as { error?: { code?: string } } | null;
        const messages: Record<string, string> = {
          USER_NOT_FOUND: '账号不存在',
          INVALID_PASSWORD: '密码错误',
        };
        setError(data?.error?.code ? messages[data.error.code] ?? `登录失败（${response.status}）` : `登录失败（${response.status}）`);
      }
    } catch {
      setError('网络错误，请重试');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="shell">
      <p className="eyebrow">TASK SPLITTING</p>
      <h1>登录</h1>
      <p className="intro">输入用户名和密码，进入工作台。</p>
      <form onSubmit={submit} className="login-form">
        <input value={username} onChange={(event) => setUsername(event.target.value)} placeholder="用户名" autoComplete="username" />
        <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="密码" autoComplete="current-password" />
        <label><input type="checkbox" checked={rememberMe} onChange={(event) => setRememberMe(event.target.checked)} /> 记住我</label>
        <button type="submit" disabled={submitting}>{submitting ? '登录中…' : '登录'}</button>
      </form>
      {error ? <p className="muted" role="alert">{error}</p> : null}
    </main>
  );
}

function TodoBoard({ token, onLogout }: { token: string; onLogout: () => void }) {
  const [todos, setTodos] = useState<Todo[]>([]);
  const [title, setTitle] = useState('');
  const [loading, setLoading] = useState(true);

  const authHeaders = { Authorization: `Bearer ${token}` };

  useEffect(() => {
    fetch('/api/todos', { headers: authHeaders })
      .then((response) => {
        if (response.status === 401) { onLogout(); return [] as Todo[]; }
        return response.json() as Promise<Todo[]>;
      })
      .then((data) => { if (Array.isArray(data)) setTodos(data); })
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  async function addTodo(event: FormEvent) {
    event.preventDefault();
    if (!title.trim()) return;
    const response = await fetch('/api/todos', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders },
      body: JSON.stringify({ title }),
    });
    if (response.status === 401) { onLogout(); return; }
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
      <button type="button" className="logout-button" onClick={onLogout}>退出登录</button>
      <section className="todo-list" aria-live="polite">
        {loading ? <p className="muted">正在加载...</p> : null}
        {!loading && todos.length === 0 ? <p className="muted">还没有任务，添加第一项吧。</p> : null}
        {todos.map((todo) => <div className="todo" key={todo.id}><span>{todo.title}</span><small>待处理</small></div>)}
      </section>
    </main>
  );
}
