package me.nexthub.sool.review;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 리뷰 한 건(docs/adr/0003 A). authorName 은 여러 사람이 보는 상품 화면에서는 가운데를 가린 이름이다.
 * mine 은 지금 보는 사람이 쓴 리뷰인지.
 */
public record Review(long id, long productId, String productName, int rating, String body, List<String> images,
                     String authorName, boolean mine, String status, String sellerReply, LocalDateTime repliedAt,
                     LocalDateTime createdAt) {
}
