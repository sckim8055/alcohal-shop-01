'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { Note, useAction } from '@/components/Bits';
import { useMe } from '@/components/Me';
import { api } from '@/lib/api';
import { when } from '@/lib/format';

/** 회원 탈퇴: 무엇이 지워지고 남는지 알려 주고, 비밀번호와 한 번 더 확인을 받은 뒤 탈퇴한다. */
function Withdraw() {
  const router = useRouter();
  const { refresh } = useMe();
  const act = useAction();
  const [password, setPassword] = useState('');
  const [confirming, setConfirming] = useState(false);

  function ask(e: React.FormEvent) {
    e.preventDefault();
    act.setError('');
    setConfirming(true);
  }
  async function withdraw() {
    const ok = await act.run(() => api('/me/withdraw', { method: 'POST', body: { password } }));
    if (ok) {
      // 첫 화면으로 먼저 옮긴 뒤 로그인 상태를 새로 읽는다(마이 화면이 로그인 안내로 바뀌지 않도록).
      router.push('/');
      refresh();
    } else {
      setConfirming(false);
    }
  }

  return (
    <form className="sheet" onSubmit={ask} aria-labelledby="withdraw-title">
      <h2 id="withdraw-title">회원 탈퇴</h2>
      <Note kind="error">{act.error}</Note>
      <p>탈퇴하면 다시 되돌릴 수 없습니다.</p>
      <ul>
        <li>지워지는 것: 장바구니, 찜한 상품, 저장한 배송지, 아직 쓰지 않은 쿠폰, 이름과 이메일</li>
        <li>남는 것: 주문 기록 (거래 기록은 법에 따라 보관합니다)</li>
      </ul>
      <p className="small">끝나지 않은 주문이나 진행 중인 취소·반품·교환 신청이 있으면 탈퇴할 수 없습니다. 가게를 운영하는 판매자는 운영자에게 문의해 주세요.</p>
      <div className="field">
        <label htmlFor="wd-pw">비밀번호</label>
        <input id="wd-pw" type="password" autoComplete="current-password" required value={password}
          onChange={(e) => { setPassword(e.target.value); setConfirming(false); }} disabled={act.busy} />
      </div>
      {!confirming ? (
        <button className="btn plain" disabled={act.busy}>탈퇴하기</button>
      ) : (
        <div className="note error" role="alertdialog" aria-labelledby="wd-confirm">
          <p id="wd-confirm"><strong>정말 탈퇴할까요?</strong> 탈퇴하면 바로 로그아웃되고 첫 화면으로 돌아갑니다.</p>
          <div className="row">
            <button className="btn" type="button" disabled={act.busy} onClick={withdraw} autoFocus>네, 탈퇴합니다</button>
            <button className="btn plain" type="button" disabled={act.busy} onClick={() => setConfirming(false)}>그만두기</button>
          </div>
        </div>
      )}
    </form>
  );
}

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
      <Withdraw />
    </div>
  );
}
