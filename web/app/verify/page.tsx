'use client';

import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { NeedLogin, Note, useAction } from '@/components/Bits';
import { useMe } from '@/components/Me';
import { api } from '@/lib/api';

export default function Verify() {
  const router = useRouter();
  const { me, refresh } = useMe();
  const act = useAction();
  const [birth, setBirth] = useState('');

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const ok = await act.run(() => api('/auth/verify-adult', { method: 'POST', body: { birth } }));
    if (ok) {
      await refresh();
      router.push('/checkout');
    }
  }

  return (
    <NeedLogin>
      <div className="narrow">
        <div className="title"><h1>성인인증</h1></div>
        {me?.user?.adultVerifiedAt ? (
          <Note kind="ok">이미 성인인증을 마쳤습니다.</Note>
        ) : (
          <>
            <Note kind="error">{act.error}</Note>
            <form className="sheet" onSubmit={submit}>
              <div className="field">
                <label htmlFor="birth">생년월일</label>
                <input id="birth" type="date" required value={birth} onChange={(e) => setBirth(e.target.value)} />
                <p className="hint">만 19세가 되는 해의 1월 1일부터 술을 살 수 있습니다.</p>
              </div>
              <button className="btn" disabled={act.busy}>인증하기</button>
            </form>
            <div className="note small" style={{ marginTop: 16 }}>
              시범 운영 중에는 생년월일만 확인합니다. 정식 운영 때는 휴대폰 본인확인으로 바뀝니다.
            </div>
          </>
        )}
      </div>
    </NeedLogin>
  );
}
