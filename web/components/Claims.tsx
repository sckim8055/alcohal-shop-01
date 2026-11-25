'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { CLAIM_STATUS_LABEL, CLAIM_TYPE_LABEL, REASON_LABEL, when } from '@/lib/format';
import type { Carrier, Claim, ClaimStatus, ClaimType, ReasonCode } from '@/lib/types';
import { Note, useAction } from './Bits';

/** 신청 상태 도장. 기다리는 중은 빨강, 승인은 파랑, 끝나면 초록, 거절·철회는 흐리게 */
export function ClaimStamp({ claim }: { claim: Pick<Claim, 'claimType' | 'status'> }) {
  const tone: Record<ClaimStatus, string> = { requested: '', approved: 'blue', done: 'good', rejected: 'off', withdrawn: 'off' };
  return (
    <span className={'stamp ' + tone[claim.status]}>
      {(CLAIM_TYPE_LABEL[claim.claimType] ?? '신청') + ' ' + (CLAIM_STATUS_LABEL[claim.status] ?? claim.status)}
    </span>
  );
}

export const reasonOf = (c: Pick<Claim, 'reasonCode' | 'reasonText'>) =>
  (REASON_LABEL[c.reasonCode] ?? '기타') + (c.reasonText ? ': ' + c.reasonText : '');

/** 택배사 코드를 이름으로 바꾼다. 목록을 못 불러오면 코드를 그대로 보여 준다. */
export function useCarriers(needed = true) {
  const [list, setList] = useState<Carrier[]>([]);
  useEffect(() => {
    if (needed) api<Carrier[]>('/carriers').then((l) => setList(Array.isArray(l) ? l : [])).catch(() => setList([]));
  }, [needed]);
  return list;
}
export const carrierName = (list: Carrier[], code: string | null) => list.find((c) => c.code === code)?.name ?? code ?? '';

/** 신청 한 건의 내용: 사유, 판매자 답, 교환 송장, 진행 시각 */
export function ClaimFacts({ claim }: { claim: Claim }) {
  const carriers = useCarriers(Boolean(claim.trackingNo));
  return (
    <table className="specs">
      <tbody>
        <tr><th scope="row">사유</th><td>{reasonOf(claim)}</td></tr>
        <tr><th scope="row">신청한 때</th><td>{when(claim.requestedAt)}</td></tr>
        {claim.sellerNote && <tr><th scope="row">판매자 답</th><td>{claim.sellerNote}</td></tr>}
        {claim.answeredAt && <tr><th scope="row">답한 때</th><td>{when(claim.answeredAt)}</td></tr>}
        {claim.trackingNo && <tr><th scope="row">새 물건 송장</th><td>{carrierName(carriers, claim.carrier)} {claim.trackingNo}</td></tr>}
        {claim.doneAt && <tr><th scope="row">처리한 때</th><td>{when(claim.doneAt)}</td></tr>}
      </tbody>
    </table>
  );
}

const REASONS = Object.entries(REASON_LABEL) as [ReasonCode, string][];

/** 구매자의 신청 폼. 단추를 누른 그 자리에 열린다. */
export function ClaimForm({ subOrderId, claimType, onDone, onClose }: {
  subOrderId: number; claimType: ClaimType; onDone: (c: Claim) => void; onClose: () => void;
}) {
  const act = useAction();
  const [reasonCode, setReasonCode] = useState<ReasonCode | ''>('');
  const [reasonText, setReasonText] = useState('');
  const kind = CLAIM_TYPE_LABEL[claimType];
  const idBase = `claim-${subOrderId}-${claimType}`;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!reasonCode) { act.setError('사유를 골라 주세요.'); return; }
    if (reasonCode === 'other' && !reasonText.trim()) { act.setError('기타를 고르면 자세한 사유를 적어 주세요.'); return; }
    let made: Claim | null = null;
    const ok = await act.run(async () => {
      made = await api<Claim>('/suborders/' + subOrderId + '/claims', { method: 'POST', body: { claimType, reasonCode, reasonText: reasonText.trim() } });
    });
    if (ok && made) onDone(made);
  }

  return (
    <form className="sheet" style={{ marginTop: 12, background: 'var(--kettle-pale)' }} onSubmit={submit} aria-label={kind + ' 신청'}>
      <h3>{kind} 신청</h3>
      <Note kind="error">{act.error}</Note>
      <div className="field">
        <label htmlFor={idBase + '-code'}>사유</label>
        <select id={idBase + '-code'} required value={reasonCode} onChange={(e) => setReasonCode(e.target.value as ReasonCode)}>
          <option value="">사유를 골라 주세요</option>
          {REASONS.map(([v, label]) => <option key={v} value={v}>{label}</option>)}
        </select>
      </div>
      <div className="field">
        <label htmlFor={idBase + '-text'}>자세한 사유{reasonCode === 'other' ? '' : ' (선택)'}</label>
        <textarea id={idBase + '-text'} maxLength={500} style={{ minHeight: 80 }} required={reasonCode === 'other'}
          value={reasonText} onChange={(e) => setReasonText(e.target.value)} />
        <p className="hint">500자까지. 판매자가 이 내용을 보고 답합니다.</p>
      </div>
      {claimType === 'cancel' && <p className="small">판매자가 승인하면 주문이 취소되고, 입금한 돈은 판매자가 직접 돌려줍니다.</p>}
      {claimType === 'return' && <p className="small">판매자가 승인하면 반품 주소를 알려 드립니다. 물건이 판매자에게 도착하면 판매자가 직접 돈을 돌려줍니다.</p>}
      {claimType === 'exchange' && <p className="small">판매자가 승인하면 물건을 돌려보내고, 판매자가 새 물건을 보내 드립니다.</p>}
      <div className="row">
        <button className="btn" disabled={act.busy}>신청하기</button>
        <button className="btn plain" type="button" disabled={act.busy} onClick={onClose}>그만두기</button>
      </div>
    </form>
  );
}
