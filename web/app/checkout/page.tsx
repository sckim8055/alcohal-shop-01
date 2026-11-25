'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { NeedLogin, Note, useAction } from '@/components/Bits';
import { useMe } from '@/components/Me';
import { api } from '@/lib/api';
import { won } from '@/lib/format';
import type { Cart, OrderDetail } from '@/lib/types';

function Checkout() {
  const router = useRouter();
  const { me, refresh } = useMe();
  const act = useAction();
  const [cart, setCart] = useState<Cart | null>(null);
  const [form, setForm] = useState({ receiver: me?.user?.name ?? '', phone: '', address: '', cashReceiptNo: '' });
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value });

  useEffect(() => {
    api<Cart>('/cart').then(setCart).catch(() => setCart({ groups: [], total: 0, hasAlcohol: false }));
  }, []);

  if (!cart) return <p className="muted">불러오는 중입니다.</p>;
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

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    let created: OrderDetail | null = null;
    const ok = await act.run(async () => {
      created = await api<OrderDetail>('/orders', { method: 'POST', body: form });
    });
    if (ok && created) {
      refresh();
      router.push('/orders/' + (created as OrderDetail).order.id);
    }
  }

  return (
    <div className="detail">
      <form className="sheet" onSubmit={submit}>
        <h2>받는 분</h2>
        <Note kind="error">{act.error}</Note>
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
        <div className="field">
          <label htmlFor="cash">현금영수증 번호</label>
          <input id="cash" maxLength={30} value={form.cashReceiptNo} onChange={set('cashReceiptNo')} />
          <p className="hint">원하면 휴대폰 번호나 사업자번호를 적어 주세요. 현금영수증은 각 판매자가 발행합니다.</p>
        </div>
        <button className="btn yellow" disabled={act.busy}>{won(cart.total)} 주문하기</button>
      </form>

      <div className="sheet">
        <h2>주문할 물건</h2>
        {cart.groups.map((g) => (
          <div key={g.sellerId} style={{ marginBottom: 14 }}>
            <h3>{g.shopName}</h3>
            <table>
              <tbody>
                {g.lines.map((l) => (
                  <tr key={l.productId}><td>{l.name} × {l.qty}</td><td className="num">{won(l.lineTotal)}</td></tr>
                ))}
                <tr><td>배송비</td><td className="num">{g.hasPhysical ? won(g.shipFee) : '없음'}</td></tr>
                <tr><td><strong>이 가게에 보낼 금액</strong></td><td className="num"><strong>{won(g.total)}</strong></td></tr>
              </tbody>
            </table>
          </div>
        ))}
        <p className="total right">모두 <b>{won(cart.total)}</b></p>
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
