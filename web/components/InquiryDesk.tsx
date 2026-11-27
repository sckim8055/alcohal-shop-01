'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { Note } from './Bits';
import { AnswerBox, InquiryStamp, inquiryKind } from './Inquiries';
import { Pager } from './Pager';
import { api } from '@/lib/api';
import { when } from '@/lib/format';
import type { Inquiry, Paged } from '@/lib/types';
import { Loading } from './Loading';

/** 받은 문의에 답하는 화면. 판매자(상품 문의·주문 문의)와 운영자(1:1 문의)가 같이 쓴다. */
export function InquiryDesk({ base, admin }: { base: '/seller/inquiries' | '/admin/inquiries'; admin?: boolean }) {
  const [unanswered, setUnanswered] = useState(true);
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paged<Inquiry> | null>(null);
  const [failed, setFailed] = useState('');
  const [message, setMessage] = useState('');
  const load = useCallback(() => {
    setFailed('');
    api<Paged<Inquiry>>(`${base}?unanswered=${unanswered}&page=${page}`)
      .then(setData).catch((e) => { setFailed(e.message); setData({ items: [], page: 1, size: 20, total: 0 }); });
  }, [base, unanswered, page]);
  useEffect(load, [load]);
  const pick = (v: boolean) => { setUnanswered(v); setPage(1); setData(null); setMessage(''); };

  return (
    <>
      <div className="tabs" role="group" aria-label="답변 여부">
        <button type="button" className="chip" aria-current={unanswered} onClick={() => pick(true)}>답 안 한 것</button>
        <button type="button" className="chip" aria-current={!unanswered} onClick={() => pick(false)}>전체</button>
      </div>
      <Note kind="error">{failed}</Note>
      <Note kind="ok">{message}</Note>
      {!data ? <Loading /> : data.items.length === 0 ? (
        <div className="sheet">
          <h3>{unanswered ? '답을 기다리는 문의가 없습니다' : '아직 받은 문의가 없습니다'}</h3>
          <p>새 문의가 오면 알림으로 알려 드립니다.{unanswered && ' 지난 문의는 “전체” 탭에서 볼 수 있습니다.'}</p>
        </div>
      ) : (
        <div className="stack">
          {data.items.map((q) => (
            <section className="sheet" key={q.id}>
              <div className="spread">
                <h3>{q.title}</h3>
                <span className="row" style={{ gap: 8 }}>
                  {q.secret && !admin && <span className="stamp off">비밀글</span>}
                  <InquiryStamp q={q} />
                </span>
              </div>
              <p className="small muted" style={{ margin: '4px 0 0' }}>
                {inquiryKind(q)}, {q.authorName}, {when(q.createdAt)}
                {q.kind === 'product' && q.productId != null && <>, <Link href={'/products/' + q.productId}>{q.productName ?? '상품 보기'}</Link></>}
                {q.kind === 'order' && q.orderId != null && <>, <Link href={'/orders/' + q.orderId + '/receipt' + (q.subOrderId != null ? '?sub=' + q.subOrderId : '')}>주문 {q.orderNo}</Link></>}
              </p>
              <p className="text" style={{ marginTop: 8 }}>{q.body}</p>
              {q.answer && (
                <div className="reply">
                  <strong>내 답변</strong> <span className="muted small">{when(q.answeredAt)}</span>
                  <p className="text" style={{ margin: '4px 0 0' }}>{q.answer}</p>
                </div>
              )}
              <AnswerBox path={base + '/' + q.id + '/answer'} current={q.answer} field="answer" max={2000} label="답변"
                onSaved={() => { setMessage(`'${q.title}' 문의에 답변을 저장했습니다. 쓴 사람에게 알림과 메일이 갑니다.`); load(); }} />
            </section>
          ))}
          <Pager page={data.page} size={data.size} total={data.total} onPage={setPage} />
        </div>
      )}
    </>
  );
}

