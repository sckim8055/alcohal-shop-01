package me.nexthub.sool.claim;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.AppClock;
import me.nexthub.sool.common.Db;
import me.nexthub.sool.common.Tenant;
import me.nexthub.sool.common.Text;
import me.nexthub.sool.notify.NotificationService;
import me.nexthub.sool.order.OrderService;
import me.nexthub.sool.order.TrackingService;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 취소·반품·교환 신청 규칙(docs/adr/0002 A). 돈은 판매자가 구매자에게 직접 돌려주므로 여기서는 과정을 기록하고,
 * 환불 기록은 기존 {@link OrderService#refund} 를 그대로 불러 재고·쿠폰 처리를 한 곳에 둔다.
 * 상태 변경은 모두 "현재 상태가 맞을 때만" 바뀌는 조건부 UPDATE 다.
 */
@Service
public class ClaimService {

    public static final List<String> TYPES = List.of("cancel", "return", "exchange");
    public static final List<String> REASONS = List.of("changed_mind", "defect", "wrong_item", "late", "other");
    /** 구매확정 뒤 반품·교환을 신청할 수 있는 날 수 */
    public static final int RETURN_DAYS = 7;

    private static final Map<String, String> TYPE_NAMES = Map.of("cancel", "취소", "return", "반품", "exchange", "교환");
    private static final Map<String, String> REASON_NAMES = Map.of("changed_mind", "단순 변심", "defect", "상품 불량",
            "wrong_item", "다른 상품이 옴", "late", "배송 지연", "other", "기타");
    private static final String NOT_ALLOWED = "이미 처리되었거나 지금은 할 수 없는 작업입니다.";

    public record ClaimRequest(String claimType, String reasonCode, String reasonText) {
    }

    private record SubState(long id, String status, boolean hasPhysical, LocalDateTime depositReportedAt,
                            LocalDateTime completedAt, long buyerId) {
    }

    private final JdbcClient db;
    private final Tenant tenant;
    private final AppClock clock;
    private final ClaimQuery query;
    private final OrderService orders;
    private final TrackingService tracking;
    private final NotificationService notifications;

    public ClaimService(JdbcClient db, Tenant tenant, AppClock clock, ClaimQuery query, OrderService orders,
                        TrackingService tracking, NotificationService notifications) {
        this.db = db;
        this.tenant = tenant;
        this.clock = clock;
        this.query = query;
        this.orders = orders;
        this.tracking = tracking;
        this.notifications = notifications;
    }

    private static String typeName(String type) {
        return TYPE_NAMES.getOrDefault(type, type);
    }

    private static String orderLink(ClaimQuery.Row r) {
        return "/orders/" + r.orderId();
    }

    private static void mustChange(int changed, String message) {
        if (changed != 1) {
            throw ApiException.conflict(message);
        }
    }

    // ---- 구매자 ----

    @Transactional
    public Claim request(long buyerId, long subId, ClaimRequest body) {
        String type = body == null ? null : body.claimType();
        String reason = body == null ? null : body.reasonCode();
        if (!Text.in(type, TYPES)) {
            throw ApiException.bad("신청 종류(취소·반품·교환)를 골라 주세요.");
        }
        if (!Text.in(reason, REASONS)) {
            throw ApiException.bad("사유를 골라 주세요.");
        }
        String raw = body.reasonText() == null ? "" : body.reasonText().trim();
        if (raw.length() > 500) {
            throw ApiException.bad("자세한 사유는 500자까지 쓸 수 있습니다.");
        }
        if ("other".equals(reason) && raw.isEmpty()) {
            throw ApiException.bad("기타 사유를 골랐다면 자세한 사유를 적어 주세요.");
        }

        // 같은 주문에 신청이 동시에 두 번 와도 하나만 들어가도록 판매자별 주문 줄을 잠근 뒤 확인한다.
        List<Long> locked = db.sql("SELECT id FROM sub_order WHERE id = :id AND tenant_id = :t FOR UPDATE")
                .param("id", subId).param("t", tenant.id()).query(Long.class).list();
        if (locked.isEmpty()) {
            throw ApiException.notFound();
        }
        SubState s = db.sql("""
                SELECT so.id, so.status, so.has_physical, so.deposit_reported_at, so.completed_at, o.buyer_id
                  FROM sub_order so JOIN shop_order o ON o.id = so.order_id WHERE so.id = :id
                """).param("id", subId).query(SubState.class).single();
        if (s.buyerId() != buyerId) {
            throw ApiException.notFound();
        }
        checkAllowed(type, s);
        if (query.hasOpen(subId)) {
            throw ApiException.conflict("이미 진행 중인 신청이 있습니다. 판매자의 답을 기다려 주세요.");
        }
        long id = Db.insert(db.sql("""
                INSERT INTO order_claim (tenant_id, sub_order_id, member_id, claim_type, reason_code, reason_text,
                                         status, requested_at)
                VALUES (:t, :s, :m, :type, :reason, :text, 'requested', :now)
                """)
                .param("t", tenant.id()).param("s", subId).param("m", buyerId).param("type", type)
                .param("reason", reason).param("text", raw).param("now", clock.now()));
        ClaimQuery.Row r = query.row(id);
        notifications.notifyWithMail(r.sellerMemberId(), typeName(type) + " 신청이 들어왔습니다",
                "주문 " + r.orderNo() + " (" + REASON_NAMES.get(reason) + "). 내 가게의 취소·반품·교환에서 처리해 주세요.",
                "/seller/claims");
        return query.view(r);
    }

    /** 판매자별 주문의 지금 상태로 이 종류의 신청을 받을 수 있는지. 안 되면 409 */
    private void checkAllowed(String type, SubState s) {
        String st = s.status();
        if ("cancel".equals(type)) {
            if ("awaiting_deposit".equals(st) && s.depositReportedAt() == null) {
                // 입금 신고 전에는 판매자 확인 없이 바로 취소할 수 있으므로 신청을 받지 않는다
                throw ApiException.conflict("입금 신고 전에는 '주문 취소'로 바로 취소할 수 있습니다.");
            }
            if (!"awaiting_deposit".equals(st) && !"paid".equals(st)) {
                throw ApiException.conflict("shipped".equals(st) || "delivered".equals(st) || "completed".equals(st)
                        ? "이미 보낸 주문은 취소 대신 반품을 신청해 주세요." : "지금은 취소를 신청할 수 없는 주문입니다.");
            }
            return;
        }
        if (!s.hasPhysical()) {
            throw ApiException.conflict("배송하는 상품이 없는 주문은 반품·교환을 신청할 수 없습니다. 취소 신청을 이용해 주세요.");
        }
        boolean inWindow = "shipped".equals(st) || "delivered".equals(st)
                || ("completed".equals(st) && s.completedAt() != null
                    && s.completedAt().isAfter(clock.now().minusDays(RETURN_DAYS)));
        if (!inWindow) {
            throw ApiException.conflict("completed".equals(st)
                    ? "구매확정 후 " + RETURN_DAYS + "일이 지나 " + typeName(type) + " 신청을 할 수 없습니다."
                    : "상품을 보낸 뒤에 " + typeName(type) + " 신청을 할 수 있습니다.");
        }
    }

    /** 판매자가 답하기 전(requested)에만 철회할 수 있다. */
    public Claim withdraw(long buyerId, long claimId) {
        ClaimQuery.Row r = query.row(claimId);
        if (r == null || r.memberId() != buyerId) {
            throw ApiException.notFound();
        }
        mustChange(db.sql("""
                UPDATE order_claim SET status = 'withdrawn', done_at = :now
                 WHERE id = :id AND member_id = :m AND status = 'requested'
                """).param("now", clock.now()).param("id", claimId).param("m", buyerId).update(),
                "판매자가 이미 답한 신청은 철회할 수 없습니다.");
        notifications.notify(r.sellerMemberId(), typeName(r.claimType()) + " 신청이 철회되었습니다",
                "주문 " + r.orderNo() + "의 " + typeName(r.claimType()) + " 신청을 구매자가 철회했습니다.", "/seller/claims");
        return query.find(claimId);
    }

    // ---- 판매자 ----

    private ClaimQuery.Row own(long sellerId, long claimId) {
        ClaimQuery.Row r = query.row(claimId);
        if (r == null || r.sellerId() != sellerId) {
            throw ApiException.notFound();
        }
        return r;
    }

    private static String note(String in) {
        String raw = in == null ? "" : in.trim();
        if (raw.length() > 500) {
            throw ApiException.bad("메모는 500자까지 쓸 수 있습니다.");
        }
        return raw;
    }

    /**
     * 승인. 취소는 승인과 함께 환불 기록까지 끝낸다(발송 전이라 돌려받을 물건이 없다).
     * 반품·교환은 물건이 오가야 하므로 approved 로 두고 {@link #complete} 에서 끝낸다.
     */
    @Transactional
    public Claim approve(long sellerId, long claimId, String noteIn) {
        ClaimQuery.Row r = own(sellerId, claimId);
        String note = note(noteIn);
        LocalDateTime now = clock.now();
        boolean cancel = "cancel".equals(r.claimType());
        mustChange(db.sql("""
                UPDATE order_claim SET status = :next, seller_note = :note, answered_at = :now,
                       done_at = :done
                 WHERE id = :id AND status = 'requested'
                """).param("next", cancel ? "done" : "approved").param("note", note.isEmpty() ? null : note)
                .param("now", now).param("done", cancel ? now : null).param("id", claimId).update(), NOT_ALLOWED);
        String body;
        if (cancel) {
            // 환불에 실패하면(이미 다른 상태가 됨) 예외로 신청 승인도 함께 되돌아간다
            orders.refund(r.subOrderId(), sellerId);
            body = r.shopName() + "에서 주문 " + r.orderNo() + "의 취소를 승인하고 환불로 기록했습니다. 환불금 "
                    + String.format("%,d원", r.total()) + "이 들어왔는지 확인해 주세요.";
        } else if ("return".equals(r.claimType())) {
            String to = r.returnAddress() == null || r.returnAddress().isBlank()
                    ? "보낼 곳은 판매자에게 문의해 주세요." : "보낼 곳: " + r.returnAddress();
            body = r.shopName() + "에서 주문 " + r.orderNo() + "의 반품을 승인했습니다. 상품을 돌려보내 주세요. " + to;
        } else {
            body = r.shopName() + "에서 주문 " + r.orderNo() + "의 교환을 승인했습니다. 판매자 안내에 따라 상품을 돌려보내 주세요.";
        }
        if (!note.isEmpty()) {
            body += " 판매자 메모: " + note;
        }
        notifications.notifyWithMail(r.buyerId(), typeName(r.claimType()) + " 신청이 승인되었습니다", body, orderLink(r));
        return query.find(claimId);
    }

    public Claim reject(long sellerId, long claimId, String noteIn) {
        ClaimQuery.Row r = own(sellerId, claimId);
        String note = note(noteIn);
        if (note.isEmpty()) {
            throw ApiException.bad("거절 사유를 입력해 주세요.");
        }
        LocalDateTime now = clock.now();
        mustChange(db.sql("""
                UPDATE order_claim SET status = 'rejected', seller_note = :note, answered_at = :now, done_at = :now
                 WHERE id = :id AND status = 'requested'
                """).param("note", note).param("now", now).param("id", claimId).update(), NOT_ALLOWED);
        notifications.notifyWithMail(r.buyerId(), typeName(r.claimType()) + " 신청이 거절되었습니다",
                r.shopName() + "에서 주문 " + r.orderNo() + "의 " + typeName(r.claimType()) + " 신청을 거절했습니다. 사유: " + note,
                orderLink(r));
        return query.find(claimId);
    }

    /**
     * 반품: 물건을 받고 돈을 돌려준 뒤 환불 기록. 교환: 새 물건을 보낸 송장을 남기고, 판매자별 주문 상태는 그대로 둔다.
     */
    @Transactional
    public Claim complete(long sellerId, long claimId, String carrier, String trackingNoIn) {
        ClaimQuery.Row r = own(sellerId, claimId);
        LocalDateTime now = clock.now();
        String body;
        if ("exchange".equals(r.claimType())) {
            String trackingNo = trackingNoIn == null ? "" : trackingNoIn.replaceAll("[^0-9A-Za-z-]", "");
            trackingNo = Text.clean(trackingNo, 40);
            if (!tracking.knownCarrier(carrier) || trackingNo.isEmpty()) {
                throw ApiException.bad("새 상품을 보낸 택배사와 송장번호를 입력해 주세요.");
            }
            mustChange(db.sql("""
                    UPDATE order_claim SET status = 'done', carrier = :c, tracking_no = :n, done_at = :now
                     WHERE id = :id AND status = 'approved' AND claim_type = 'exchange'
                    """).param("c", carrier).param("n", trackingNo).param("now", now).param("id", claimId).update(),
                    NOT_ALLOWED);
            body = r.shopName() + "에서 주문 " + r.orderNo() + "의 교환 상품을 보냈습니다. 송장번호 " + trackingNo;
        } else {
            mustChange(db.sql("""
                    UPDATE order_claim SET status = 'done', done_at = :now
                     WHERE id = :id AND status = 'approved' AND claim_type = 'return'
                    """).param("now", now).param("id", claimId).update(), NOT_ALLOWED);
            orders.refundReturned(r.subOrderId(), sellerId);
            body = r.shopName() + "에서 주문 " + r.orderNo() + "의 반품을 받고 환불로 기록했습니다. 환불금 "
                    + String.format("%,d원", r.total()) + "이 들어왔는지 확인해 주세요.";
        }
        notifications.notifyWithMail(r.buyerId(), typeName(r.claimType()) + "이 끝났습니다", body, orderLink(r));
        return query.find(claimId);
    }
}
