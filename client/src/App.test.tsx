import { act } from 'react';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest';
import App, { formatRelativeTime } from './App';

type TestTodo = { id: number; title: string; completed: boolean; createdAt: string };

afterEach(() => {
  cleanup();
  delete document.documentElement.dataset.theme;
  window.localStorage.clear();
  vi.restoreAllMocks();
});

async function renderBoard(todos: TestTodo[]) {
  const fetchMock = vi.fn(async (input: string | URL | Request) => {
    const url = String(input);
    const payload = url.includes('/api/auth/login') ? { token: 'test-token' } : todos;
    return new Response(JSON.stringify(payload), { status: 200, headers: { 'Content-Type': 'application/json' } });
  });
  vi.stubGlobal('fetch', fetchMock);
  const { container } = render(<App />);
  fireEvent.change(screen.getByPlaceholderText('用户名'), { target: { value: 'alice' } });
  fireEvent.change(screen.getByPlaceholderText('密码'), { target: { value: 'secret' } });
  await act(async () => {
    fireEvent.submit(screen.getByRole('button', { name: '登录' }));
  });
  return { container, fetchMock };
}

function todoTitles(container: HTMLElement): string[] {
  return Array.from(container.querySelectorAll('.todo span')).map((el) => el.textContent);
}

function todosFetchCalls(fetchMock: ReturnType<typeof vi.fn>): number {
  return fetchMock.mock.calls.filter((call) => String(call[0]).includes('/api/todos')).length;
}

describe('App', () => {
  it('shows the login form before login', () => {
    render(<App />);
    expect(screen.getByPlaceholderText('用户名')).toBeDefined();
    expect(screen.getByPlaceholderText('密码')).toBeDefined();
  });

  it('switches from the login form to the workboard after a successful login', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: string | URL | Request) => {
      const url = String(input);
      const payload = url.includes('/api/auth/login')
        ? { token: 'test-token' }
        : [];
      return new Response(JSON.stringify(payload), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }));

    const { container } = render(<App />);
    expect(container.querySelector('.todo-list')).toBeNull();

    fireEvent.change(screen.getByPlaceholderText('用户名'), { target: { value: 'alice' } });
    fireEvent.change(screen.getByPlaceholderText('密码'), { target: { value: 'secret' } });
    await act(async () => {
      fireEvent.submit(screen.getByRole('button', { name: '登录' }));
    });

    expect(container.querySelector('.todo-list')).not.toBeNull();
  });

  it('leaves remember me unchecked and sends true when selected', async () => {
    const fetchMock = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
      const payload = String(input).includes('/api/auth/login') ? { token: 'test-token' } : [];
      return new Response(JSON.stringify(payload), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<App />);
    const rememberMe = screen.getByLabelText('记住我') as HTMLInputElement;
    expect(rememberMe.checked).toBe(false);
    fireEvent.click(rememberMe);
    await act(async () => {
      fireEvent.submit(screen.getByRole('button', { name: '登录' }));
    });

    const request = fetchMock.mock.calls[0]?.[1] as RequestInit | undefined;
    expect(request).toBeDefined();
    expect(JSON.parse(String(request?.body)).rememberMe).toBe(true);
  });

  it.each([
    ['USER_NOT_FOUND', '账号不存在'],
    ['INVALID_PASSWORD', '密码错误'],
  ])('shows the right message for %s', async (code, message) => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(
      JSON.stringify({ error: { code, message: 'server message' } }),
      { status: 401, headers: { 'Content-Type': 'application/json' } },
    )));

    render(<App />);
    await act(async () => {
      fireEvent.submit(screen.getByRole('button', { name: '登录' }));
    });

    expect(screen.getByRole('alert').textContent).toBe(message);
  });
});

describe('TodoBoard completion-status filter (T-UI-01)', () => {
  const sampleTodos: TestTodo[] = [
    { id: 1, title: '写周报', completed: false, createdAt: '2026-01-01T10:00:00Z' },
    { id: 2, title: '评审 PR', completed: true, createdAt: '2026-01-01T09:00:00Z' },
    { id: 3, title: '回复消息', completed: false, createdAt: '2026-01-01T08:00:00Z' },
  ];

  it('shows all todos by default', async () => {
    const { container } = await renderBoard(sampleTodos);
    expect(todoTitles(container)).toEqual(['写周报', '评审 PR', '回复消息']);
    expect(screen.getByRole('button', { name: '全部' }).classList.contains('active')).toBe(true);
  });

  it('shows only incomplete todos when "未完成" is selected', async () => {
    const { container } = await renderBoard(sampleTodos);
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '未完成' }));
    });
    expect(todoTitles(container)).toEqual(['写周报', '回复消息']);
  });

  it('shows only completed todos when "已完成" is selected', async () => {
    const { container } = await renderBoard(sampleTodos);
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '已完成' }));
    });
    expect(todoTitles(container)).toEqual(['评审 PR']);
  });

  it('does not refetch /api/todos when switching filters', async () => {
    const { container, fetchMock } = await renderBoard(sampleTodos);
    const callsBefore = todosFetchCalls(fetchMock);
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '未完成' }));
    });
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '已完成' }));
    });
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '全部' }));
    });
    expect(todoTitles(container)).toEqual(['写周报', '评审 PR', '回复消息']);
    expect(todosFetchCalls(fetchMock)).toBe(callsBefore);
  });

  it('shows the empty state when the active filter leaves no visible todos', async () => {
    const { container } = await renderBoard([{ id: 1, title: '唯一已完成', completed: true, createdAt: '2026-01-01T10:00:00Z' }]);
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '未完成' }));
    });
    expect(container.querySelector('.todo')).toBeNull();
    expect(screen.getByText('还没有任务，添加第一项吧。')).toBeDefined();
  });
});

describe('TodoBoard relative time (T-UI-02)', () => {
  const now = new Date('2026-01-01T12:00:00Z');

  it('labels a just-created todo as 刚刚', async () => {
    const justCreated = new Date().toISOString();
    const { container } = await renderBoard([{ id: 1, title: '新任务', completed: false, createdAt: justCreated }]);
    expect(container.querySelector('.todo small')?.textContent).toBe('刚刚');
  });

  it('formats minutes/hours/date buckets correctly', () => {
    expect(formatRelativeTime('2026-01-01T11:55:00Z', now)).toBe('5分钟前');
    expect(formatRelativeTime('2026-01-01T09:00:00Z', now)).toBe('3小时前');
    expect(formatRelativeTime('2025-12-31T12:00:00Z', now)).toBe('2025-12-31');
  });

  it('treats an unknown time format as its raw value', () => {
    expect(formatRelativeTime('not-a-date', now)).toBe('not-a-date');
  });
});

describe('LoginForm password toggle (T-UI-03)', () => {
  it('defaults the password input to type="password"', () => {
    render(<App />);
    expect(screen.getByPlaceholderText('密码').getAttribute('type')).toBe('password');
  });

  it('switches the password input to type="text" when the toggle is clicked', async () => {
    render(<App />);
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '显示' }));
    });
    expect(screen.getByPlaceholderText('密码').getAttribute('type')).toBe('text');
    expect(screen.getByRole('button', { name: '隐藏' })).toBeDefined();
  });

  it('switches back to type="password" on a second click', async () => {
    render(<App />);
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '显示' }));
    });
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '隐藏' }));
    });
    expect(screen.getByPlaceholderText('密码').getAttribute('type')).toBe('password');
    expect(screen.getByRole('button', { name: '显示' })).toBeDefined();
  });
});

describe('App theme toggle (T-UI-04)', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('defaults to the light theme', () => {
    render(<App />);
    expect(document.documentElement.dataset.theme).toBe('light');
    expect(window.localStorage.getItem('tasksplitting-theme')).toBe('light');
  });

  it('switches to dark and persists the choice when the toggle is clicked', async () => {
    render(<App />);
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '切换主题' }));
    });
    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(window.localStorage.getItem('tasksplitting-theme')).toBe('dark');
  });

  it('restores the stored theme on remount', async () => {
    window.localStorage.setItem('tasksplitting-theme', 'dark');
    render(<App />);
    expect(document.documentElement.dataset.theme).toBe('dark');
  });
});

describe('LoginForm submit loading disables inputs (T-UI-05)', () => {
  it('disables the username and password inputs while a submit is in flight', async () => {
    let resolveLogin!: (value: Response) => void;
    const loginPromise = new Promise<Response>((resolve) => {
      resolveLogin = resolve;
    });
    vi.stubGlobal('fetch', vi.fn((input: string | URL | Request) => {
      if (String(input).includes('/api/auth/login')) {
        return loginPromise;
      }
      return Promise.resolve(new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } }));
    }));
    render(<App />);
    fireEvent.change(screen.getByPlaceholderText('用户名'), { target: { value: 'alice' } });
    fireEvent.change(screen.getByPlaceholderText('密码'), { target: { value: 'secret' } });
    await act(async () => {
      fireEvent.submit(screen.getByRole('button', { name: '登录' }));
    });
    expect(screen.getByPlaceholderText('用户名').getAttribute('disabled') !== null).toBe(true);
    expect(screen.getByPlaceholderText('密码').getAttribute('disabled') !== null).toBe(true);
    await act(async () => {
      resolveLogin(new Response(JSON.stringify({ token: 'test-token' }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
    });
  });

  it('restores the inputs to enabled after a failed submit', async () => {
    const fetchMock = vi.fn(async (input: string | URL | Request) => {
      if (String(input).includes('/api/auth/login')) {
        return new Response(JSON.stringify({ error: { code: 'INVALID_PASSWORD' } }), { status: 401, headers: { 'Content-Type': 'application/json' } });
      }
      return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } });
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<App />);
    fireEvent.change(screen.getByPlaceholderText('用户名'), { target: { value: 'alice' } });
    fireEvent.change(screen.getByPlaceholderText('密码'), { target: { value: 'wrong' } });
    expect(screen.getByPlaceholderText('用户名').getAttribute('disabled')).toBeNull();
    expect(screen.getByPlaceholderText('密码').getAttribute('disabled')).toBeNull();
    await act(async () => {
      fireEvent.submit(screen.getByRole('button', { name: '登录' }));
    });
    expect(screen.getByPlaceholderText('用户名').getAttribute('disabled')).toBeNull();
    expect(screen.getByPlaceholderText('密码').getAttribute('disabled')).toBeNull();
  });
});

describe('TodoBoard stats (T-UI-06)', () => {
  it('shows total and completed counts for the full todo set', async () => {
    const { container } = await renderBoard([
      { id: 1, title: '已完成A', completed: true, createdAt: '2026-01-01T10:00:00Z' },
      { id: 2, title: '未完成B', completed: false, createdAt: '2026-01-01T10:00:00Z' },
      { id: 3, title: '已完成C', completed: true, createdAt: '2026-01-01T10:00:00Z' },
    ]);
    expect(container.querySelector('.todo-stats')?.textContent).toBe('共3项，已完成2项');
  });

  it('reflects the unfiltered total even when a filter is active', async () => {
    const { container } = await renderBoard([
      { id: 1, title: '已完成A', completed: true, createdAt: '2026-01-01T10:00:00Z' },
      { id: 2, title: '未完成B', completed: false, createdAt: '2026-01-01T10:00:00Z' },
    ]);
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '未完成' }));
    });
    expect(container.querySelector('.todo-stats')?.textContent).toBe('共2项，已完成1项');
  });

  it('updates the completed count after a new todo is added', async () => {
    const { container, fetchMock } = await renderBoard([
      { id: 1, title: '未完成A', completed: false, createdAt: '2026-01-01T10:00:00Z' },
    ]);
    expect(container.querySelector('.todo-stats')?.textContent).toBe('共1项，已完成0项');
    fetchMock.mockImplementationOnce(async (input: string | URL | Request) => {
      if (String(input).includes('/api/todos')) {
        return new Response(JSON.stringify({ id: 2, title: '新任务', completed: true, createdAt: '2026-01-02T10:00:00Z' }), { status: 201, headers: { 'Content-Type': 'application/json' } });
      }
      return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } });
    });
    fireEvent.change(screen.getByPlaceholderText('添加一个任务'), { target: { value: '新任务' } });
    await act(async () => {
      fireEvent.submit(screen.getByRole('button', { name: '添加' }));
    });
    expect(container.querySelector('.todo-stats')?.textContent).toBe('共2项，已完成1项');
  });
});

describe('LoginForm remembered username (T-UI-07)', () => {
  it('stores the submitted username in localStorage after a successful login', async () => {
    await renderBoard([]);
    expect(window.localStorage.getItem('tasksplitting-remembered-username')).toBe('alice');
  });

  it('prefills the username input from localStorage on remount', () => {
    window.localStorage.setItem('tasksplitting-remembered-username', 'bob');
    render(<App />);
    expect((screen.getByPlaceholderText('用户名') as HTMLInputElement).value).toBe('bob');
  });

  it('never prefills the password input', () => {
    window.localStorage.setItem('tasksplitting-remembered-username', 'carol');
    render(<App />);
    expect((screen.getByPlaceholderText('密码') as HTMLInputElement).value).toBe('');
  });
});
