'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { Note, useAction } from '@/components/Bits';
import { InquiryFacts, InquiryForm, InquiryStamp, inquiryKind } from '@/components/Inquiries';
import { Pager } from '@/components/Pager';
import { api } from '@/lib/api';
import { when } from '@/lib/format';
import type { Inquiry, Paged } from '@/lib/types';
import { Loading } from '@/components/Loading';

export default function MyInquiries() {
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paged<Inquiry> | null>(null);
  const [writing, setWriting] = useState(false);
  const [message, setMessage] = useState('');
  const act = useAction();
  const load = useCallback(() => {
    api<Paged<Inquiry>>('/my/inquiries?page=' + page).then(setData).catch((e) => { act.setError(e.message); setData({ items: [], page: 1, size: 10, total: 0 }); });
  }, [page]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(load, [load]);

  async function remove(q: Inquiry) {
    if (!window.confirm(`'${q.title}' 문의를 지울까요? 지우면 되돌릴 수 없습니다.`)) return;
    setMessage('');
    const ok = await act.run(() => api('/inquiries/' + q.id + '/delete', { method: 'POST' }), '문의를 지웠습니다.');
    if (!ok) return;
    if (data && data.items.length === 1 && page > 1) setPage(page - 1); else load();
  }

  return (
    <>
      <div className="spread title">
        <h1>문의 내역</h1>
        {!writing && <button type="button" className="btn yellow" onClick={() => { setWriting(true); setMessage(''); }}>운영자에게 1:1 문의</button>}
      </div>
      <Note kind="error">{act.error}</Note>
      <Note kind="ok">{message || act.done}</Note>
      {writing && (
        <div style={{ marginBottom: 20 }}>
          <InquiryForm target="admin" alwaysSecret heading="운영자에게 1:1 문의" onCancel={() => setWriting(false)}
            onDone={() => { setWriting(false); setMessage('1:1 문의를 올렸습니다. 운영자가 답하면 알림과 메일로 알려 드립니다.'); if (page === 1) load(); else setPage(1); }} />
        </div>
      )}
      {!data ? <Loading /> : data.items.length === 0 ? (
        <div className="sheet">
          <h3>남긴 문의가 없습니다</h3>
          <p>상품이 궁금하면 상품 화면의 “문의하기”, 주문한 것이 궁금하면 주문 상세의 “판매자에게 문의”를 눌러 주세요. 장터 이용에 대한 것은 위의 “운영자에게 1:1 문의”로 물어보세요.</p>
          <Link className="btn plain" href="/my/orders">주문목록 보기</Link>
        </div>
      ) : (
        <div className="sheet">
          <ul className="notice-list">
            {data.items.map((q) => (
              <li key={q.id}>
                <details>
                  <summary>
                    <span className="row" style={{ gap: 8, display: 'inline-flex' }}>
                      <InquiryStamp q={q} />
                      <span className="small">{inquiryKind(q)}</span>
                      <strong>{q.title}</strong>
                    </span>
                    <span className="muted small"> {when(q.createdAt, false)}</span>
                  </summary>
                  <p className="small muted" style={{ margin: '6px 0 0' }}>
                    {q.kind === 'product' && q.productId != null && <><Link href={'/products/' + q.productId}>{q.productName}</Link>{q.shopName && <>, {q.shopName}</>}</>}
                    {q.kind === 'order' && <>{q.orderId != null ? <Link href={'/orders/' + q.orderId}>주문 {q.orderNo}</Link> : <>주문 {q.orderNo}</>}{q.shopName && <>, {q.shopName}</>}</>}
                    {q.kind === 'admin' && <>술장터 운영자에게</>}
                  </p>
                  <InquiryFacts q={q} />
                  {q.status === 'open' && (
                    <p style={{ marginTop: 8, marginBottom: 0 }}>
                      <span className="muted small">답변이 달리면 알림과 메일로 알려 드립니다. </span>
                      <button type="button" className="btn plain small" disabled={act.busy} onClick={() => remove(q)}>이 문의 지우기</button>
                    </p>
                  )}
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
