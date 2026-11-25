package me.nexthub.sool.coupon;

import java.time.LocalDateTime;

/** 회원이 가진 쿠폰 한 장. expired 는 아직 쓰지 않았는데 기한이 지났다는 뜻이다. */
public record MemberCoupon(long id, Coupon coupon, String status, LocalDateTime issuedAt, LocalDateTime expiresAt,
                           LocalDateTime usedAt, boolean expired) {
}
