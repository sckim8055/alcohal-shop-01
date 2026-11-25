'use client';

// 브라우저에서 서버 API를 부르는 함수. 값을 바꾸는 요청에는 위조 방지 토큰을 붙인다.

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

let csrf: string | null = null;

export async function api<T = unknown>(path: string, opts: { method?: 'GET' | 'POST' | 'PUT'; body?: unknown } = {}): Promise<T> {
  const method = opts.method ?? 'GET';
  const headers: Record<string, string> = {};
  if (method !== 'GET') {
    if (!csrf) {
      const me = await fetch('/api/me', { credentials: 'same-origin', cache: 'no-store' }).then((r) => r.json());
      csrf = me.csrfToken;
    }
    headers['X-CSRF-Token'] = csrf ?? '';
    headers['Content-Type'] = 'application/json';
  }
  let res: Response;
  try {
    res = await fetch('/api' + path, {
      method, headers, credentials: 'same-origin', cache: 'no-store',
      body: method === 'GET' ? undefined : JSON.stringify(opts.body ?? {}),
    });
  } catch {
    throw new ApiError(0, '서버에 연결할 수 없습니다. 서버가 켜져 있는지 확인해 주세요.');
  }
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    throw new ApiError(res.status, data?.message ?? '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.');
  }
  // 로그인하면 토큰이 새로 발급된다.
  if (data && typeof data.csrfToken === 'string') csrf = data.csrfToken;
  return data as T;
}
