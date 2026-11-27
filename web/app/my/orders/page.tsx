'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { Stamp } from '@/components/Bits';
import { Pager } from '@/components/Pager';
import { api } from '@/lib/api';
import { couponOff, when, won } from '@/lib/format';
import type { OrderDetail, Paged } from '@/lib/types';
import { Loading } from '@/components/Loading';

export default function MyOrders() {
  const thisYear = new Date().getFullYear();
  const periods = [['6m', '최근 6개월'], ...[0, 1, 2].map((i) => [String(thisYear - i), String(thisYear - i)]), ['all', '전체']];
  const [period, setPeriod] = useState('6m');
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paged<OrderDetail> | null>(null);

  useEffect(() => {
    setData(null);
    api<Paged<OrderDetail>>(`/orders?period=${period}&page=${page}`).then(setData).catch(() => setData({ items: [], page: 1, size: 10, total: 0 }));
  }, [period, page]);

  return (
    <>
      <div className="title"><h1>주문목록</h1></div>
      <div className="tabs" role="group" aria-label="기간">
        {periods.map(([value, label]) => (
          <button key={value} type="button" className="chip" aria-current={period === value} onClick={() => { setPeriod(value); setPage(1); }}>{label}</button>
        ))}
      </div>
      {!data ? <Loading kind="table" /> : data.items.length === 0 ? (
        <div className="sheet"><h3>이 기간에 주문한 것이 없습니다</h3><Link className="btn" href="/">장 보러 가기</Link></div>
      ) : (
        <div className="stack">
          {data.items.map((o) => (
            <section className="sheet" key={o.order.id}>
              <div className="spread">
                <h3>{when(o.order.createdAt, false)} 주문</h3>
                <Link href={'/orders/' + o.order.id}>주문 상세보기</Link>
              </div>
              <table>
                <tbody>
                  {o.subs.map((s) => (
                    <tr key={s.sub.id}>
                      <td>
                        <Stamp status={s.sub.status} /> <strong>{s.sub.shopName}</strong>
                        <div className="small">{s.items.map((i) => i.name + ' × ' + i.qty).join(', ')}</div>
                        {s.tracking && <div className="muted small">{s.tracking.carrierName} {s.sub.trackingNo}, {s.tracking.steps[s.tracking.steps.length - 1]?.label}</div>}
                      </td>
                      <td className="num">
                        {won(s.sub.total)}
                        {couponOff(s.sub) > 0 && <div className="muted small">쿠폰 할인 ({s.sub.couponName ?? '쿠폰'}) -{won(couponOff(s.sub))}</div>}
                      </td>
                      <td className="right">
                        <Link className="btn plain small" href={'/orders/' + o.order.id}>{s.sub.status === 'awaiting_deposit' ? '입금 안내' : s.tracking ? '배송 조회' : '보기'}</Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <p className="muted small" style={{ marginTop: 8 }}>{o.order.orderNo}, 모두 {won(o.order.total)}</p>
            </section>
          ))}
          <Pager page={data.page} size={data.size} total={data.total} onPage={setPage} />
        </div>
      )}
    </>
  );
}
