'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';

type Summary = { members: number; pendingSellers: number; productsOnSale: number; awaitingDeposit: number; toShip: number; unpaidInvoices: number; claimsOpen?: number; inquiriesUnanswered?: number };

export default function AdminHome() {
  const [s, setS] = useState<Summary | null>(null);
  useEffect(() => {
    api<Summary>('/admin/summary').then(setS).catch(() => setS(null));
  }, []);
  return (
    <>
      <div className="title"><h1>오늘의 장터</h1></div>
      {!s ? <p className="muted">불러오는 중입니다.</p> : (
        <div className="figures">
          <Link className="figure" href="/admin/sellers"><b>{s.pendingSellers}</b>심사를 기다리는 가게</Link>
          <Link className="figure" href="/admin/orders?status=awaiting_deposit"><b>{s.awaitingDeposit}</b>입금을 기다리는 주문</Link>
          <Link className="figure" href="/admin/orders?status=paid"><b>{s.toShip}</b>발송을 기다리는 주문</Link>
          <Link className="figure" href="/admin/claims"><b>{s.claimsOpen ?? 0}</b>진행 중인 취소·반품·교환</Link>
          <Link className="figure" href="/admin/inquiries"><b>{s.inquiriesUnanswered ?? 0}</b>답할 1:1 문의</Link>
          <Link className="figure" href="/admin/invoices"><b>{s.unpaidInvoices}</b>납부 전 청구서</Link>
          <Link className="figure" href="/admin/products?status=on_sale"><b>{s.productsOnSale}</b>판매 중인 상품</Link>
          <Link className="figure" href="/admin/members"><b>{s.members}</b>회원</Link>
        </div>
      )}
    </>
  );
}
