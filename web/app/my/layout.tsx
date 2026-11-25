'use client';

import { NeedLogin } from '@/components/Bits';
import { useMe } from '@/components/Me';
import { WithSideMenu } from '@/components/SideMenu';

export default function MyLayout({ children }: { children: React.ReactNode }) {
  const { me } = useMe();
  return (
    <NeedLogin>
      <WithSideMenu title="마이" groups={[
        { title: '쇼핑', items: [{ href: '/my/orders', label: '주문목록·배송조회' }, { href: '/cart', label: '장바구니', badge: me?.cartCount || undefined }] },
        { title: '활동', items: [{ href: '/my/notifications', label: '알림', badge: me?.unreadCount || undefined }, { href: '/board', label: '이야기 게시판' }] },
        { title: '내 정보', items: [{ href: '/my/profile', label: '개인정보 확인·수정' }, { href: '/verify', label: '성인인증' }] },
      ]}>
        {children}
      </WithSideMenu>
    </NeedLogin>
  );
}
