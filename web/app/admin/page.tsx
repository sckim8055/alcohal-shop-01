'use client';

import { useCallback, useEffect, useState } from 'react';
import { NeedLogin, Note, Stamp, useAction } from '@/components/Bits';
import { api } from '@/lib/api';
import { percent, when, won } from '@/lib/format';
import type { Category, Invoice, SubStatus } from '@/lib/types';

type SellerRow = { id: number; email: string; shopName: string; bizNo: string; status: string; licenseType: string; licenseNo: string; licenseVerified: boolean; commissionRateBp: number | null };
type Dashboard = {
  sellers: SellerRow[]; categories: Category[]; defaultRateBp: number; invoices: Invoice[];
  rateLog: { id: number; scope: string; targetId: number | null; rateBp: number | null; changedByName: string; changedAt: string }[];
  recentSubs: { id: number; orderNo: string; shopName: string; total: number; status: SubStatus; depositDueAt: string }[];
  currentPeriod: string;
};

const SELLER_STATUS: Record<string, string> = { pending: '심사 중', approved: '승인', rejected: '반려' };
const SCOPE: Record<string, string> = { default: '기본', category: '분류', seller: '판매자' };

/** 수수료율(%) 입력 한 칸. 비우면 "따로 정하지 않음" */
function RateInput({ value, label, allowEmpty, onSave }: { value: number | null; label: string; allowEmpty: boolean; onSave: (v: number | null) => void }) {
  const [v, setV] = useState(percent(value));
  useEffect(() => setV(percent(value)), [value]);
  return (
    <span className="inline-form" style={{ flexWrap: 'nowrap' }}>
      <input aria-label={label} type="number" min={0} max={30} step={0.01} placeholder={allowEmpty ? '기본값' : ''} value={v}
        style={{ flex: '0 0 90px', width: 90 }} onChange={(e) => setV(e.target.value)} />
      <span>%</span>
      <button className="btn plain small" type="button" onClick={() => onSave(v === '' ? null : Number(v))}>저장</button>
    </span>
  );
}

function Admin() {
  const [d, setD] = useState<Dashboard | null>(null);
  const [period, setPeriod] = useState('');
  const act = useAction();
  const load = useCallback(() => {
    api<Dashboard>('/admin').then((x) => {
      setD(x);
      setPeriod((p) => p || x.currentPeriod);
    }).catch((e) => act.setError(e.message));
  }, []); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(load, [load]);

  const call = (path: string, method: 'POST' | 'PUT', body?: unknown, ok = '저장했습니다.') =>
    act.run(() => api(path, { method, body }), ok).then((done) => done && load());

  if (!d) return <><Note kind="error">{act.error}</Note>{!act.error && <p className="muted">불러오는 중입니다.</p>}</>;

  return (
    <div className="stack">
      <Note kind="error">{act.error}</Note>
      <Note kind="ok">{act.done}</Note>

      <section className="sheet">
        <h2>판매자</h2>
        <div className="scroll">
          <table>
            <thead><tr><th>가게</th><th>입점</th><th>주류 면허</th><th>수수료율</th></tr></thead>
            <tbody>
              {d.sellers.map((s) => (
                <tr key={s.id}>
                  <td>{s.shopName}<div className="muted small">{s.email}, {s.bizNo}</div></td>
                  <td>
                    <select aria-label={s.shopName + ' 입점 상태'} value={s.status} style={{ width: 'auto' }}
                      onChange={(e) => call('/admin/sellers/' + s.id + '/status', 'POST', { status: e.target.value })}>
                      {Object.entries(SELLER_STATUS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                    </select>
                  </td>
                  <td>
                    {s.licenseType ? <>{s.licenseType} {s.licenseNo}</> : <span className="muted">없음</span>}
                    <div>
                      <label style={{ fontWeight: 400 }}>
                        <input type="checkbox" checked={s.licenseVerified}
                          onChange={(e) => call('/admin/sellers/' + s.id + '/license', 'POST', { verified: e.target.checked },
                            e.target.checked ? '면허를 확인 처리했습니다.' : '면허 확인을 취소하고 그 가게의 술을 숨겼습니다.')} /> 확인함
                      </label>
                    </div>
                  </td>
                  <td><RateInput label={s.shopName + ' 수수료율'} value={s.commissionRateBp} allowEmpty
                    onSave={(v) => call('/admin/sellers/' + s.id + '/commission', 'PUT', { ratePercent: v })} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="sheet">
        <h2>수수료율</h2>
        <p>판매자에게 따로 정한 값이 있으면 그 값을, 없으면 분류의 값을, 그것도 없으면 기본값을 씁니다. 바꾼 값은 이후 들어오는 주문부터 적용됩니다.</p>
        <table>
          <tbody>
            <tr><td><strong>기본</strong></td><td><RateInput label="기본 수수료율" value={d.defaultRateBp} allowEmpty={false}
              onSave={(v) => call('/admin/commission/default', 'PUT', { ratePercent: v })} /></td></tr>
            {d.categories.map((c) => (
              <tr key={c.id}><td>{c.name}</td><td><RateInput label={c.name + ' 수수료율'} value={c.commissionRateBp} allowEmpty
                onSave={(v) => call('/admin/categories/' + c.id + '/commission', 'PUT', { ratePercent: v })} /></td></tr>
            ))}
          </tbody>
        </table>
        {d.rateLog.length > 0 && (
          <p className="muted small" style={{ marginTop: 10 }}>
            최근 변경: {d.rateLog.slice(0, 3).map((l) => `${SCOPE[l.scope]} ${l.rateBp == null ? '기본값으로' : percent(l.rateBp) + '%'} (${l.changedByName}, ${when(l.changedAt)})`).join(' / ')}
          </p>
        )}
      </section>

      <section className="sheet">
        <h2>수수료 청구</h2>
        <div className="inline-form" style={{ maxWidth: 420 }}>
          <input aria-label="청구 기간" type="month" value={period} onChange={(e) => setPeriod(e.target.value)} />
          <button className="btn" type="button" disabled={act.busy}
            onClick={() => call('/admin/invoices/generate', 'POST', { period }, period + ' 청구서를 만들었습니다.')}>청구서 만들기</button>
        </div>
        {d.invoices.length === 0 ? <p style={{ marginTop: 12 }}>아직 청구서가 없습니다.</p> : (
          <div className="scroll" style={{ marginTop: 12 }}>
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
      </section>

      <section className="sheet">
        <h2>최근 주문</h2>
        <div className="scroll">
          <table>
            <thead><tr><th>주문번호</th><th>가게</th><th className="num">금액</th><th>상태</th></tr></thead>
            <tbody>
              {d.recentSubs.map((s) => (
                <tr key={s.id}><td>{s.orderNo}</td><td>{s.shopName}</td><td className="num">{won(s.total)}</td><td><Stamp status={s.status} /></td></tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}

export default function AdminPage() {
  return (
    <>
      <div className="title"><h1>운영</h1></div>
      <NeedLogin><Admin /></NeedLogin>
    </>
  );
}
