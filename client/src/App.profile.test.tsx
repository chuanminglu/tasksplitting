import { afterEach, describe, expect, it, vi } from 'vitest';
import { act } from 'react';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import App from './App';

/**
 * T00301 前端:个人中心头像上传闭环。
 * - 登录后可进入个人中心;
 * - 默认显示占位头像(未上传时 img src 为占位 data-URI);
 * - 选择文件并上传成功后,fetch 调用 `${API_BASE}/api/avatars`(multipart,带 Bearer),
 *   且头像 URL 回填(返回工作台后 img src 更新为上传返回的 URL)。
 */

afterEach(() => {
  cleanup();
  delete document.documentElement.dataset.theme;
  window.localStorage.clear();
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
});

function stubFetch(handler: (url: string, init?: RequestInit) => Response) {
  const fetchMock = vi.fn(async (input: string | URL | Request, init?: RequestInit) =>
    handler(String(input), init),
  );
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

async function selectFile(file: File) {
  const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
  // jsdom's `files` setter requires a real FileList; instead define an own
  // property so React's onChange reads the value we provide.
  Object.defineProperty(fileInput, 'files', { value: [file] as unknown as FileList, configurable: true });
  await act(async () => {
    fileInput.dispatchEvent(new Event('change', { bubbles: true }));
  });
}

type FetchHandler = (url: string, init?: RequestInit) => Response;

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

async function login(handler?: FetchHandler): Promise<{ container: HTMLElement; fetchMock: ReturnType<typeof vi.fn> }> {
  const defaultHandler: FetchHandler = (url) =>
    json(url.includes('/api/auth/login') ? { token: 'test-token' } : []);
  const fetchMock = stubFetch(handler ?? defaultHandler);
  const { container } = render(<App />);
  fireEvent.change(screen.getByPlaceholderText('用户名'), { target: { value: 'alice' } });
  fireEvent.change(screen.getByPlaceholderText('密码'), { target: { value: 'secret' } });
  await act(async () => {
    fireEvent.submit(screen.getByRole('button', { name: '登录' }));
  });
  return { container, fetchMock };
}

describe('T00301 个人中心头像上传', () => {
  it('工作台显示"个人中心"入口,登录后默认为占位头像', async () => {
    const { container } = await login();
    expect(screen.getByRole('button', { name: '个人中心' })).toBeDefined();
    const avatar = container.querySelector('.workboard-avatar') as HTMLImageElement;

    expect(avatar).not.toBeNull();
    expect(avatar.getAttribute('src')).toContain('data:image/svg+xml');
  });

  it('未选择文件时上传按钮禁用', async () => {
    await login();
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '个人中心' }));
    });
    expect((screen.getByRole('button', { name: '上传头像' }) as HTMLButtonElement).disabled).toBe(true);
    const preview = screen.getByAltText('当前头像') as HTMLImageElement;
    expect(preview.getAttribute('src')).toContain('data:image/svg+xml');
  });

  it('选择文件后点击上传,POST /api/avatars 成功并以返回 URL 回填头像', async () => {
    const handler: FetchHandler = (url, init) => {
      if (url.includes('/api/auth/login')) return json({ token: 'test-token' });
      if (url.includes('/api/avatars') && init?.method === 'POST') return json({ url: '/api/avatars/abcd.png' });
      return json([]);
    };

    const { fetchMock } = await login(handler);
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '个人中心' }));
    });

    const file = new File(['fake'], 'me.png', { type: 'image/png' });
    await selectFile(file);

    const uploadButton = screen.getByRole('button', { name: '上传头像' }) as HTMLButtonElement;
    expect(uploadButton.disabled).toBe(false);
    await act(async () => {
      fireEvent.click(uploadButton);
    });

    const postCall = fetchMock.mock.calls.find((call) => String(call[0]).includes('/api/avatars'));
    expect(postCall).toBeDefined();
    const init = postCall?.[1] as RequestInit | undefined;
    expect(init?.method).toBe('POST');
    expect(String((init?.headers as Record<string, string>).Authorization)).toBe('Bearer test-token');
    // multipart body 由浏览器/FormData 生成,不应手动设置 Content-Type
    expect((init?.headers as Record<string, string>)['Content-Type']).toBeUndefined();
    expect(init?.body).toBeInstanceOf(FormData);

    // 上传成功后回填头像并返回工作台
    const avatar = screen.getByAltText('我的头像') as HTMLImageElement;
    expect(avatar.getAttribute('src')).toBe('/api/avatars/abcd.png');
  });

  it('上传失败(400)时展示错误信息并停留在个人中心', async () => {
    const handler: FetchHandler = (url, init) => {
      if (url.includes('/api/auth/login')) return json({ token: 'test-token' });
      if (url.includes('/api/avatars') && init?.method === 'POST') return json({ message: 'empty file' }, 400);
      return json([]);
    };

    await login(handler);
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '个人中心' }));
    });

    await selectFile(new File(['x'], 'a.png', { type: 'image/png' }));
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '上传头像' }));
    });

    expect(screen.getByRole('alert').textContent).toContain('empty file');
  });
});
