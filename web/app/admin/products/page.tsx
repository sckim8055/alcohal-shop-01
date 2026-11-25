'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense, useCallback, useEffect, useState } from 'react';
import { Note, useAction } from '@/components/Bits';
import { Pager } from '@/components/Pager';
import { api } from '@/lib/api';
import { PRODUCT_STATUS_LABEL, won } from '@/lib/format';
import type { Paged, Product } from '@/lib/types';

const TABS: [string, string][] = [['', '전체'], ['on_sale', '판매 중'], ['paused', '판매 중지'], ['draft', '임시저장'], ['ended', '판매 종료'], ['blocked', '운영자 차단']];

function List() {
  const [status, setStatus] = useState(useSearchParams().get('status') ?? '');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paged<Product> | null>(null);
  const act = useAction();
  const load = useCallback(() => {
    api<Paged<Product>>(`/admin/products?status=${status}&q=${encodeURIComponent(q)}&page=${page}`).then(setData).catch((e) => act.setError(e.message));
  }, [status, q, page]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(load, [load]);
  const block = (p: Product, blocked: boolean) =>
    act.run(() => api(`/admin/products/${p.id}/block`, { method: 'POST', body: { blocked } }), blocked ? '판매를 막고 판매자에게 알렸습니다.' : '차단을 풀고 판매자에게 알렸습니다.').then((ok) => ok && load());

  return (
    <>
      <div className="title"><h1>상품</h1></div>
      <Note kind="error">{act.error}</Note>
      <Note kind="ok">{act.done}</Note>
      <div className="tabs" role="group" aria-label="상품 상태">
        {TABS.map(([v, label]) => <button key={v} type="button" className="chip" aria-current={status === v} onClick={() => { setStatus(v); setPage(1); }}>{label}</button>)}
      </div>
      <form className="filters" onSubmit={(e) => { e.preventDefault(); setPage(1); setQ(new FormData(e.currentTarget).get('q') as string); }}>
        <input name="q" placeholder="상품 이름" aria-label="상품 이름으로 찾기" />
        <button className="btn plain small">찾기</button>
      </form>
      {!data ? <p className="muted">불러오는 중입니다.</p> : data.items.length === 0 ? <div className="sheet"><h3>해당하는 상품이 없습니다</h3></div> : (
        <div className="sheet scroll">
          <table>
            <thead><tr><th>상품</th><th>가게</th><th className="num">값</th><th className="num">재고</th><th>상태</th><th /></tr></thead>
            <tbody>
              {data.items.map((p) => (
                <tr key={p.id}>
                  <td>{p.status === 'on_sale' ? <Link href={'/products/' + p.id}>{p.name}</Link> : p.name}<div className="muted small">{p.topCategoryName === p.categoryName ? p.categoryName : p.topCategoryName + ' > ' + p.categoryName}</div></td>
                  <td>{p.shopName}</td>
                  <td className="num">{won(p.currentPrice)}</td>
                  <td className="num">{p.stock}</td>
                  <td>{p.status === 'blocked' ? <strong style={{ color: 'var(--marker)' }}>운영자 차단</strong> : PRODUCT_STATUS_LABEL[p.status]}</td>
                  <td className="right">
                    {p.status === 'blocked'
                      ? <button type="button" className="btn small" disabled={act.busy} onClick={() => block(p, false)}>차단 풀기</button>
                      : <button type="button" className="btn plain small" disabled={act.busy} onClick={() => window.confirm(`'${p.name}' 상품의 판매를 막을까요?`) && block(p, true)}>판매 막기</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pager page={data.page} size={data.size} total={data.total} onPage={setPage} />
        </div>
      )}
    </>
  );
}

export default function AdminProducts() {
  return <Suspense fallback={null}><List /></Suspense>;
}
