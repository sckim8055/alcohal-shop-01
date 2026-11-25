'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { NeedLogin, Stamp } from '@/components/Bits';
import { api } from '@/lib/api';
import { when, won } from '@/lib/format';
import type { OrderDetail } from '@/lib/types';

function Orders() {
  const [list, setList] = useState<OrderDetail[] | null>(null);
  useEffect(() => {
    api<OrderDetail[]>('/orders').then(setList).catch(() => setList([]));
  }, []);
  if (!list) return <p className="muted">불러오는 중입니다.</p>;
  if (list.length === 0) {
    return <div className="sheet"><h3>아직 주문한 것이 없습니다</h3><Link className="btn" href="/">장 보러 가기</Link></div>;
  }
  return (
    <div className="stack">
      {list.map((o) => (
        <section className="sheet" key={o.order.id}>
          <div className="spread">
            <h3><Link href={'/orders/' + o.order.id}>{o.order.orderNo}</Link></h3>
            <span className="muted small">{when(o.order.createdAt)}</span>
          </div>
          <table>
            <tbody>
              {o.subs.map((s) => (
                <tr key={s.sub.id}>
                  <td>{s.sub.shopName}<div className="muted small">{s.items.map((i) => i.name + ' × ' + i.qty).join(', ')}</div></td>
                  <td className="num">{won(s.sub.total)}</td>
                  <td className="right"><Stamp status={s.sub.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="right" style={{ marginTop: 10 }}><Link className="btn plain small" href={'/orders/' + o.order.id}>주문 보기</Link></p>
        </section>
      ))}
    </div>
  );
}

export default function OrdersPage() {
  return (
    <>
      <div className="title"><h1>주문내역</h1></div>
      <NeedLogin><Orders /></NeedLogin>
    </>
  );
}
