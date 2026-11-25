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

/** 로그인이 풀렸을 때(401) 부를 함수. 화면 쪽에서 등록한다. */
let onUnauthorized: (() => void) | null = null;
export function setUnauthorizedHandler(fn: (() => void) | null) {
  onUnauthorized = fn;
}

async function token(): Promise<string> {
  if (!csrf) {
    const me = await fetch('/api/me', { credentials: 'same-origin', cache: 'no-store' }).then((r) => r.json());
    csrf = me.csrfToken;
  }
  return csrf ?? '';
}

export async function api<T = unknown>(path: string, opts: { method?: 'GET' | 'POST' | 'PUT'; body?: unknown } = {}): Promise<T> {
  const method = opts.method ?? 'GET';
  const headers: Record<string, string> = {};
  if (method !== 'GET') {
    headers['X-CSRF-Token'] = await token();
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
  if (res.status === 401 && path !== '/auth/login' && onUnauthorized) onUnauthorized();
  if (!res.ok) {
    throw new ApiError(res.status, data?.message ?? '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.');
  }
  // 로그인하면 토큰이 새로 발급된다.
  if (data && typeof data.csrfToken === 'string') csrf = data.csrfToken;
  return data as T;
}

/** 사진·동영상 파일 올리기 */
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
export const MAX_VIDEO_BYTES = 100 * 1024 * 1024;

export async function uploadFile(file: File): Promise<{ url: string; kind: 'image' | 'video'; compressing: boolean }> {
  // 큰 파일은 보내기 전에 막는다(서버도 같은 기준으로 다시 확인한다).
  const video = file.type.startsWith('video/');
  if (file.size > (video ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES)) {
    throw new ApiError(400, `'${file.name}' 파일이 너무 큽니다. ${video ? '동영상은 100MB' : '사진은 5MB'} 이하만 올릴 수 있습니다.`);
  }
  const form = new FormData();
  form.append('file', file);
  let res: Response;
  try {
    res = await fetch('/api/files', { method: 'POST', body: form, credentials: 'same-origin', headers: { 'X-CSRF-Token': await token() } });
  } catch {
    throw new ApiError(0, '파일을 올리지 못했습니다. 연결을 확인해 주세요.');
  }
  const data = await res.json().catch(() => null);
  if (res.status === 401 && onUnauthorized) onUnauthorized();
  if (!res.ok) throw new ApiError(res.status, data?.message ?? '파일을 올리지 못했습니다. 크기와 종류를 확인해 주세요.');
  return data;
}
