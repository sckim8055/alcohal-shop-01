'use client';

import { NeedLogin } from '@/components/Bits';
import { useMe } from '@/components/Me';
import { WithSideMenu } from '@/components/SideMenu';

/** 승인된 판매자에게만 왼쪽 메뉴를 보여 준다. 신청 전이거나 심사 중이면 내용만 보여 준다. */
function Inner({ children }: { children: React.ReactNode }) {
  const { me } = useMe();
  if (me?.seller?.status !== 'approved') return <>{children}</>;
  return (
    <WithSideMenu title="내 가게" groups={[
      { title: '가게', items: [{ href: '/seller', label: '가게 현황' }, { href: '/seller/settings', label: '가게 설정' }] },
      { title: '상품', items: [{ href: '/seller/products', label: '상품 관리' }, { href: '/seller/products/new', label: '상품 올리기' }, { href: '/seller/coupons', label: '쿠폰' }] },
      { title: '주문과 정산', items: [{ href: '/seller/orders', label: '주문 처리' }, { href: '/seller/claims', label: '취소·반품·교환' }, { href: '/seller/invoices', label: '수수료 청구서' }] },
    ]}>
      {children}
    </WithSideMenu>
  );
}

export default function SellerLayout({ children }: { children: React.ReactNode }) {
  return <NeedLogin><Inner>{children}</Inner></NeedLogin>;
}
