'use client';

import { useEffect, useState } from 'react';
import { Note } from '@/components/Bits';
import { Pager } from '@/components/Pager';
import { api } from '@/lib/api';
import { AUDIENCE_LABEL, when } from '@/lib/format';
import type { Notice, Paged } from '@/lib/types';

export default function SellerNotices() {
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paged<Notice> | null>(null);
  const [failed, setFailed] = useState('');
  useEffect(() => {
    setFailed('');
    api<Paged<Notice>>('/seller/notices?page=' + page).then(setData).catch((e) => { setFailed(e.message); setData({ items: [], page: 1, size: 10, total: 0 }); });
  }, [page]);

  return (
    <>
      <div className="title"><h1>공지</h1></div>
      <Note kind="error">{failed}</Note>
      {!data ? <p className="muted">불러오는 중입니다.</p> : data.items.length === 0 ? (
        <div className="sheet"><h3>아직 공지가 없습니다</h3><p>운영자가 판매자에게 알릴 소식을 올리면 여기에서 볼 수 있습니다.</p></div>
      ) : (
        <div className="sheet">
          <ul className="notice-list">
            {data.items.map((n) => (
              <li key={n.id}>
                <details>
                  <summary>
                    <span className="row" style={{ gap: 8, display: 'inline-flex' }}>
                      {n.pinned && <span className="stamp">고정</span>}
                      {n.audience === 'seller' && <span className="stamp blue">{AUDIENCE_LABEL.seller}</span>}
                      <strong>{n.title}</strong>
                    </span>
                    <span className="muted small"> {when(n.createdAt, false)}</span>
                  </summary>
                  <p className="text" style={{ marginTop: 8, marginBottom: 0 }}>{n.body}</p>
                </details>
              </li>
            ))}
          </ul>
          <Pager page={data.page} size={data.size} total={data.total} onPage={setPage} />
        </div>
      )}
    </>
  );
}
