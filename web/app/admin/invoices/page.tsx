'use client';

import { useEffect, useState } from 'react';
import { useAdminData } from '@/components/AdminBits';
import { Note } from '@/components/Bits';
import { invoiceNet, when, won } from '@/lib/format';
import { Loading } from '@/components/Loading';

export default function Invoices() {
  const { d, act, call } = useAdminData();
  const [period, setPeriod] = useState('');
  useEffect(() => {
    if (d && !period) setPeriod(d.currentPeriod);
  }, [d, period]);
  return (
    <>
      <div className="title"><h1>수수료 청구</h1></div>
      <p>그 달에 입금이 확인된 상품 금액을 기준으로 가게별 청구서를 만듭니다. 다시 만들면 납부 전 청구서만 새로 계산됩니다.</p>
      <Note kind="error">{act.error}</Note>
      <Note kind="ok">{act.done}</Note>
      <div className="inline-form" style={{ maxWidth: 440, marginBottom: 16 }}>
        <input aria-label="청구 기간" type="month" value={period} onChange={(e) => setPeriod(e.target.value)} />
        <button className="btn" type="button" disabled={act.busy || !period}
          onClick={() => call('/admin/invoices/generate', 'POST', { period }, period + ' 청구서를 만들고 판매자에게 알렸습니다.')}>청구서 만들기</button>
      </div>
      {!d ? <Loading kind="table" /> : d.invoices.length === 0 ? <div className="sheet"><h3>아직 청구서가 없습니다</h3></div> : (
        <div className="sheet scroll">
          <table>
            <thead><tr><th>기간</th><th>가게</th><th className="num">매출</th><th className="num">수수료</th><th className="num">플랫폼 쿠폰 차감</th><th className="num">낼 금액</th><th>상태</th></tr></thead>
            <tbody>
              {d.invoices.map((i) => (
                <tr key={i.id}>
                  <td>{i.periodYm}</td><td>{i.shopName}</td><td className="num">{won(i.salesTotal)}</td><td className="num">{won(i.amount)}</td>
                  <td className="num">{(i.platformCouponCredit ?? 0) > 0 ? '-' + won(i.platformCouponCredit ?? 0) : won(0)}</td>
                  <td className="num">{invoiceNet(i) < 0 ? <>플랫폼이 보낼 금액 <strong>{won(-invoiceNet(i))}</strong></> : <strong>{won(invoiceNet(i))}</strong>}</td>
                  <td>{i.status === 'paid' ? <>{invoiceNet(i) < 0 ? '지급 완료' : '납부 완료'} <span className="muted small">{when(i.paidAt, false)}</span></> : (
                    <button className="btn plain small" type="button" onClick={() => call('/admin/invoices/' + i.id + '/paid', 'POST', undefined, invoiceNet(i) < 0 ? '판매자에게 보낸 것으로 기록했습니다.' : '납부 완료로 기록했습니다.')}>{invoiceNet(i) < 0 ? '보냈음으로 기록' : invoiceNet(i) === 0 ? '정리 완료로 기록' : '납부 확인'}</button>
                  )}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
