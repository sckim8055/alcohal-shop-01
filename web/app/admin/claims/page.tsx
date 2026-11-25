'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense, useEffect, useState } from 'react';
import { ClaimStamp, reasonOf } from '@/components/Claims';
import { Pager } from '@/components/Pager';
import { api } from '@/lib/api';
import { when, won } from '@/lib/format';
import type { Claim, Paged } from '@/lib/types';

const TABS: [string, string][] = [['', '전체'], ['requested', '신청됨'], ['approved', '승인됨'], ['done', '처리 완료'], ['rejected', '거절됨'], ['withdrawn', '철회함']];

function List() {
  const [status, setStatus] = useState(useSearchParams().get('status') ?? '');
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paged<Claim> | null>(null);
  useEffect(() => {
    setData(null);
    api<Paged<Claim>>(`/admin/claims?status=${status}&page=${page}`).then(setData).catch(() => setData({ items: [], page: 1, size: 20, total: 0 }));
  }, [status, page]);
  return (
    <>
      <div className="title"><h1>취소·반품·교환</h1></div>
      <div className="tabs" role="group" aria-label="신청 상태">
        {TABS.map(([v, label]) => <button key={v} type="button" className="chip" aria-current={status === v} onClick={() => { setStatus(v); setPage(1); }}>{label}</button>)}
      </div>
      {!data ? <p className="muted">불러오는 중입니다.</p> : data.items.length === 0 ? <div className="sheet"><h3>해당하는 신청이 없습니다</h3><p>다른 상태 탭을 눌러 보세요.</p></div> : (
        <div className="sheet scroll">
          <table>
            <thead><tr><th>주문번호</th><th>구매자</th><th>가게</th><th>상품·사유</th><th className="num">금액</th><th>상태</th><th>신청일</th></tr></thead>
            <tbody>
              {data.items.map((c) => (
                <tr key={c.id}>
                  <td><Link href={'/orders/' + c.orderId}>{c.orderNo}</Link></td><td>{c.buyerName}</td><td>{c.shopName}</td>
                  <td>
                    <div className="small">{c.items}</div>
                    <div className="muted small">{reasonOf(c)}</div>
                    {c.sellerNote && <div className="muted small">판매자 답: {c.sellerNote}</div>}
                  </td>
                  <td className="num">{won(c.total ?? 0)}</td><td><ClaimStamp claim={c} /></td><td className="small">{when(c.requestedAt, false)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pager page={data.page} size={data.size} total={data.total} onPage={setPage} />
        </div>
      )}
      <p className="hint" style={{ marginTop: 12 }}>운영자는 신청을 볼 수만 있습니다. 승인·거절과 환불은 각 판매자가 처리합니다.</p>
    </>
  );
}

export default function AdminClaims() {
  return <Suspense fallback={null}><List /></Suspense>;
}
