'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense, useCallback, useEffect, useState } from 'react';
import { Note, useAction } from '@/components/Bits';
import { ClaimFacts, ClaimStamp, useCarriers } from '@/components/Claims';
import { Pager } from '@/components/Pager';
import { api } from '@/lib/api';
import { CLAIM_TYPE_LABEL, when, won } from '@/lib/format';
import type { Carrier, Claim, Paged } from '@/lib/types';
import { Loading } from '@/components/Loading';

/** 신청 한 건과 판매자가 할 수 있는 일 */
function Row({ c, carriers, onDone }: { c: Claim; carriers: Carrier[]; onDone: (msg: string) => void }) {
  const act = useAction();
  const [note, setNote] = useState('');
  const [carrier, setCarrier] = useState('');
  const [trackingNo, setTrackingNo] = useState('');
  const kind = CLAIM_TYPE_LABEL[c.claimType];
  const noteId = 'note-' + c.id;
  const post = (action: string, body: unknown, ok: string) =>
    act.run(() => api('/seller/claims/' + c.id + '/' + action, { method: 'POST', body })).then((done) => done && onDone(ok));

  function approve() {
    if (c.claimType === 'cancel' && !window.confirm('승인하면 이 주문이 바로 환불로 기록되고 재고와 쿠폰이 돌아갑니다. 입금받은 돈은 구매자에게 직접 돌려주세요. 승인할까요?')) return;
    post('approve', { sellerNote: note.trim() }, kind + ' 신청을 승인했습니다.');
  }
  function reject() {
    if (!note.trim()) { act.setError('거절하려면 구매자에게 보여 줄 거절 사유를 적어 주세요.'); document.getElementById(noteId)?.focus(); return; }
    post('reject', { sellerNote: note.trim() }, kind + ' 신청을 거절했습니다.');
  }
  function complete() {
    if (c.claimType === 'exchange') {
      if (!carrier || !trackingNo.trim()) { act.setError('새로 보낸 물건의 택배사와 송장번호를 적어 주세요.'); return; }
      post('complete', { carrier, trackingNo: trackingNo.trim() }, '교환을 마쳤습니다. 구매자에게 송장번호를 알렸습니다.');
    } else {
      if (!window.confirm('돌려받은 물건을 확인하고 구매자에게 돈을 직접 돌려준 뒤에 눌러 주세요. 반품을 마치고 환불로 기록할까요?')) return;
      post('complete', {}, '반품을 마치고 환불로 기록했습니다.');
    }
  }

  return (
    <section className="sheet">
      <div className="spread">
        <h3>{kind} 신청, 주문 {c.orderNo}</h3>
        <ClaimStamp claim={c} />
      </div>
      <Note kind="error">{act.error}</Note>
      <p>
        {c.items}<br />
        구매자 {c.buyerName}, 주문 금액 <strong>{won(c.total ?? 0)}</strong>
      </p>
      <ClaimFacts claim={c} />

      {c.status === 'requested' && (
        <div style={{ marginTop: 12 }}>
          <div className="field">
            <label htmlFor={noteId}>구매자에게 보낼 답 (승인할 때는 선택, 거절할 때는 꼭 적기)</label>
            <textarea id={noteId} maxLength={500} style={{ minHeight: 70 }} value={note} onChange={(e) => setNote(e.target.value)} />
          </div>
          {c.claimType === 'cancel' && <p className="small">승인하면 주문이 바로 환불로 기록됩니다. 입금받은 돈은 구매자에게 직접 돌려주세요.</p>}
          {c.claimType === 'return' && <p className="note">가게 설정의 반품 주소가 구매자에게 안내됩니다. <Link href="/seller/settings">반품 주소 확인하기</Link></p>}
          {c.claimType === 'exchange' && <p className="small">승인하면 구매자가 물건을 돌려보냅니다. 새 물건을 보낸 뒤 송장번호를 적어 교환을 마쳐 주세요.</p>}
          <div className="row">
            <button className="btn" type="button" disabled={act.busy} onClick={approve}>승인하기</button>
            <button className="btn plain" type="button" disabled={act.busy} onClick={reject}>거절하기</button>
          </div>
        </div>
      )}

      {c.status === 'approved' && c.claimType === 'return' && (
        <div className="row" style={{ marginTop: 12 }}>
          <button className="btn" type="button" disabled={act.busy} onClick={complete}>물건을 받고 돈을 돌려줬어요</button>
          <span className="muted small">누르면 반품을 마치고 주문을 환불로 기록합니다.</span>
        </div>
      )}
      {c.status === 'approved' && c.claimType === 'exchange' && (
        <div style={{ marginTop: 12 }}>
          <p className="small">돌려받은 물건을 확인하고 새 물건을 보낸 뒤 송장번호를 적어 주세요.</p>
          <div className="inline-form">
            <select aria-label="택배사" value={carrier} onChange={(e) => setCarrier(e.target.value)}>
              <option value="">택배사 고르기</option>
              {carriers.map((x) => <option key={x.code} value={x.code}>{x.name}</option>)}
            </select>
            <input placeholder="송장번호" aria-label="송장번호" maxLength={40} value={trackingNo} onChange={(e) => setTrackingNo(e.target.value)} />
            <button className="btn" type="button" disabled={act.busy} onClick={complete}>새 물건 보냈어요</button>
          </div>
        </div>
      )}

      <p className="right" style={{ marginTop: 10, marginBottom: 0 }}>
        <span className="muted small">{when(c.requestedAt)} 신청 </span>
        <Link className="btn plain small" href={'/orders/' + c.orderId + '/receipt?sub=' + c.subOrderId}>영수증</Link>
      </p>
    </section>
  );
}

// 서버의 status 값은 하나씩만 받으므로 거절과 철회를 따로 둔다.
const TABS: [string, string][] = [['requested', '신청됨'], ['approved', '승인함'], ['done', '끝남'], ['rejected', '거절함'], ['withdrawn', '철회됨'], ['', '전체']];
const EMPTY: Record<string, string> = {
  requested: '새로 들어온 신청이 없습니다. 신청이 들어오면 알림과 메일로 알려 드립니다.',
  approved: '마무리를 기다리는 반품·교환이 없습니다.',
};

function List() {
  const [status, setStatus] = useState(useSearchParams().get('status') ?? 'requested');
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paged<Claim> | null>(null);
  const [failed, setFailed] = useState('');
  const [message, setMessage] = useState('');
  const carriers = useCarriers();
  const load = useCallback(() => {
    setFailed('');
    api<Paged<Claim>>(`/seller/claims?status=${status}&page=${page}`).then(setData).catch((e) => { setFailed(e.message); setData({ items: [], page: 1, size: 20, total: 0 }); });
  }, [status, page]);
  useEffect(load, [load]);

  return (
    <>
      <div className="tabs" role="group" aria-label="신청 상태">
        {TABS.map(([v, label]) => <button key={v} type="button" className="chip" aria-current={status === v} onClick={() => { setStatus(v); setPage(1); setData(null); setMessage(''); }}>{label}</button>)}
      </div>
      <Note kind="error">{failed}</Note>
      <Note kind="ok">{message}</Note>
      {!data ? <Loading /> : data.items.length === 0 ? (
        <div className="sheet">
          <h3>해당하는 신청이 없습니다</h3>
          <p>{EMPTY[status] ?? '다른 상태 탭을 눌러 보세요.'}</p>
          <Link className="btn plain" href="/seller/orders">주문 처리로 가기</Link>
        </div>
      ) : (
        <div className="stack">
          {data.items.map((c) => <Row key={c.id} c={c} carriers={carriers} onDone={(m) => { setMessage(m); load(); }} />)}
          <Pager page={data.page} size={data.size} total={data.total} onPage={setPage} />
        </div>
      )}
      <p className="hint" style={{ marginTop: 16 }}>대금은 구매자가 가게에 직접 보냈으므로, 취소·반품 환불도 가게가 구매자에게 직접 돌려줍니다. 여기서는 처리 과정을 기록합니다.</p>
    </>
  );
}

export default function SellerClaims() {
  return (
    <>
      <div className="title"><h1>취소·반품·교환</h1></div>
      <Suspense fallback={null}><List /></Suspense>
    </>
  );
}
