package me.nexthub.sool.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 상품 목록·상세에 보여 주는 정보. currentPrice 는 할인 기간이면 할인가, 아니면 정가.
 * topCategoryName 은 대분류 이름, mainImage 는 대표 사진 주소(없으면 null).
 */
public record ProductView(
        long id, long sellerId, long categoryId, String name, String description, String productType,
        long price, Long salePrice, LocalDate saleStart, LocalDate saleEnd, long currentPrice,
        int stock, String status, String categoryName, String topCategoryName, boolean alcohol,
        BigDecimal abv, Integer volumeMl, String origin, String mainImage,
        String shopName, String sellerStatus, String sellerIntro, int baseShipFee, Integer freeShipMin) {

    public boolean onSale() {
        return "on_sale".equals(status) && "approved".equals(sellerStatus);
    }
}
