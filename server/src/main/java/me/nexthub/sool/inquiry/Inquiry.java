package me.nexthub.sool.inquiry;

import java.time.LocalDateTime;

/**
 * 문의 한 건(docs/adr/0003 B). kind: product(상품 문의), order(주문 문의), admin(운영자에게 1:1 문의).
 * 볼 수 없는 비밀글이면 title 은 "비밀글입니다", body 와 answer 는 null 이다.
 */
public record Inquiry(long id, String target, String kind, Long productId, String productName, Long subOrderId,
                      Long orderId, String orderNo, String shopName, String title, String body, boolean secret,
                      String authorName, boolean mine, String status, String answer, LocalDateTime answeredAt,
                      LocalDateTime createdAt) {
}
