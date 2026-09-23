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

/**
 * Base URL for backend API calls. When left empty (default), requests use
 * relative paths and are routed through the Vite dev-server proxy to port
 * 4000; set VITE_API_BASE_URL to a full origin (e.g. https://api.example.com)
 * to point the frontend at a different deployment.
 */
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '';

/**
 * T00301: neutral default avatar shown when the user has not uploaded one.
 * A small inline SVG data-URI keeps the placeholder dependency-free.
 */
const avatarPlaceholder =
  'data:image/svg+xml;utf8,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">' +
      '<circle cx="32" cy="32" r="32" fill="#cdd6d1"/>' +
      '<circle cx="32" cy="26" r="11" fill="#8a9a92"/>' +
      '<path d="M12 54c3-11 12-16 20-16s17 5 20 16z" fill="#8a9a92"/>' +
    '</svg>',
  );

function readStoredTheme(): Theme {
  return window.localStorage.getItem(THEME_STORAGE_KEY) === 'dark' ? 'dark' : 'light';
}

type View = 'workboard' | 'profile';

export default function App() {
  const [token, setToken] = useState<string | null>(null);
  const [theme, setTheme] = useState<Theme>(readStoredTheme);
  // T00301: top-level avatar state so the workboard (T00302) can reflect the
  // freshly-uploaded URL without any extra API call.
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null);
  const [view, setView] = useState<View>('workboard');

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
      ) : view === 'profile' ? (
        <ProfileView
          token={token}
          avatarUrl={avatarUrl}
          onAvatarUploaded={setAvatarUrl}
          onBack={() => setView('workboard')}
        />
      ) : (
        <TodoBoard
          token={token}
          onLogout={() => setToken(null)}
          avatarUrl={avatarUrl}
          onOpenProfile={() => setView('profile')}
        />
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
      const response = await fetch(`${API_BASE}/api/auth/login`, {
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

type ProfileViewProps = {
  token: string;
  avatarUrl: string | null;
  onAvatarUploaded: (url: string) => void;
  onBack: () => void;
};

function ProfileView({ token, avatarUrl, onAvatarUploaded, onBack }: ProfileViewProps) {
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function upload() {
    if (!file || uploading) return;
    setUploading(true);
    setError(null);
    try {
      const form = new FormData();
      form.append('file', file);
      const response = await fetch(`${API_BASE}/api/avatars`, {
        method: 'POST',
        // Do NOT set Content-Type manually: the browser sets the multipart
        // boundary automatically.
        headers: { Authorization: `Bearer ${token}` },
        body: form,
      });
      if (response.status === 401) {
        onBack();
        return;
      }
      if (response.ok) {
        const data = (await response.json()) as { url: string };
        onAvatarUploaded(data.url);
        setFile(null);
        onBack();
      } else {
        const message = (await response.json().catch(() => null)) as { message?: string } | null;
        setError(message?.message ?? `上传失败（${response.status}）`);
      }
    } catch {
      setError('网络错误，请重试');
    } finally {
      setUploading(false);
    }
  }

  return (
    <main className="shell">
      <p className="eyebrow">TASK SPLITTING</p>
      <h1>个人中心</h1>
      <p className="intro">上传或更新你的头像。</p>
      <div className="profile-view">
        <img
          className="avatar-preview"
          src={avatarUrl ?? avatarPlaceholder}
          alt="当前头像"
        />
        <div className="profile-upload-row">
          <input
            type="file"
            aria-label="选择头像文件"
            accept="image/jpeg,image/png,image/gif,image/webp"
            onChange={(event) => setFile(event.target.files?.[0] ?? null)}
          />
          <button type="button" onClick={upload} disabled={!file || uploading}>
            {uploading ? '上传中…' : '上传头像'}
          </button>
        </div>
        <button type="button" className="profile-back" onClick={onBack}>返回工作台</button>
      </div>
      {error ? <p className="muted" role="alert">{error}</p> : null}
    </main>
  );
}

function TodoBoard({
  token,
  onLogout,
  avatarUrl,
  onOpenProfile,
}: {
  token: string;
  onLogout: () => void;
  avatarUrl: string | null;
  onOpenProfile: () => void;
}) {
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
    fetch(`${API_BASE}/api/todos`, { headers: authHeaders })
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
    const response = await fetch(`${API_BASE}/api/todos`, {
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
      <div className="workboard-actions">
        <button type="button" className="open-profile" onClick={onOpenProfile}>
          个人中心
        </button>
        <img
          className="workboard-avatar"
          src={avatarUrl ?? avatarPlaceholder}
          alt="我的头像"
        />
      </div>
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
