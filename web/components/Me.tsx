'use client';

import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api } from '@/lib/api';
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
  return <Ctx.Provider value={{ me, loading, refresh }}>{children}</Ctx.Provider>;
}

export const useMe = () => useContext(Ctx);
