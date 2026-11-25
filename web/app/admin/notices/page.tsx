'use client';

import { useCallback, useEffect, useState } from 'react';
import { Note, useAction } from '@/components/Bits';
import { Pager } from '@/components/Pager';
import { api } from '@/lib/api';
import { AUDIENCE_LABEL, when } from '@/lib/format';
import type { Notice, NoticeAudience, Paged } from '@/lib/types';

type Draft = { audience: NoticeAudience; title: string; body: string; pinned: boolean };
const EMPTY: Draft = { audience: 'all', title: '', body: '', pinned: false };

function NoticeForm({ editing, onSaved, onCancel }: { editing: Notice | null; onSaved: (msg: string) => void; onCancel: () => void }) {
  const act = useAction();
  const [d, setD] = useState<Draft>(editing ? { audience: editing.audience, title: editing.title, body: editing.body, pinned: editing.pinned } : EMPTY);
  const set = <K extends keyof Draft>(k: K, v: Draft[K]) => setD((cur) => ({ ...cur, [k]: v }));

  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (!d.title.trim()) { act.setError('제목을 적어 주세요.'); return; }
    if (!d.body.trim()) { act.setError('내용을 적어 주세요.'); return; }
    const body = { ...d, title: d.title.trim(), body: d.body.trim() };
    const ok = await act.run(() => editing
      ? api('/admin/notices/' + editing.id, { method: 'PUT', body })
      : api('/admin/notices', { method: 'POST', body }));
    if (ok) onSaved(editing ? '공지를 고쳤습니다.' : '공지를 올렸습니다.');
  }

  return (
    <form className="sheet" style={{ boxShadow: 'var(--hard)', marginBottom: 20 }} onSubmit={save}>
      <h3>{editing ? '공지 고치기' : '새 공지 쓰기'}</h3>
      <Note kind="error">{act.error}</Note>
      <div className="fields">
        <div className="field">
          <label htmlFor="n-audience">보는 사람</label>
          <select id="n-audience" value={d.audience} onChange={(e) => set('audience', e.target.value as NoticeAudience)}>
            <option value="all">전체 (누구나)</option>
            <option value="seller">판매자만</option>
          </select>
        </div>
        <div className="field" style={{ alignSelf: 'end' }}>
          <label className="row" style={{ gap: 6, fontWeight: 400 }}>
            <input type="checkbox" checked={d.pinned} onChange={(e) => set('pinned', e.target.checked)} />
            맨 위에 고정 (전체 공지는 첫 화면 띠에도 나옵니다)
          </label>
        </div>
      </div>
      <div className="field">
        <label htmlFor="n-title">제목</label>
        <input id="n-title" maxLength={100} value={d.title} onChange={(e) => set('title', e.target.value)} />
      </div>
      <div className="field">
        <label htmlFor="n-body">내용</label>
        <textarea id="n-body" maxLength={5000} style={{ minHeight: 200 }} value={d.body} onChange={(e) => set('body', e.target.value)} aria-describedby="n-body-hint" />
        <div className="hint" id="n-body-hint">{d.body.length}/5000자. 글자 그대로 보이고 줄바꿈만 살립니다(HTML 은 쓸 수 없습니다).</div>
      </div>
      <div className="row">
        <button className="btn" type="submit" disabled={act.busy}>{editing ? '고친 내용 저장하기' : '공지 올리기'}</button>
        <button className="btn plain" type="button" onClick={onCancel}>그만 쓰기</button>
      </div>
    </form>
  );
}

export default function AdminNotices() {
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paged<Notice> | null>(null);
  const [form, setForm] = useState<{ editing: Notice | null } | null>(null);
  const [message, setMessage] = useState('');
  const act = useAction();
  const load = useCallback(() => {
    api<Paged<Notice>>('/admin/notices?page=' + page).then(setData).catch((e) => { act.setError(e.message); setData({ items: [], page: 1, size: 10, total: 0 }); });
  }, [page]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(load, [load]);

  async function remove(n: Notice) {
    if (!window.confirm(`'${n.title}' 공지를 지울까요? 지우면 아무도 볼 수 없습니다.`)) return;
    setMessage('');
    const ok = await act.run(() => api('/admin/notices/' + n.id + '/delete', { method: 'POST' }), '공지를 지웠습니다.');
    if (!ok) return;
    if (data && data.items.length === 1 && page > 1) setPage(page - 1); else load();
  }

  return (
    <>
      <div className="spread title">
        <h1>공지</h1>
        {!form && <button type="button" className="btn yellow" onClick={() => { setForm({ editing: null }); setMessage(''); }}>새 공지 쓰기</button>}
      </div>
      <Note kind="error">{act.error}</Note>
      <Note kind="ok">{message || act.done}</Note>
      {form && (
        <NoticeForm key={form.editing?.id ?? 'new'} editing={form.editing} onCancel={() => setForm(null)}
          onSaved={(m) => { setForm(null); setMessage(m); load(); }} />
      )}
      {!data ? <p className="muted">불러오는 중입니다.</p> : data.items.length === 0 ? (
        <div className="sheet"><h3>올린 공지가 없습니다</h3><p>“새 공지 쓰기”를 눌러 장터 소식을 알려 주세요.</p></div>
      ) : (
        <div className="sheet scroll">
          <table>
            <thead><tr><th scope="col">제목</th><th scope="col">보는 사람</th><th scope="col">올린 때</th><th /></tr></thead>
            <tbody>
              {data.items.map((n) => (
                <tr key={n.id}>
                  <td>
                    {n.pinned && <span className="stamp" style={{ marginRight: 8 }}>고정</span>}
                    <a href={'/notices/' + n.id} target="_blank" rel="noopener">{n.title}</a>
                  </td>
                  <td className="nowrap">{AUDIENCE_LABEL[n.audience] ?? n.audience}</td>
                  <td className="small nowrap">{when(n.createdAt, false)}</td>
                  <td className="right">
                    <span className="row" style={{ justifyContent: 'flex-end', gap: 6 }}>
                      <button type="button" className="btn plain small" onClick={() => { setForm({ editing: n }); setMessage(''); window.scrollTo({ top: 0 }); }}>고치기</button>
                      <button type="button" className="btn plain small" disabled={act.busy} onClick={() => remove(n)}>지우기</button>
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
