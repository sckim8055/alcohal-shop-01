'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import QRCode from 'qrcode';
import { useCallback, useEffect, useState } from 'react';
import { NeedLogin, Note, Stamp, useAction } from '@/components/Bits';
import { api } from '@/lib/api';
import { when, won } from '@/lib/format';
import type { OrderDetail, Sub } from '@/lib/types';

/** 송금 링크를 QR로 보여 준다. 휴대폰으로 찍으면 판매자의 송금 화면이 열린다. */
function PayQr({ link }: { link: string }) {
  const [src, setSrc] = useState('');
  useEffect(() => {
    QRCode.toDataURL(link, { margin: 1, width: 150 }).then(setSrc).catch(() => setSrc(''));
  }, [link]);
  return src ? <img src={src} width={150} height={150} alt="송금 QR 코드" style={{ border: 'var(--line)' }} /> : null;
}

function SubCard({ orderId, s, reload }: { orderId: number; s: Sub; reload: () => void }) {
  const act = useAction();
  const [depositor, setDepositor] = useState('');
  const sub = s.sub;
  const post = (action: string, body?: unknown, ok = '') =>
    act.run(() => api('/suborders/' + sub.id + '/' + action, { method: 'POST', body }), ok).then((done) => done && reload());

  return (
    <section className="sheet">
      <div className="spread">
        <h3>{sub.shopName}</h3>
        <Stamp status={sub.status} />
      </div>
      <Note kind="error">{act.error}</Note>
      <table>
        <tbody>
          {s.items.map((i) => (
            <tr key={i.id}><td>{i.name} × {i.qty}</td><td className="num">{won(i.unitPrice * i.qty)}</td></tr>
          ))}
          <tr><td>배송비</td><td className="num">{sub.hasPhysical ? won(sub.shipFee) : '없음'}</td></tr>
          <tr><td><strong>이 가게에 보낼 금액</strong></td><td className="num"><strong>{won(sub.total)}</strong></td></tr>
        </tbody>
      </table>

      {sub.status === 'awaiting_deposit' && (
        <>
          <div className="pay">
            <div>
              <div className="account">{sub.bankName} {sub.bankAccount}</div>
              <div>예금주 {sub.bankHolder}, {won(sub.total)}</div>
              <div className="small muted">{when(sub.depositDueAt)}까지 입금해 주세요.</div>
            </div>
            {sub.payLink.startsWith('https://') && <PayQr link={sub.payLink} />}
          </div>
          {sub.depositReportedAt ? (
            <p>{sub.depositorName} 이름으로 입금했다고 알렸습니다. 판매자가 확인하면 상태가 바뀝니다.</p>
          ) : (
            <div className="inline-form">
              <input placeholder="입금자명" aria-label="입금자명" maxLength={40} value={depositor} onChange={(e) => setDepositor(e.target.value)} />
              <button className="btn" type="button" disabled={act.busy} onClick={() => post('report', { depositor })}>입금했어요</button>
              <button className="btn plain" type="button" disabled={act.busy} onClick={() => post('cancel')}>주문 취소</button>
            </div>
          )}
        </>
      )}

      {sub.status === 'paid' && <p>판매자가 입금을 확인했습니다. {sub.hasPhysical ? '곧 발송합니다.' : '일정과 장소를 따로 안내합니다.'}</p>}

      {s.tracking && (
        <div style={{ marginTop: 12 }}>
          <strong>{s.tracking.carrierName} {sub.trackingNo}</strong>
          {s.tracking.link && <> <a href={s.tracking.link} target="_blank" rel="noopener noreferrer">택배사에서 보기</a></>}
          <ul className="steps">
            {s.tracking.steps.map((st) => <li key={st.label}>{st.label} <span className="muted small">{when(st.at)}</span></li>)}
          </ul>
        </div>
      )}

      {(sub.status === 'shipped' || sub.status === 'delivered') && (
        <p style={{ marginTop: 12 }}>
          <button className="btn" type="button" disabled={act.busy} onClick={() => post('receive')}>잘 받았어요</button>
          <span className="muted small"> 배송 완료 후 7일이 지나면 자동으로 구매가 확정됩니다.</span>
        </p>
      )}
      {sub.closeReason && <p className="muted">{sub.closeReason} ({when(sub.closedAt)})</p>}
      {sub.cashReceiptApproval && <p className="small">현금영수증 승인번호 {sub.cashReceiptApproval}</p>}
      <p className="right no-print" style={{ marginTop: 10 }}>
        <Link className="btn plain small" href={'/orders/' + orderId + '/receipt?sub=' + sub.id}>이 가게 영수증</Link>
      </p>
    </section>
  );
}

function Detail() {
  const { id } = useParams<{ id: string }>();
  const [o, setO] = useState<OrderDetail | null>(null);
  const [missing, setMissing] = useState(false);
  const load = useCallback(() => {
    api<OrderDetail>('/orders/' + id).then(setO).catch(() => setMissing(true));
  }, [id]);
  useEffect(load, [load]);

  if (missing) return <div className="sheet"><h3>주문을 찾을 수 없습니다</h3><Link className="btn" href="/my/orders">주문목록으로</Link></div>;
  if (!o) return <p className="muted">불러오는 중입니다.</p>;
  const waiting = o.subs.some((s) => s.sub.status === 'awaiting_deposit' && !s.sub.depositReportedAt);

  return (
    <div className="stack">
      <div className="spread">
        <div className="title" style={{ margin: 0 }}><h1>주문 {o.order.orderNo}</h1></div>
        <Link className="btn plain" href={'/orders/' + o.order.id + '/receipt'}>통합 영수증</Link>
      </div>
      {waiting && <Note>가게마다 따로 입금해 주세요. 입금한 뒤 가게별로 “입금했어요”를 눌러 알려 주세요.</Note>}
      <p>
        {when(o.order.createdAt)} 주문, 받는 분 {o.order.receiver} ({o.order.phone})
        {o.order.address && <>, {o.order.address}</>}
      </p>
      {o.subs.map((s) => <SubCard key={s.sub.id} orderId={o.order.id} s={s} reload={load} />)}
      <p className="total right">모두 <b>{won(o.order.total)}</b></p>
    </div>
  );
}

export default function OrderPage() {
  return <NeedLogin><Detail /></NeedLogin>;
}
