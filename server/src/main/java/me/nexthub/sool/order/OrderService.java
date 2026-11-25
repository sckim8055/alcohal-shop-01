package me.nexthub.sool.order;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.nexthub.sool.auth.Member;
import me.nexthub.sool.catalog.Catalog;
import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.AppClock;
import me.nexthub.sool.common.Db;
import me.nexthub.sool.common.Paged;
import me.nexthub.sool.common.Tenant;
import me.nexthub.sool.common.Text;
import me.nexthub.sool.coupon.CouponService;
import me.nexthub.sool.coupon.MemberCoupon;
import me.nexthub.sool.notify.NotificationService;

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
    private final NotificationService notifications;
    private final CouponService coupons;

    public OrderService(JdbcClient db, Tenant tenant, AppClock clock, TrackingService tracking,
                        NotificationService notifications, CouponService coupons) {
        this.notifications = notifications;
        this.coupons = coupons;
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
        return "on_sale".equals(l.status()) && "approved".equals(l.sellerStatus());
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

    // ---- 쿠폰 ----

    /** 주문할 때 고른 쿠폰: 가게(sellerId)마다 회원 쿠폰 한 장 */
    public record CouponChoice(Long sellerId, Long memberCouponId) {
    }

    /** 가게 묶음 하나에 적용할 쿠폰과 계산 결과 */
    private record Applied(MemberCoupon held, CouponService.Quote quote) {
        long discount() {
            return quote.itemDiscount() + quote.shipDiscount();
        }
    }

    private static Map<Long, List<CartLine>> bySeller(List<CartLine> lines) {
        Map<Long, List<CartLine>> bySeller = new LinkedHashMap<>();
        for (CartLine l : lines) {
            bySeller.computeIfAbsent(l.sellerId(), k -> new ArrayList<>()).add(l);
        }
        return bySeller;
    }

    /** 쿠폰 계산용 묶음. 줄 순서는 sellerLines 와 같다(나눈 할인을 order_item 에 그대로 적기 위해). */
    private static CouponService.Basket basket(CartSummary.Group g, List<CartLine> sellerLines, Map<Long, Long> parents) {
        List<CouponService.Line> lines = new ArrayList<>();
        for (CartLine l : sellerLines) {
            lines.add(new CouponService.Line(l.categoryId(), parents.get(l.categoryId()), l.alcohol(), l.lineTotal()));
        }
        return new CouponService.Basket(g.sellerId(), g.shopName(), lines, g.shipFee());
    }

    /** 주문서: 지금 장바구니 기준으로 가게별로 쓸 수 있는 내 쿠폰 */
    public List<CouponService.CheckoutGroup> checkoutCoupons(long memberId) {
        List<CartLine> lines = cartLines(memberId);
        CartSummary sum = summarize(lines);
        Map<Long, List<CartLine>> bySeller = bySeller(lines);
        Map<Long, Long> parents = coupons.categoryParents();
        List<CouponService.Basket> baskets = new ArrayList<>();
        for (CartSummary.Group g : sum.groups()) {
            baskets.add(basket(g, bySeller.get(g.sellerId()), parents));
        }
        return coupons.options(memberId, baskets);
    }

    /** 고른 쿠폰을 확인하고 할인액을 계산한다. 하나라도 맞지 않으면 주문 전체를 받지 않는다. */
    private Map<Long, Applied> applyCoupons(long memberId, List<CouponChoice> choices, CartSummary sum,
                                            Map<Long, List<CartLine>> bySeller) {
        Map<Long, Applied> result = new HashMap<>();
        if (choices == null || choices.isEmpty()) {
            return result;
        }
        Map<Long, CartSummary.Group> groups = new HashMap<>();
        for (CartSummary.Group g : sum.groups()) {
            groups.put(g.sellerId(), g);
        }
        Map<Long, Long> parents = coupons.categoryParents();
        Set<Long> chosen = new HashSet<>();
        for (CouponChoice ch : choices) {
            if (ch == null || ch.memberCouponId() == null) {
                continue;   // "쓰지 않음"
            }
            if (ch.sellerId() == null || !groups.containsKey(ch.sellerId())) {
                throw ApiException.bad("장바구니에 없는 가게에 쿠폰을 골랐습니다.");
            }
            if (result.containsKey(ch.sellerId())) {
                throw ApiException.bad("가게마다 쿠폰은 한 장만 쓸 수 있습니다.");
            }
            if (!chosen.add(ch.memberCouponId())) {
                throw ApiException.bad("같은 쿠폰을 두 가게에 쓸 수 없습니다.");
            }
            MemberCoupon held = coupons.held(memberId, ch.memberCouponId());
            if (held == null) {
                throw ApiException.bad("가지고 있지 않은 쿠폰입니다.");
            }
            CouponService.Basket b = basket(groups.get(ch.sellerId()), bySeller.get(ch.sellerId()), parents);
            String reason = coupons.unusableReason(held, b);
            if (reason != null) {
                throw ApiException.bad("'" + held.coupon().name() + "' 쿠폰: " + reason);
            }
            result.put(ch.sellerId(), new Applied(held, coupons.quote(held.coupon(), b)));
        }
        return result;
    }

    // ---- 주문 생성 ----

    /** 쿠폰 없이 주문한다(시범 데이터와 기존 호출용). */
    @Transactional
    public long createOrder(Member user, String receiverIn, String phoneIn, String addressIn, String cashReceiptNoIn) {
        return createOrder(user, receiverIn, phoneIn, addressIn, cashReceiptNoIn, null);
    }

    @Transactional
    public long createOrder(Member user, String receiverIn, String phoneIn, String addressIn, String cashReceiptNoIn,
                            List<CouponChoice> couponChoices) {
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
        Map<Long, List<CartLine>> bySeller = bySeller(lines);
        Map<Long, Applied> applied = applyCoupons(user.id(), couponChoices, sum, bySeller);
        long totalDiscount = 0;
        for (Applied a : applied.values()) {
            totalDiscount += a.discount();
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
                .param("t", tenant.id()).param("no", newOrderNo()).param("b", user.id()).param("total", sum.total() - totalDiscount)
                .param("r", receiver).param("p", phone).param("a", address)
                .param("c", Text.clean(cashReceiptNoIn, 30)).param("now", now));

        for (CartSummary.Group g : sum.groups()) {
            Applied a = applied.get(g.sellerId());
            long itemDiscount = a == null ? 0 : a.quote().itemDiscount();
            long shipDiscount = a == null ? 0 : a.quote().shipDiscount();
            long subId = Db.insert(db.sql("""
                    INSERT INTO sub_order (tenant_id, order_id, seller_id, items_total, ship_fee, total, has_physical, deposit_due_at,
                                           item_discount, ship_discount, member_coupon_id, coupon_issuer, coupon_name)
                    VALUES (:t, :o, :s, :items, :fee, :total, :phys, :due, :itemDisc, :shipDisc, :mc, :issuer, :cname)
                    """)
                    .param("t", tenant.id()).param("o", orderId).param("s", g.sellerId())
                    .param("items", g.itemsTotal()).param("fee", g.shipFee())
                    .param("total", g.total() - itemDiscount - shipDiscount)
                    .param("phys", g.hasPhysical()).param("due", now.plusDays(DEPOSIT_DAYS))
                    .param("itemDisc", itemDiscount).param("shipDisc", shipDiscount)
                    .param("mc", a == null ? null : a.held().id())
                    .param("issuer", a == null ? null : a.held().coupon().issuer())
                    .param("cname", a == null ? null : a.held().coupon().name()));
            List<CartLine> sellerLines = bySeller.get(g.sellerId());
            for (int i = 0; i < sellerLines.size(); i++) {
                CartLine l = sellerLines.get(i);
                // 상품명·가격·수수료율은 주문 시점 값으로 복사해 둔다. 나중에 바뀌어도 이 주문은 그대로다.
                db.sql("""
                        INSERT INTO order_item (sub_order_id, product_id, name, product_type, unit_price, qty, commission_rate_bp,
                                                coupon_discount)
                        VALUES (:s, :p, :n, :type, :price, :q, :rate, :disc)
                        """)
                        .param("s", subId).param("p", l.id()).param("n", l.name()).param("type", l.productType())
                        .param("price", l.currentPrice()).param("q", l.qty()).param("rate", commissionRateBp(l))
                        .param("disc", a == null ? 0L : a.quote().perLine().get(i))
                        .update();
            }
            if (a != null) {
                // 같은 트랜잭션 안에서 쓴 것으로 바꾼다. 실패하면 주문 전체가 되돌아간다.
                coupons.markUsed(a.held().id(), user.id(), subId);
            }
        }
        db.sql("DELETE FROM cart_item WHERE member_id = :m").param("m", user.id()).update();
        for (Party p : partiesOfOrder(orderId)) {
            notifications.notifyWithMail(p.sellerMemberId(), "새 주문이 들어왔습니다",
                    "주문 " + p.orderNo() + ", 받을 금액 " + won(p.total()) + ". 입금을 확인한 뒤 처리해 주세요.", "/seller/orders");
        }
        return orderId;
    }

    private String newOrderNo() {
        byte[] bytes = new byte[3];
        RANDOM.nextBytes(bytes);
        return "S" + clock.today().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + HexFormat.of().withUpperCase().formatHex(bytes);
    }

    // ---- 상태 변경 ----

    /** 알림을 보낼 때 필요한 주문의 당사자 정보 */
    private record Party(long subId, long orderId, String orderNo, long buyerId, long sellerMemberId, String shopName,
                         long total) {
    }

    private static final String PARTY_SELECT = """
            SELECT so.id AS sub_id, o.id AS order_id, o.order_no, o.buyer_id, s.member_id AS seller_member_id,
                   s.shop_name, so.total
              FROM sub_order so JOIN shop_order o ON o.id = so.order_id JOIN seller s ON s.id = so.seller_id
            """;

    private Party party(long subId) {
        return db.sql(PARTY_SELECT + " WHERE so.id = :id").param("id", subId).query(Party.class).single();
    }

    private List<Party> partiesOfOrder(long orderId) {
        return db.sql(PARTY_SELECT + " WHERE so.order_id = :o").param("o", orderId).query(Party.class).list();
    }

    private static String won(long amount) {
        return String.format("%,d원", amount);
    }

    private static String orderLink(Party p) {
        return "/orders/" + p.orderId();
    }

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
        Party p = party(subId);
        notifications.notifyWithMail(p.sellerMemberId(), "입금했다는 알림이 왔습니다",
                "주문 " + p.orderNo() + ", 입금자명 " + depositor + ", " + won(p.total()) + ". 통장을 확인해 주세요.", "/seller/orders");
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
        coupons.restore(subId);
        Party p = party(subId);
        notifications.notify(p.sellerMemberId(), "주문이 취소되었습니다", "주문 " + p.orderNo() + "을 구매자가 취소했습니다.", "/seller/orders");
    }

    /** 판매자: 입금 확인. 현금영수증 승인번호는 선택 입력 */
    public void confirmDeposit(long subId, long sellerId, String approvalIn) {
        String approval = Text.clean(approvalIn, 30);
        mustChange(db.sql("""
                UPDATE sub_order SET status = 'paid', deposit_confirmed_at = :now, cash_receipt_approval = :a
                 WHERE id = :id AND seller_id = :s AND status = 'awaiting_deposit'
                """).param("now", clock.now()).param("a", approval.isEmpty() ? null : approval)
                .param("id", subId).param("s", sellerId).update(), NOT_ALLOWED);
        Party p = party(subId);
        notifications.notifyWithMail(p.buyerId(), "입금이 확인되었습니다",
                p.shopName() + "에서 주문 " + p.orderNo() + "의 입금 " + won(p.total()) + "을 확인했습니다.", orderLink(p));
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
        Party p = party(subId);
        notifications.notifyWithMail(p.buyerId(), "상품을 보냈습니다",
                p.shopName() + "에서 주문 " + p.orderNo() + "을 발송했습니다. 송장번호 " + trackingNo, orderLink(p));
    }

    /** 판매자: 수업·이용권처럼 배송이 없는 주문의 전달 완료 */
    public void deliverTicket(long subId, long sellerId) {
        mustChange(db.sql("""
                UPDATE sub_order SET status = 'delivered', delivered_at = :now
                 WHERE id = :id AND seller_id = :s AND status = 'paid' AND has_physical = FALSE
                """).param("now", clock.now()).param("id", subId).param("s", sellerId).update(), NOT_ALLOWED);
        Party p = party(subId);
        notifications.notify(p.buyerId(), "전달이 완료되었습니다", p.shopName() + "에서 주문 " + p.orderNo() + "을 전달 완료로 처리했습니다.", orderLink(p));
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
            coupons.restore(subId);
            notifyRefund(subId);
            return;
        }
        mustChange(db.sql("""
                UPDATE sub_order SET status = 'refunded', closed_at = :now, close_reason = '판매자 환불'
                 WHERE id = :id AND seller_id = :s AND status IN ('shipped', 'delivered')
                """).param("now", clock.now()).param("id", subId).param("s", sellerId).update(), NOT_ALLOWED);
        coupons.restore(subId);
        notifyRefund(subId);
    }

    private void notifyRefund(long subId) {
        Party p = party(subId);
        notifications.notifyWithMail(p.buyerId(), "환불 처리되었습니다",
                p.shopName() + "에서 주문 " + p.orderNo() + "을 환불로 기록했습니다. 환불금 " + won(p.total()) + "이 들어왔는지 확인해 주세요.", orderLink(p));
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
                coupons.restore(id);
                cancelled++;
                Party p = party(id);
                notifications.notifyWithMail(p.buyerId(), "입금 기한이 지나 주문이 취소되었습니다",
                        p.shopName() + " 주문 " + p.orderNo() + "이 자동으로 취소되었습니다.", orderLink(p));
                notifications.notify(p.sellerMemberId(), "입금 기한이 지나 주문이 취소되었습니다", "주문 " + p.orderNo(), "/seller/orders");
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
                   so.item_discount, so.ship_discount, so.coupon_name, so.coupon_issuer,
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

    /** from(포함)부터 to(제외)까지 주문한 것. 둘 다 null 이면 전체 기간. */
    public Paged<OrderDetail> listForBuyer(long buyerId, LocalDateTime from, LocalDateTime to, int page, int size) {
        String where = " WHERE buyer_id = :b AND (:from IS NULL OR created_at >= :from) AND (:to IS NULL OR created_at < :to)";
        long total = db.sql("SELECT COUNT(*) FROM shop_order" + where)
                .param("b", buyerId).param("from", from).param("to", to).query(Long.class).single();
        List<OrderDetail> result = new ArrayList<>();
        for (OrderDetail.Order o : db.sql(ORDER_SELECT + where + " ORDER BY id DESC LIMIT :limit OFFSET :offset")
                .param("b", buyerId).param("from", from).param("to", to)
                .param("limit", size).param("offset", Paged.offset(page, size))
                .query(OrderDetail.Order.class).list()) {
            result.add(withSubs(o));
        }
        return new Paged<>(result, page, size, total);
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
                SELECT id, sub_order_id, product_id, name, product_type, unit_price, qty, commission_rate_bp, coupon_discount
                  FROM order_item WHERE sub_order_id = :s ORDER BY id
                """).param("s", s.id()).query(OrderDetail.Item.class).list();
        return new OrderDetail.Sub(s, items, tracking.track(s.carrier(), s.trackingNo(), s.shippedAt()));
    }

    /** 판매자 화면용: 내 가게로 들어온 판매자별 주문과 배송지 */
    public record SellerOrder(OrderDetail.Order order, OrderDetail.Sub sub) {
    }

    /** status 가 null 이면 모든 상태 */
    public Paged<SellerOrder> listForSeller(long sellerId, String status, int page, int size) {
        String where = " WHERE so.seller_id = :s AND (:st IS NULL OR so.status = :st)";
        long total = db.sql("SELECT COUNT(*) FROM sub_order so" + where)
                .param("s", sellerId).param("st", status).query(Long.class).single();
        List<SellerOrder> result = new ArrayList<>();
        for (OrderDetail.SubRow s : db.sql(SUB_SELECT + where + " ORDER BY so.id DESC LIMIT :limit OFFSET :offset")
                .param("s", sellerId).param("st", status).param("limit", size).param("offset", Paged.offset(page, size))
                .query(OrderDetail.SubRow.class).list()) {
            OrderDetail.Order o = db.sql(ORDER_SELECT + " WHERE id = :id").param("id", s.orderId())
                    .query(OrderDetail.Order.class).single();
            result.add(new SellerOrder(o, toSub(s)));
        }
        return new Paged<>(result, page, size, total);
    }
}
