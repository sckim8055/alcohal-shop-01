'use client';

import { useEffect, useState } from 'react';
import { useAdminData } from '@/components/AdminBits';
import { Note } from '@/components/Bits';
import { when, won } from '@/lib/format';

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
      {!d ? <p className="muted">불러오는 중입니다.</p> : d.invoices.length === 0 ? <div className="sheet"><h3>아직 청구서가 없습니다</h3></div> : (
        <div className="sheet scroll">
          <table>
            <thead><tr><th>기간</th><th>가게</th><th className="num">매출</th><th className="num">수수료</th><th>상태</th></tr></thead>
            <tbody>
              {d.invoices.map((i) => (
                <tr key={i.id}>
                  <td>{i.periodYm}</td><td>{i.shopName}</td><td className="num">{won(i.salesTotal)}</td><td className="num">{won(i.amount)}</td>
                  <td>{i.status === 'paid' ? <>납부 완료 <span className="muted small">{when(i.paidAt, false)}</span></> : (
                    <button className="btn plain small" type="button" onClick={() => call('/admin/invoices/' + i.id + '/paid', 'POST', undefined, '납부 완료로 기록했습니다.')}>납부 확인</button>
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
