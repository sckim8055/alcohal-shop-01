'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { CouponTicket } from '@/components/CouponTicket';
import { Note } from '@/components/Bits';
import { Pager } from '@/components/Pager';
import { api } from '@/lib/api';
import { when } from '@/lib/format';
import type { MemberCoupon, Paged } from '@/lib/types';

type Tab = 'usable' | 'used' | 'expired';
const TABS: [Tab, string][] = [['usable', '쓸 수 있는 쿠폰'], ['used', '쓴 쿠폰'], ['expired', '기한 지난 쿠폰']];
const EMPTY: Record<Tab, [string, string]> = {
  usable: ['쓸 수 있는 쿠폰이 없습니다', '쿠폰 받기 화면에서 쿠폰을 받거나 쿠폰 코드를 넣어 보세요.'],
  used: ['아직 쓴 쿠폰이 없습니다', '주문할 때 가게마다 쿠폰을 한 장씩 고를 수 있습니다.'],
  expired: ['기한 지난 쿠폰이 없습니다', '쿠폰은 기한 안에 써야 합니다. 받을 수 있는 쿠폰을 둘러보세요.'],
};

export default function MyCoupons() {
  const [tab, setTab] = useState<Tab>('usable');
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paged<MemberCoupon> | null>(null);
  const [failed, setFailed] = useState('');

  useEffect(() => {
    setData(null);
    setFailed('');
    api<Paged<MemberCoupon>>(`/my/coupons?status=${tab}&page=${page}`).then(setData).catch((e) => { setFailed(e.message); setData({ items: [], page: 1, size: 20, total: 0 }); });
  }, [tab, page]);

  return (
    <>
      <div className="spread title">
        <div><h1>내 쿠폰</h1></div>
        <Link className="btn plain" href="/coupons">쿠폰 받으러 가기</Link>
      </div>
      <div className="tabs" role="group" aria-label="쿠폰 상태">
        {TABS.map(([v, label]) => <button key={v} type="button" className="chip" aria-current={tab === v} onClick={() => { setTab(v); setPage(1); }}>{label}</button>)}
      </div>
      <Note kind="error">{failed}</Note>
      {!data ? <p className="muted">불러오는 중입니다.</p> : data.items.length === 0 ? (
        <div className="sheet"><h3>{EMPTY[tab][0]}</h3><p>{EMPTY[tab][1]}</p><Link className="btn" href="/coupons">쿠폰 받으러 가기</Link></div>
      ) : (
        <>
          <div className="tickets">
            {data.items.map((m) => {
              const used = m.status === 'used';
              const expired = !used && m.expired;
              return (
                <CouponTicket key={m.id} coupon={m.coupon} expiresAt={m.expiresAt} off={used || expired}
                  stamp={used ? <span className="stamp off">썼어요</span> : expired ? <span className="stamp off">기한 지남</span> : null}>
                  <span className="muted small">
                    {when(m.issuedAt, false)}에 받음{used && m.usedAt && <>, {when(m.usedAt, false)}에 씀</>}
                  </span>
                </CouponTicket>
              );
            })}
          </div>
          <Pager page={data.page} size={data.size} total={data.total} onPage={setPage} />
        </>
      )}
      <p className="hint" style={{ marginTop: 16 }}>
        쿠폰은 주문할 때 가게마다 한 장씩 고를 수 있습니다. 주문이 취소되거나 환불되면 쓴 쿠폰을 돌려 드립니다(기한이 남았을 때만 다시 쓸 수 있습니다).
      </p>
    </>
  );
}
