'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { Note, useAction } from '@/components/Bits';
import { useMe } from '@/components/Me';
import { api } from '@/lib/api';

/** 찜하기 / 찜 풀기. 로그인하지 않았으면 로그인 링크를 보여 준다. */
export function WishButton({ productId }: { productId: number }) {
  const { me, loading } = useMe();
  const loggedIn = Boolean(me?.user);
  const [wished, setWished] = useState<boolean | null>(null);
  const act = useAction();

  useEffect(() => {
    if (!loggedIn) return;
    api<number[]>('/wishlist/ids')
      .then((ids) => setWished(Array.isArray(ids) && ids.includes(productId)))
      .catch(() => setWished(false));
  }, [loggedIn, productId]);

  if (loading) return null;
  if (!loggedIn) {
    return <p><Link className="btn plain" href={'/login?next=' + encodeURIComponent('/products/' + productId)}>로그인하고 찜하기</Link></p>;
  }

  async function toggle() {
    const next = !wished;
    let res: { wished?: boolean } | null = null;
    const ok = await act.run(async () => {
      res = await api<{ wished: boolean }>('/wishlist/' + productId + (next ? '' : '/remove'), { method: 'POST' });
    }, next ? '찜한 상품에 넣었습니다.' : '찜을 풀었습니다.');
    if (ok) setWished((res as { wished?: boolean } | null)?.wished ?? next);
  }

  return (
    <div>
      <Note kind="error">{act.error}</Note>
      <div className="row">
        <button className="btn plain" type="button" aria-pressed={wished === true} disabled={act.busy || wished === null} onClick={toggle}>
          {wished ? '♥ 찜 풀기' : '♡ 찜하기'}
        </button>
        {act.done && <span className="small" role="status">{act.done} {wished && <Link href="/my/wishlist">찜한 상품 보기</Link>}</span>}
      </div>
    </div>
  );
}
