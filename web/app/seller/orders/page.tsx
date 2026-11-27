'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense, useCallback, useEffect, useState } from 'react';
import { Note, Stamp, useAction } from '@/components/Bits';
import { Pager } from '@/components/Pager';
import { api } from '@/lib/api';
import { CLAIM_TYPE_LABEL, claimOpen, couponOff, when, won } from '@/lib/format';
import type { Carrier, Paged, SellerOrder } from '@/lib/types';
import { Loading } from '@/components/Loading';

function Row({ o, carriers, reload }: { o: SellerOrder; carriers: Carrier[]; reload: () => void }) {
  const act = useAction();
  const sub = o.sub.sub;
  const [approval, setApproval] = useState('');
  const [carrier, setCarrier] = useState('cj');
  const [trackingNo, setTrackingNo] = useState('');
  const post = (action: string, body?: unknown) =>
    act.run(() => api('/seller/suborders/' + sub.id + '/' + action, { method: 'POST', body })).then((ok) => ok && reload());
  const canRefund = ['awaiting_deposit', 'paid', 'shipped', 'delivered'].includes(sub.status);
  const claim = o.sub.claim;

  return (
    <section className="sheet">
      <div className="spread">
        <h3>{o.order.orderNo}</h3>
        <Stamp status={sub.status} />
      </div>
      <Note kind="error">{act.error}</Note>
      {claimOpen(claim) && (
        <div className="note error">
          {claim.status === 'requested'
            ? <>{CLAIM_TYPE_LABEL[claim.claimType]} 신청이 들어왔습니다. </>
            : <>승인한 {CLAIM_TYPE_LABEL[claim.claimType]} 신청이 마무리를 기다립니다. </>}
          <Link href={'/seller/claims?status=' + claim.status}>신청 처리하러 가기</Link>
        </div>
      )}
      <p>
        {when(o.order.createdAt)} 주문, {o.sub.items.map((i) => i.name + ' × ' + i.qty).join(', ')}<br />
        받는 분 {o.order.receiver} ({o.order.phone}){o.order.address && <>, {o.order.address}</>}<br />
        받을 금액 <strong>{won(sub.total)}</strong> (상품 {won(sub.itemsTotal)} + 배송비 {won(sub.shipFee)}{couponOff(sub) > 0 && <> - 쿠폰 할인 {won(couponOff(sub))}</>})
        {couponOff(sub) > 0 && (
          <><br />쿠폰 할인 ({sub.couponName ?? '쿠폰'}) <strong>-{won(couponOff(sub))}</strong>{' '}
            <span className="muted small">{sub.couponIssuer === 'platform' ? '이 금액은 수수료 청구서에서 빼 드립니다.' : sub.couponIssuer === 'seller' ? '내 가게 쿠폰으로 깎아 준 금액입니다.' : ''}</span></>
        )}
        {o.order.cashReceiptNo && <><br />현금영수증 요청 번호 {o.order.cashReceiptNo}</>}
      </p>

      {sub.status === 'awaiting_deposit' && (
        <>
          <p>{sub.depositReportedAt
            ? <>구매자가 <strong>{sub.depositorName}</strong> 이름으로 입금했다고 알렸습니다 ({when(sub.depositReportedAt)}). 통장을 확인해 주세요.</>
            : <>아직 입금 신고가 없습니다. 기한은 {when(sub.depositDueAt)}입니다.</>}</p>
          <div className="inline-form">
            <input placeholder="현금영수증 승인번호 (선택)" aria-label="현금영수증 승인번호" maxLength={30} value={approval} onChange={(e) => setApproval(e.target.value)} />
            <button className="btn" type="button" disabled={act.busy} onClick={() => post('confirm', { cashReceiptApproval: approval })}>입금 확인</button>
          </div>
        </>
      )}
      {sub.status === 'paid' && sub.hasPhysical && (
        <div className="inline-form">
          <select aria-label="택배사" value={carrier} onChange={(e) => setCarrier(e.target.value)}>
            {carriers.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}
          </select>
          <input placeholder="송장번호" aria-label="송장번호" maxLength={40} value={trackingNo} onChange={(e) => setTrackingNo(e.target.value)} />
          <button className="btn" type="button" disabled={act.busy} onClick={() => post('ship', { carrier, trackingNo })}>발송했어요</button>
        </div>
      )}
      {sub.status === 'paid' && !sub.hasPhysical && (
        <button className="btn" type="button" disabled={act.busy} onClick={() => post('deliver')}>전달 완료</button>
      )}
      {o.sub.tracking && <p className="small">{o.sub.tracking.carrierName} {sub.trackingNo}, {o.sub.tracking.steps[o.sub.tracking.steps.length - 1]?.label}</p>}
      {sub.closeReason && <p className="muted">{sub.closeReason} ({when(sub.closedAt)})</p>}

      <div className="row" style={{ marginTop: 12 }}>
        <Link className="btn plain small" href={'/orders/' + o.order.id + '/receipt?sub=' + sub.id}>영수증</Link>
        {canRefund && (
          <button className="btn plain small" type="button" disabled={act.busy}
            onClick={() => window.confirm('구매자에게 돈을 직접 돌려준 뒤에 눌러 주세요. 환불로 기록할까요?') && post('refund')}>
            환불로 기록
          </button>
        )}
      </div>
    </section>
  );
}

const TABS: [string, string][] = [['', '전체'], ['awaiting_deposit', '입금 대기'], ['paid', '보낼 주문'], ['shipped', '배송 중'], ['delivered', '배송 완료'], ['completed', '구매 확정'], ['cancelled', '취소'], ['refunded', '환불']];

function SellerOrders() {
  const [status, setStatus] = useState(useSearchParams().get('status') ?? '');
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paged<SellerOrder> | null>(null);
  const [carriers, setCarriers] = useState<Carrier[]>([]);
  const [denied, setDenied] = useState('');
  const load = useCallback(() => {
    api<Paged<SellerOrder>>(`/seller/orders?status=${status}&page=${page}`).then(setData).catch((e) => setDenied(e.message));
  }, [status, page]);
  useEffect(load, [load]);
  useEffect(() => {
    api<Carrier[]>('/carriers').then(setCarriers).catch(() => setCarriers([]));
  }, []);

  if (denied) return <Note kind="error">{denied}</Note>;
  return (
    <>
      <div className="tabs" role="group" aria-label="주문 상태">
        {TABS.map(([v, label]) => <button key={v} type="button" className="chip" aria-current={status === v} onClick={() => { setStatus(v); setPage(1); }}>{label}</button>)}
      </div>
      {!data ? <Loading /> : data.items.length === 0 ? (
        <div className="sheet"><h3>해당하는 주문이 없습니다</h3></div>
      ) : (
        <div className="stack">
          {data.items.map((o) => <Row key={o.sub.sub.id} o={o} carriers={carriers} reload={load} />)}
          <Pager page={data.page} size={data.size} total={data.total} onPage={setPage} />
        </div>
      )}
    </>
  );
}

export default function SellerOrdersPage() {
  return (
    <>
      <div className="title"><h1>주문 처리</h1></div>
      <Suspense fallback={null}><SellerOrders /></Suspense>
    </>
  );
}
