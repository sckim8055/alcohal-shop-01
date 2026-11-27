'use client';

import { usePathname, useSearchParams } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';

// 링크를 누른 순간 천막 아래에 진행 막대를 띄운다.
// 다음 화면을 서버에서 그리는 동안 "눌렀는데 아무 일도 없다"고 느끼지 않게 하려는 것이다.
// Next.js 앱 라우터에는 이동 시작을 알려 주는 기능이 없어서, 내부 링크 클릭을 보고 시작하고 주소가 바뀌면 끝낸다.
export function NavProgress() {
  const path = usePathname();
  const search = useSearchParams();
  const [state, setState] = useState<'idle' | 'busy' | 'done'>('idle');
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const where = path + '?' + search.toString();
  const last = useRef(where);

  useEffect(() => {
    function onClick(e: MouseEvent) {
      if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
      const a = (e.target as Element | null)?.closest?.('a');
      if (!a || a.target === '_blank' || a.hasAttribute('download')) return;
      const url = new URL(a.href, location.href);
      if (url.origin !== location.origin || !url.pathname.startsWith('/') || url.pathname.startsWith('/api/')) return;
      // 같은 화면 안의 이동(#)이나 지금 보고 있는 주소는 기다릴 것이 없다.
      if (url.pathname === location.pathname && url.search === location.search) return;
      start();
    }
    function onSubmit(e: SubmitEvent) {
      const f = e.target as HTMLFormElement;
      // 검색처럼 주소를 바꾸는 GET 폼만. 다른 폼은 각 화면이 단추에 진행 상태를 보여 준다.
      if (!e.defaultPrevented && (f.method || 'get').toLowerCase() === 'get' && f.action.startsWith(location.origin)) start();
    }
    document.addEventListener('click', onClick, true);
    document.addEventListener('submit', onSubmit);
    return () => {
      document.removeEventListener('click', onClick, true);
      document.removeEventListener('submit', onSubmit);
    };
  }, []);

  function start() {
    if (timer.current) clearTimeout(timer.current);
    setState('busy');
    // 이동이 취소되거나 실패해도 막대가 계속 남지 않게 한다.
    timer.current = setTimeout(() => setState('idle'), 15000);
  }

  // 주소는 빈 자리 화면(loading.tsx)이 뜨는 순간 이미 바뀐다. 그래서 주소가 바뀐 뒤에도
  // 화면에 빈 자리(.loading)가 남아 있으면 내용이 다 들어올 때까지 막대를 유지한다.
  useEffect(() => {
    if (last.current === where) return;
    last.current = where;
    if (state !== 'busy') return;
    const finish = () => {
      if (document.querySelector('.loading')) return false;
      observer.disconnect();
      if (timer.current) clearTimeout(timer.current);
      setState('done');
      timer.current = setTimeout(() => setState('idle'), 400);
      return true;
    };
    const observer = new MutationObserver(() => { finish(); });
    if (!finish()) observer.observe(document.body, { childList: true, subtree: true });
    return () => observer.disconnect();
  }, [where, state]);

  return <div className={'nav-progress ' + state} aria-hidden="true" />;
}
