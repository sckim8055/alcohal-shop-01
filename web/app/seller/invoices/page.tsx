'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { invoiceNet, when, won } from '@/lib/format';
import type { SellerDashboard } from '../page';

export default function SellerInvoices() {
  const [d, setD] = useState<SellerDashboard | null>(null);
  useEffect(() => {
    api<SellerDashboard>('/seller').then(setD).catch(() => setD(null));
  }, []);
  return (
    <>
      <div className="title"><h1>수수료 청구서</h1></div>
      {!d ? <p className="muted">불러오는 중입니다.</p> : d.invoices.length === 0 ? (
        <div className="sheet"><h3>아직 청구서가 없습니다</h3><p>운영자가 매달 청구서를 만들면 여기에 나타납니다.</p></div>
      ) : (
        <div className="sheet scroll">
          <table>
            <thead><tr><th>기간</th><th className="num">입금 확인된 매출</th><th className="num">수수료</th><th className="num">플랫폼 쿠폰 차감</th><th className="num">낼 금액</th><th>상태</th></tr></thead>
            <tbody>
              {d.invoices.map((i) => (
                <tr key={i.id}><td>{i.periodYm}</td><td className="num">{won(i.salesTotal)}</td><td className="num">{won(i.amount)}</td>
                  <td className="num">{(i.platformCouponCredit ?? 0) > 0 ? '-' + won(i.platformCouponCredit ?? 0) : won(0)}</td>
                  <td className="num">{invoiceNet(i) < 0 ? <>플랫폼이 보낼 금액 <strong>{won(-invoiceNet(i))}</strong></> : <strong>{won(invoiceNet(i))}</strong>}</td>
                  <td>{invoiceNet(i) < 0
                    ? (i.status === 'paid' ? <>받음 <span className="muted small">{when(i.paidAt, false)}</span></> : '플랫폼이 보낼 예정')
                    : invoiceNet(i) === 0 ? '낼 금액 없음'
                    : (i.status === 'paid' ? <>납부 완료 <span className="muted small">{when(i.paidAt, false)}</span></> : '납부 전')}</td></tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className="hint" style={{ marginTop: 12 }}>수수료는 입금이 확인된 상품 금액에만 붙고 배송비에는 붙지 않습니다. 환불된 주문은 빠집니다.<br />술장터 쿠폰으로 구매자가 덜 낸 금액은 수수료에서 빼 드립니다. 뺀 금액이 수수료보다 크면 남는 금액을 플랫폼이 보내 드립니다.</p>
    </>
  );
}
