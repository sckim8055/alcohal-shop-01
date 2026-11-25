'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { NeedLogin, Note, useAction } from '@/components/Bits';
import { useMe } from '@/components/Me';
import { api } from '@/lib/api';
import { STATUS_LABEL, won } from '@/lib/format';
import type { Invoice, Product, Seller, SubStatus } from '@/lib/types';

type Dashboard = { seller: Seller; products: Product[]; counts: { status: SubStatus; count: number }[]; invoices: Invoice[] };

function Apply({ onDone }: { onDone: () => void }) {
  const act = useAction();
  const [f, setF] = useState({ shopName: '', bizNo: '', intro: '', licenseType: '', licenseNo: '' });
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => setF({ ...f, [k]: e.target.value });
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (await act.run(() => api('/seller/apply', { method: 'POST', body: f }))) onDone();
  }
  return (
    <div style={{ maxWidth: 640 }}>
      <div className="title"><h1>입점 신청</h1></div>
      <p>가게를 내고 싶다면 아래 내용을 적어 주세요. 운영자가 확인한 뒤 판매를 시작할 수 있습니다.</p>
      <Note kind="error">{act.error}</Note>
      <form className="sheet" onSubmit={submit}>
        <div className="fields">
          <div className="field"><label htmlFor="shop">가게 이름</label><input id="shop" required maxLength={40} value={f.shopName} onChange={set('shopName')} /></div>
          <div className="field"><label htmlFor="biz">사업자등록번호</label><input id="biz" required maxLength={20} value={f.bizNo} onChange={set('bizNo')} /></div>
        </div>
        <div className="field"><label htmlFor="intro">가게 소개</label><textarea id="intro" maxLength={500} value={f.intro} onChange={set('intro')} /></div>
        <div className="fields">
          <div className="field"><label htmlFor="lt">주류 면허 종류</label><input id="lt" maxLength={40} placeholder="예: 지역특산주" value={f.licenseType} onChange={set('licenseType')} /></div>
          <div className="field"><label htmlFor="ln">면허 번호</label><input id="ln" maxLength={40} value={f.licenseNo} onChange={set('licenseNo')} /></div>
        </div>
        <p className="hint">술을 팔 가게만 면허를 적습니다. 운영자가 면허를 확인해야 술을 올릴 수 있습니다.</p>
        <button className="btn" disabled={act.busy}>신청하기</button>
      </form>
    </div>
  );
}

function Settings({ seller, onSaved }: { seller: Seller; onSaved: () => void }) {
  const act = useAction();
  const [f, setF] = useState({
    intro: seller.intro, bankName: seller.bankName, bankAccount: seller.bankAccount, bankHolder: seller.bankHolder, payLink: seller.payLink,
    baseShipFee: String(seller.baseShipFee), freeShipMin: seller.freeShipMin == null ? '' : String(seller.freeShipMin),
    freeShipStart: seller.freeShipStart ?? '', freeShipEnd: seller.freeShipEnd ?? '',
  });
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => setF({ ...f, [k]: e.target.value });
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const body = { ...f, baseShipFee: Number(f.baseShipFee), freeShipMin: f.freeShipMin === '' ? null : Number(f.freeShipMin) };
    if (await act.run(() => api('/seller/settings', { method: 'PUT', body }), '가게 설정을 저장했습니다.')) onSaved();
  }
  return (
    <form className="sheet" onSubmit={submit}>
      <h2>가게 설정</h2>
      <Note kind="error">{act.error}</Note>
      <Note kind="ok">{act.done}</Note>
      <div className="field"><label htmlFor="intro">가게 소개</label><textarea id="intro" maxLength={500} style={{ minHeight: 70 }} value={f.intro} onChange={set('intro')} /></div>
      <div className="fields">
        <div className="field"><label htmlFor="bn">은행</label><input id="bn" maxLength={30} value={f.bankName} onChange={set('bankName')} /></div>
        <div className="field"><label htmlFor="ba">계좌번호</label><input id="ba" maxLength={40} value={f.bankAccount} onChange={set('bankAccount')} /></div>
        <div className="field"><label htmlFor="bh">예금주</label><input id="bh" maxLength={40} value={f.bankHolder} onChange={set('bankHolder')} /></div>
      </div>
      <div className="field">
        <label htmlFor="link">송금 링크</label>
        <input id="link" maxLength={300} placeholder="https://" value={f.payLink} onChange={set('payLink')} />
        <p className="hint">카카오페이 송금 링크처럼 https:// 로 시작하는 주소를 넣으면 구매자에게 QR로 보여 줍니다.</p>
      </div>
      <div className="fields">
        <div className="field"><label htmlFor="fee">기본 배송비(원)</label><input id="fee" type="number" min={0} required value={f.baseShipFee} onChange={set('baseShipFee')} /></div>
        <div className="field"><label htmlFor="min">무료배송 기준 금액(원)</label><input id="min" type="number" min={0} value={f.freeShipMin} onChange={set('freeShipMin')} /></div>
        <div className="field"><label htmlFor="fs">무료배송 행사 시작</label><input id="fs" type="date" value={f.freeShipStart} onChange={set('freeShipStart')} /></div>
        <div className="field"><label htmlFor="fe">무료배송 행사 끝</label><input id="fe" type="date" value={f.freeShipEnd} onChange={set('freeShipEnd')} /></div>
      </div>
      <button className="btn" disabled={act.busy}>저장하기</button>
    </form>
  );
}

function SellerHome() {
  const { me, refresh } = useMe();
  const [d, setD] = useState<Dashboard | null>(null);
  const load = useCallback(() => {
    api<Dashboard>('/seller').then(setD).catch(() => setD(null));
  }, []);
  useEffect(() => {
    if (me?.seller) load();
  }, [me?.seller, load]);

  if (!me?.seller) return <Apply onDone={refresh} />;
  if (!d) return <p className="muted">불러오는 중입니다.</p>;
  const s = d.seller;

  if (s.status !== 'approved') {
    return (
      <div className="sheet narrow">
        <h2>{s.shopName}</h2>
        <p>{s.status === 'pending' ? '입점 심사 중입니다. 운영자가 확인하면 판매를 시작할 수 있습니다.' : '입점 신청이 반려되었습니다. 운영자에게 문의해 주세요.'}</p>
      </div>
    );
  }
  const count = (st: SubStatus) => d.counts.find((c) => c.status === st)?.count ?? 0;

  return (
    <div className="stack">
      <div className="spread">
        <div className="title" style={{ margin: 0 }}><h1>{s.shopName}</h1></div>
        <div className="row">
          <Link className="btn yellow" href="/seller/orders">주문 처리</Link>
          <Link className="btn" href="/seller/products/new">상품 올리기</Link>
        </div>
      </div>
      <p>
        입금 대기 {count('awaiting_deposit')}건, 발송할 주문 {count('paid')}건, 배송 중 {count('shipped')}건.
        {' '}주류 면허는 {s.licenseVerified ? '확인되었습니다.' : '확인되지 않아 술은 올릴 수 없습니다.'}
      </p>

      <section className="sheet">
        <h2>내 상품</h2>
        {d.products.length === 0 ? <p>아직 올린 상품이 없습니다.</p> : (
          <div className="scroll">
            <table>
              <thead><tr><th>상품</th><th>분류</th><th className="num">값</th><th className="num">재고</th><th>상태</th><th /></tr></thead>
              <tbody>
                {d.products.map((p) => (
                  <tr key={p.id}>
                    <td>{p.name}</td><td>{p.categoryName}</td>
                    <td className="num">{won(p.currentPrice)}{p.currentPrice < p.price && <div className="muted small"><s>{won(p.price)}</s></div>}</td>
                    <td className="num">{p.stock}</td>
                    <td>{p.status === 'active' ? '판매 중' : '숨김'}</td>
                    <td className="right"><Link className="btn plain small" href={'/seller/products/' + p.id}>고치기</Link></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="sheet">
        <h2>수수료 청구서</h2>
        {d.invoices.length === 0 ? <p>아직 청구서가 없습니다.</p> : (
          <table>
            <thead><tr><th>기간</th><th className="num">입금 확인된 매출</th><th className="num">수수료</th><th>상태</th></tr></thead>
            <tbody>
              {d.invoices.map((i) => (
                <tr key={i.id}><td>{i.periodYm}</td><td className="num">{won(i.salesTotal)}</td><td className="num">{won(i.amount)}</td><td>{i.status === 'paid' ? '납부 완료' : '납부 전'}</td></tr>
              ))}
            </tbody>
          </table>
        )}
        <p className="hint">수수료는 입금이 확인된 상품 금액에만 붙고 배송비에는 붙지 않습니다.</p>
      </section>

      <Settings seller={s} onSaved={load} />
      <p className="muted small">주문 상태 이름: {Object.values(STATUS_LABEL).join(', ')}</p>
    </div>
  );
}

export default function SellerPage() {
  return <NeedLogin><SellerHome /></NeedLogin>;
}
