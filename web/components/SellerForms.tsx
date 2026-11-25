'use client';

import { useState } from 'react';
import { Note, useAction } from '@/components/Bits';
import { api } from '@/lib/api';
import type { Seller } from '@/lib/types';

export function Apply({ onDone }: { onDone: () => void }) {
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

export function Settings({ seller, onSaved }: { seller: Seller; onSaved: () => void }) {
  const act = useAction();
  const [f, setF] = useState({
    intro: seller.intro, bankName: seller.bankName, bankAccount: seller.bankAccount, bankHolder: seller.bankHolder, payLink: seller.payLink,
    baseShipFee: String(seller.baseShipFee), freeShipMin: seller.freeShipMin == null ? '' : String(seller.freeShipMin),
    freeShipStart: seller.freeShipStart ?? '', freeShipEnd: seller.freeShipEnd ?? '',
    ceoName: seller.ceoName ?? '', bizAddress: seller.bizAddress ?? '', mailOrderNo: seller.mailOrderNo ?? '',
    csPhone: seller.csPhone ?? '', returnAddress: seller.returnAddress ?? '',
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
      <h3 style={{ margin: '8px 0 6px' }}>판매자 정보</h3>
      <p className="hint" style={{ marginTop: 0, marginBottom: 10 }}>상품 화면 아래 “판매자 정보”에 보입니다(전자상거래법에 따른 표시). 계좌 정보는 보이지 않습니다. 빈 칸은 보이지 않습니다.</p>
      <div className="fields">
        <div className="field"><label htmlFor="ceo">대표자</label><input id="ceo" maxLength={40} value={f.ceoName} onChange={set('ceoName')} /></div>
        <div className="field"><label htmlFor="mo">통신판매업 신고번호</label><input id="mo" maxLength={40} placeholder="예: 제2026-서울종로-0000호" value={f.mailOrderNo} onChange={set('mailOrderNo')} /></div>
        <div className="field"><label htmlFor="cs">고객 전화</label><input id="cs" maxLength={30} inputMode="tel" value={f.csPhone} onChange={set('csPhone')} /></div>
      </div>
      <div className="field"><label htmlFor="baddr">사업장 주소</label><input id="baddr" maxLength={300} value={f.bizAddress} onChange={set('bizAddress')} /></div>
      <div className="field">
        <label htmlFor="raddr">반품 주소</label>
        <input id="raddr" maxLength={300} value={f.returnAddress} onChange={set('returnAddress')} />
        <p className="hint">반품 신청을 승인하면 이 주소가 구매자에게 안내됩니다.</p>
      </div>
      <button className="btn" disabled={act.busy}>저장하기</button>
    </form>
  );
}
