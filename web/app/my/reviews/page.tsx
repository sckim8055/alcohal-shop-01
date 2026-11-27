'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { Note, useAction } from '@/components/Bits';
import { Pager } from '@/components/Pager';
import { ReviewForm, ReviewItem } from '@/components/Reviews';
import { api } from '@/lib/api';
import { when } from '@/lib/format';
import type { Paged, Review, Writable } from '@/lib/types';
import { Loading } from '@/components/Loading';

/** 쓸 수 있는 리뷰: 구매 확정한 상품마다 "리뷰 쓰기" */
function WritableList({ onWritten }: { onWritten: () => void }) {
  const [list, setList] = useState<Writable[] | null>(null);
  const [failed, setFailed] = useState('');
  const [open, setOpen] = useState<number | null>(null);
  const [message, setMessage] = useState('');
  const load = useCallback(() => {
    setFailed('');
    api<Writable[]>('/my/reviews/writable').then((l) => setList(Array.isArray(l) ? l : [])).catch((e) => { setFailed(e.message); setList([]); });
  }, []);
  useEffect(load, [load]);

  return (
    <>
      <Note kind="error">{failed}</Note>
      <Note kind="ok">{message}</Note>
      {!list ? <Loading /> : list.length === 0 ? (
        <div className="sheet">
          <h3>지금 쓸 수 있는 리뷰가 없습니다</h3>
          <p>물건을 받고 주문 상세에서 “잘 받았어요”를 눌러 구매를 확정하면, 90일 안에 여기서 리뷰를 쓸 수 있습니다.</p>
          <Link className="btn" href="/my/orders">주문목록 보기</Link>
        </div>
      ) : (
        <div className="stack">
          {list.map((w) => (
            <section className="sheet" key={w.orderItemId}>
              <div className="spread">
                <div>
                  <h3><Link href={'/products/' + w.productId}>{w.productName}</Link></h3>
                  <p className="muted small" style={{ margin: 0 }}>{w.shopName}, {w.qty}개, {when(w.completedAt, false)} 구매 확정</p>
                </div>
                {open !== w.orderItemId && (
                  <button type="button" className="btn yellow small" onClick={() => { setOpen(w.orderItemId); setMessage(''); }}>리뷰 쓰기</button>
                )}
              </div>
              {open === w.orderItemId && (
                <ReviewForm orderItemId={w.orderItemId} productName={w.productName} onCancel={() => setOpen(null)}
                  onDone={() => { setOpen(null); setMessage(`'${w.productName}' 리뷰를 올렸습니다. “내가 쓴 리뷰”에서 볼 수 있습니다.`); load(); onWritten(); }} />
              )}
            </section>
          ))}
        </div>
      )}
    </>
  );
}

/** 내가 쓴 리뷰: 지우기 */
function MineList({ stamp }: { stamp: number }) {
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paged<Review> | null>(null);
  const act = useAction();
  const load = useCallback(() => {
    api<Paged<Review>>('/my/reviews?page=' + page).then(setData).catch((e) => { act.setError(e.message); setData({ items: [], page: 1, size: 10, total: 0 }); });
  }, [page, stamp]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(load, [load]);

  async function remove(r: Review) {
    if (!window.confirm(`'${r.productName}' 리뷰를 지울까요? 지우면 이 상품에는 다시 리뷰를 쓸 수 없습니다.`)) return;
    const ok = await act.run(() => api('/reviews/' + r.id + '/delete', { method: 'POST' }), '리뷰를 지웠습니다.');
    if (!ok) return;
    if (data && data.items.length === 1 && page > 1) setPage(page - 1); else load();
  }

  return (
    <>
      <Note kind="error">{act.error}</Note>
      <Note kind="ok">{act.done}</Note>
      {!data ? <Loading /> : data.items.length === 0 ? (
        <div className="sheet">
          <h3>아직 쓴 리뷰가 없습니다</h3>
          <p>“쓸 수 있는 리뷰” 탭에서 구매 확정한 상품의 리뷰를 남겨 보세요.</p>
        </div>
      ) : (
        <div className="sheet">
          {data.items.map((r) => (
            <ReviewItem key={r.id} r={r} showProduct>
              {r.status === 'hidden' && <p className="hint">운영자가 숨긴 리뷰는 상품 화면에 보이지 않고 평점에도 들어가지 않습니다.</p>}
              <p style={{ marginTop: 8, marginBottom: 0 }}>
                <button type="button" className="btn plain small" disabled={act.busy} onClick={() => remove(r)}>이 리뷰 지우기</button>
              </p>
            </ReviewItem>
          ))}
          <Pager page={data.page} size={data.size} total={data.total} onPage={setPage} />
        </div>
      )}
    </>
  );
}

export default function MyReviews() {
  const [tab, setTab] = useState<'writable' | 'mine'>('writable');
  // 리뷰를 쓰면 "내가 쓴 리뷰"를 다시 불러오게 한다.
  const [stamp, setStamp] = useState(0);
  return (
    <>
      <div className="title"><h1>리뷰 관리</h1></div>
      <div className="tabs" role="group" aria-label="리뷰 종류">
        <button type="button" className="chip" aria-current={tab === 'writable'} onClick={() => setTab('writable')}>쓸 수 있는 리뷰</button>
        <button type="button" className="chip" aria-current={tab === 'mine'} onClick={() => setTab('mine')}>내가 쓴 리뷰</button>
      </div>
      {tab === 'writable' ? <WritableList onWritten={() => setStamp((n) => n + 1)} /> : <MineList stamp={stamp} />}
    </>
  );
}
