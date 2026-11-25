'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { Note, useAction } from '@/components/Bits';
import { useMe } from '@/components/Me';
import { api } from '@/lib/api';

export default function Register() {
  const router = useRouter();
  const { refresh } = useMe();
  const act = useAction();
  const [form, setForm] = useState({ email: '', name: '', password: '' });
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value });

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const ok = await act.run(() => api('/auth/register', { method: 'POST', body: form }));
    if (ok) {
      await refresh();
      router.push('/');
    }
  }

  return (
    <div className="narrow">
      <div className="title"><h1>가입</h1></div>
      <Note kind="error">{act.error}</Note>
      <form className="sheet" onSubmit={submit}>
        <div className="field">
          <label htmlFor="email">이메일</label>
          <input id="email" type="email" autoComplete="username" required value={form.email} onChange={set('email')} />
        </div>
        <div className="field">
          <label htmlFor="name">이름</label>
          <input id="name" required maxLength={40} value={form.name} onChange={set('name')} />
        </div>
        <div className="field">
          <label htmlFor="pw">비밀번호</label>
          <input id="pw" type="password" autoComplete="new-password" required minLength={8} value={form.password} onChange={set('password')} />
          <p className="hint">8자 이상</p>
        </div>
        <button className="btn" disabled={act.busy}>가입하기</button>
      </form>
      <p style={{ marginTop: 16 }}>이미 가입했나요? <Link href="/login">로그인</Link></p>
    </div>
  );
}
