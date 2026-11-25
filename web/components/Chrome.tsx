'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { api } from '@/lib/api';
import { useMe } from './Me';

export function Header() {
  const { me, refresh } = useMe();
  const path = usePathname();
  const router = useRouter();
  const user = me?.user;
  const cur = (href: string) => (path === href || (href !== '/' && path.startsWith(href)) ? 'page' : undefined);

  async function logout() {
    await api('/auth/logout', { method: 'POST' }).catch(() => null);
    await refresh();
    router.push('/');
  }

  return (
    <header>
      <div className="awning" aria-hidden="true" />
      <div className="awning-edge" aria-hidden="true" />
      <div className="wrap masthead">
        <Link href="/" className="sign">
          술장터
          <small>술과 술에 관한 모든 것</small>
        </Link>
        <nav className="nav" aria-label="주 메뉴">
          <Link href="/" aria-current={cur('/')}>장 보기</Link>
          <Link href="/board" aria-current={cur('/board')}>이야기</Link>
          <Link href="/coupons" aria-current={cur('/coupons')}>쿠폰</Link>
          {user && (
            <>
              <Link href="/cart" aria-current={cur('/cart')}>
                장바구니{me!.cartCount > 0 && <span className="count">{me!.cartCount}</span>}
              </Link>
              <Link href="/my/notifications" aria-current={cur('/my/notifications')}>
                알림{me!.unreadCount > 0 && <span className="count">{me!.unreadCount}</span>}
              </Link>
              <Link href="/my/orders" aria-current={path.startsWith('/my') && !path.startsWith('/my/notifications') || path.startsWith('/orders') ? 'page' : undefined}>마이</Link>
              <Link href="/seller" aria-current={cur('/seller')}>{me!.seller ? '내 가게' : '입점 신청'}</Link>
              {user.role === 'admin' && <Link href="/admin" aria-current={cur('/admin')}>운영</Link>}
              <span className="hello">{user.name} 님</span>
              <button type="button" onClick={logout}>로그아웃</button>
            </>
          )}
          {!user && (
            <>
              <Link href="/login" aria-current={cur('/login')}>로그인</Link>
              <Link href="/register" aria-current={cur('/register')}>가입</Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}

export function Footer() {
  return (
    <footer className="foot">
      <div className="wrap">
        <p><strong>술장터</strong></p>
        <p>19세 미만에게는 술을 팔지 않습니다. 주류는 주문할 때 성인인증을 확인합니다.</p>
        <p>
          술장터는 판매자와 구매자를 이어 주는 장터입니다. 대금은 구매자가 판매자에게 직접 보내며,
          상품과 배송, 환불의 책임은 각 판매자에게 있습니다.
        </p>
        <p>지금은 시범 운영 중이라 가게와 상품은 모두 예시입니다.</p>
      </div>
    </footer>
  );
}
