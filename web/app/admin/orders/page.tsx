'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense, useEffect, useState } from 'react';
import { Stamp } from '@/components/Bits';
import { Pager } from '@/components/Pager';
import { api } from '@/lib/api';
import { when, won } from '@/lib/format';
import type { Paged, SubStatus } from '@/lib/types';

type Row = { id: number; orderId: number; orderNo: string; buyerName: string; shopName: string; total: number; status: SubStatus; createdAt: string };
const TABS: [string, string][] = [['', '전체'], ['awaiting_deposit', '입금 대기'], ['paid', '입금 확인'], ['shipped', '배송 중'], ['delivered', '배송 완료'], ['completed', '구매 확정'], ['cancelled', '취소'], ['refunded', '환불']];

function List() {
  const [status, setStatus] = useState(useSearchParams().get('status') ?? '');
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paged<Row> | null>(null);
  useEffect(() => {
    api<Paged<Row>>(`/admin/orders?status=${status}&page=${page}`).then(setData).catch(() => setData({ items: [], page: 1, size: 20, total: 0 }));
  }, [status, page]);
  return (
    <>
      <div className="title"><h1>주문</h1></div>
      <div className="tabs" role="group" aria-label="주문 상태">
        {TABS.map(([v, label]) => <button key={v} type="button" className="chip" aria-current={status === v} onClick={() => { setStatus(v); setPage(1); }}>{label}</button>)}
      </div>
      {!data ? <p className="muted">불러오는 중입니다.</p> : data.items.length === 0 ? <div className="sheet"><h3>해당하는 주문이 없습니다</h3></div> : (
        <div className="sheet scroll">
          <table>
            <thead><tr><th>주문번호</th><th>구매자</th><th>가게</th><th className="num">금액</th><th>상태</th><th>주문일</th></tr></thead>
            <tbody>
              {data.items.map((r) => (
                <tr key={r.id}>
                  <td><Link href={'/orders/' + r.orderId}>{r.orderNo}</Link></td><td>{r.buyerName}</td><td>{r.shopName}</td>
                  <td className="num">{won(r.total)}</td><td><Stamp status={r.status} /></td><td className="small">{when(r.createdAt, false)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pager page={data.page} size={data.size} total={data.total} onPage={setPage} />
        </div>
      )}
      <p className="hint" style={{ marginTop: 12 }}>운영자는 주문을 볼 수만 있습니다. 입금 확인과 발송, 환불은 각 판매자가 처리합니다.</p>
    </>
  );
}

export default function AdminOrders() {
  return <Suspense fallback={null}><List /></Suspense>;
}
