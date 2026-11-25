'use client';

import { useCallback, useEffect, useState } from 'react';
import { Note, useAction } from '@/components/Bits';
import { Pager } from '@/components/Pager';
import { ReviewItem } from '@/components/Reviews';
import { api } from '@/lib/api';
import type { Paged, Review } from '@/lib/types';

const TABS: [string, string][] = [['', '전체'], ['visible', '보이는 리뷰'], ['hidden', '숨긴 리뷰']];

export default function AdminReviews() {
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paged<Review> | null>(null);
  const act = useAction();
  const load = useCallback(() => {
    api<Paged<Review>>(`/admin/reviews?status=${status}&page=${page}`)
      .then(setData).catch((e) => { act.setError(e.message); setData({ items: [], page: 1, size: 20, total: 0 }); });
  }, [status, page]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(load, [load]);

  async function hide(r: Review, hidden: boolean) {
    if (hidden && !window.confirm(`${r.authorName} 님의 '${r.productName}' 리뷰를 숨길까요? 상품 화면과 평점에서 빠집니다.`)) return;
    const ok = await act.run(() => api('/admin/reviews/' + r.id + '/hide', { method: 'POST', body: { hidden } }), hidden ? '리뷰를 숨겼습니다.' : '리뷰를 다시 보이게 했습니다.');
    if (ok) load();
  }

  return (
    <>
      <div className="title"><h1>리뷰</h1></div>
      <div className="tabs" role="group" aria-label="리뷰 상태">
        {TABS.map(([v, label]) => <button key={v} type="button" className="chip" aria-current={status === v} onClick={() => { setStatus(v); setPage(1); setData(null); }}>{label}</button>)}
      </div>
      <Note kind="error">{act.error}</Note>
      <Note kind="ok">{act.done}</Note>
      {!data ? <p className="muted">불러오는 중입니다.</p> : data.items.length === 0 ? (
        <div className="sheet"><h3>해당하는 리뷰가 없습니다</h3><p>다른 탭을 눌러 보세요.</p></div>
      ) : (
        <div className="sheet">
          {data.items.map((r) => (
            <ReviewItem key={r.id} r={r} showProduct>
              <p style={{ marginTop: 8, marginBottom: 0 }}>
                {r.status === 'visible' && <button type="button" className="btn plain small" disabled={act.busy} onClick={() => hide(r, true)}>숨기기</button>}
                {r.status === 'hidden' && <button type="button" className="btn small" disabled={act.busy} onClick={() => hide(r, false)}>다시 보이기</button>}
                {r.status === 'deleted' && <span className="stamp off">쓴 사람이 지움</span>}
              </p>
            </ReviewItem>
          ))}
          <Pager page={data.page} size={data.size} total={data.total} onPage={setPage} />
        </div>
      )}
      <p className="hint" style={{ marginTop: 16 }}>숨긴 리뷰는 상품 화면과 평점 계산에서 빠집니다. 쓴 사람의 “내가 쓴 리뷰”에는 숨김 표시와 함께 남습니다.</p>
    </>
  );
}
