'use client';

import Link from 'next/link';
import { useEffect, useRef, useState } from 'react';
import { ApiError, api, uploadFile } from '@/lib/api';
import { rating1, when } from '@/lib/format';
import type { Paged, Review, ReviewSummary } from '@/lib/types';
import { Note, useAction } from './Bits';
import { Pager } from './Pager';
import { Stars } from './Stars';

/** 리뷰 사진 작은 그림. 누르면 큰 그림을 띄운다(Esc 나 "닫기"로 닫는다). */
export function ReviewPhotos({ images, label }: { images: string[]; label: string }) {
  const ref = useRef<HTMLDialogElement>(null);
  const [at, setAt] = useState<number | null>(null);
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (at != null && !d.open) d.showModal();
    if (at == null && d.open) d.close();
  }, [at]);
  if (!images || images.length === 0) return null;
  return (
    <>
      <div className="thumbs">
        {images.map((src, i) => (
          <button key={src} type="button" aria-label={`${label} 사진 ${i + 1} 크게 보기`} onClick={() => setAt(i)}>
            <img src={src} alt="" loading="lazy" />
          </button>
        ))}
      </div>
      <dialog ref={ref} className="lightbox" aria-label={label + ' 사진'} onClose={() => setAt(null)}
        onClick={(e) => { if (e.target === e.currentTarget) setAt(null); }}>
        {at != null && (
          <>
            <img src={images[at]} alt={`${label} 사진 ${at + 1}`} />
            <div className="row" style={{ justifyContent: 'space-between', marginTop: 10 }}>
              <span className="row" style={{ gap: 6 }}>
                {images.length > 1 && <>
                  <button type="button" className="btn plain small" disabled={at === 0} onClick={() => setAt(at - 1)}>앞 사진</button>
                  <button type="button" className="btn plain small" disabled={at === images.length - 1} onClick={() => setAt(at + 1)}>다음 사진</button>
                  <span className="small muted">{at + 1} / {images.length}</span>
                </>}
              </span>
              <button type="button" className="btn small" onClick={() => setAt(null)}>닫기</button>
            </div>
          </>
        )}
      </dialog>
    </>
  );
}

/** 리뷰 한 건: 별점, 쓴 사람, 글, 사진, 판매자 답글 */
export function ReviewItem({ r, showProduct, children }: { r: Review; showProduct?: boolean; children?: React.ReactNode }) {
  return (
    <article className="review">
      <div className="spread">
        <span className="row" style={{ gap: 10 }}>
          <Stars value={r.rating} />
          <strong>{r.authorName}</strong>
          <span className="muted small">{when(r.createdAt, false)}</span>
        </span>
        {r.status === 'hidden' && <span className="stamp off">운영자가 숨김</span>}
      </div>
      {showProduct && <p className="small" style={{ margin: '4px 0 0' }}><Link href={'/products/' + r.productId}>{r.productName}</Link></p>}
      <p className="text" style={{ marginTop: 6 }}>{r.body}</p>
      <ReviewPhotos images={r.images ?? []} label={r.authorName + ' 님 리뷰'} />
      {r.sellerReply && (
        <div className="reply">
          <strong>판매자 답글</strong> <span className="muted small">{when(r.repliedAt, false)}</span>
          <p className="text" style={{ margin: '4px 0 0' }}>{r.sellerReply}</p>
        </div>
      )}
      {children}
    </article>
  );
}

/** 상품 상세 아래 "리뷰" 구역. 첫 쪽은 서버에서 받아 와 바로 그리고, 쪽을 넘기면 브라우저에서 불러온다. */
export function ProductReviews({ productId, initial }: { productId: number; initial: ReviewSummary | null }) {
  const [data, setData] = useState<ReviewSummary | null>(initial);
  const [page, setPage] = useState(initial?.page.page ?? 1);
  const [failed, setFailed] = useState('');
  const first = useRef(true);
  useEffect(() => {
    // 처음 그릴 때는 서버에서 받은 자료가 있으면 다시 부르지 않는다.
    if (first.current) {
      first.current = false;
      if (initial) return;
    }
    setFailed('');
    api<ReviewSummary>(`/products/${productId}/reviews?page=${page}`).then(setData).catch((e) => setFailed(e.message));
  }, [productId, page, initial]);

  const total = data?.reviewCount ?? 0;
  const counts = data?.counts?.length === 5 ? data.counts : [0, 0, 0, 0, 0];
  const list: Paged<Review> = data?.page ?? { items: [], page: 1, size: 10, total: 0 };

  return (
    <section className="sheet" id="reviews" aria-labelledby="reviews-title">
      <h2 id="reviews-title" style={{ marginBottom: 14 }}>리뷰 {total > 0 && <span className="muted" style={{ fontSize: '1.2rem' }}>{total}개</span>}</h2>
      <Note kind="error">{failed}</Note>
      {!data && !failed ? <p className="muted">불러오는 중입니다.</p> : total === 0 ? (
        <p>아직 리뷰가 없습니다. 이 상품을 사고 구매를 확정하면 <Link href="/my/reviews">리뷰 관리</Link>에서 첫 리뷰를 남길 수 있습니다.</p>
      ) : (
        <>
          <div className="review-summary">
            <div>
              <div className="avg">{rating1(data?.ratingAvg)}</div>
              <Stars value={data?.ratingAvg ?? 0} />
              <div className="muted small">리뷰 {total}개 평균</div>
            </div>
            <ul className="bars" aria-label="별점별 리뷰 수">
              {[5, 4, 3, 2, 1].map((star) => {
                const n = counts[star - 1] ?? 0;
                const pct = total > 0 ? Math.round((n / total) * 100) : 0;
                return (
                  <li key={star}>
                    <span>{star}점</span>
                    <span className="bar" aria-hidden="true"><span style={{ width: pct + '%' }} /></span>
                    <span className="num">{n}개</span>
                  </li>
                );
              })}
            </ul>
          </div>
          <div>
            {list.items.map((r) => <ReviewItem key={r.id} r={r} />)}
          </div>
          <Pager page={list.page} size={list.size} total={list.total} onPage={(p) => { setPage(p); document.getElementById('reviews')?.scrollIntoView(); }} />
        </>
      )}
    </section>
  );
}

const STAR_WORD = ['', '별로예요', '그저 그래요', '괜찮아요', '좋아요', '아주 좋아요'];

/** 별점 고르기. 실제로는 라디오 단추라서 Tab 으로 들어와 화살표 키로 고를 수 있다. */
export function StarPicker({ value, onChange, name }: { value: number; onChange: (n: number) => void; name: string }) {
  return (
    <fieldset className="star-pick">
      <legend>별점</legend>
      <span className="row" style={{ gap: 8 }}>
        <span>
          {[1, 2, 3, 4, 5].map((n) => (
            <label key={n} className={n <= value ? 'on' : ''}>
              <input type="radio" name={name} value={n} checked={value === n} onChange={() => onChange(n)} />
              <span aria-hidden="true">{n <= value ? '★' : '☆'}</span>
              <span className="sr-only">별 {n}개, {STAR_WORD[n]}</span>
            </label>
          ))}
        </span>
        <span className="small muted" aria-hidden="true">{value ? `${value}점, ${STAR_WORD[value]}` : '별을 눌러 골라 주세요'}</span>
      </span>
    </fieldset>
  );
}

/** 구매 확정한 상품 한 줄의 리뷰 쓰기 */
export function ReviewForm({ orderItemId, productName, onDone, onCancel }: { orderItemId: number; productName: string; onDone: () => void; onCancel: () => void }) {
  const act = useAction();
  const [rating, setRating] = useState(0);
  const [body, setBody] = useState('');
  const [images, setImages] = useState<string[]>([]);
  const [uploading, setUploading] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);
  const bodyId = 'review-body-' + orderItemId;
  const len = body.trim().length;

  async function pick(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? []).slice(0, 3 - images.length);
    e.target.value = '';
    if (files.length === 0) return;
    setUploading(true);
    act.setError('');
    const added: string[] = [];
    try {
      for (const f of files) {
        if (!f.type.startsWith('image/')) throw new ApiError(400, '리뷰에는 사진만 올릴 수 있습니다.');
        const up = await uploadFile(f);
        if (up.kind !== 'image') throw new ApiError(400, '리뷰에는 사진만 올릴 수 있습니다.');
        added.push(up.url);
      }
    } catch (err) {
      act.setError(err instanceof ApiError ? err.message : '사진을 올리지 못했습니다.');
    } finally {
      if (added.length) setImages((cur) => [...cur, ...added].slice(0, 3));
      setUploading(false);
    }
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (rating < 1) { act.setError('별점을 골라 주세요.'); return; }
    if (len < 10) { act.setError('리뷰는 10자 이상 써 주세요.'); document.getElementById(bodyId)?.focus(); return; }
    const ok = await act.run(() => api<Review>('/reviews', { method: 'POST', body: { orderItemId, rating, body: body.trim(), images } }));
    if (ok) onDone();
  }

  return (
    <form className="sheet" style={{ marginTop: 10, boxShadow: 'var(--hard)' }} onSubmit={submit} aria-label={productName + ' 리뷰 쓰기'}>
      <h3>{productName} 리뷰 쓰기</h3>
      <Note kind="error">{act.error}</Note>
      <div className="field"><StarPicker name={'rating-' + orderItemId} value={rating} onChange={setRating} /></div>
      <div className="field">
        <label htmlFor={bodyId}>리뷰</label>
        <textarea id={bodyId} maxLength={1000} value={body} onChange={(e) => setBody(e.target.value)}
          placeholder="맛, 향, 포장, 배송은 어땠나요? 다른 분께 도움이 되게 적어 주세요." aria-describedby={bodyId + '-count'} />
        <div className="hint" id={bodyId + '-count'} aria-live="polite">
          {len}/1000자{len < 10 && ` (10자 이상 써 주세요. ${10 - len}자 더)`}
        </div>
      </div>
      <div className="field">
        <span style={{ fontWeight: 700 }}>사진 (3장까지, 선택)</span>
        {images.length > 0 && (
          <ul className="media-list" style={{ marginTop: 6 }}>
            {images.map((src, i) => (
              <li key={src}>
                <div className="thumb"><img src={src} alt={`올린 사진 ${i + 1}`} /></div>
                <button type="button" className="btn plain small" onClick={() => setImages(images.filter((x) => x !== src))}>사진 {i + 1} 빼기</button>
              </li>
            ))}
          </ul>
        )}
        <input ref={fileRef} type="file" hidden multiple accept="image/jpeg,image/png,image/gif,image/webp" onChange={pick} />
        <div className="row" style={{ marginTop: 6 }}>
          <button type="button" className="btn plain small" disabled={uploading || images.length >= 3} onClick={() => fileRef.current?.click()}>
            {uploading ? '올리는 중' : '사진 올리기'}
          </button>
          <span className="hint" style={{ margin: 0 }}>JPG·PNG·GIF·WEBP, 한 장에 5MB 이하</span>
        </div>
      </div>
      <div className="row">
        <button className="btn" type="submit" disabled={act.busy || uploading}>리뷰 올리기</button>
        <button className="btn plain" type="button" onClick={onCancel}>그만 쓰기</button>
      </div>
    </form>
  );
}
