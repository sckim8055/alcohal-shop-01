'use client';

import { useParams, useSearchParams } from 'next/navigation';
import { Suspense, useEffect, useState } from 'react';
import { NeedLogin } from '@/components/Bits';
import { CouponRow } from '@/components/CouponTicket';
import { api } from '@/lib/api';
import { STATUS_LABEL, when, won } from '@/lib/format';
import type { OrderDetail, Sub } from '@/lib/types';

function SubTable({ s }: { s: Sub }) {
  return (
    <div style={{ marginTop: 18 }}>
      <h3>{s.sub.shopName}</h3>
      <p className="small">
        사업자등록번호 {s.sub.bizNo}, 상태 {STATUS_LABEL[s.sub.status]}
        {s.sub.depositConfirmedAt && <>, 입금 확인 {when(s.sub.depositConfirmedAt)}</>}
        {s.sub.cashReceiptApproval && <>, 현금영수증 승인번호 {s.sub.cashReceiptApproval}</>}
      </p>
      <table>
        <thead><tr><th>품목</th><th className="num">단가</th><th className="num">수량</th><th className="num">금액</th></tr></thead>
        <tbody>
          {s.items.map((i) => (
            <tr key={i.id}><td>{i.name}</td><td className="num">{won(i.unitPrice)}</td><td className="num">{i.qty}</td><td className="num">{won(i.unitPrice * i.qty)}</td></tr>
          ))}
          <tr><td colSpan={3}>배송비</td><td className="num">{won(s.sub.shipFee)}</td></tr>
          <CouponRow sub={s.sub} colSpan={3} />
          <tr><td colSpan={3}><strong>합계</strong></td><td className="num"><strong>{won(s.sub.total)}</strong></td></tr>
        </tbody>
      </table>
    </div>
  );
}

function Receipt() {
  const { id } = useParams<{ id: string }>();
  const subId = useSearchParams().get('sub');
  const [o, setO] = useState<OrderDetail | null>(null);
  const [missing, setMissing] = useState(false);
  useEffect(() => {
    api<OrderDetail>('/orders/' + id + (subId ? '/receipt/' + subId : '')).then(setO).catch(() => setMissing(true));
  }, [id, subId]);

  if (missing) return <div className="sheet"><h3>영수증을 찾을 수 없습니다</h3></div>;
  if (!o) return <p className="muted">불러오는 중입니다.</p>;
  const sum = o.subs.reduce((a, s) => a + s.sub.total, 0);

  return (
    <>
      <p className="no-print"><button className="btn" type="button" onClick={() => window.print()}>인쇄하기</button></p>
      <article className="sheet receipt">
        <div className="spread">
          <h2>{subId ? '판매자별 영수증' : '통합 영수증'}</h2>
          <span>{o.order.orderNo}</span>
        </div>
        <p>
          주문일 {when(o.order.createdAt)}<br />
          받는 분 {o.order.receiver} ({o.order.phone}){o.order.address && <>, {o.order.address}</>}
        </p>
        {o.subs.map((s) => <SubTable key={s.sub.id} s={s} />)}
        <p className="total right" style={{ marginTop: 16 }}>{subId ? '합계' : '모두'} <b>{won(sum)}</b></p>
        <p className="small muted">
          이 문서는 거래 내용을 확인하는 명세입니다. 세금계산서나 현금영수증은 각 판매자가 발행합니다.
        </p>
      </article>
    </>
  );
}

export default function ReceiptPage() {
  return <NeedLogin><Suspense fallback={<p className="muted">불러오는 중입니다.</p>}><Receipt /></Suspense></NeedLogin>;
}
