'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import type { Product } from '@/lib/types';
import { ProductCard } from './Bits';

// 최근 본 상품은 서버에 저장하지 않고 이 브라우저에만 적어 둔다(docs/adr/0003 F).
const KEY = 'sool.recentProducts';
const MAX = 12;

function read(): number[] {
  // 사생활 보호 모드나 저장소가 막힌 브라우저에서는 읽기·쓰기가 오류를 낸다. 그때는 기록 없이 지나간다.
  try {
    const raw = window.localStorage.getItem(KEY);
    const list = raw ? JSON.parse(raw) : [];
    return Array.isArray(list) ? list.filter((n): n is number => Number.isInteger(n) && n > 0).slice(0, MAX) : [];
  } catch {
    return [];
  }
}

export function rememberViewed(id: number) {
  try {
    const next = [id, ...read().filter((n) => n !== id)].slice(0, MAX);
    window.localStorage.setItem(KEY, JSON.stringify(next));
  } catch {
    // 저장하지 못해도 화면에는 지장이 없다.
  }
}

/** 상품 상세를 열면 번호를 적어 둔다. 그리는 것은 없다. */
export function RecordView({ productId }: { productId: number }) {
  useEffect(() => { rememberViewed(productId); }, [productId]);
  return null;
}

/** 첫 화면 아래 "최근 본 상품" 줄. 본 상품이 없거나 불러오지 못하면 아무것도 그리지 않는다. */
export function RecentViewed() {
  const [items, setItems] = useState<Product[]>([]);
  useEffect(() => {
    const ids = read();
    if (ids.length === 0) return;
    api<Product[]>('/products/by-ids?ids=' + ids.join(','))
      .then((list) => setItems(Array.isArray(list) ? list : []))
      .catch(() => setItems([]));
  }, []);
  if (items.length === 0) return null;
  return (
    <section aria-labelledby="recent-title" style={{ marginTop: 48 }}>
      <h2 id="recent-title" className="under">최근 본 상품</h2>
      <div className="stalls">
        {items.map((p) => <ProductCard key={p.id} p={p} />)}
      </div>
    </section>
  );
}
