'use client';

import { useCallback, useEffect, useState } from 'react';
import { Note, useAction } from '@/components/Bits';
import { CouponTicket } from '@/components/CouponTicket';
import { Pager } from '@/components/Pager';
import { api } from '@/lib/api';
import { COUPON_STATUS_LABEL, ISSUE_TYPE_LABEL, categoryTree, couponHeadline, day, todayKr, won } from '@/lib/format';
import type { Category, Coupon, CouponInput, CouponStatus, DiscountType, IssueType, Paged } from '@/lib/types';

// 판매자(/seller/coupons)와 운영자(/admin/coupons) 쿠폰 화면이 같이 쓰는 목록과 만들기 양식.

type Mode = 'seller' | 'admin';
const BASE: Record<Mode, string> = { seller: '/seller/coupons', admin: '/admin/coupons' };

function CouponStamp({ status }: { status: CouponStatus }) {
  const tone = status === 'active' ? 'good' : status === 'ended' ? 'off' : '';
  return <span className={'stamp ' + tone}>{COUPON_STATUS_LABEL[status] ?? status}</span>;
}

/** 운영자가 회원 한 명에게 쿠폰을 직접 지급하는 작은 양식 */
function GrantForm({ coupon, onGrant, busy }: { coupon: Coupon; onGrant: (email: string) => Promise<boolean>; busy: boolean }) {
  const [open, setOpen] = useState(false);
  const [email, setEmail] = useState('');
  if (!open) return <button type="button" className="btn plain small" onClick={() => setOpen(true)}>회원에게 지급</button>;
  const id = 'grant-' + coupon.id;
  return (
    <form className="inline-form" style={{ marginTop: 6, justifyContent: 'flex-end' }}
      onSubmit={async (e) => { e.preventDefault(); if (await onGrant(email.trim())) { setEmail(''); setOpen(false); } }}>
      <label htmlFor={id} className="small" style={{ margin: 0 }}>받을 회원 이메일</label>
      <input id={id} type="email" required autoFocus maxLength={200} value={email} onChange={(e) => setEmail(e.target.value)} />
      <button className="btn small" disabled={busy}>지급하기</button>
      <button type="button" className="btn plain small" onClick={() => setOpen(false)}>닫기</button>
    </form>
  );
}

/** 쿠폰 목록. 상태 바꾸기(멈춤·다시 시작·끝내기)와, 운영자는 회원에게 지급까지 */
export function CouponList({ mode, reloadKey }: { mode: Mode; reloadKey: number }) {
  const admin = mode === 'admin';
  const base = BASE[mode];
  const act = useAction();
  const [issuer, setIssuer] = useState('');
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paged<Coupon> | null>(null);
  const [failed, setFailed] = useState('');
  const load = useCallback(() => {
    api<Paged<Coupon>>(`${base}?page=${page}` + (admin ? `&issuer=${issuer}` : ''))
      .then((d) => { setData(d); setFailed(''); })
      .catch((e) => { setFailed(e.message); setData({ items: [], page: 1, size: 20, total: 0 }); });
  }, [base, admin, issuer, page]);
  useEffect(load, [load, reloadKey]);

  const change = (c: Coupon, status: CouponStatus, ok: string) =>
    act.run(() => api(`${base}/${c.id}/status`, { method: 'POST', body: { status } }), ok).then((done) => { if (done) load(); });
  const end = (c: Coupon) => {
    if (window.confirm(`'${c.name}' 쿠폰을 끝낼까요? 끝낸 쿠폰은 다시 시작할 수 없고, 이미 받은 사람도 더는 쓸 수 없습니다.`)) {
      change(c, 'ended', `'${c.name}' 쿠폰을 끝냈습니다.`);
    }
  };
  const grant = (c: Coupon, email: string) =>
    act.run(() => api(`${base}/${c.id}/grant`, { method: 'POST', body: { email } }), `${email} 회원에게 '${c.name}' 쿠폰을 지급하고 알림을 보냈습니다.`)
      .then((done) => { if (done) load(); return done; });

  return (
    <>
      {admin && (
        <div className="tabs" role="group" aria-label="발행한 곳">
          {[['', '전체'], ['platform', '플랫폼'], ['seller', '판매자']].map(([v, label]) => (
            <button key={v} type="button" className="chip" aria-current={issuer === v} onClick={() => { setIssuer(v); setPage(1); }}>{label}</button>
          ))}
        </div>
      )}
      <Note kind="error">{act.error || failed}</Note>
      <Note kind="ok">{act.done}</Note>
      {!data ? <p className="muted">불러오는 중입니다.</p> : data.items.length === 0 ? (
        !failed && <div className="sheet"><h3>{admin && issuer ? '해당하는 쿠폰이 없습니다' : '아직 만든 쿠폰이 없습니다'}</h3><p>아래 양식에서 {admin ? '플랫폼 쿠폰을' : '첫 쿠폰을'} 만들어 보세요.</p><a className="btn" href="#new-coupon">쿠폰 만들러 가기</a></div>
      ) : (
        <div className="sheet scroll">
          <table>
            <thead><tr><th>쿠폰</th><th>받는 방법</th><th>기간</th><th className="num">받은 수</th><th>상태</th><th /></tr></thead>
            <tbody>
              {data.items.map((c) => (
                <tr key={c.id}>
                  <td>
                    <strong>{c.name}</strong>
                    <div className="small">{couponHeadline(c)}{c.minOrder > 0 && <>, {won(c.minOrder)} 이상</>}</div>
                    <div className="muted small">
                      {admin && <>{c.issuer === 'platform' ? '술장터' : c.shopName}, </>}
                      {c.categoryName ? c.categoryName + ' 상품만' : '모든 상품'}, {c.excludeAlcohol ? '술 제외' : <strong>술 포함</strong>}
                    </div>
                  </td>
                  <td className="small">{ISSUE_TYPE_LABEL[c.issueType] ?? c.issueType}{c.code && <div>코드 <strong>{c.code}</strong></div>}</td>
                  <td className="small">{day(c.validFrom)} ~ {day(c.validTo)}{c.validDays && <div className="muted">받은 날부터 {c.validDays}일</div>}</td>
                  <td className="num">{(c.issuedCount ?? 0).toLocaleString('ko-KR')}장<div className="muted small">{c.totalLimit ? '/ ' + c.totalLimit.toLocaleString('ko-KR') + '장' : '제한 없음'}</div></td>
                  <td><CouponStamp status={c.status} /></td>
                  <td className="right">
                    <span className="row" style={{ justifyContent: 'flex-end', gap: 6 }}>
                      {c.status === 'active' && <button type="button" className="btn plain small" disabled={act.busy} onClick={() => change(c, 'paused', `'${c.name}' 쿠폰을 멈췄습니다.`)}>멈춤</button>}
                      {c.status === 'paused' && <button type="button" className="btn small" disabled={act.busy} onClick={() => change(c, 'active', `'${c.name}' 쿠폰을 다시 시작했습니다.`)}>다시 시작</button>}
                      {c.status !== 'ended' && <button type="button" className="btn plain small" disabled={act.busy} onClick={() => end(c)}>끝내기</button>}
                    </span>
                    {admin && c.status !== 'ended' && ['grant', 'signup', 'download'].includes(c.issueType) && (
                      <div style={{ marginTop: 6 }}><GrantForm coupon={c} busy={act.busy} onGrant={(email) => grant(c, email)} /></div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pager page={data.page} size={data.size} total={data.total} onPage={setPage} />
        </div>
      )}
      <p className="hint" style={{ marginTop: 10 }}>멈추거나 끝낸 쿠폰은 새로 받을 수 없고, 이미 받은 사람도 쓸 수 없습니다. 멈춘 쿠폰은 다시 시작할 수 있습니다.</p>
    </>
  );
}

const EMPTY = {
  name: '', discountType: 'amount' as DiscountType, discountValue: '', maxDiscount: '', minOrder: '0', categoryId: '',
  issueType: 'download' as IssueType, code: '', totalLimit: '', validFrom: '', validTo: '', validDays: '', allowAlcohol: false,
};

/** 쿠폰 만들기 양식. 오른쪽에 만들어질 쿠폰 모양을 미리 보여 준다. */
export function CouponForm({ mode, onCreated }: { mode: Mode; onCreated: () => void }) {
  const admin = mode === 'admin';
  const act = useAction();
  const [cats, setCats] = useState<Category[]>([]);
  const [f, setF] = useState(() => ({ ...EMPTY, validFrom: todayKr(), validTo: todayKr(30) }));
  const set = (k: keyof typeof EMPTY) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value });
  useEffect(() => {
    api<Category[]>('/categories').then(setCats).catch(() => setCats([]));
  }, []);

  const free = f.discountType === 'free_shipping';
  const pct = f.discountType === 'percent';
  // 술이 빠지는 쿠폰에서 술 분류를 고르면 할인할 상품이 없으므로 아예 보여 주지 않는다.
  const usable = cats.filter((c) => c.active !== false && (f.allowAlcohol || !c.alcohol));
  const tree = categoryTree(usable);
  const cat = cats.find((c) => String(c.id) === f.categoryId);
  const issueTypes: IssueType[] = admin ? ['signup', 'download', 'code', 'grant'] : ['download', 'code'];

  const toggleAlcohol = (on: boolean) => {
    const keepCat = on || !cat?.alcohol;
    setF({ ...f, allowAlcohol: on, categoryId: keepCat ? f.categoryId : '' });
  };

  const body = (): CouponInput => ({
    name: f.name.trim(),
    discountType: f.discountType,
    discountValue: free ? 0 : Number(f.discountValue),
    maxDiscount: pct && f.maxDiscount !== '' ? Number(f.maxDiscount) : null,
    minOrder: Number(f.minOrder || 0),
    categoryId: f.categoryId ? Number(f.categoryId) : null,
    issueType: f.issueType,
    code: f.issueType === 'code' ? f.code.trim().toUpperCase() : null,
    totalLimit: f.totalLimit !== '' ? Number(f.totalLimit) : null,
    validFrom: f.validFrom,
    validTo: f.validTo,
    validDays: f.validDays !== '' ? Number(f.validDays) : null,
    ...(admin ? { excludeAlcohol: !f.allowAlcohol } : {}),
  });

  const b = body();
  const preview: Coupon = {
    ...b, id: 0, issuer: admin ? 'platform' : 'seller', sellerId: null, shopName: admin ? null : '내 가게',
    name: b.name || '쿠폰 이름', discountValue: Number.isFinite(b.discountValue) ? b.discountValue : 0,
    categoryName: cat?.name ?? null, excludeAlcohol: admin ? !f.allowAlcohol : true,
    issuedCount: 0, status: 'active', createdAt: '',
  };

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const ok = await act.run(() => api<Coupon>(BASE[mode], { method: 'POST', body: body() }), `'${b.name}' 쿠폰을 만들었습니다.`);
    if (ok) {
      setF({ ...EMPTY, validFrom: todayKr(), validTo: todayKr(30) });
      onCreated();
    }
  }

  return (
    <section className="sheet" id="new-coupon">
      <h2>{admin ? '플랫폼 쿠폰 만들기' : '새 쿠폰 만들기'}</h2>
      {admin
        ? <Note>플랫폼 쿠폰의 할인은 술장터가 부담합니다. 구매자가 덜 낸 금액은 그 가게의 수수료 청구서에서 빼 줍니다.</Note>
        : <Note>판매자 쿠폰에서 술은 항상 빠집니다(주류 할인 규정을 확인하기 전까지). 술이 아닌 상품에만 할인이 들어가고, 할인한 금액은 가게가 부담합니다.</Note>}
      <Note kind="error">{act.error}</Note>
      <Note kind="ok">{act.done}</Note>
      <div className="detail">
        <form onSubmit={submit}>
          <div className="field">
            <label htmlFor="cp-name">쿠폰 이름</label>
            <input id="cp-name" required maxLength={60} placeholder="예: 첫 주문 3,000원 할인" value={f.name} onChange={set('name')} />
          </div>
          <div className="fields">
            <div className="field">
              <label htmlFor="cp-type">할인 방식</label>
              <select id="cp-type" value={f.discountType} onChange={(e) => setF({ ...f, discountType: e.target.value as DiscountType, discountValue: '', maxDiscount: '' })}>
                <option value="amount">금액 깎기</option>
                <option value="percent">비율(%) 깎기</option>
                <option value="free_shipping">배송비 무료</option>
              </select>
            </div>
            {f.discountType === 'amount' && (
              <div className="field">
                <label htmlFor="cp-value">깎아 줄 금액(원)</label>
                <input id="cp-value" type="number" inputMode="numeric" required min={100} max={1000000} value={f.discountValue} onChange={set('discountValue')} />
                <p className="hint">100원부터 1,000,000원까지</p>
              </div>
            )}
            {pct && (
              <>
                <div className="field">
                  <label htmlFor="cp-value">깎아 줄 비율(%)</label>
                  <input id="cp-value" type="number" inputMode="numeric" required min={1} max={90} value={f.discountValue} onChange={set('discountValue')} />
                  <p className="hint">1%부터 90%까지</p>
                </div>
                <div className="field">
                  <label htmlFor="cp-max">최대 할인 금액(원)</label>
                  <input id="cp-max" type="number" inputMode="numeric" min={1} value={f.maxDiscount} onChange={set('maxDiscount')} />
                  <p className="hint">비워 두면 제한 없이 비율대로 깎습니다.</p>
                </div>
              </>
            )}
          </div>
          {free && <p className="hint" style={{ marginTop: -8, marginBottom: 14 }}>그 가게 배송비를 모두 깎아 줍니다.</p>}
          <div className="fields">
            <div className="field">
              <label htmlFor="cp-min">최소 주문 금액(원)</label>
              <input id="cp-min" type="number" inputMode="numeric" required min={0} value={f.minOrder} onChange={set('minOrder')} />
              <p className="hint">쿠폰 범위에 드는 상품 금액 기준. 0이면 조건 없음</p>
            </div>
            <div className="field">
              <label htmlFor="cp-cat">상품 분류 (선택)</label>
              <select id="cp-cat" value={f.categoryId} onChange={set('categoryId')}>
                <option value="">모든 분류</option>
                {tree.flatMap((t) => [
                  <option key={t.top.id} value={t.top.id}>{t.top.name}{t.children.length > 0 ? ' (아래 분류 모두)' : ''}</option>,
                  ...t.children.map((c) => <option key={c.id} value={c.id}>{'  └ ' + c.name}</option>),
                ])}
              </select>
            </div>
          </div>
          {admin && (
            <div className="field">
              <label className="row" style={{ gap: 8, fontWeight: 700 }}>
                <input type="checkbox" checked={f.allowAlcohol} onChange={(e) => toggleAlcohol(e.target.checked)} /> 술에도 적용
              </label>
              {f.allowAlcohol
                ? <Note kind="error">술 할인에는 지켜야 할 규정이 있습니다. 주류 할인 규정을 먼저 확인한 뒤에만 이 쿠폰을 만들어 주세요.</Note>
                : <p className="hint">켜지 않으면 술은 할인에서 빠집니다. 켜기 전에 주류 할인 규정을 먼저 확인해 주세요.</p>}
            </div>
          )}
          <div className="fields">
            <div className="field">
              <label htmlFor="cp-issue">받는 방법</label>
              <select id="cp-issue" value={f.issueType} onChange={set('issueType')}>
                {issueTypes.map((t) => <option key={t} value={t}>{ISSUE_TYPE_LABEL[t]}</option>)}
              </select>
            </div>
            {f.issueType === 'code' && (
              <div className="field">
                <label htmlFor="cp-code">쿠폰 코드</label>
                <input id="cp-code" required minLength={4} maxLength={40} pattern="[A-Za-z0-9\-]{4,40}" autoComplete="off" placeholder="예: NURUK-10"
                  value={f.code} onChange={set('code')} />
                <p className="hint">영문·숫자·- 4~40자. 대문자로 저장합니다.</p>
              </div>
            )}
            <div className="field">
              <label htmlFor="cp-limit">전체 발급 수 (선택)</label>
              <input id="cp-limit" type="number" inputMode="numeric" min={1} value={f.totalLimit} onChange={set('totalLimit')} />
              <p className="hint">비워 두면 제한 없음</p>
            </div>
          </div>
          <div className="fields">
            <div className="field">
              <label htmlFor="cp-from">시작일</label>
              <input id="cp-from" type="date" required value={f.validFrom} onChange={set('validFrom')} />
            </div>
            <div className="field">
              <label htmlFor="cp-to">마지막 날</label>
              <input id="cp-to" type="date" required min={f.validFrom || undefined} value={f.validTo} onChange={set('validTo')} />
            </div>
            <div className="field">
              <label htmlFor="cp-days">받은 날부터 쓸 수 있는 날 (선택)</label>
              <input id="cp-days" type="number" inputMode="numeric" min={1} max={365} value={f.validDays} onChange={set('validDays')} />
              <p className="hint">비워 두면 마지막 날까지 쓸 수 있습니다.</p>
            </div>
          </div>
          <button className="btn yellow" disabled={act.busy}>쿠폰 만들기</button>
        </form>
        <div>
          <p className="muted small">이렇게 보입니다</p>
          <CouponTicket coupon={preview} />
        </div>
      </div>
    </section>
  );
}
