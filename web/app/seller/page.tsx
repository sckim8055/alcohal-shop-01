'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useMe } from '@/components/Me';
import { Apply } from '@/components/SellerForms';
import { api } from '@/lib/api';
import type { Invoice, Seller, SellerTodo } from '@/lib/types';

type Count = { status: string; count: number };
export type SellerDashboard = { seller: Seller; orderCounts: Count[]; productCounts: Count[]; invoices: Invoice[]; todo?: SellerTodo };

export default function SellerHome() {
  const { me, refresh } = useMe();
  const [d, setD] = useState<SellerDashboard | null>(null);
  useEffect(() => {
    if (me?.seller) api<SellerDashboard>('/seller').then(setD).catch(() => setD(null));
  }, [me?.seller]);

  if (!me?.seller) return <Apply onDone={refresh} />;
  const s = me.seller;
  if (s.status !== 'approved') {
    return (
      <div className="sheet narrow">
        <h2>{s.shopName}</h2>
        <p>{s.status === 'pending' ? '입점 심사 중입니다. 운영자가 확인하면 알림으로 알려 드립니다.' : '입점 신청이 반려되었습니다. 운영자에게 문의해 주세요.'}</p>
      </div>
    );
  }
  if (!d) return <p className="muted">불러오는 중입니다.</p>;
  const of = (list: Count[], st: string) => list.find((c) => c.status === st)?.count ?? 0;
  const unpaid = d.invoices.filter((i) => i.status === 'issued').length;
  const todo = { claimsRequested: d.todo?.claimsRequested ?? 0, returnsApproved: d.todo?.returnsApproved ?? 0, soldOut: d.todo?.soldOut ?? 0, shipLate: d.todo?.shipLate ?? 0, inquiriesUnanswered: d.todo?.inquiriesUnanswered ?? 0, reviewsUnanswered: d.todo?.reviewsUnanswered ?? 0 };

  return (
    <div className="stack">
      <div className="title"><h1>{s.shopName}</h1></div>
      <div className="figures">
        <Link className="figure" href="/seller/orders?status=awaiting_deposit"><b>{of(d.orderCounts, 'awaiting_deposit')}</b>입금을 기다리는 주문</Link>
        <Link className="figure" href="/seller/orders?status=paid"><b>{of(d.orderCounts, 'paid')}</b>보내야 할 주문</Link>
        <Link className="figure" href="/seller/orders?status=shipped"><b>{of(d.orderCounts, 'shipped')}</b>배송 중</Link>
        <Link className="figure" href="/seller/products?status=on_sale"><b>{of(d.productCounts, 'on_sale')}</b>판매 중인 상품</Link>
        <Link className="figure" href="/seller/invoices"><b>{unpaid}</b>내야 할 청구서</Link>
        <Link className="figure" href="/seller/claims?status=requested"><b>{todo.claimsRequested}</b>처리할 신청</Link>
        <Link className="figure" href="/seller/products?status=on_sale"><b>{todo.soldOut}</b>품절 상품</Link>
        <Link className="figure" href="/seller/orders?status=paid"><b>{todo.shipLate}</b>발송이 늦은 주문</Link>
        <Link className="figure" href="/seller/inquiries"><b>{todo.inquiriesUnanswered}</b>답할 문의</Link>
        <Link className="figure" href="/seller/reviews"><b>{todo.reviewsUnanswered}</b>답글 없는 리뷰</Link>
      </div>
      {todo.returnsApproved > 0 && <div className="note">승인한 반품·교환 {todo.returnsApproved}건이 마무리를 기다립니다. 물건을 돌려받았다면 <Link href="/seller/claims?status=approved">처리를 마쳐 주세요</Link>.</div>}
      {todo.shipLate > 0 && <div className="note">입금을 확인한 지 3일이 지났는데 아직 보내지 않은 주문이 {todo.shipLate}건 있습니다. <Link href="/seller/orders?status=paid">보낼 주문 보기</Link></div>}
      {todo.soldOut > 0 && <div className="note">판매 중인데 재고가 0인 상품이 {todo.soldOut}개 있습니다. 상품 관리에서 재고를 채우거나 판매를 멈춰 주세요. <Link href="/seller/products?status=on_sale">판매 중인 상품 보기</Link></div>}
      {!s.licenseVerified && <div className="note">주류 면허가 확인되지 않아 술은 올릴 수 없습니다. 술을 팔려면 운영자에게 면허 확인을 요청해 주세요.</div>}
      {!s.bankAccount && <div className="note">입금받을 계좌가 비어 있습니다. <Link href="/seller/settings">가게 설정</Link>에서 계좌를 적어 주세요.</div>}
      {of(d.productCounts, 'blocked') > 0 && <div className="note error">운영자가 판매를 막은 상품이 {of(d.productCounts, 'blocked')}개 있습니다. <Link href="/seller/products?status=blocked">확인하기</Link></div>}
    </div>
  );
}
