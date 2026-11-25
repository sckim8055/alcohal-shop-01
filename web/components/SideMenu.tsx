'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';

export type MenuGroup = { title: string; items: { href: string; label: string; badge?: number }[] };

/** 왼쪽 메뉴 + 오른쪽 내용. 구매자·판매자·운영자 화면이 같이 쓴다. */
export function WithSideMenu({ title, groups, children }: { title: string; groups: MenuGroup[]; children: React.ReactNode }) {
  const path = usePathname();
  // 가장 길게 맞는 메뉴 하나만 현재 위치로 표시한다.
  const all = groups.flatMap((g) => g.items.map((i) => i.href));
  const current = all.filter((h) => path === h || path.startsWith(h + '/')).sort((a, b) => b.length - a.length)[0];
  return (
    <div className="with-side">
      <nav className="side" aria-label={title + ' 메뉴'}>
        <div className="side-title">{title}</div>
        {groups.map((g) => (
          <div className="side-group" key={g.title}>
            <div className="side-head">{g.title}</div>
            <ul>
              {g.items.map((i) => (
                <li key={i.href}>
                  <Link href={i.href} aria-current={i.href === current ? 'page' : undefined}>
                    {i.label}{i.badge ? <span className="count">{i.badge}</span> : null}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </nav>
      <div className="side-body">{children}</div>
    </div>
  );
}
