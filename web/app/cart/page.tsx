'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { NeedLogin, Note, useAction } from '@/components/Bits';
import { useMe } from '@/components/Me';
import { api } from '@/lib/api';
import { won } from '@/lib/format';
import type { Cart } from '@/lib/types';
import { Loading } from '@/components/Loading';

function CartView() {
  const { refresh } = useMe();
  const [cart, setCart] = useState<Cart | null>(null);
  const act = useAction();

  const load = useCallback(() => act.run(async () => setCart(await api<Cart>('/cart'))), []); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    load();
  }, [load]);

  async function setQty(productId: number, qty: number) {
    await act.run(async () => setCart(await api<Cart>('/cart/items/' + productId, { method: 'PUT', body: { qty } })));
    refresh();
  }

  if (!cart) return <><Note kind="error">{act.error}</Note><Loading kind="table" /></>;
  if (cart.groups.length === 0) {
    return (
      <div className="sheet">
        <h3>장바구니가 비어 있습니다</h3>
        <p>좌판을 둘러보고 마음에 드는 것을 담아 보세요.</p>
        <Link className="btn" href="/">장 보러 가기</Link>
      </div>
    );
  }

  return (
    <div className="stack">
      <Note kind="error">{act.error}</Note>
      {cart.groups.map((g) => (
        <section className="sheet" key={g.sellerId}>
          <h3>{g.shopName}</h3>
          <div className="scroll">
            <table>
              <thead>
                <tr><th>상품</th><th className="num">값</th><th>수량</th><th className="num">합계</th><th /></tr>
              </thead>
              <tbody>
                {g.lines.map((l) => (
                  <tr key={l.productId}>
                    <td>
                      <Link href={'/products/' + l.productId}>{l.name}</Link>
                      {!l.orderable && <div className="small" style={{ color: 'var(--marker)' }}>지금은 살 수 없는 상품입니다. 빼고 주문해 주세요.</div>}
                    </td>
                    <td className="num">{won(l.unitPrice)}</td>
                    <td>
                      <input type="number" min={1} max={99} defaultValue={l.qty} aria-label={l.name + ' 수량'} style={{ width: 76, padding: '4px 6px' }}
                        onBlur={(e) => {
                          const n = Math.max(1, Math.min(99, Number(e.target.value) || 1));
                          if (n !== l.qty) setQty(l.productId, n);
                        }} />
                    </td>
                    <td className="num">{won(l.lineTotal)}</td>
                    <td className="right"><button className="btn plain small" type="button" disabled={act.busy} onClick={() => setQty(l.productId, 0)}>빼기</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="right" style={{ marginTop: 10 }}>
            상품 {won(g.itemsTotal)} + 배송비 {g.hasPhysical ? won(g.shipFee) : '없음'} = <strong>{won(g.total)}</strong>
          </p>
        </section>
      ))}
      <div className="spread">
        <p className="total">모두 <b>{won(cart.total)}</b></p>
        <Link className="btn yellow" href="/checkout">주문하기</Link>
      </div>
      <p className="muted small">가게마다 따로 입금합니다. 주문한 뒤 가게별 계좌를 안내해 드립니다.</p>
    </div>
  );
}

export default function CartPage() {
  return (
    <>
      <div className="title"><h1>장바구니</h1></div>
      <NeedLogin><CartView /></NeedLogin>
    </>
  );
}
