import type { ApiResult } from './types';

const BASE = (import.meta.env.VITE_API_BASE ?? '').replace(/\/$/, '');
const TOKEN_KEY = 'platform.token';

export const tokenStore = {
  get(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  },
  set(token: string) {
    localStorage.setItem(TOKEN_KEY, token);
  },
  clear() {
    localStorage.removeItem(TOKEN_KEY);
  },
};

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body) {
    headers.set('Content-Type', 'application/json');
  }
  const token = tokenStore.get();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  const response = await fetch(`${BASE}/api${path}`, { ...init, headers });
  if (response.status === 401) {
    tokenStore.clear();
    localStorage.removeItem('platform.user');
    if (window.location.pathname !== '/login') {
      window.location.assign('/login');
    }
    throw new ApiError(401, '未登录或登录已过期');
  }
  const text = await response.text();
  let body: ApiResult<T> | null = null;
  if (text) {
    try {
      body = JSON.parse(text) as ApiResult<T>;
    } catch {
      body = null;
    }
  }
  if (!response.ok || !body || body.code !== 0) {
    throw new ApiError(response.status, body?.message ?? `请求失败（HTTP ${response.status}）`);
  }
  return body.data;
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
  put: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
};

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

export function formatTime(value: string | null): string {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 19);
}

export function formatSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
