package me.nexthub.sool.order;

import java.util.List;

/** 장바구니를 판매자별로 묶어 계산한 결과. 주문하면 이 묶음 하나가 판매자별 주문 하나가 된다. */
public record CartSummary(List<Group> groups, long total, boolean hasAlcohol) {

    public record Group(long sellerId, String shopName, List<Line> lines,
                        long itemsTotal, boolean hasPhysical, long shipFee, long total) {
    }

    public record Line(long productId, String name, String productType, String categoryName, boolean alcohol,
                       long unitPrice, int qty, long lineTotal, int stock, boolean orderable) {
    }
}
