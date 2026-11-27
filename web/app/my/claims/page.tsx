'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { ClaimFacts, ClaimStamp } from '@/components/Claims';
import { Note } from '@/components/Bits';
import { Pager } from '@/components/Pager';
import { api } from '@/lib/api';
import { when, won } from '@/lib/format';
import type { Claim, Paged } from '@/lib/types';
import { Loading } from '@/components/Loading';

export default function MyClaims() {
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paged<Claim> | null>(null);
  const [failed, setFailed] = useState('');

  useEffect(() => {
    setData(null);
    setFailed('');
    api<Paged<Claim>>('/my/claims?page=' + page).then(setData).catch((e) => { setFailed(e.message); setData({ items: [], page: 1, size: 10, total: 0 }); });
  }, [page]);

  return (
    <>
      <div className="title"><h1>취소·반품·교환 내역</h1></div>
      <Note kind="error">{failed}</Note>
      {!data ? <Loading /> : data.items.length === 0 ? (
        <div className="sheet">
          <h3>신청한 내역이 없습니다</h3>
          <p>취소·반품·교환은 주문 상세 화면에서 가게별로 신청할 수 있습니다.</p>
          <Link className="btn" href="/my/orders">주문목록 보기</Link>
        </div>
      ) : (
        <div className="stack">
          {data.items.map((c) => (
            <section className="sheet" key={c.id}>
              <div className="spread">
                <h3>{c.shopName}</h3>
                <ClaimStamp claim={c} />
              </div>
              <p>
                {c.items}<br />
                <span className="muted small">주문 {c.orderNo}, {won(c.total ?? 0)}, {when(c.requestedAt, false)} 신청</span>
              </p>
              <ClaimFacts claim={c} />
              <p className="right" style={{ marginTop: 10, marginBottom: 0 }}>
                <Link className="btn plain small" href={'/orders/' + c.orderId}>주문 상세보기</Link>
              </p>
            </section>
          ))}
          <Pager page={data.page} size={data.size} total={data.total} onPage={setPage} />
        </div>
      )}
      <p className="hint" style={{ marginTop: 16 }}>
        대금은 판매자에게 직접 보냈으므로, 취소·반품이 승인되면 돈도 판매자가 직접 돌려줍니다. 신청 중일 때는 주문 상세에서 철회할 수 있습니다.
      </p>
    </>
  );
}
