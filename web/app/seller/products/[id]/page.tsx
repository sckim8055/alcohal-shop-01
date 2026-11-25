'use client';

import { useParams, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { NeedLogin, Note, useAction } from '@/components/Bits';
import { api } from '@/lib/api';
import type { Category, Product } from '@/lib/types';

const EMPTY = { categoryId: '', name: '', description: '', productType: 'physical', price: '', salePrice: '', saleStart: '', saleEnd: '', stock: '', status: 'active' };

function Form() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const isNew = id === 'new';
  const act = useAction();
  const [cats, setCats] = useState<Category[]>([]);
  const [f, setF] = useState(EMPTY);
  const [ready, setReady] = useState(isNew);
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value });

  useEffect(() => {
    api<Category[]>('/categories').then(setCats).catch(() => setCats([]));
    if (!isNew) {
      api<Product>('/seller/products/' + id).then((p) => {
        setF({
          categoryId: String(p.categoryId), name: p.name, description: p.description, productType: p.productType,
          price: String(p.price), salePrice: p.salePrice == null ? '' : String(p.salePrice),
          saleStart: p.saleStart ?? '', saleEnd: p.saleEnd ?? '', stock: String(p.stock), status: p.status,
        });
        setReady(true);
      }).catch(() => act.setError('상품을 찾을 수 없습니다.'));
    }
  }, [id, isNew]); // eslint-disable-line react-hooks/exhaustive-deps

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const body = {
      ...f, categoryId: Number(f.categoryId), price: Number(f.price), stock: Number(f.stock),
      salePrice: f.salePrice === '' ? null : Number(f.salePrice),
    };
    const ok = await act.run(() => api(isNew ? '/seller/products' : '/seller/products/' + id, { method: isNew ? 'POST' : 'PUT', body }));
    if (ok) router.push('/seller');
  }

  return (
    <div style={{ maxWidth: 720 }}>
      <div className="title"><h1>{isNew ? '상품 올리기' : '상품 고치기'}</h1></div>
      <Note kind="error">{act.error}</Note>
      {ready && (
        <form className="sheet" onSubmit={submit}>
          <div className="field"><label htmlFor="name">상품 이름</label><input id="name" required maxLength={80} value={f.name} onChange={set('name')} /></div>
          <div className="fields">
            <div className="field">
              <label htmlFor="cat">분류</label>
              <select id="cat" required value={f.categoryId} onChange={set('categoryId')}>
                <option value="">고르기</option>
                {cats.map((c) => <option key={c.id} value={c.id}>{c.name}{c.alcohol ? ' (면허 필요)' : ''}</option>)}
              </select>
            </div>
            <div className="field">
              <label htmlFor="type">파는 방식</label>
              <select id="type" value={f.productType} onChange={set('productType')}>
                <option value="physical">택배로 보내는 물건</option>
                <option value="ticket">수업·이용권 (배송 없음)</option>
              </select>
            </div>
          </div>
          <div className="field"><label htmlFor="desc">설명</label><textarea id="desc" maxLength={2000} value={f.description} onChange={set('description')} /></div>
          <div className="fields">
            <div className="field"><label htmlFor="price">값(원)</label><input id="price" type="number" min={0} required value={f.price} onChange={set('price')} /></div>
            <div className="field"><label htmlFor="stock">재고</label><input id="stock" type="number" min={0} required value={f.stock} onChange={set('stock')} /></div>
          </div>
          <div className="fields">
            <div className="field"><label htmlFor="sale">할인가(원)</label><input id="sale" type="number" min={0} value={f.salePrice} onChange={set('salePrice')} /></div>
            <div className="field"><label htmlFor="ss">할인 시작</label><input id="ss" type="date" value={f.saleStart} onChange={set('saleStart')} /></div>
            <div className="field"><label htmlFor="se">할인 끝</label><input id="se" type="date" value={f.saleEnd} onChange={set('saleEnd')} /></div>
          </div>
          <p className="hint">할인 기간을 비워 두면 할인가가 계속 적용됩니다.</p>
          <div className="field" style={{ maxWidth: 240 }}>
            <label htmlFor="status">공개</label>
            <select id="status" value={f.status} onChange={set('status')}>
              <option value="active">판매 중</option>
              <option value="hidden">숨김</option>
            </select>
          </div>
          <button className="btn" disabled={act.busy}>{isNew ? '올리기' : '저장하기'}</button>
        </form>
      )}
    </div>
  );
}

export default function ProductFormPage() {
  return <NeedLogin><Form /></NeedLogin>;
}
