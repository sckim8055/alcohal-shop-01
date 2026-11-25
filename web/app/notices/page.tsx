import type { Metadata } from 'next';
import Link from 'next/link';
import { Pager } from '@/components/Pager';
import { when } from '@/lib/format';
import { serverGet } from '@/lib/server';
import type { Notice, Paged } from '@/lib/types';

export const dynamic = 'force-dynamic';
export const metadata: Metadata = { title: '공지' };

export default async function Notices({ searchParams }: { searchParams: Promise<{ page?: string }> }) {
  const { page } = await searchParams;
  const data = await serverGet<Paged<Notice>>('/notices' + (page && /^\d+$/.test(page) ? '?page=' + page : ''));
  return (
    <>
      <div className="title"><h1>공지</h1></div>
      {!data ? (
        <div className="note error">공지를 불러오지 못했습니다. 잠시 뒤 새로 고쳐 주세요.</div>
      ) : data.items.length === 0 ? (
        <div className="sheet">
          <h3>아직 올라온 공지가 없습니다</h3>
          <p>새 소식이 생기면 여기에 올립니다.</p>
          <Link className="btn" href="/">장 보러 가기</Link>
        </div>
      ) : (
        <div className="sheet" style={{ maxWidth: 860 }}>
          <ul className="notice-list">
            {data.items.map((n) => (
              <li key={n.id}>
                <div className="row" style={{ gap: 8 }}>
                  {n.pinned && <span className="stamp">고정</span>}
                  <Link href={'/notices/' + n.id}><strong>{n.title}</strong></Link>
                </div>
                <span className="muted small">{when(n.createdAt, false)}</span>
              </li>
            ))}
          </ul>
          <Pager page={data.page} size={data.size} total={data.total} href={(p) => '/notices' + (p > 1 ? '?page=' + p : '')} />
        </div>
      )}
    </>
  );
}
