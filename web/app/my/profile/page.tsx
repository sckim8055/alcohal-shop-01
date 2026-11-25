'use client';

import Link from 'next/link';
import { useState } from 'react';
import { Note, useAction } from '@/components/Bits';
import { useMe } from '@/components/Me';
import { api } from '@/lib/api';
import { when } from '@/lib/format';

export default function Profile() {
  const { me, refresh } = useMe();
  const user = me!.user!;
  const nameAct = useAction();
  const pwAct = useAction();
  const [name, setName] = useState(user.name);
  const [pw, setPw] = useState({ current: '', next: '', again: '' });

  async function saveName(e: React.FormEvent) {
    e.preventDefault();
    if (await nameAct.run(() => api('/me', { method: 'PUT', body: { name } }), '이름을 바꿨습니다.')) refresh();
  }
  async function savePw(e: React.FormEvent) {
    e.preventDefault();
    if (pw.next !== pw.again) {
      pwAct.setError('새 비밀번호 두 칸이 서로 다릅니다.');
      return;
    }
    const ok = await pwAct.run(() => api('/me/password', { method: 'POST', body: { current: pw.current, next: pw.next } }),
      '비밀번호를 바꿨습니다. 다른 기기에서는 로그아웃되었습니다.');
    if (ok) {
      setPw({ current: '', next: '', again: '' });
      refresh();
    }
  }

  return (
    <div className="stack" style={{ maxWidth: 560 }}>
      <div className="title"><h1>개인정보</h1></div>
      <form className="sheet" onSubmit={saveName}>
        <h2>기본 정보</h2>
        <Note kind="error">{nameAct.error}</Note>
        <Note kind="ok">{nameAct.done}</Note>
        <div className="field"><label>이메일</label><input value={user.email} disabled /></div>
        <div className="field"><label htmlFor="name">이름</label><input id="name" required maxLength={40} value={name} onChange={(e) => setName(e.target.value)} /></div>
        <p>
          성인인증: {user.adultVerifiedAt ? when(user.adultVerifiedAt, false) + '에 마쳤습니다.' : <>아직 하지 않았습니다. <Link href="/verify">성인인증 하기</Link></>}
        </p>
        <button className="btn" disabled={nameAct.busy}>저장하기</button>
      </form>
      <form className="sheet" onSubmit={savePw}>
        <h2>비밀번호 바꾸기</h2>
        <Note kind="error">{pwAct.error}</Note>
        <Note kind="ok">{pwAct.done}</Note>
        <div className="field"><label htmlFor="cur">지금 비밀번호</label><input id="cur" type="password" autoComplete="current-password" required value={pw.current} onChange={(e) => setPw({ ...pw, current: e.target.value })} /></div>
        <div className="field"><label htmlFor="next">새 비밀번호</label><input id="next" type="password" autoComplete="new-password" required minLength={8} value={pw.next} onChange={(e) => setPw({ ...pw, next: e.target.value })} /><p className="hint">8자 이상</p></div>
        <div className="field"><label htmlFor="again">새 비밀번호 한 번 더</label><input id="again" type="password" autoComplete="new-password" required minLength={8} value={pw.again} onChange={(e) => setPw({ ...pw, again: e.target.value })} /></div>
        <button className="btn" disabled={pwAct.busy}>비밀번호 바꾸기</button>
      </form>
    </div>
  );
}
