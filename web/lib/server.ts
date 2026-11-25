// 서버에서 화면을 미리 그릴 때(상품 목록·상세) Java 서버를 직접 부른다. 검색에 잡히게 하려는 것이다.
const API_BASE = process.env.API_BASE || 'http://localhost:8080';

export async function serverGet<T>(path: string): Promise<T | null> {
  try {
    const res = await fetch(API_BASE + '/api' + path, { cache: 'no-store' });
    if (!res.ok) return null;
    return (await res.json()) as T;
  } catch {
    return null;
  }
}
