'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import { NeedLogin, Note, useAction } from '@/components/Bits';
import { useMe } from '@/components/Me';
import { api } from '@/lib/api';
import { won } from '@/lib/format';
import type { Address, Cart, CheckoutCouponGroup, CheckoutCouponOption, OrderDetail } from '@/lib/types';
import { Loading } from '@/components/Loading';

const optionOff = (o: CheckoutCouponOption) => (o.discount ?? 0) + (o.shipDiscount ?? 0);
const optionLabel = (o: CheckoutCouponOption) => `${o.name}: ${o.shipDiscount > 0 ? '배송비 ' : ''}${won(optionOff(o))} 할인`;

function Checkout() {
  const router = useRouter();
  const { me, refresh } = useMe();
  const act = useAction();
  const [cart, setCart] = useState<Cart | null>(null);
  const [form, setForm] = useState({ receiver: me?.user?.name ?? '', phone: '', address: '', cashReceiptNo: '' });
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm({ ...form, [k]: e.target.value });
    // 고른 배송지와 달라지면 "직접 입력"으로 돌린다.
    if (k !== 'cashReceiptNo') setPickedAddress('');
  };

  // 저장한 배송지. 불러오지 못해도 직접 입력해서 주문할 수 있다.
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [pickedAddress, setPickedAddress] = useState('');
  const [saveAddress, setSaveAddress] = useState(false);
  const formRef = useRef(form);
  useEffect(() => { formRef.current = form; });
  const fillFrom = (a: Address) => setForm((f) => ({ ...f, receiver: a.receiver, phone: a.phone, address: a.address }));

  // 쿠폰: 가게(sellerId)마다 고른 회원 쿠폰. 쿠폰을 불러오지 못해도 쿠폰 없이 주문할 수 있다.
  const [couponGroups, setCouponGroups] = useState<CheckoutCouponGroup[]>([]);
  const [couponFailed, setCouponFailed] = useState(false);
  const [chosen, setChosen] = useState<Record<number, number>>({});

  useEffect(() => {
    api<Cart>('/cart').then(setCart).catch(() => setCart({ groups: [], total: 0, hasAlcohol: false }));
    api<CheckoutCouponGroup[]>('/checkout/coupons')
      .then((l) => setCouponGroups(Array.isArray(l) ? l : []))
      .catch(() => { setCouponGroups([]); setCouponFailed(true); });
    api<Address[]>('/my/addresses')
      .then((l) => {
        const list = Array.isArray(l) ? l : [];
        setAddresses(list);
        // 기본 배송지는 처음부터 채워 둔다. 이미 적은 내용(연락처·주소)이 있으면 덮어쓰지 않는다.
        const def = list.find((a) => a.isDefault) ?? null;
        if (def && !formRef.current.phone && !formRef.current.address) {
          setPickedAddress(String(def.id));
          fillFrom(def);
        }
      })
      .catch(() => setAddresses([]));
  }, []);

  if (!cart) return <Loading kind="table" />;
  if (cart.groups.length === 0) {
    return <div className="sheet"><h3>주문할 상품이 없습니다</h3><Link className="btn" href="/">장 보러 가기</Link></div>;
  }
  if (cart.hasAlcohol && !me?.user?.adultVerifiedAt) {
    return (
      <div className="sheet narrow">
        <h3>성인인증이 필요합니다</h3>
        <p>장바구니에 술이 들어 있습니다. 성인인증을 마치면 주문할 수 있습니다.</p>
        <Link className="btn" href="/verify">성인인증 하기</Link>
      </div>
    );
  }
  const needsAddress = cart.groups.some((g) => g.hasPhysical);
  const optionsOf = (sellerId: number) => couponGroups.find((c) => c.sellerId === sellerId)?.options ?? [];
  const chosenOption = (sellerId: number) => optionsOf(sellerId).find((o) => o.memberCouponId === chosen[sellerId]);
  const offOf = (sellerId: number) => { const o = chosenOption(sellerId); return o ? optionOff(o) : 0; };
  const grand = cart.total - cart.groups.reduce((a, g) => a + offOf(g.sellerId), 0);
  // 다른 가게에서 이미 고른 쿠폰(같은 회원 쿠폰)은 고를 수 없다.
  const takenElsewhere = (sellerId: number, memberCouponId: number) =>
    Object.entries(chosen).some(([sid, mc]) => Number(sid) !== sellerId && mc === memberCouponId);
  const choose = (sellerId: number, value: string) => {
    const next = { ...chosen };
    if (value) next[sellerId] = Number(value); else delete next[sellerId];
    setChosen(next);
  };

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    let created: OrderDetail | null = null;
    const ok = await act.run(async () => {
      const coupons = cart!.groups
        .filter((g) => chosenOption(g.sellerId))
        .map((g) => ({ sellerId: g.sellerId, memberCouponId: chosen[g.sellerId] }));
      created = await api<OrderDetail>('/orders', { method: 'POST', body: { ...form, coupons } });
    });
    if (ok && created) {
      // 주문은 이미 끝났다. 배송지 저장에 실패해도 주문 화면으로 간다.
      if (saveAddress && needsAddress && !pickedAddress) {
        await api('/my/addresses', { method: 'POST', body: { label: '', receiver: form.receiver.trim(), phone: form.phone.trim(), address: form.address.trim(), isDefault: false } }).catch(() => null);
      }
      refresh();
      router.push('/orders/' + (created as OrderDetail).order.id);
    }
  }

  return (
    <div className="detail">
      <form className="sheet" onSubmit={submit}>
        <h2>받는 분</h2>
        <Note kind="error">{act.error}</Note>
        {addresses.length > 0 && (
          <div className="field">
            <label htmlFor="saved-address">배송지 고르기</label>
            <select id="saved-address" value={pickedAddress} onChange={(e) => {
              setPickedAddress(e.target.value);
              const a = addresses.find((x) => String(x.id) === e.target.value);
              if (a) { fillFrom(a); setSaveAddress(false); }
            }}>
              <option value="">직접 입력</option>
              {addresses.map((a) => (
                <option key={a.id} value={a.id}>{(a.label || a.receiver) + (a.isDefault ? ' (기본)' : '') + ': ' + a.address}</option>
              ))}
            </select>
            <p className="hint">고르면 이름·연락처·주소 칸이 채워집니다. <Link href="/my/addresses">배송지 관리</Link></p>
          </div>
        )}
        <div className="field">
          <label htmlFor="receiver">이름</label>
          <input id="receiver" required maxLength={40} value={form.receiver} onChange={set('receiver')} />
        </div>
        <div className="field">
          <label htmlFor="phone">연락처</label>
          <input id="phone" required maxLength={30} inputMode="tel" value={form.phone} onChange={set('phone')} />
        </div>
        {needsAddress && (
          <div className="field">
            <label htmlFor="address">주소</label>
            <input id="address" required maxLength={300} value={form.address} onChange={set('address')} />
          </div>
        )}
        {needsAddress && !pickedAddress && (
          <div className="field">
            <label className="row" style={{ fontWeight: 400, gap: 8 }}>
              <input type="checkbox" checked={saveAddress} onChange={(e) => setSaveAddress(e.target.checked)} />
              이 주소를 배송지로 저장
            </label>
            <p className="hint">주문이 끝나면 배송지 관리에 저장합니다. 다음 주문 때 골라 쓸 수 있습니다.</p>
          </div>
        )}
        <div className="field">
          <label htmlFor="cash">현금영수증 번호</label>
          <input id="cash" maxLength={30} value={form.cashReceiptNo} onChange={set('cashReceiptNo')} />
          <p className="hint">원하면 휴대폰 번호나 사업자번호를 적어 주세요. 현금영수증은 각 판매자가 발행합니다.</p>
        </div>
        <button className="btn yellow" disabled={act.busy}>{won(grand)} 주문하기</button>
      </form>

      <div className="sheet">
        <h2>주문할 물건</h2>
        {couponFailed && <p className="muted small">쿠폰을 불러오지 못했습니다. 쿠폰 없이 주문할 수 있습니다.</p>}
        {cart.groups.map((g) => {
          const options = optionsOf(g.sellerId);
          const picked = chosenOption(g.sellerId);
          const off = offOf(g.sellerId);
          return (
          <div key={g.sellerId} style={{ marginBottom: 14 }}>
            <h3>{g.shopName}</h3>
            <table>
              <tbody>
                {g.lines.map((l) => (
                  <tr key={l.productId}><td>{l.name} × {l.qty}</td><td className="num">{won(l.lineTotal)}</td></tr>
                ))}
                <tr><td>배송비</td><td className="num">{g.hasPhysical ? won(g.shipFee) : '없음'}</td></tr>
                {picked && <tr><td>쿠폰 할인 ({picked.name})</td><td className="num">-{won(off)}</td></tr>}
                <tr><td><strong>이 가게에 보낼 금액</strong></td><td className="num"><strong>{won(g.total - off)}</strong></td></tr>
              </tbody>
            </table>
            {options.length > 0 && (
              <div className="field" style={{ marginTop: 8 }}>
                <label htmlFor={'coupon-' + g.sellerId}>쿠폰</label>
                <select id={'coupon-' + g.sellerId} value={picked ? String(picked.memberCouponId) : ''} onChange={(e) => choose(g.sellerId, e.target.value)}>
                  <option value="">쓰지 않음</option>
                  {options.map((o) => {
                    const taken = takenElsewhere(g.sellerId, o.memberCouponId);
                    return (
                      <option key={o.memberCouponId} value={o.memberCouponId} disabled={taken}>
                        {optionLabel(o)}{taken ? ' (다른 가게에 고름)' : ''}
                      </option>
                    );
                  })}
                </select>
              </div>
            )}
          </div>
          );
        })}
        <p className="total right">모두 <b>{won(grand)}</b>{grand < cart.total && <span className="small muted"> (쿠폰으로 {won(cart.total - grand)} 아낌)</span>}</p>
        <p className="muted small">쿠폰은 가게마다 한 장씩 쓸 수 있습니다. <Link href="/coupons">쿠폰 받으러 가기</Link></p>
        <p className="muted small">주문한 뒤 3일 안에 가게별 계좌로 입금해 주세요. 입금 신고가 없으면 주문이 자동으로 취소됩니다.</p>
      </div>
    </div>
  );
}

export default function CheckoutPage() {
  return (
    <>
      <div className="title"><h1>주문하기</h1></div>
      <NeedLogin><Checkout /></NeedLogin>
    </>
  );
}
