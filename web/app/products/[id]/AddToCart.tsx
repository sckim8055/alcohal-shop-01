'use client';

import Link from 'next/link';
import { useState } from 'react';
import { Note, useAction } from '@/components/Bits';
import { useMe } from '@/components/Me';
import { api } from '@/lib/api';

export function AddToCart({ productId, stock }: { productId: number; stock: number }) {
  const { me, refresh } = useMe();
  const [qty, setQty] = useState(1);
  const act = useAction();

  if (stock === 0) return <p><span className="stamp off">다 팔렸어요</span></p>;
  if (!me?.user) {
    return <p><Link className="btn yellow" href="/login">로그인하고 담기</Link></p>;
  }

  async function add() {
    const ok = await act.run(() => api('/cart/items', { method: 'POST', body: { productId, qty } }), '장바구니에 담았습니다.');
    if (ok) refresh();
  }

  return (
    <div>
      <Note kind="error">{act.error}</Note>
      {act.done && <Note kind="ok">{act.done} <Link href="/cart">장바구니 보기</Link></Note>}
      <div className="row">
        <label htmlFor="qty" style={{ margin: 0 }}>수량</label>
        <input id="qty" type="number" min={1} max={Math.min(99, stock)} value={qty} style={{ width: 90 }}
          onChange={(e) => setQty(Math.max(1, Math.min(99, Number(e.target.value) || 1)))} />
        <button className="btn yellow" type="button" disabled={act.busy} onClick={add}>장바구니에 담기</button>
        <span className="muted small">남은 수량 {stock}</span>
      </div>
    </div>
  );
}
