'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { Note } from '@/components/Bits';
import { AnswerBox } from '@/components/Inquiries';
import { Pager } from '@/components/Pager';
import { ReviewItem } from '@/components/Reviews';
import { api } from '@/lib/api';
import type { Paged, Review } from '@/lib/types';

export default function SellerReviews() {
  const [unanswered, setUnanswered] = useState(true);
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paged<Review> | null>(null);
  const [failed, setFailed] = useState('');
  const [message, setMessage] = useState('');
  const load = useCallback(() => {
    setFailed('');
    api<Paged<Review>>(`/seller/reviews?unanswered=${unanswered}&page=${page}`)
      .then(setData).catch((e) => { setFailed(e.message); setData({ items: [], page: 1, size: 20, total: 0 }); });
  }, [unanswered, page]);
  useEffect(load, [load]);
  const pick = (v: boolean) => { setUnanswered(v); setPage(1); setData(null); setMessage(''); };

  return (
    <>
      <div className="title"><h1>리뷰</h1></div>
      <div className="tabs" role="group" aria-label="답글 여부">
        <button type="button" className="chip" aria-current={unanswered} onClick={() => pick(true)}>답 안 한 것</button>
        <button type="button" className="chip" aria-current={!unanswered} onClick={() => pick(false)}>전체</button>
      </div>
      <Note kind="error">{failed}</Note>
      <Note kind="ok">{message}</Note>
      {!data ? <p className="muted">불러오는 중입니다.</p> : data.items.length === 0 ? (
        <div className="sheet">
          <h3>{unanswered ? '답글을 기다리는 리뷰가 없습니다' : '아직 받은 리뷰가 없습니다'}</h3>
          <p>{unanswered ? '새 리뷰가 달리면 알림으로 알려 드립니다. 지난 리뷰는 “전체” 탭에서 볼 수 있습니다.' : '구매자가 구매를 확정하면 리뷰를 남길 수 있습니다. 첫 리뷰가 달리면 알림으로 알려 드립니다.'}</p>
          <Link className="btn plain" href="/seller/orders">주문 처리로 가기</Link>
        </div>
      ) : (
        <div className="sheet">
          {data.items.map((r) => (
            <ReviewItem key={r.id} r={r} showProduct>
              {r.status === 'hidden' && <p className="hint">운영자가 숨긴 리뷰입니다. 상품 화면과 평점에서 빠져 있습니다.</p>}
              <AnswerBox path={'/seller/reviews/' + r.id + '/reply'} current={r.sellerReply} field="reply" max={500} label="답글"
                onSaved={() => { setMessage(`'${r.productName}' 리뷰에 답글을 저장했습니다. 구매자에게 알림이 갑니다.`); load(); }} />
            </ReviewItem>
          ))}
          <Pager page={data.page} size={data.size} total={data.total} onPage={setPage} />
        </div>
      )}
      <p className="hint" style={{ marginTop: 16 }}>답글은 리뷰 아래에 모두에게 보입니다. 리뷰마다 하나씩 달 수 있고, 나중에 고칠 수 있습니다.</p>
    </>
  );
}
