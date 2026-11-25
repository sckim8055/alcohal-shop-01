'use client';

import { useState } from 'react';
import { CouponForm, CouponList } from '@/components/CouponAdmin';

export default function SellerCoupons() {
  const [key, setKey] = useState(0);
  return (
    <>
      <div className="spread title">
        <div><h1>쿠폰</h1></div>
        <a className="btn" href="#new-coupon">새 쿠폰 만들기</a>
      </div>
      <p>내 가게 주문에만 쓸 수 있는 쿠폰입니다. 구매자는 쿠폰 받기 화면에서 받거나 코드를 넣어 받습니다.</p>
      <div className="stack">
        <div><CouponList mode="seller" reloadKey={key} /></div>
        <CouponForm mode="seller" onCreated={() => setKey((k) => k + 1)} />
      </div>
    </>
  );
}
