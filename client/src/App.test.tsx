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
});
