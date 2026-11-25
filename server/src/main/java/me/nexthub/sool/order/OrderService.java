package me.nexthub.sool.order;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.nexthub.sool.auth.Member;
import me.nexthub.sool.catalog.Catalog;
import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.AppClock;
import me.nexthub.sool.common.Db;
import me.nexthub.sool.common.Tenant;
import me.nexthub.sool.common.Text;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 생성과 판매자별 주문(sub_order)의 상태 변경.
 * 상태 변경은 모두 "현재 상태가 맞을 때만" 바뀌는 조건부 UPDATE로 처리한다.
 * 같은 요청이 두 번 오거나 두 사람이 동시에 눌러도 한 번만 반영된다.
 */
@Service
public class OrderService {

    public static final int DEPOSIT_DAYS = 3;        // 입금 기한
    public static final int AUTO_COMPLETE_DAYS = 7;  // 배송 완료 후 자동 구매확정
    private static final String NOT_ALLOWED = "이미 처리되었거나 지금은 할 수 없는 작업입니다.";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcClient db;
    private final Tenant tenant;
    private final AppClock clock;
    private final TrackingService tracking;

    public OrderService(JdbcClient db, Tenant tenant, AppClock clock, TrackingService tracking) {
        this.db = db;
        this.tenant = tenant;
        this.clock = clock;
        this.tracking = tracking;
    }

    // ---- 장바구니 ----

    List<CartLine> cartLines(long memberId) {
        String sql = Catalog.PRODUCT_SELECT.replace("SELECT p.id,", "SELECT ci.qty, p.id,")
                + " JOIN cart_item ci ON ci.product_id = p.id WHERE ci.member_id = :m ORDER BY s.id, p.id";
        return db.sql(sql).param("today", clock.today()).param("m", memberId).query(CartLine.class).list();
    }

    public CartSummary cart(long memberId) {
        return summarize(cartLines(memberId));
    }

    CartSummary summarize(List<CartLine> lines) {
        Map<Long, List<CartLine>> bySeller = new LinkedHashMap<>();
        for (CartLine l : lines) {
            bySeller.computeIfAbsent(l.sellerId(), k -> new ArrayList<>()).add(l);
        }
        List<CartSummary.Group> groups = new ArrayList<>();
        long total = 0;
        boolean hasAlcohol = false;
        for (List<CartLine> sellerLines : bySeller.values()) {
            CartLine first = sellerLines.get(0);
            long itemsTotal = 0;
            long physicalTotal = 0;
            boolean hasPhysical = false;
            List<CartSummary.Line> view = new ArrayList<>();
            for (CartLine l : sellerLines) {
                itemsTotal += l.lineTotal();
                if (l.physical()) {
                    hasPhysical = true;
                    physicalTotal += l.lineTotal();
                }
                hasAlcohol = hasAlcohol || l.alcohol();
                view.add(new CartSummary.Line(l.id(), l.name(), l.productType(), l.categoryName(), l.alcohol(),
                        l.currentPrice(), l.qty(), l.lineTotal(), l.stock(), orderable(l)));
            }
            long fee = shipFee(first, physicalTotal, hasPhysical);
            groups.add(new CartSummary.Group(first.sellerId(), first.shopName(), view,
                    itemsTotal, hasPhysical, fee, itemsTotal + fee));
            total += itemsTotal + fee;
        }
        return new CartSummary(groups, total, hasAlcohol);
    }

    private static boolean orderable(CartLine l) {
        return "active".equals(l.status()) && "approved".equals(l.sellerStatus());
    }

    /** 판매자별 배송비: 실물 상품이 없으면 0, 무료배송 행사 기간이거나 기준 금액 이상이면 0 */
    private long shipFee(CartLine seller, long physicalTotal, boolean hasPhysical) {
        if (!hasPhysical) {
            return 0;
        }
        var today = clock.today();
        boolean hasEvent = seller.freeShipStart() != null || seller.freeShipEnd() != null;
        boolean inEvent = (seller.freeShipStart() == null || !seller.freeShipStart().isAfter(today))
                && (seller.freeShipEnd() == null || !today.isAfter(seller.freeShipEnd()));
        if (hasEvent && inEvent) {
            return 0;
        }
        if (seller.freeShipMin() != null && physicalTotal >= seller.freeShipMin()) {
            return 0;
        }
        return seller.baseShipFee();
    }

    /** 수수료율 우선순위: 판매자별 > 카테고리별 > 몰 기본 */
    private int commissionRateBp(CartLine l) {
        if (l.sellerRateBp() != null) {
            return l.sellerRateBp();
        }
        if (l.categoryRateBp() != null) {
            return l.categoryRateBp();
        }
        return defaultRateBp();
    }

    public int defaultRateBp() {
        return db.sql("SELECT setting_value FROM app_setting WHERE tenant_id = :t AND setting_key = 'default_commission_rate_bp'")
                .param("t", tenant.id()).query(String.class).optional().map(Integer::parseInt).orElse(300);
    }

    public void addToCart(long memberId, long productId, int qty) {
        int updated = db.sql("""
                UPDATE cart_item SET qty = CASE WHEN qty + :q > 99 THEN 99 ELSE qty + :q END
                 WHERE member_id = :m AND product_id = :p
                """).param("q", qty).param("m", memberId).param("p", productId).update();
        if (updated == 0) {
            db.sql("INSERT INTO cart_item (member_id, product_id, qty) VALUES (:m, :p, :q)")
                    .param("m", memberId).param("p", productId).param("q", qty).update();
        }
    }

    public void setCartQty(long memberId, long productId, int qty) {
        if (qty == 0) {
            db.sql("DELETE FROM cart_item WHERE member_id = :m AND product_id = :p")
                    .param("m", memberId).param("p", productId).update();
        } else {
            db.sql("UPDATE cart_item SET qty = :q WHERE member_id = :m AND product_id = :p")
                    .param("q", qty).param("m", memberId).param("p", productId).update();
        }
    }

    // ---- 주문 생성 ----

    @Transactional
    public long createOrder(Member user, String receiverIn, String phoneIn, String addressIn, String cashReceiptNoIn) {
        List<CartLine> lines = cartLines(user.id());
        if (lines.isEmpty()) {
            throw ApiException.bad("장바구니가 비어 있습니다.");
        }
        for (CartLine l : lines) {
            if (!orderable(l)) {
                throw ApiException.conflict("'" + l.name() + "' 상품은 지금 주문할 수 없습니다.");
            }
        }
        CartSummary sum = summarize(lines);
        if (sum.hasAlcohol() && user.adultVerifiedAt() == null) {
            throw ApiException.forbidden("주류가 포함된 주문은 성인인증이 필요합니다.");
        }
        String receiver = Text.clean(receiverIn, 40);
        String phone = Text.clean(phoneIn, 30);
        String address = Text.clean(addressIn, 300);
        boolean needsAddress = sum.groups().stream().anyMatch(CartSummary.Group::hasPhysical);
        if (receiver.isEmpty() || phone.isEmpty() || (needsAddress && address.isEmpty())) {
            throw ApiException.bad("받는 분, 연락처, 주소를 입력해 주세요.");
        }

        // 재고가 충분할 때만 차감한다. 동시에 주문이 들어와도 초과 판매되지 않는다.
        for (CartLine l : lines) {
            int changed = db.sql("UPDATE product SET stock = stock - :q WHERE id = :id AND stock >= :q")
                    .param("q", l.qty()).param("id", l.id()).update();
            if (changed != 1) {
                throw ApiException.conflict("'" + l.name() + "' 재고가 부족합니다.");
            }
        }

        LocalDateTime now = clock.now();
        long orderId = Db.insert(db.sql("""
                INSERT INTO shop_order (tenant_id, order_no, buyer_id, total, receiver, phone, address, cash_receipt_no, created_at)
                VALUES (:t, :no, :b, :total, :r, :p, :a, :c, :now)
                """)
                .param("t", tenant.id()).param("no", newOrderNo()).param("b", user.id()).param("total", sum.total())
                .param("r", receiver).param("p", phone).param("a", address)
                .param("c", Text.clean(cashReceiptNoIn, 30)).param("now", now));

        Map<Long, List<CartLine>> bySeller = new LinkedHashMap<>();
        for (CartLine l : lines) {
            bySeller.computeIfAbsent(l.sellerId(), k -> new ArrayList<>()).add(l);
        }
        for (CartSummary.Group g : sum.groups()) {
            long subId = Db.insert(db.sql("""
                    INSERT INTO sub_order (tenant_id, order_id, seller_id, items_total, ship_fee, total, has_physical, deposit_due_at)
                    VALUES (:t, :o, :s, :items, :fee, :total, :phys, :due)
                    """)
                    .param("t", tenant.id()).param("o", orderId).param("s", g.sellerId())
                    .param("items", g.itemsTotal()).param("fee", g.shipFee()).param("total", g.total())
                    .param("phys", g.hasPhysical()).param("due", now.plusDays(DEPOSIT_DAYS)));
            for (CartLine l : bySeller.get(g.sellerId())) {
                // 상품명·가격·수수료율은 주문 시점 값으로 복사해 둔다. 나중에 바뀌어도 이 주문은 그대로다.
                db.sql("""
                        INSERT INTO order_item (sub_order_id, product_id, name, product_type, unit_price, qty, commission_rate_bp)
                        VALUES (:s, :p, :n, :type, :price, :q, :rate)
                        """)
                        .param("s", subId).param("p", l.id()).param("n", l.name()).param("type", l.productType())
                        .param("price", l.currentPrice()).param("q", l.qty()).param("rate", commissionRateBp(l))
                        .update();
            }
        }
        db.sql("DELETE FROM cart_item WHERE member_id = :m").param("m", user.id()).update();
        return orderId;
    }

    private String newOrderNo() {
        byte[] bytes = new byte[3];
        RANDOM.nextBytes(bytes);
        return "S" + clock.today().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + HexFormat.of().withUpperCase().formatHex(bytes);
    }

    // ---- 상태 변경 ----

    private static void mustChange(int changed, String message) {
        if (changed != 1) {
            throw ApiException.conflict(message);
        }
    }

    private void restoreStock(long subId) {
        db.sql("""
                UPDATE product SET stock = stock + (SELECT SUM(oi.qty) FROM order_item oi
                                                     WHERE oi.sub_order_id = :s AND oi.product_id = product.id)
                 WHERE id IN (SELECT product_id FROM order_item WHERE sub_order_id = :s)
                """).param("s", subId).update();
    }

    /** 구매자: "입금했어요" */
    public void reportDeposit(long subId, long buyerId, String depositorIn) {
        String depositor = Text.clean(depositorIn, 40);
        if (depositor.isEmpty()) {
            throw ApiException.bad("입금자명을 입력해 주세요.");
        }
        mustChange(db.sql("""
                UPDATE sub_order SET depositor_name = :n, deposit_reported_at = :now
                 WHERE id = :id AND status = 'awaiting_deposit' AND deposit_reported_at IS NULL
                   AND order_id IN (SELECT id FROM shop_order WHERE buyer_id = :b)
                """).param("n", depositor).param("now", clock.now()).param("id", subId).param("b", buyerId).update(),
                NOT_ALLOWED);
    }

    /** 구매자: 입금 신고 전 취소 */
    @Transactional
    public void cancelByBuyer(long subId, long buyerId) {
        mustChange(db.sql("""
                UPDATE sub_order SET status = 'cancelled', closed_at = :now, close_reason = '구매자 취소'
                 WHERE id = :id AND status = 'awaiting_deposit' AND deposit_reported_at IS NULL
                   AND order_id IN (SELECT id FROM shop_order WHERE buyer_id = :b)
                """).param("now", clock.now()).param("id", subId).param("b", buyerId).update(),
                "입금 신고 후에는 판매자에게 취소를 요청해 주세요.");
        restoreStock(subId);
    }

    /** 판매자: 입금 확인. 현금영수증 승인번호는 선택 입력 */
    public void confirmDeposit(long subId, long sellerId, String approvalIn) {
        String approval = Text.clean(approvalIn, 30);
        mustChange(db.sql("""
                UPDATE sub_order SET status = 'paid', deposit_confirmed_at = :now, cash_receipt_approval = :a
                 WHERE id = :id AND seller_id = :s AND status = 'awaiting_deposit'
                """).param("now", clock.now()).param("a", approval.isEmpty() ? null : approval)
                .param("id", subId).param("s", sellerId).update(), NOT_ALLOWED);
    }

    /** 판매자: 발송(실물 상품) */
    public void ship(long subId, long sellerId, String carrier, String trackingNoIn) {
        String trackingNo = trackingNoIn == null ? "" : trackingNoIn.replaceAll("[^0-9A-Za-z-]", "");
        trackingNo = Text.clean(trackingNo, 40);
        if (!tracking.knownCarrier(carrier) || trackingNo.isEmpty()) {
            throw ApiException.bad("택배사와 송장번호를 입력해 주세요.");
        }
        mustChange(db.sql("""
                UPDATE sub_order SET status = 'shipped', carrier = :c, tracking_no = :n, shipped_at = :now
                 WHERE id = :id AND seller_id = :s AND status = 'paid' AND has_physical = TRUE
                """).param("c", carrier).param("n", trackingNo).param("now", clock.now())
                .param("id", subId).param("s", sellerId).update(), NOT_ALLOWED);
    }

    /** 판매자: 수업·이용권처럼 배송이 없는 주문의 전달 완료 */
    public void deliverTicket(long subId, long sellerId) {
        mustChange(db.sql("""
                UPDATE sub_order SET status = 'delivered', delivered_at = :now
                 WHERE id = :id AND seller_id = :s AND status = 'paid' AND has_physical = FALSE
                """).param("now", clock.now()).param("id", subId).param("s", sellerId).update(), NOT_ALLOWED);
    }

    /** 구매자: 수령 확인(구매확정) */
    public void confirmReceipt(long subId, long buyerId) {
        mustChange(db.sql("""
                UPDATE sub_order SET status = 'completed', completed_at = :now, delivered_at = COALESCE(delivered_at, :now)
                 WHERE id = :id AND status IN ('shipped', 'delivered')
                   AND order_id IN (SELECT id FROM shop_order WHERE buyer_id = :b)
                """).param("now", clock.now()).param("id", subId).param("b", buyerId).update(), NOT_ALLOWED);
    }

    /** 판매자: 환불 완료 기록. 돈은 판매자가 구매자에게 직접 돌려준다. */
    @Transactional
    public void refund(long subId, long sellerId) {
        // 발송 전 환불만 재고를 되돌린다. 발송 전 상태에서 먼저 시도하고, 아니면 발송 후 상태에서 시도한다.
        int beforeShipping = db.sql("""
                UPDATE sub_order SET status = 'refunded', closed_at = :now, close_reason = '판매자 환불'
                 WHERE id = :id AND seller_id = :s AND status IN ('awaiting_deposit', 'paid')
                """).param("now", clock.now()).param("id", subId).param("s", sellerId).update();
        if (beforeShipping == 1) {
            restoreStock(subId);
            return;
        }
        mustChange(db.sql("""
                UPDATE sub_order SET status = 'refunded', closed_at = :now, close_reason = '판매자 환불'
                 WHERE id = :id AND seller_id = :s AND status IN ('shipped', 'delivered')
                """).param("now", clock.now()).param("id", subId).param("s", sellerId).update(), NOT_ALLOWED);
    }

    // ---- 기한 처리 ----

    @Scheduled(fixedDelayString = "${sool.sweep-interval-ms:60000}", initialDelayString = "${sool.sweep-interval-ms:60000}")
    void scheduledSweep() {
        sweep();
    }

    /**
     * 기한이 지난 주문 정리.
     * 1) 입금 신고 없이 기한이 지난 주문은 취소하고 재고를 되돌린다.
     * 2) 배송 조회 결과가 배송 완료이면 상태에 반영한다.
     * 3) 배송 완료 후 7일이 지나면 구매확정으로 바꾼다.
     */
    public int sweep() {
        LocalDateTime now = clock.now();
        List<Long> overdue = db.sql("""
                SELECT id FROM sub_order
                 WHERE tenant_id = :t AND status = 'awaiting_deposit' AND deposit_reported_at IS NULL AND deposit_due_at < :now
                """).param("t", tenant.id()).param("now", now).query(Long.class).list();
        int cancelled = 0;
        for (Long id : overdue) {
            int changed = db.sql("""
                    UPDATE sub_order SET status = 'cancelled', closed_at = :now, close_reason = '입금 기한 경과'
                     WHERE id = :id AND status = 'awaiting_deposit' AND deposit_reported_at IS NULL
                    """).param("now", now).param("id", id).update();
            if (changed == 1) {
                restoreStock(id);
                cancelled++;
            }
        }

        List<OrderDetail.SubRow> shipped = db.sql(SUB_SELECT + " WHERE so.tenant_id = :t AND so.status = 'shipped'")
                .param("t", tenant.id()).query(OrderDetail.SubRow.class).list();
        for (OrderDetail.SubRow s : shipped) {
            var t = tracking.track(s.carrier(), s.trackingNo(), s.shippedAt());
            if (t != null && t.delivered()) {
                db.sql("UPDATE sub_order SET status = 'delivered', delivered_at = :now WHERE id = :id AND status = 'shipped'")
                        .param("now", now).param("id", s.id()).update();
            }
        }

        db.sql("""
                UPDATE sub_order SET status = 'completed', completed_at = :now
                 WHERE tenant_id = :t AND status = 'delivered' AND delivered_at < :limit
                """).param("now", now).param("t", tenant.id()).param("limit", now.minusDays(AUTO_COMPLETE_DAYS)).update();
        return cancelled;
    }

    // ---- 조회 ----

    static final String SUB_SELECT = """
            SELECT so.id, so.order_id, so.seller_id, so.items_total, so.ship_fee, so.total, so.has_physical, so.status,
                   so.deposit_due_at, so.depositor_name, so.deposit_reported_at, so.deposit_confirmed_at,
                   so.cash_receipt_approval, so.carrier, so.tracking_no, so.shipped_at, so.delivered_at,
                   so.completed_at, so.closed_at, so.close_reason,
                   s.shop_name, s.biz_no, s.bank_name, s.bank_account, s.bank_holder, s.pay_link,
                   s.member_id AS seller_member_id
              FROM sub_order so JOIN seller s ON s.id = so.seller_id
            """;

    private static final String ORDER_SELECT = """
            SELECT id, order_no, buyer_id, total, receiver, phone, address, cash_receipt_no, created_at
              FROM shop_order
            """;

    /** 없으면 null. 볼 권한은 호출하는 쪽에서 확인한다. */
    public OrderDetail find(long orderId) {
        OrderDetail.Order order = db.sql(ORDER_SELECT + " WHERE id = :id AND tenant_id = :t")
                .param("id", orderId).param("t", tenant.id()).query(OrderDetail.Order.class).optional().orElse(null);
        return order == null ? null : withSubs(order);
    }

    public List<OrderDetail> listForBuyer(long buyerId) {
        List<OrderDetail> result = new ArrayList<>();
        for (OrderDetail.Order o : db.sql(ORDER_SELECT + " WHERE buyer_id = :b ORDER BY id DESC LIMIT 100")
                .param("b", buyerId).query(OrderDetail.Order.class).list()) {
            result.add(withSubs(o));
        }
        return result;
    }

    private OrderDetail withSubs(OrderDetail.Order order) {
        List<OrderDetail.Sub> subs = new ArrayList<>();
        for (OrderDetail.SubRow s : db.sql(SUB_SELECT + " WHERE so.order_id = :o ORDER BY so.id")
                .param("o", order.id()).query(OrderDetail.SubRow.class).list()) {
            subs.add(toSub(s));
        }
        return new OrderDetail(order, subs);
    }

    OrderDetail.Sub toSub(OrderDetail.SubRow s) {
        List<OrderDetail.Item> items = db.sql("""
                SELECT id, sub_order_id, product_id, name, product_type, unit_price, qty, commission_rate_bp
                  FROM order_item WHERE sub_order_id = :s ORDER BY id
                """).param("s", s.id()).query(OrderDetail.Item.class).list();
        return new OrderDetail.Sub(s, items, tracking.track(s.carrier(), s.trackingNo(), s.shippedAt()));
    }

    /** 판매자 화면용: 내 가게로 들어온 판매자별 주문과 배송지 */
    public record SellerOrder(OrderDetail.Order order, OrderDetail.Sub sub) {
    }

    public List<SellerOrder> listForSeller(long sellerId) {
        List<SellerOrder> result = new ArrayList<>();
        for (OrderDetail.SubRow s : db.sql(SUB_SELECT + " WHERE so.seller_id = :s ORDER BY so.id DESC LIMIT 200")
                .param("s", sellerId).query(OrderDetail.SubRow.class).list()) {
            OrderDetail.Order o = db.sql(ORDER_SELECT + " WHERE id = :id").param("id", s.orderId())
                    .query(OrderDetail.Order.class).single();
            result.add(new SellerOrder(o, toSub(s)));
        }
        return result;
    }
}
