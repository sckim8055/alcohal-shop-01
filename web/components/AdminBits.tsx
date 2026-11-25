'use client';

import { useCallback, useEffect, useState } from 'react';
import { useAction } from '@/components/Bits';
import { api } from '@/lib/api';
import { percent } from '@/lib/format';
import type { Category, Invoice, SubStatus } from '@/lib/types';

export type SellerRow = { id: number; memberId: number; email: string; shopName: string; bizNo: string; status: string; licenseType: string; licenseNo: string; licenseVerified: boolean; commissionRateBp: number | null };
export type AdminData = {
  sellers: SellerRow[]; categories: Category[]; defaultRateBp: number; invoices: Invoice[];
  rateLog: { id: number; scope: string; targetId: number | null; rateBp: number | null; changedByName: string; changedAt: string }[];
  recentSubs: { id: number; orderNo: string; shopName: string; total: number; status: SubStatus; depositDueAt: string }[];
  currentPeriod: string;
};

/** 운영 화면들이 같이 쓰는 자료와 저장 함수 */
export function useAdminData() {
  const [d, setD] = useState<AdminData | null>(null);
  const act = useAction();
  const load = useCallback(() => {
    api<AdminData>('/admin').then(setD).catch((e) => act.setError(e.message));
  }, []); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(load, [load]);
  const call = (path: string, method: 'POST' | 'PUT', body?: unknown, ok = '저장했습니다.') =>
    act.run(() => api(path, { method, body }), ok).then((done) => { if (done) load(); return done; });
  return { d, act, call, load };
}

/** 수수료율(%) 입력 한 칸. 비우면 "따로 정하지 않음" */
export function RateInput({ value, label, allowEmpty, onSave }: { value: number | null; label: string; allowEmpty: boolean; onSave: (v: number | null) => void }) {
  const [v, setV] = useState(percent(value));
  useEffect(() => setV(percent(value)), [value]);
  return (
    <span className="inline-form" style={{ flexWrap: 'nowrap' }}>
      <input aria-label={label} type="number" min={0} max={30} step={0.01} placeholder={allowEmpty ? '위 단계 값' : ''} value={v}
        style={{ flex: '0 0 96px', width: 96 }} onChange={(e) => setV(e.target.value)} />
      <span>%</span>
      <button className="btn plain small" type="button" onClick={() => onSave(v === '' ? null : Number(v))}>저장</button>
    </span>
  );
}
