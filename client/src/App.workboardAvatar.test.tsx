import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, act, cleanup } from '@testing-library/react';
import App from './App';

type FetchHandler = (url: string, init?: RequestInit) => Response;

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function stubFetch(handler: FetchHandler) {
  const fetchMock = vi.fn(async (input: string | URL | Request, init?: RequestInit) =>
    handler(String(input), init),
  );
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

async function login(handler?: FetchHandler) {
  const defaultHandler: FetchHandler = (url) =>
    json(url.includes('/api/auth/login') ? { token: 'test-token' } : []);
  const fetchMock = stubFetch(handler ?? defaultHandler);
  render(<App />);
  fireEvent.change(screen.getByPlaceholderText('用户名'), { target: { value: 'alice' } });
  fireEvent.change(screen.getByPlaceholderText('密码'), { target: { value: 'secret' } });
  await act(async () => {
    fireEvent.submit(screen.getByRole('button', { name: '登录' }));
  });
  return { fetchMock };
}

async function selectFile(file: File) {
  const input = screen.getByLabelText('选择头像文件') as HTMLInputElement;
  Object.defineProperty(input, 'files', { value: [file] as unknown as FileList, configurable: true });
  await act(async () => {
    input.dispatchEvent(new Event('change', { bubbles: true }));
  });
}

describe('T00302 工作台顶部头像同步展示', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('初始(未上传过头像)时工作台顶部展示默认占位头像', async () => {
    await login();
    const avatar = screen.getByAltText('我的头像') as HTMLImageElement;
    expect(avatar.getAttribute('src')).toContain('data:image/svg+xml');
  });

  it('个人中心上传成功后,切回工作台顶部缩略图立即反映新头像(无需刷新页面)', async () => {
    const handler: FetchHandler = (url, init) => {
      if (url.includes('/api/auth/login')) return json({ token: 'test-token' });
      if (url.includes('/api/avatars') && init?.method === 'POST') return json({ url: '/api/avatars/new-avatar.png' });
      return json([]);
    };

    await login(handler);

    // 1) 工作台初始占位
    expect((screen.getByAltText('我的头像') as HTMLImageElement).getAttribute('src')).toContain('data:image/svg+xml');

    // 2) 进入个人中心
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '个人中心' }));
    });

    // 3) 选择文件并上传
    await selectFile(new File(['x'], 'me.png', { type: 'image/png' }));
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '上传头像' }));
    });

    // 4) 上传成功后自动回到工作台,顶部缩略图立即为新头像(同一共享 state,无重复请求)
    const avatar = screen.getByAltText('我的头像') as HTMLImageElement;
    expect(avatar.getAttribute('src')).toBe('/api/avatars/new-avatar.png');
  });

  it('上传后复用顶层共享 state,不为工作台额外发起"获取当前头像"的 GET 查询请求', async () => {
    const handler: FetchHandler = (url, init) => {
      if (url.includes('/api/auth/login')) return json({ token: 'test-token' });
      if (url.includes('/api/avatars') && init?.method === 'POST') return json({ url: '/api/avatars/shared.png' });
      return json([]);
    };

    const { fetchMock } = await login(handler);
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '个人中心' }));
    });
    await selectFile(new File(['x'], 'a.png', { type: 'image/png' }));
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '上传头像' }));
    });

    // 工作台立即显示新头像
    expect((screen.getByAltText('我的头像') as HTMLImageElement).getAttribute('src')).toBe('/api/avatars/shared.png');

    // 关键:头像同步来自共享的顶层 avatarUrl state,而不是工作台切换时重新 GET 当前头像。
    // 因此对 /api/avatars 的请求只有那一次 POST(上传),不存在任何 GET /api/avatars 的查询请求。
    const avatarCalls = fetchMock.mock.calls
      .map((c) => c[0])
      .filter((u) => String(u).includes('/api/avatars'));
    const uploadPosts = fetchMock.mock.calls.filter((c) => String(c[0]).includes('/api/avatars') && (c[1] as RequestInit | undefined)?.method === 'POST');
    const avatarGets = fetchMock.mock.calls.filter((c) => String(c[0]).includes('/api/avatars') && ((c[1] as RequestInit | undefined)?.method === 'GET' || (c[1] as RequestInit | undefined)?.method == null));

    expect(uploadPosts.length).toBe(1);
    expect(avatarGets.length).toBe(0);
    expect(avatarCalls.length).toBe(1);
  });
});
