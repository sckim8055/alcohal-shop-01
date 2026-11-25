'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { CouponTicket } from '@/components/CouponTicket';
import { Note, useAction } from '@/components/Bits';
import { useMe } from '@/components/Me';
import { api } from '@/lib/api';
import type { CouponOffer, MemberCoupon } from '@/lib/types';

const LOGIN = '/login?next=' + encodeURIComponent('/coupons');

export default function CouponsPage() {
  const { me, loading } = useMe();
  const user = me?.user;
  const act = useAction();
  const [list, setList] = useState<CouponOffer[] | null>(null);
  const [failed, setFailed] = useState('');
  const [code, setCode] = useState('');
  const [redeemed, setRedeemed] = useState('');

  const load = useCallback(() => {
    api<CouponOffer[]>('/coupons').then((l) => { setList(l); setFailed(''); }).catch((e) => { setList([]); setFailed(e.message); });
  }, []);
  // 로그인 상태가 바뀌면 "받았어요" 표시가 달라지므로 다시 불러온다.
  useEffect(() => { if (!loading) load(); }, [load, loading, user?.id]);

  const claim = (o: CouponOffer) => { setRedeemed(''); return act.run(() => api<MemberCoupon>(`/coupons/${o.coupon.id}/claim`, { method: 'POST' }), `'${o.coupon.name}' 쿠폰을 받았어요. 주문할 때 고를 수 있습니다.`).then((ok) => ok && load()); };

  async function redeem(e: React.FormEvent) {
    e.preventDefault();
    setRedeemed('');
    const ok = await act.run(async () => {
      const mc = await api<MemberCoupon>('/coupons/redeem', { method: 'POST', body: { code: code.trim() } });
      setRedeemed(`'${mc.coupon.name}' 쿠폰을 받았어요. 주문할 때 고를 수 있습니다.`);
    });
    if (ok) { setCode(''); load(); }
  }

  return (
    <>
      <div className="title"><h1>쿠폰 받기</h1></div>
      <p>받은 쿠폰은 주문할 때 가게마다 한 장씩 골라 쓸 수 있습니다. 받은 쿠폰은 <Link href="/my/coupons">내 쿠폰</Link>에서 볼 수 있습니다.</p>
      <Note kind="error">{act.error}</Note>
      <Note kind="ok">{act.done || redeemed}</Note>

      <section className="sheet narrow" style={{ marginBottom: 24 }}>
        <h3>쿠폰 코드가 있나요?</h3>
        {user ? (
          <form className="inline-form" onSubmit={redeem} style={{ marginTop: 8 }}>
            <label htmlFor="coupon-code" className="small" style={{ flexBasis: '100%', margin: 0 }}>쿠폰 코드</label>
            <input id="coupon-code" required maxLength={40} autoComplete="off" autoCapitalize="characters" placeholder="예: SOOL-FREE" value={code} onChange={(e) => setCode(e.target.value)} />
            <button className="btn" disabled={act.busy || !code.trim()}>코드로 쿠폰 받기</button>
          </form>
        ) : (
          <p style={{ marginTop: 8 }}><Link className="btn plain" href={LOGIN}>로그인하고 코드 넣기</Link></p>
        )}
      </section>

      {!list ? <p className="muted">불러오는 중입니다.</p> : failed ? (
        <Note kind="error">{failed}</Note>
      ) : list.length === 0 ? (
        <div className="sheet"><h3>지금 받을 수 있는 쿠폰이 없습니다</h3><p>새 쿠폰이 나오면 여기에 놓아 둘게요. 그동안 장을 둘러보세요.</p><Link className="btn" href="/">장 보러 가기</Link></div>
      ) : (
        <div className="tickets">
          {list.map((o) => (
            <CouponTicket key={o.coupon.id} coupon={o.coupon} off={o.soldOut && !o.owned}
              stamp={o.owned ? <span className="stamp good">받았어요</span> : o.soldOut ? <span className="stamp off">모두 나갔어요</span> : null}>
              {o.owned ? (
                <Link className="btn plain small" href="/my/coupons">내 쿠폰 보기</Link>
              ) : o.soldOut ? null : user ? (
                <button type="button" className="btn small" disabled={act.busy} onClick={() => claim(o)}>받기</button>
              ) : (
                <Link className="btn small" href={LOGIN}>로그인하고 받기</Link>
              )}
            </CouponTicket>
          ))}
        </div>
      )}
    </>
  );
}
