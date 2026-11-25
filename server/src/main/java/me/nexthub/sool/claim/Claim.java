package me.nexthub.sool.claim;

import java.time.LocalDateTime;

/**
 * 취소·반품·교환 신청 한 건(docs/adr/0002). items 는 "상품A × 2, 상품B × 1" 한 줄 요약,
 * total 은 그 판매자별 주문의 합계다.
 * returnAddress 는 승인된 반품·교환일 때만 채운다(구매자가 물건을 돌려보낼 곳).
 */
public record Claim(
        long id, long subOrderId, long orderId, String orderNo, String shopName, String buyerName,
        String claimType, String reasonCode, String reasonText, String status, String sellerNote,
        String carrier, String trackingNo, String items, long total,
        LocalDateTime requestedAt, LocalDateTime answeredAt, LocalDateTime doneAt,
        String returnAddress) {
}
