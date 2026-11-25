'use client';

import { NeedLogin } from '@/components/Bits';
import { useMe } from '@/components/Me';
import { WithSideMenu } from '@/components/SideMenu';

function Inner({ children }: { children: React.ReactNode }) {
  const { me } = useMe();
  if (me?.user?.role !== 'admin') {
    return <div className="sheet narrow"><h2>운영자만 볼 수 있습니다</h2><p>운영자 계정으로 로그인해 주세요.</p></div>;
  }
  return (
    <WithSideMenu title="운영" groups={[
      { title: '현황', items: [{ href: '/admin', label: '오늘의 장터' }] },
      { title: '사람', items: [{ href: '/admin/members', label: '회원' }, { href: '/admin/sellers', label: '판매자·입점 심사' }] },
      { title: '상품', items: [{ href: '/admin/products', label: '상품' }, { href: '/admin/categories', label: '분류' }] },
      { title: '주문과 정산', items: [{ href: '/admin/orders', label: '주문' }, { href: '/admin/commission', label: '수수료율' }, { href: '/admin/invoices', label: '수수료 청구' }] },
    ]}>
      {children}
    </WithSideMenu>
  );
}

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return <NeedLogin><Inner>{children}</Inner></NeedLogin>;
}
