package me.nexthub.sool.order;

import java.time.LocalDate;

/** 장바구니 한 줄: 상품, 판매자 배송 조건, 수수료율 계산에 필요한 값까지 한 번에 읽는다. */
record CartLine(
        long id, long sellerId, long categoryId, String name, String productType,
        long currentPrice, int stock, String status, int qty,
        String categoryName, boolean alcohol, Integer categoryRateBp,
        String shopName, String sellerStatus, int baseShipFee, Integer freeShipMin,
        LocalDate freeShipStart, LocalDate freeShipEnd, Integer sellerRateBp) {

    boolean physical() {
        return "physical".equals(productType);
    }

    long lineTotal() {
        return currentPrice * qty;
    }
}
