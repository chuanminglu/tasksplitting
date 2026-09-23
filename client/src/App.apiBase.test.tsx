import { afterEach, describe, expect, it, vi } from 'vitest';
import { act } from 'react';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';

/**
 * Verifies that VITE_API_BASE_URL correctly prefixes API fetch URLs.
 * Because App.tsx reads `import.meta.env` at module-load time (top-level const),
 * each test stubs the env variable, resets the module cache, and dynamically
 * imports App to ensure the const captures the stubbed value.
 */

afterEach(() => {
  cleanup();
  vi.unstubAllEnvs();
  vi.restoreAllMocks();
});

async function renderAndLogin(apiBaseUrl: string) {
  if (apiBaseUrl) {
    vi.stubEnv('VITE_API_BASE_URL', apiBaseUrl);
  } else {
    vi.unstubAllEnvs();
  }
  vi.resetModules();
  const { default: App } = await import('./App');

  const fetchMock = vi.fn(async (input: string | URL | Request) =>
    new Response(JSON.stringify({ token: 't' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }),
  );
  vi.stubGlobal('fetch', fetchMock);

  render(<App />);
  fireEvent.change(screen.getByPlaceholderText('用户名'), { target: { value: 'alice' } });
  fireEvent.change(screen.getByPlaceholderText('密码'), { target: { value: 'secret' } });
  await act(async () => {
    fireEvent.submit(screen.getByRole('button', { name: '登录' }));
  });
  return fetchMock;
}

describe('VITE_API_BASE_URL', () => {
  it('sends relative /api/auth/login when the variable is not set', async () => {
    const fetchMock = await renderAndLogin('');
    const calledUrl = String(fetchMock.mock.calls[0][0]);
    expect(calledUrl).toBe('/api/auth/login');
  });

  it('prefixes all fetch URLs when VITE_API_BASE_URL is set', async () => {
    const base = 'https://api.example.com';
    const fetchMock = await renderAndLogin(base);
    const calledUrl = String(fetchMock.mock.calls[0][0]);
    expect(calledUrl).toBe(`${base}/api/auth/login`);
  });
});
