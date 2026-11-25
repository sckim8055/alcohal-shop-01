'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { Note, ProductCard, useAction } from '@/components/Bits';
import { Pager } from '@/components/Pager';
import { api } from '@/lib/api';
import { PRODUCT_STATUS_LABEL } from '@/lib/format';
import type { Paged, Product } from '@/lib/types';

export default function MyWishlist() {
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paged<Product> | null>(null);
  const act = useAction();

  const load = useCallback(() => {
    api<Paged<Product>>('/my/wishlist?page=' + page).then(setData).catch((e) => { act.setError(e.message); setData({ items: [], page: 1, size: 24, total: 0 }); });
  }, [page]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(load, [load]);

  async function unwish(p: Product) {
    const ok = await act.run(() => api('/wishlist/' + p.id + '/remove', { method: 'POST' }), `'${p.name}' 찜을 풀었습니다.`);
    if (!ok) return;
    // 이 쪽의 마지막 상품을 풀었으면 앞 쪽으로 간다.
    if (data && data.items.length === 1 && page > 1) setPage(page - 1); else load();
  }

  return (
    <>
      <div className="title"><h1>찜한 상품</h1></div>
      <Note kind="error">{act.error}</Note>
      <Note kind="ok">{act.done}</Note>
      {!data ? <p className="muted">불러오는 중입니다.</p> : data.items.length === 0 ? (
        <div className="sheet">
          <h3>찜한 상품이 없습니다</h3>
          <p>마음에 드는 상품 화면에서 “찜하기”를 누르면 여기에 모아 둡니다.</p>
          <Link className="btn" href="/">장 보러 가기</Link>
        </div>
      ) : (
        <>
          <div className="stalls">
            {data.items.map((p) => (
              <div key={p.id}>
                <ProductCard p={p} />
                <div className="row" style={{ marginTop: 8, justifyContent: 'space-between' }}>
                  {p.status !== 'on_sale' ? <span className="stamp off">{PRODUCT_STATUS_LABEL[p.status] ?? '판매 안 함'}</span> : <span />}
                  <button className="btn plain small" type="button" disabled={act.busy} onClick={() => unwish(p)} aria-label={`'${p.name}' 찜 풀기`}>찜 풀기</button>
                </div>
              </div>
            ))}
          </div>
          <Pager page={data.page} size={data.size} total={data.total} onPage={setPage} />
        </>
      )}
    </>
  );
}
