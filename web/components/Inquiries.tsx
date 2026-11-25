'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { INQUIRY_KIND_LABEL, INQUIRY_STATUS_LABEL, when } from '@/lib/format';
import type { Inquiry, InquiryInput, Paged } from '@/lib/types';
import { Note, useAction } from './Bits';
import { useMe } from './Me';
import { Pager } from './Pager';

export function InquiryStamp({ q }: { q: Pick<Inquiry, 'status'> }) {
  return <span className={'stamp ' + (q.status === 'answered' ? 'good' : '')}>{INQUIRY_STATUS_LABEL[q.status] ?? q.status}</span>;
}

/** 문의 쓰기. 상품 문의는 비밀글을 고를 수 있고, 주문 문의와 1:1 문의는 늘 비밀글이다. */
export function InquiryForm({ target, productId = null, subOrderId = null, heading, alwaysSecret, onDone, onCancel }: {
  target: 'seller' | 'admin'; productId?: number | null; subOrderId?: number | null; heading: string; alwaysSecret?: boolean;
  onDone: (q: Inquiry) => void; onCancel?: () => void;
}) {
  const act = useAction();
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [secret, setSecret] = useState(Boolean(alwaysSecret));
  const id = 'inq-' + target + '-' + (productId ?? '') + '-' + (subOrderId ?? '');

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const t = title.trim();
    const b = body.trim();
    if (t.length < 2) { act.setError('제목을 2자 이상 써 주세요.'); document.getElementById(id + '-title')?.focus(); return; }
    if (b.length < 5) { act.setError('내용을 5자 이상 써 주세요.'); document.getElementById(id + '-body')?.focus(); return; }
    const input: InquiryInput = { target, productId, subOrderId, title: t, body: b, secret: alwaysSecret ? true : secret };
    let saved: Inquiry | null = null;
    const ok = await act.run(async () => { saved = await api<Inquiry>('/inquiries', { method: 'POST', body: input }); });
    if (ok && saved) {
      setTitle('');
      setBody('');
      onDone(saved);
    }
  }

  return (
    <form className="sheet" style={{ boxShadow: 'var(--hard)' }} onSubmit={submit} aria-labelledby={id + '-h'}>
      <h3 id={id + '-h'}>{heading}</h3>
      <Note kind="error">{act.error}</Note>
      <div className="field">
        <label htmlFor={id + '-title'}>제목</label>
        <input id={id + '-title'} maxLength={100} value={title} onChange={(e) => setTitle(e.target.value)} />
      </div>
      <div className="field">
        <label htmlFor={id + '-body'}>내용</label>
        <textarea id={id + '-body'} maxLength={2000} value={body} onChange={(e) => setBody(e.target.value)} aria-describedby={id + '-count'} />
        <div className="hint" id={id + '-count'}>{body.trim().length}/2000자</div>
      </div>
      {alwaysSecret ? (
        <p className="hint">비밀글로 올라가서 나와 {target === 'admin' ? '운영자' : '판매자'}만 볼 수 있습니다.</p>
      ) : (
        <div className="field">
          <label className="row" style={{ gap: 6, fontWeight: 400 }}>
            <input type="checkbox" checked={secret} onChange={(e) => setSecret(e.target.checked)} />
            비밀글로 쓰기 (나와 판매자만 볼 수 있습니다)
          </label>
        </div>
      )}
      <div className="row">
        <button className="btn" type="submit" disabled={act.busy}>문의 올리기</button>
        {onCancel && <button className="btn plain" type="button" onClick={onCancel}>그만 쓰기</button>}
      </div>
    </form>
  );
}

/** 판매자·운영자가 답변을 달거나 고친다. path 는 '/seller/inquiries/1/answer' 같은 주소 */
export function AnswerBox({ path, current, field, max, label, onSaved }: {
  path: string; current: string | null; field: 'answer' | 'reply'; max: number; label: string; onSaved: () => void;
}) {
  const act = useAction();
  const [editing, setEditing] = useState(!current);
  const [text, setText] = useState(current ?? '');
  const id = 'ans-' + path.replace(/\W+/g, '-');
  useEffect(() => { setText(current ?? ''); setEditing(!current); }, [current]);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (text.trim().length < (field === 'answer' ? 2 : 1)) { act.setError(label + '을 적어 주세요.'); return; }
    const ok = await act.run(() => api(path, { method: 'POST', body: { [field]: text.trim() } }));
    if (ok) onSaved();
  }

  if (!editing) {
    return (
      <p style={{ marginTop: 8, marginBottom: 0 }}>
        <button type="button" className="btn plain small" onClick={() => setEditing(true)}>{label} 고치기</button>
      </p>
    );
  }
  return (
    <form onSubmit={save} style={{ marginTop: 10 }}>
      <Note kind="error">{act.error}</Note>
      <div className="field">
        <label htmlFor={id}>{current ? label + ' 고치기' : label + ' 쓰기'}</label>
        <textarea id={id} maxLength={max} style={{ minHeight: 80 }} value={text} onChange={(e) => setText(e.target.value)} aria-describedby={id + '-count'} />
        <div className="hint" id={id + '-count'}>{text.trim().length}/{max}자</div>
      </div>
      <div className="row">
        <button className="btn small" type="submit" disabled={act.busy}>{current ? label + ' 고쳐 저장하기' : label + ' 달기'}</button>
        {current && <button className="btn plain small" type="button" onClick={() => { setText(current); setEditing(false); }}>그만 고치기</button>}
      </div>
    </form>
  );
}

/** 문의 한 건의 내용과 답변. body 가 null 이면(남의 비밀글) 내용을 보여 주지 않는다. */
export function InquiryFacts({ q }: { q: Inquiry }) {
  if (q.body == null) return <p className="muted small" style={{ margin: '6px 0 0' }}>비밀글은 쓴 사람과 판매자만 볼 수 있습니다.</p>;
  return (
    <>
      <p className="text" style={{ margin: '6px 0 0' }}>{q.body}</p>
      {q.answer && (
        <div className="reply">
          <strong>답변</strong> <span className="muted small">{when(q.answeredAt)}</span>
          <p className="text" style={{ margin: '4px 0 0' }}>{q.answer}</p>
        </div>
      )}
    </>
  );
}

/** 상품 상세 아래 "상품 문의" 구역. 로그인한 사람에 따라 비밀글이 보이므로 브라우저에서 불러온다. */
export function ProductInquiries({ productId }: { productId: number }) {
  const { me, loading } = useMe();
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paged<Inquiry> | null>(null);
  const [failed, setFailed] = useState('');
  const [writing, setWriting] = useState(false);
  const [message, setMessage] = useState('');
  const act = useAction();
  const userId = me?.user?.id;
  const load = useCallback(() => {
    setFailed('');
    api<Paged<Inquiry>>(`/products/${productId}/inquiries?page=${page}`)
      .then(setData).catch((e) => { setFailed(e.message); setData({ items: [], page: 1, size: 10, total: 0 }); });
  }, [productId, page, userId]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { if (!loading) load(); }, [load, loading]);

  async function remove(q: Inquiry) {
    if (!window.confirm('이 문의를 지울까요? 지우면 되돌릴 수 없습니다.')) return;
    if (await act.run(() => api('/inquiries/' + q.id + '/delete', { method: 'POST' }), '문의를 지웠습니다.')) load();
  }

  return (
    <section className="sheet" id="inquiries" aria-labelledby="inquiries-title">
      <div className="spread" style={{ marginBottom: 12 }}>
        <h2 id="inquiries-title">상품 문의</h2>
        {!loading && (me?.user
          ? !writing && <button type="button" className="btn yellow small" onClick={() => { setWriting(true); setMessage(''); }}>문의하기</button>
          : <Link className="btn plain small" href={'/login?next=' + encodeURIComponent('/products/' + productId + '#inquiries')}>로그인하고 문의하기</Link>)}
      </div>
      <Note kind="error">{failed || act.error}</Note>
      <Note kind="ok">{message || act.done}</Note>
      {writing && (
        <div style={{ marginBottom: 16 }}>
          <InquiryForm target="seller" productId={productId} heading="이 상품에 대해 판매자에게 묻기"
            onCancel={() => setWriting(false)}
            onDone={() => { setWriting(false); setMessage('문의를 올렸습니다. 판매자가 답하면 알림으로 알려 드립니다.'); if (page === 1) load(); else setPage(1); }} />
        </div>
      )}
      {!data ? <p className="muted">불러오는 중입니다.</p> : data.items.length === 0 ? (
        <p>아직 문의가 없습니다. 궁금한 점이 있으면 “문의하기”를 눌러 판매자에게 물어보세요.</p>
      ) : (
        <>
          <ul className="notice-list">
            {data.items.map((q) => (
              <li key={q.id}>
                <details>
                  <summary>
                    <span className="row" style={{ gap: 8, display: 'inline-flex' }}>
                      <InquiryStamp q={q} />
                      {q.secret && <span className="stamp off">비밀글</span>}
                      <strong>{q.title}</strong>
                    </span>
                    <span className="muted small"> {q.authorName}, {when(q.createdAt, false)}</span>
                  </summary>
                  <InquiryFacts q={q} />
                  {q.mine && q.status === 'open' && (
                    <p style={{ marginTop: 8, marginBottom: 0 }}>
                      <button type="button" className="btn plain small" disabled={act.busy} onClick={() => remove(q)}>이 문의 지우기</button>
                    </p>
                  )}
                </details>
              </li>
            ))}
          </ul>
          <Pager page={data.page} size={data.size} total={data.total} onPage={setPage} />
        </>
      )}
      <p className="hint" style={{ marginTop: 12 }}>주문한 상품의 배송·교환 문의는 주문 상세에서 “판매자에게 문의”로 남겨 주세요.</p>
    </section>
  );
}

export const inquiryKind = (q: Pick<Inquiry, 'kind'>) => INQUIRY_KIND_LABEL[q.kind] ?? '문의';
