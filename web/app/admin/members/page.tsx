'use client';

import { useCallback, useEffect, useState } from 'react';
import { Note, useAction } from '@/components/Bits';
import { useMe } from '@/components/Me';
import { Pager } from '@/components/Pager';
import { api } from '@/lib/api';
import { when } from '@/lib/format';
import type { Paged } from '@/lib/types';
import { Loading } from '@/components/Loading';

type MemberRow = { id: number; email: string; name: string; role: string; status: string; shopName: string | null; adultVerifiedAt: string | null; lastLoginAt: string | null; createdAt: string };
const ROLE: Record<string, string> = { buyer: '구매자', seller: '판매자', admin: '운영자' };
const STATUS: Record<string, string> = { active: '이용 중', suspended: '정지', withdrawn: '탈퇴' };

export default function Members() {
  const { me } = useMe();
  const act = useAction();
  const [filter, setFilter] = useState({ q: '', role: '', status: '' });
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paged<MemberRow> | null>(null);
  const load = useCallback(() => {
    const p = new URLSearchParams({ ...filter, page: String(page) });
    api<Paged<MemberRow>>('/admin/members?' + p).then(setData).catch((e) => act.setError(e.message));
  }, [filter, page]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(load, [load]);

  const call = (path: string, body: unknown, ok: string) => act.run(() => api(path, { method: 'POST', body }), ok).then((done) => done && load());

  return (
    <>
      <div className="title"><h1>회원</h1></div>
      <Note kind="error">{act.error}</Note>
      <Note kind="ok">{act.done}</Note>
      <form className="filters" onSubmit={(e) => { e.preventDefault(); const f = new FormData(e.currentTarget); setPage(1); setFilter({ q: f.get('q') as string, role: f.get('role') as string, status: f.get('status') as string }); }}>
        <input name="q" placeholder="이메일이나 이름" aria-label="이메일이나 이름으로 찾기" />
        <select name="role" aria-label="역할"><option value="">모든 역할</option>{Object.entries(ROLE).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
        <select name="status" aria-label="상태"><option value="">모든 상태</option>{Object.entries(STATUS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
        <button className="btn plain small">찾기</button>
      </form>
      {!data ? <Loading kind="table" /> : data.items.length === 0 ? <div className="sheet"><h3>해당하는 회원이 없습니다</h3></div> : (
        <div className="sheet scroll">
          <table>
            <thead><tr><th>회원</th><th>역할</th><th>상태</th><th>가입일</th><th>마지막 로그인</th><th /></tr></thead>
            <tbody>
              {data.items.map((m) => {
                const self = m.id === me?.user?.id;
                return (
                  <tr key={m.id}>
                    <td>{m.name}<div className="muted small">{m.email}{m.shopName && <>, {m.shopName}</>}</div></td>
                    <td className="nowrap">{ROLE[m.role]}{m.adultVerifiedAt && <div className="muted small">성인인증</div>}</td>
                    <td className="nowrap">{m.status === 'suspended' ? <strong style={{ color: 'var(--marker)' }}>정지</strong> : STATUS[m.status]}</td>
                    <td className="small">{when(m.createdAt, false)}</td>
                    <td className="small">{m.lastLoginAt ? when(m.lastLoginAt) : '없음'}</td>
                    <td className="right">
                      {self ? <span className="muted small">나</span> : m.status === 'withdrawn' ? null : (
                        <span className="row" style={{ justifyContent: 'flex-end', gap: 6 }}>
                          {m.role === 'admin'
                            ? <button type="button" className="btn plain small" disabled={act.busy} onClick={() => window.confirm(m.name + ' 님의 운영자 권한을 없앨까요?') && call(`/admin/members/${m.id}/admin`, { admin: false }, '운영자 권한을 없앴습니다.')}>운영자 해제</button>
                            : <button type="button" className="btn plain small" disabled={act.busy} onClick={() => window.confirm(m.name + ' 님을 운영자로 지정할까요? 운영자는 모든 회원과 주문을 볼 수 있습니다.') && call(`/admin/members/${m.id}/admin`, { admin: true }, '운영자로 지정했습니다.')}>운영자 지정</button>}
                          {m.status === 'active'
                            ? <button type="button" className="btn plain small" disabled={act.busy} onClick={() => window.confirm(m.name + ' 님을 정지할까요? 바로 로그아웃되고 다시 로그인할 수 없습니다.') && call(`/admin/members/${m.id}/status`, { status: 'suspended' }, '이용을 정지했습니다.')}>정지</button>
                            : <button type="button" className="btn small" disabled={act.busy} onClick={() => call(`/admin/members/${m.id}/status`, { status: 'active' }, '정지를 풀었습니다.')}>정지 풀기</button>}
                        </span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <Pager page={data.page} size={data.size} total={data.total} onPage={setPage} />
        </div>
      )}
    </>
  );
}
