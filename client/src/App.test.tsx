import { act } from 'react';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import App from './App';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

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
