import { FormEvent, useEffect, useState } from 'react';

type Todo = { id: number; title: string; completed: boolean; createdAt: string };

type TodosFilter = 'all' | 'active' | 'completed';

export function formatRelativeTime(createdAt: string, now: Date = new Date()): string {
  const created = new Date(createdAt);
  if (Number.isNaN(created.getTime())) {
    return createdAt;
  }
  const diffMinutes = Math.max(0, Math.floor((now.getTime() - created.getTime()) / 60000));
  if (diffMinutes < 1) {
    return '刚刚';
  }
  if (diffMinutes < 60) {
    return `${diffMinutes}分钟前`;
  }
  if (diffMinutes < 24 * 60) {
    const hours = Math.floor(diffMinutes / 60);
    return `${hours}小时前`;
  }
  const year = created.getFullYear();
  const month = String(created.getMonth() + 1).padStart(2, '0');
  const day = String(created.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

const TODOS_FILTERS: Array<{ key: TodosFilter; label: string }> = [
  { key: 'all', label: '全部' },
  { key: 'active', label: '未完成' },
  { key: 'completed', label: '已完成' },
];

type Theme = 'light' | 'dark';

const THEME_STORAGE_KEY = 'tasksplitting-theme';
const REMEMBERED_USERNAME_KEY = 'tasksplitting-remembered-username';

function readStoredTheme(): Theme {
  return window.localStorage.getItem(THEME_STORAGE_KEY) === 'dark' ? 'dark' : 'light';
}

export default function App() {
  const [token, setToken] = useState<string | null>(null);
  const [theme, setTheme] = useState<Theme>(readStoredTheme);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    window.localStorage.setItem(THEME_STORAGE_KEY, theme);
  }, [theme]);

  return (
    <>
      <button
        type="button"
        className="theme-toggle"
        aria-label="切换主题"
        onClick={() => setTheme((current) => (current === 'light' ? 'dark' : 'light'))}
      >
        {theme === 'light' ? '深色' : '浅色'}
      </button>
      {!token ? (
        <LoginForm onLogin={setToken} />
      ) : (
        <TodoBoard token={token} onLogout={() => setToken(null)} />
      )}
    </>
  );
}

function LoginForm({ onLogin }: { onLogin: (token: string) => void }) {
  const [username, setUsername] = useState(() => window.localStorage.getItem(REMEMBERED_USERNAME_KEY) ?? '');
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
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
        if (username) {
          window.localStorage.setItem(REMEMBERED_USERNAME_KEY, username);
        }
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
        <input value={username} onChange={(event) => setUsername(event.target.value)} placeholder="用户名" autoComplete="username" disabled={submitting} />
        <div className="password-field">
          <input type={showPassword ? 'text' : 'password'} value={password} onChange={(event) => setPassword(event.target.value)} placeholder="密码" autoComplete="current-password" disabled={submitting} />
          <button type="button" className="password-toggle" onClick={() => setShowPassword((current) => !current)}>{showPassword ? '隐藏' : '显示'}</button>
        </div>
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
  const [filter, setFilter] = useState<TodosFilter>('all');

  const visibleTodos = todos.filter((todo) => {
    if (filter === 'active') return todo.completed === false;
    if (filter === 'completed') return todo.completed === true;
    return true;
  });

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
      <button type="button" className="logout-button" onClick={() => { if (window.confirm('确定要退出登录吗？')) { onLogout(); } }}>退出登录</button>
      <div className="todo-filters" role="group" aria-label="按完成状态筛选">
        {TODOS_FILTERS.map(({ key, label }) => (
          <button key={key} type="button" className={`todo-filter${filter === key ? ' active' : ''}`} aria-pressed={filter === key} onClick={() => setFilter(key)}>
            {label}
          </button>
        ))}
      </div>
      <p className="todo-stats" aria-live="polite">共{todos.length}项，已完成{todos.filter((todo) => todo.completed).length}项</p>
      <section className="todo-list" aria-live="polite">
        {loading ? <p className="muted">正在加载...</p> : null}
        {!loading && visibleTodos.length === 0 ? <p className="muted">还没有任务，添加第一项吧。</p> : null}
        {visibleTodos.map((todo) => <div className="todo" key={todo.id}><span>{todo.title}</span><small>{formatRelativeTime(todo.createdAt)}</small></div>)}
      </section>
    </main>
  );
}
