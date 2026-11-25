'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import QRCode from 'qrcode';
import { useCallback, useEffect, useState } from 'react';
import { NeedLogin, Note, Stamp, useAction } from '@/components/Bits';
import { ClaimFacts, ClaimForm, ClaimStamp } from '@/components/Claims';
import { CouponRow } from '@/components/CouponTicket';
import { InquiryForm } from '@/components/Inquiries';
import { useMe } from '@/components/Me';
import { api } from '@/lib/api';
import { CLAIM_TYPE_LABEL, claimOpen, when, won } from '@/lib/format';
import type { Claim, ClaimType, OrderDetail, ReorderResult, Sub, SubRow } from '@/lib/types';

const RETURN_DAYS = 7;
const utcMs = (t: string) => new Date(t.endsWith('Z') ? t : t + 'Z').getTime();

/** 지금 신청할 수 있는 종류. 서버의 규칙(docs/adr/0002 A)과 같게 맞춘다. 최종 판단은 서버가 한다. */
function claimableTypes(sub: SubRow): ClaimType[] {
  const types: ClaimType[] = [];
  // 입금 신고 전에는 "주문 취소"로 바로 취소되므로 신청 대상이 아니다.
  if ((sub.status === 'awaiting_deposit' && sub.depositReportedAt) || sub.status === 'paid') types.push('cancel');
  const recentDone = sub.status === 'completed' && !!sub.completedAt && Date.now() - utcMs(sub.completedAt) <= RETURN_DAYS * 86400000;
  if (sub.hasPhysical && (sub.status === 'shipped' || sub.status === 'delivered' || recentDone)) types.push('return', 'exchange');
  return types;
}

/** 이 판매자별 주문의 신청 상태와 신청 단추 */
function ClaimBox({ s, mine, reload }: { s: Sub; mine: boolean; reload: () => void }) {
  const act = useAction();
  const [opening, setOpening] = useState<ClaimType | null>(null);
  const claim: Claim | null = s.claim ?? null;
  const open = claimOpen(claim);
  // 운영자가 남의 주문을 볼 때는 신청 단추를 그리지 않는다.
  const types = open || !mine ? [] : claimableTypes(s.sub);

  async function withdraw(c: Claim) {
    if (!window.confirm(CLAIM_TYPE_LABEL[c.claimType] + ' 신청을 철회할까요?')) return;
    if (await act.run(() => api('/claims/' + c.id + '/withdraw', { method: 'POST' }))) reload();
  }

  if (!claim && types.length === 0) return null;
  return (
    <div style={{ marginTop: 12 }}>
      <Note kind="error">{act.error}</Note>
      {claim && (
        <div className={open ? 'note' : ''} style={open ? undefined : { marginBottom: 10 }}>
          <div className="row" style={{ marginBottom: 6 }}>
            <ClaimStamp claim={claim} />
            {claim.status === 'requested' && <span className="small">판매자가 확인하고 답하면 알림으로 알려 드립니다.</span>}
            {claim.status === 'approved' && claim.claimType === 'return' && <span className="small">판매자가 알려 준 반품 주소로 물건을 돌려보내 주세요. 물건이 도착하면 판매자가 직접 돈을 돌려줍니다.</span>}
            {claim.status === 'approved' && claim.claimType === 'exchange' && <span className="small">물건을 판매자에게 돌려보내 주세요. 판매자가 새 물건을 보내면 송장 번호가 여기에 나옵니다.</span>}
          </div>
          <ClaimFacts claim={claim} />
          {claim.status === 'approved' && claim.claimType !== 'cancel' && s.items[0] && (
            <p className="small" style={{ marginTop: 8 }}>
              {claim.returnAddress
                ? <>물건을 돌려보낼 곳: <strong>{claim.returnAddress}</strong></>
                : <>판매자가 반품 주소를 적어 두지 않았습니다. 판매자에게 주소를 물어봐 주세요.</>}
            </p>
          )}
          {claim.status === 'requested' && mine && (
            <p style={{ marginTop: 10, marginBottom: 0 }}>
              <button className="btn plain small" type="button" disabled={act.busy} onClick={() => withdraw(claim)}>신청 철회</button>
            </p>
          )}
        </div>
      )}
      {types.length > 0 && !opening && (
        <div className="row">
          {types.map((t) => (
            <button key={t} className="btn plain small" type="button" onClick={() => setOpening(t)}>{CLAIM_TYPE_LABEL[t]} 신청</button>
          ))}
          {types.includes('return') && <span className="muted small">반품·교환은 구매 확정 뒤 {RETURN_DAYS}일까지 신청할 수 있습니다.</span>}
        </div>
      )}
      {opening && (
        <ClaimForm subOrderId={s.sub.id} claimType={opening} onClose={() => setOpening(null)} onDone={() => { setOpening(null); reload(); }} />
      )}
    </div>
  );
}

/** 판매자별 주문마다 "판매자에게 문의". 주문 문의는 늘 비밀글이다. */
function AskSeller({ sub }: { sub: SubRow }) {
  const [open, setOpen] = useState(false);
  const [sent, setSent] = useState(false);
  if (open) {
    return (
      <div style={{ marginTop: 12 }}>
        <InquiryForm target="seller" subOrderId={sub.id} alwaysSecret heading={sub.shopName + '에 이 주문 문의하기'}
          onCancel={() => setOpen(false)} onDone={() => { setOpen(false); setSent(true); }} />
      </div>
    );
  }
  return (
    <>
      {sent && <Note kind="ok">문의를 보냈습니다. 판매자가 답하면 알림과 메일로 알려 드립니다. <Link href="/my/inquiries">문의 내역 보기</Link></Note>}
      <button className="btn plain small" type="button" onClick={() => { setOpen(true); setSent(false); }}>판매자에게 문의</button>
    </>
  );
}

/** 이 주문의 상품을 장바구니에 다시 담는다. 단추와 결과 안내를 따로 돌려줘 서로 다른 자리에 그린다. */
function useReorder(orderId: number) {
  const { refresh } = useMe();
  const act = useAction();
  const [result, setResult] = useState<ReorderResult | null>(null);
  async function go() {
    setResult(null);
    let r: ReorderResult | null = null;
    const ok = await act.run(async () => { r = await api<ReorderResult>('/orders/' + orderId + '/reorder', { method: 'POST' }); });
    if (ok && r) {
      setResult(r);
      refresh();
    }
  }
  const added = result?.added ?? 0;
  const skipped = result?.skipped ?? [];
  const button = <button className="btn plain" type="button" disabled={act.busy} onClick={go}>다시 담기</button>;
  const message = (
    <>
      <Note kind="error">{act.error}</Note>
      {result && (
        <Note kind={added > 0 ? 'ok' : undefined}>
          {added > 0 ? <>상품 {added}개를 장바구니에 담았습니다. </> : <>지금 담을 수 있는 상품이 없습니다. </>}
          {skipped.length > 0 && <>판매가 끝났거나 다 팔려서 담지 못한 상품: {skipped.join(', ')}. </>}
          {added > 0 && <Link href="/cart">장바구니 보기</Link>}
        </Note>
      )}
    </>
  );
  return { button, message };
}

/** 송금 링크를 QR로 보여 준다. 휴대폰으로 찍으면 판매자의 송금 화면이 열린다. */
function PayQr({ link }: { link: string }) {
  const [src, setSrc] = useState('');
  useEffect(() => {
    QRCode.toDataURL(link, { margin: 1, width: 150 }).then(setSrc).catch(() => setSrc(''));
  }, [link]);
  return src ? <img src={src} width={150} height={150} alt="송금 QR 코드" style={{ border: 'var(--line)' }} /> : null;
}

function SubCard({ orderId, s, mine, reload }: { orderId: number; s: Sub; mine: boolean; reload: () => void }) {
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
          <CouponRow sub={sub} />
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

      {(sub.status === 'shipped' || sub.status === 'delivered') && !claimOpen(s.claim) && (
        <p style={{ marginTop: 12 }}>
          <button className="btn" type="button" disabled={act.busy} onClick={() => post('receive')}>잘 받았어요</button>
          <span className="muted small"> 배송 완료 후 7일이 지나면 자동으로 구매가 확정됩니다.</span>
        </p>
      )}
      <ClaimBox s={s} mine={mine} reload={reload} />
      {sub.closeReason && <p className="muted">{sub.closeReason} ({when(sub.closedAt)})</p>}
      {sub.cashReceiptApproval && <p className="small">현금영수증 승인번호 {sub.cashReceiptApproval}</p>}
      {mine && <div className="no-print" style={{ marginTop: 12 }}><AskSeller sub={sub} /></div>}
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

  const { me } = useMe();
  const reorder = useReorder(Number(id));
  if (missing) return <div className="sheet"><h3>주문을 찾을 수 없습니다</h3><Link className="btn" href="/my/orders">주문목록으로</Link></div>;
  if (!o) return <p className="muted">불러오는 중입니다.</p>;
  const mine = me?.user?.id === o.order.buyerId;
  const waiting = o.subs.some((s) => s.sub.status === 'awaiting_deposit' && !s.sub.depositReportedAt);

  return (
    <div className="stack">
      <div className="spread">
        <div className="title" style={{ margin: 0 }}><h1>주문 {o.order.orderNo}</h1></div>
        <div className="row">
          {mine && reorder.button}
          <Link className="btn plain" href={'/orders/' + o.order.id + '/receipt'}>통합 영수증</Link>
        </div>
      </div>
      {reorder.message}
      {waiting && <Note>가게마다 따로 입금해 주세요. 입금한 뒤 가게별로 “입금했어요”를 눌러 알려 주세요.</Note>}
      <p>
        {when(o.order.createdAt)} 주문, 받는 분 {o.order.receiver} ({o.order.phone})
        {o.order.address && <>, {o.order.address}</>}
      </p>
      {o.subs.map((s) => <SubCard key={s.sub.id} orderId={o.order.id} s={s} mine={mine} reload={load} />)}
      <p className="total right">모두 <b>{won(o.order.total)}</b></p>
    </div>
  );
}

export default function OrderPage() {
  return <NeedLogin><Detail /></NeedLogin>;
}
