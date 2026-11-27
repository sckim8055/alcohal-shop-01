'use client';

import Link from 'next/link';
import { useState } from 'react';
import { ApiError } from '@/lib/api';
import { STATUS_LABEL, won } from '@/lib/format';
import type { Product, SubStatus } from '@/lib/types';
import { Art } from './Art';
import { Stars } from './Stars';
import { useMe } from './Me';
import { Loading } from './Loading';

export function Stamp({ status }: { status: SubStatus }) {
  const tone = status === 'awaiting_deposit' ? '' : status === 'cancelled' || status === 'refunded' ? 'off' : status === 'shipped' ? 'blue' : 'good';
  return <span className={'stamp ' + tone}>{STATUS_LABEL[status]}</span>;
}

export function Note({ kind, children }: { kind?: 'error' | 'ok'; children: React.ReactNode }) {
  if (!children) return null;
  return <div className={'note ' + (kind ?? '')} role={kind === 'error' ? 'alert' : 'status'}>{children}</div>;
}

/** 요청 한 번의 진행 상태와 안내 문구를 관리한다. */
export function useAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState('');
  async function run(fn: () => Promise<unknown>, okMessage = '') {
    setBusy(true);
    setError('');
    setDone('');
    try {
      await fn();
      setDone(okMessage);
      return true;
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '요청을 처리하지 못했습니다.');
      return false;
    } finally {
      setBusy(false);
    }
  }
  return { busy, error, done, run, setError };
}

/** 로그인해야 볼 수 있는 화면을 감싼다. */
export function NeedLogin({ children }: { children: React.ReactNode }) {
  const { me, loading } = useMe();
  if (loading) return <Loading />;
  if (!me?.user) {
    return (
      <div className="sheet narrow">
        <h2>로그인이 필요합니다</h2>
        <p>이 화면은 로그인한 뒤에 볼 수 있습니다.</p>
        <Link className="btn" href={'/login?next=' + encodeURIComponent(typeof window === 'undefined' ? '/' : window.location.pathname + window.location.search)}>로그인</Link>
      </div>
    );
  }
  return <>{children}</>;
}

export function ProductCard({ p }: { p: Product }) {
  const onSale = p.currentPrice < p.price;
  return (
    <Link href={'/products/' + p.id} className="stall">
      <span className="art">{p.mainImage ? <img src={p.mainImage} alt="" loading="lazy" /> : <Art category={p.topCategoryName} />}</span>
      {p.alcohol && <span className="age" title="19세 이상만 살 수 있습니다">19</span>}
      {p.stock === 0 && <span className="soldout"><span className="stamp off">다 팔렸어요</span></span>}
      <span className="info">
        <span className="name">{p.name}</span>
        <span className="shop">{p.shopName}</span>
        {(p.reviewCount ?? 0) > 0 && p.ratingAvg != null && (
          <span className="rate small"><Stars value={p.ratingAvg} /> {p.ratingAvg.toFixed(1)} <span className="muted">(리뷰 {p.reviewCount})</span></span>
        )}
      </span>
      <span className="tag">
        {onSale && <s>{won(p.price)}</s>}
        {won(p.currentPrice)}
      </span>
    </Link>
  );
}
