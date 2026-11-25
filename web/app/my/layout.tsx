'use client';

import { NeedLogin } from '@/components/Bits';
import { useMe } from '@/components/Me';
import { WithSideMenu } from '@/components/SideMenu';

export default function MyLayout({ children }: { children: React.ReactNode }) {
  const { me } = useMe();
  return (
    <NeedLogin>
      <WithSideMenu title="마이" groups={[
        { title: '쇼핑', items: [{ href: '/my/orders', label: '주문목록·배송조회' }, { href: '/my/claims', label: '취소·반품·교환 내역' }, { href: '/my/coupons', label: '내 쿠폰' }, { href: '/cart', label: '장바구니', badge: me?.cartCount || undefined }] },
        { title: '활동', items: [{ href: '/my/wishlist', label: '찜한 상품' }, { href: '/my/notifications', label: '알림', badge: me?.unreadCount || undefined }, { href: '/board', label: '이야기 게시판' }] },
        { title: '내 정보', items: [{ href: '/my/profile', label: '개인정보 확인·수정' }, { href: '/my/addresses', label: '배송지 관리' }, { href: '/verify', label: '성인인증' }] },
      ]}>
        {children}
      </WithSideMenu>
    </NeedLogin>
  );
}
