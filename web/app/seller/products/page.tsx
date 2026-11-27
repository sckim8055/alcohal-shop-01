'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense, useCallback, useEffect, useState } from 'react';
import { Note, useAction } from '@/components/Bits';
import { Pager } from '@/components/Pager';
import { api } from '@/lib/api';
import { PRODUCT_STATUS_LABEL, won } from '@/lib/format';
import type { Paged, Product, ProductStatus } from '@/lib/types';
import { Loading } from '@/components/Loading';

const TABS: [string, string][] = [['', '전체'], ['on_sale', '판매 중'], ['paused', '판매 중지'], ['draft', '임시저장'], ['ended', '판매 종료'], ['blocked', '운영자 차단']];

function List() {
  const [status, setStatus] = useState(useSearchParams().get('status') ?? '');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paged<Product> | null>(null);
  const act = useAction();
  const load = useCallback(() => {
    api<Paged<Product>>(`/seller/products?status=${status}&q=${encodeURIComponent(q)}&page=${page}`).then(setData).catch((e) => act.setError(e.message));
  }, [status, q, page]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(load, [load]);

  const change = (p: Product, next: ProductStatus) =>
    act.run(() => api(`/seller/products/${p.id}/status`, { method: 'POST', body: { status: next } }), `'${p.name}' 상품을 ${PRODUCT_STATUS_LABEL[next]}(으)로 바꿨습니다.`).then((ok) => ok && load());

  return (
    <>
      <div className="spread title">
        <div><h1>상품 관리</h1></div>
        <Link className="btn" href="/seller/products/new">상품 올리기</Link>
      </div>
      <Note kind="error">{act.error}</Note>
      <Note kind="ok">{act.done}</Note>
      <div className="tabs" role="group" aria-label="상품 상태">
        {TABS.map(([v, label]) => <button key={v} type="button" className="chip" aria-current={status === v} onClick={() => { setStatus(v); setPage(1); }}>{label}</button>)}
      </div>
      <form className="filters" onSubmit={(e) => { e.preventDefault(); setPage(1); setQ(new FormData(e.currentTarget).get('q') as string); }}>
        <input name="q" placeholder="상품 이름" aria-label="상품 이름으로 찾기" />
        <button className="btn plain small">찾기</button>
      </form>
      {!data ? <Loading kind="table" /> : data.items.length === 0 ? (
        <div className="sheet"><h3>해당하는 상품이 없습니다</h3></div>
      ) : (
        <div className="sheet scroll">
          <table>
            <thead><tr><th>상품</th><th className="num">값</th><th className="num">재고</th><th>상태</th><th /></tr></thead>
            <tbody>
              {data.items.map((p) => (
                <tr key={p.id}>
                  <td><Link href={'/seller/products/' + p.id}>{p.name}</Link><div className="muted small">{p.topCategoryName === p.categoryName ? p.categoryName : p.topCategoryName + ' > ' + p.categoryName}</div></td>
                  <td className="num">{won(p.currentPrice)}{p.currentPrice < p.price && <div className="muted small"><s>{won(p.price)}</s></div>}</td>
                  <td className="num">{p.stock === 0 ? <strong style={{ color: 'var(--marker)' }}>품절</strong> : p.stock}</td>
                  <td>{PRODUCT_STATUS_LABEL[p.status]}</td>
                  <td className="right">
                    <span className="row" style={{ justifyContent: 'flex-end', gap: 6 }}>
                      {(p.status === 'paused' || p.status === 'draft' || p.status === 'ended') && <button type="button" className="btn small" disabled={act.busy} onClick={() => change(p, 'on_sale')}>판매 시작</button>}
                      {p.status === 'on_sale' && <button type="button" className="btn plain small" disabled={act.busy} onClick={() => change(p, 'paused')}>판매 중지</button>}
                      {(p.status === 'on_sale' || p.status === 'paused') && <button type="button" className="btn plain small" disabled={act.busy} onClick={() => change(p, 'ended')}>판매 종료</button>}
                      <Link className="btn plain small" href={'/seller/products/' + p.id}>고치기</Link>
                    </span>
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

export default function SellerProducts() {
  return <Suspense fallback={null}><List /></Suspense>;
}
