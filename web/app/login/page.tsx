'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { Note, useAction } from '@/components/Bits';
import { useMe } from '@/components/Me';
import { api } from '@/lib/api';

export default function Login() {
  const router = useRouter();
  const { refresh } = useMe();
  const act = useAction();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const ok = await act.run(() => api('/auth/login', { method: 'POST', body: { email, password } }));
    if (ok) {
      await refresh();
      router.push('/');
    }
  }

  return (
    <div className="narrow">
      <div className="title"><h1>로그인</h1></div>
      <Note kind="error">{act.error}</Note>
      <form className="sheet" onSubmit={submit}>
        <div className="field">
          <label htmlFor="email">이메일</label>
          <input id="email" type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="pw">비밀번호</label>
          <input id="pw" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
        </div>
        <button className="btn" disabled={act.busy}>로그인</button>
      </form>
      <p style={{ marginTop: 16 }}>처음 오셨나요? <Link href="/register">가입하기</Link></p>
      <div className="note small">
        시범 계정: buyer@test.local(구매자), brewery@test.local(판매자), admin@test.local(운영자). 비밀번호는 모두 test1234 입니다.
      </div>
    </div>
  );
}
