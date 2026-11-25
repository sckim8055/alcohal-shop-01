'use client';

import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { api, setUnauthorizedHandler } from '@/lib/api';
import type { Me } from '@/lib/types';

type MeState = { me: Me | null; loading: boolean; refresh: () => Promise<Me | null> };

const Ctx = createContext<MeState>({ me: null, loading: true, refresh: async () => null });

/** 로그인 상태를 화면 전체에서 함께 쓴다. */
export function MeProvider({ children }: { children: React.ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);
  const refresh = useCallback(async () => {
    try {
      const m = await api<Me>('/me');
      setMe(m);
      return m;
    } catch {
      setMe(null);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => {
    refresh();
  }, [refresh]);

  // 쓰는 도중에 로그인이 풀리면(시간 초과, 다른 기기에서 비밀번호 변경, 이용 정지) 로그인 화면으로 보낸다.
  const router = useRouter();
  const path = usePathname();
  const loggedIn = Boolean(me?.user);
  useEffect(() => {
    setUnauthorizedHandler(() => {
      if (!loggedIn) return;
      setMe((m) => (m ? { ...m, user: null, seller: null, cartCount: 0, unreadCount: 0 } : m));
      router.push('/login?expired=1&next=' + encodeURIComponent(path));
    });
    return () => setUnauthorizedHandler(null);
  }, [loggedIn, path, router]);
  return <Ctx.Provider value={{ me, loading, refresh }}>{children}</Ctx.Provider>;
}

export const useMe = () => useContext(Ctx);
