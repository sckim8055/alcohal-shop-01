'use client';

import { useState } from 'react';
import { CouponForm, CouponList } from '@/components/CouponAdmin';

export default function AdminCoupons() {
  const [key, setKey] = useState(0);
  return (
    <>
      <div className="spread title">
        <div><h1>쿠폰</h1></div>
        <a className="btn" href="#new-coupon">플랫폼 쿠폰 만들기</a>
      </div>
      <p>술장터 쿠폰과 가게 쿠폰을 모두 봅니다. 문제가 있는 가게 쿠폰은 멈추거나 끝낼 수 있습니다.</p>
      <div className="stack">
        <div><CouponList mode="admin" reloadKey={key} /></div>
        <CouponForm mode="admin" onCreated={() => setKey((k) => k + 1)} />
      </div>
    </>
  );
}
