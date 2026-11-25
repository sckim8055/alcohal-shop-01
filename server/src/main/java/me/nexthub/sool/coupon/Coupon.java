package me.nexthub.sool.coupon;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 쿠폰 한 종류. code 는 만든 사람(판매자·운영자)에게만 보인다.
 * 구매자에게 내보낼 때는 반드시 {@link #forBuyer()} 를 거친다.
 */
public record Coupon(
        long id, String issuer, Long sellerId, String shopName, String name,
        String discountType, int discountValue, Long maxDiscount, long minOrder,
        Long categoryId, String categoryName, boolean excludeAlcohol,
        String issueType, String code, Integer totalLimit, int issuedCount,
        LocalDate validFrom, LocalDate validTo, Integer validDays,
        String status, LocalDateTime createdAt) {

    /** 구매자 응답용: 코드를 숨긴다. 코드를 알면 받기 화면 없이도 쿠폰을 받을 수 있기 때문이다. */
    public Coupon forBuyer() {
        return new Coupon(id, issuer, sellerId, shopName, name, discountType, discountValue, maxDiscount, minOrder,
                categoryId, categoryName, excludeAlcohol, issueType, null, totalLimit, issuedCount,
                validFrom, validTo, validDays, status, createdAt);
    }

    public boolean soldOut() {
        return totalLimit != null && issuedCount >= totalLimit;
    }

    public boolean inPeriod(LocalDate today) {
        return !today.isBefore(validFrom) && !today.isAfter(validTo);
    }
}
