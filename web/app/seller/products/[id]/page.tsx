'use client';

import { useParams, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { Note, useAction } from '@/components/Bits';
import { MediaEditor } from '@/components/MediaEditor';
import { RichEditor } from '@/components/RichEditor';
import { api } from '@/lib/api';
import { categoryTree } from '@/lib/format';
import type { Category, Media, ProductDetail } from '@/lib/types';

const EMPTY = { topId: '', categoryId: '', name: '', productType: 'physical', price: '', salePrice: '', saleStart: '', saleEnd: '', stock: '', status: 'on_sale', abv: '', volumeMl: '', origin: '' };

export default function ProductForm() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const isNew = id === 'new';
  const act = useAction();
  const [cats, setCats] = useState<Category[]>([]);
  const [f, setF] = useState(EMPTY);
  const [media, setMedia] = useState<Media[]>([]);
  const [doc, setDoc] = useState<object | null>(null);
  const [initialDoc, setInitialDoc] = useState<string | null>(null);
  const [plain, setPlain] = useState('');
  const [blocked, setBlocked] = useState(false);
  const [ready, setReady] = useState(isNew);
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value });

  useEffect(() => {
    (async () => {
      const list = await api<Category[]>('/categories').catch(() => [] as Category[]);
      setCats(list);
      if (isNew) return;
      try {
        const d = await api<ProductDetail>('/seller/products/' + id);
        const p = d.product;
        const cat = list.find((c) => c.id === p.categoryId);
        setF({
          topId: String(cat?.parentId ?? p.categoryId), categoryId: String(p.categoryId), name: p.name, productType: p.productType,
          price: String(p.price), salePrice: p.salePrice == null ? '' : String(p.salePrice), saleStart: p.saleStart ?? '', saleEnd: p.saleEnd ?? '',
          stock: String(p.stock), status: p.status === 'blocked' ? 'paused' : p.status,
          abv: p.abv == null ? '' : String(p.abv), volumeMl: p.volumeMl == null ? '' : String(p.volumeMl), origin: p.origin,
        });
        setMedia(d.media);
        setPlain(p.description);
        setInitialDoc(d.descriptionDoc);
        if (d.descriptionDoc) setDoc(JSON.parse(d.descriptionDoc));
        setBlocked(p.status === 'blocked');
        setReady(true);
      } catch {
        act.setError('상품을 찾을 수 없습니다.');
      }
    })();
  }, [id, isNew]); // eslint-disable-line react-hooks/exhaustive-deps

  const tree = categoryTree(cats);
  const top = tree.find((t) => String(t.top.id) === f.topId);
  const alcohol = top?.top.alcohol ?? false;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const body = {
      categoryId: Number(f.categoryId || f.topId), name: f.name, productType: alcohol ? 'physical' : f.productType,
      price: Number(f.price), stock: Number(f.stock), salePrice: f.salePrice === '' ? null : Number(f.salePrice),
      saleStart: f.saleStart, saleEnd: f.saleEnd, status: f.status,
      abv: f.abv === '' ? null : Number(f.abv), volumeMl: f.volumeMl === '' ? null : Number(f.volumeMl), origin: f.origin,
      // 편집기로 쓴 내용이 없으면 예전에 글자로만 쓴 설명을 그대로 둔다.
      descriptionDoc: doc, description: doc ? '' : plain, media,
    };
    const ok = await act.run(() => api(isNew ? '/seller/products' : '/seller/products/' + id, { method: isNew ? 'POST' : 'PUT', body }));
    if (ok) router.push('/seller/products');
  }

  return (
    <div style={{ maxWidth: 820 }}>
      <div className="title"><h1>{isNew ? '상품 올리기' : '상품 고치기'}</h1></div>
      <Note kind="error">{act.error}</Note>
      {blocked && <Note kind="error">운영자가 판매를 막은 상품입니다. 내용은 고칠 수 있지만 판매 상태는 운영자가 풀어야 바꿀 수 있습니다.</Note>}
      {ready && (
        <form className="stack" onSubmit={submit}>
          <section className="sheet">
            <h2>기본 정보</h2>
            <div className="field"><label htmlFor="name">상품 이름</label><input id="name" required maxLength={80} value={f.name} onChange={set('name')} /></div>
            <div className="fields">
              <div className="field">
                <label htmlFor="top">대분류</label>
                <select id="top" required value={f.topId} onChange={(e) => setF({ ...f, topId: e.target.value, categoryId: '' })}>
                  <option value="">고르기</option>
                  {tree.map((t) => <option key={t.top.id} value={t.top.id}>{t.top.name}{t.top.alcohol ? ' (면허 필요)' : ''}</option>)}
                </select>
              </div>
              <div className="field">
                <label htmlFor="cat">중분류</label>
                <select id="cat" value={f.categoryId === f.topId ? '' : f.categoryId} onChange={set('categoryId')} disabled={!top || top.children.length === 0}>
                  <option value="">{top && top.children.length === 0 ? '없음' : '고르기'}</option>
                  {top?.children.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </div>
              <div className="field">
                <label htmlFor="type">파는 방식</label>
                <select id="type" value={alcohol ? 'physical' : f.productType} onChange={set('productType')} disabled={alcohol}>
                  <option value="physical">택배로 보내는 물건</option>
                  <option value="ticket">수업·이용권 (배송 없음)</option>
                </select>
              </div>
            </div>
            <p className="hint">맞는 분류가 없으면 운영자에게 분류를 추가해 달라고 요청해 주세요.</p>
            <div className="fields">
              <div className="field"><label htmlFor="abv">도수(%)</label><input id="abv" type="number" min={0} max={99} step={0.1} value={f.abv} onChange={set('abv')} /></div>
              <div className="field"><label htmlFor="vol">용량(ml)</label><input id="vol" type="number" min={1} value={f.volumeMl} onChange={set('volumeMl')} /></div>
              <div className="field"><label htmlFor="origin">원재료·원산지</label><input id="origin" maxLength={60} placeholder="예: 국내산 쌀" value={f.origin} onChange={set('origin')} /></div>
            </div>
            <p className="hint">도수와 용량은 술일 때만 적으면 됩니다. 적은 값은 상품 화면의 표에 나옵니다.</p>
          </section>

          <section className="sheet">
            <h2>값과 재고</h2>
            <div className="fields">
              <div className="field"><label htmlFor="price">값(원)</label><input id="price" type="number" min={0} required value={f.price} onChange={set('price')} /></div>
              <div className="field"><label htmlFor="stock">재고</label><input id="stock" type="number" min={0} required value={f.stock} onChange={set('stock')} /></div>
            </div>
            <div className="fields">
              <div className="field"><label htmlFor="sale">할인가(원)</label><input id="sale" type="number" min={0} value={f.salePrice} onChange={set('salePrice')} /></div>
              <div className="field"><label htmlFor="ss">할인 시작</label><input id="ss" type="date" value={f.saleStart} onChange={set('saleStart')} /></div>
              <div className="field"><label htmlFor="se">할인 끝</label><input id="se" type="date" value={f.saleEnd} onChange={set('saleEnd')} /></div>
            </div>
            <p className="hint">할인 기간을 비워 두면 할인가가 계속 적용됩니다. 재고가 0이면 품절로 표시됩니다.</p>
          </section>

          <section className="sheet">
            <h2>사진과 동영상</h2>
            <MediaEditor value={media} onChange={setMedia} />
          </section>

          <section className="sheet">
            <h2>상품 설명</h2>
            <RichEditor key={initialDoc ?? 'new'} initial={initialDoc} onChange={setDoc} />
            {!doc && plain && <p className="hint">지금은 예전에 글자로만 쓴 설명이 보입니다: “{plain.slice(0, 80)}” 위 편집기에 새로 쓰면 그 내용으로 바뀝니다.</p>}
          </section>

          <section className="sheet">
            <h2>판매 상태</h2>
            <div className="field" style={{ maxWidth: 280 }}>
              <label htmlFor="status">상태</label>
              <select id="status" value={f.status} onChange={set('status')} disabled={blocked}>
                <option value="on_sale">판매 중 (바로 좌판에 올림)</option>
                <option value="draft">임시저장 (아직 올리지 않음)</option>
                <option value="paused">판매 중지 (잠시 내림)</option>
                <option value="ended">판매 종료</option>
              </select>
            </div>
            <button className="btn" disabled={act.busy}>{isNew ? '올리기' : '저장하기'}</button>
          </section>
        </form>
      )}
    </div>
  );
}
