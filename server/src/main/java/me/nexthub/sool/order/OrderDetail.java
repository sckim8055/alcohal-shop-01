package me.nexthub.sool.order;

import java.time.LocalDateTime;
import java.util.List;

/** 주문 한 건과 그 안의 판매자별 주문들. 영수증도 이 자료로 그린다. */
public record OrderDetail(Order order, List<Sub> subs) {

    public record Order(long id, String orderNo, long buyerId, long total, String receiver, String phone,
                        String address, String cashReceiptNo, LocalDateTime createdAt) {
    }

    /** 판매자별 주문 + 입금받을 판매자 정보 */
    public record SubRow(
            long id, long orderId, long sellerId, long itemsTotal, long shipFee, long total, boolean hasPhysical,
            String status, LocalDateTime depositDueAt, String depositorName, LocalDateTime depositReportedAt,
            LocalDateTime depositConfirmedAt, String cashReceiptApproval, String carrier, String trackingNo,
            LocalDateTime shippedAt, LocalDateTime deliveredAt, LocalDateTime completedAt,
            LocalDateTime closedAt, String closeReason,
            String shopName, String bizNo, String bankName, String bankAccount, String bankHolder, String payLink,
            long sellerMemberId,
            long itemDiscount, long shipDiscount, String couponName, String couponIssuer) {
    }

    public record Item(long id, long subOrderId, long productId, String name, String productType,
                       long unitPrice, int qty, int commissionRateBp, long couponDiscount) {
    }

    public record Sub(SubRow sub, List<Item> items, TrackingService.Tracking tracking) {
    }
}
