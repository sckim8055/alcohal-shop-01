import { couponHeadlineParts, couponOff, couponTerms, won } from '@/lib/format';
import type { Coupon, SubRow } from '@/lib/types';

/** 쿠폰 한 장. 골판지 가격표처럼 왼쪽은 할인(큰 글씨), 오른쪽은 조건, 그 사이에 점선 절취선 하나. */
export function CouponTicket({ coupon, expiresAt, off, stamp, children }: {
  coupon: Coupon;
  /** 받은 쿠폰이면 실제 만료 시각(UTC) */
  expiresAt?: string | null;
  /** 쓴 쿠폰·기한 지난 쿠폰은 흐리게 */
  off?: boolean;
  stamp?: React.ReactNode;
  children?: React.ReactNode;
}) {
  const [main, sub] = couponHeadlineParts(coupon);
  return (
    <article className={'ticket' + (off ? ' off' : '')} aria-label={coupon.name + ' 쿠폰'}>
      <div className="ticket-face">
        <span className="ticket-from">{coupon.issuer === 'platform' ? '술장터' : coupon.shopName}</span>
        <strong>{main}{sub && <small>{sub}</small>}</strong>
        {stamp}
      </div>
      <div className="ticket-terms">
        <h3>{coupon.name}</h3>
        <ul>{couponTerms(coupon, expiresAt).map((t) => <li key={t}>{t}</li>)}</ul>
        {children && <div className="ticket-act">{children}</div>}
      </div>
    </article>
  );
}

/** 주문 표 안의 "쿠폰 할인 (이름)" 줄. 할인이 없으면 그리지 않는다. */
export function CouponRow({ sub, colSpan }: { sub: SubRow; colSpan?: number }) {
  const off = couponOff(sub);
  if (off <= 0) return null;
  return (
    <tr>
      <td colSpan={colSpan}>쿠폰 할인 ({sub.couponName ?? '쿠폰'})</td>
      <td className="num">-{won(off)}</td>
    </tr>
  );
}
