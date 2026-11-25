package me.nexthub.sool.catalog;

import java.time.LocalDate;

/** 상품 목록·상세에 보여 주는 정보. currentPrice는 할인 기간이면 할인가, 아니면 정가. */
public record ProductView(
        long id, long sellerId, long categoryId, String name, String description, String productType,
        long price, Long salePrice, LocalDate saleStart, LocalDate saleEnd, long currentPrice,
        int stock, String status, String categoryName, boolean alcohol,
        String shopName, String sellerStatus, String sellerIntro, int baseShipFee, Integer freeShipMin) {
}
