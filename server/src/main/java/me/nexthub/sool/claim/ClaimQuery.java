package me.nexthub.sool.claim;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.Paged;
import me.nexthub.sool.common.Tenant;
import me.nexthub.sool.common.Text;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 신청 읽기만 맡는다. 주문 조회(OrderService)도 이것을 쓰고, 신청 규칙(ClaimService)은 OrderService 를 쓰므로
 * 읽기를 따로 떼어 서로 기대는 고리를 끊는다.
 */
@Component
public class ClaimQuery {

    public static final List<String> STATUSES = List.of("requested", "approved", "rejected", "done", "withdrawn");
    private static final int PAGE_SIZE = 20;
    private static final int MY_PAGE_SIZE = 10;

    /** 권한 확인에 필요한 값(가게, 신청한 회원)까지 담은 내부용 줄. 응답으로는 {@link Claim} 만 내보낸다. */
    record Row(long id, long subOrderId, long orderId, String orderNo, String shopName, String buyerName,
               String claimType, String reasonCode, String reasonText, String status, String sellerNote,
               String carrier, String trackingNo, long total,
               LocalDateTime requestedAt, LocalDateTime answeredAt, LocalDateTime doneAt,
               long sellerId, long memberId, long sellerMemberId, long buyerId, String returnAddress) {
    }

    private record ItemLine(String name, int qty) {
    }

    private static final String SELECT = """
            SELECT c.id, c.sub_order_id, so.order_id, o.order_no, s.shop_name, m.name AS buyer_name,
                   c.claim_type, c.reason_code, c.reason_text, c.status, c.seller_note, c.carrier, c.tracking_no,
                   so.total, c.requested_at, c.answered_at, c.done_at,
                   so.seller_id, c.member_id, s.member_id AS seller_member_id, o.buyer_id, s.return_address
              FROM order_claim c
              JOIN sub_order so ON so.id = c.sub_order_id
              JOIN shop_order o ON o.id = so.order_id
              JOIN seller s ON s.id = so.seller_id
              JOIN member m ON m.id = c.member_id
            """;

    private final JdbcClient db;
    private final Tenant tenant;

    public ClaimQuery(JdbcClient db, Tenant tenant) {
        this.db = db;
        this.tenant = tenant;
    }

    Row row(long claimId) {
        return db.sql(SELECT + " WHERE c.id = :id AND c.tenant_id = :t").param("id", claimId).param("t", tenant.id())
                .query(Row.class).optional().orElse(null);
    }

    Claim view(Row r) {
        List<String> parts = new ArrayList<>();
        for (ItemLine l : db.sql("SELECT name, qty FROM order_item WHERE sub_order_id = :s ORDER BY id")
                .param("s", r.subOrderId()).query(ItemLine.class).list()) {
            parts.add(l.name() + " × " + l.qty());
        }
        return new Claim(r.id(), r.subOrderId(), r.orderId(), r.orderNo(), r.shopName(), r.buyerName(),
                r.claimType(), r.reasonCode(), r.reasonText(), r.status(), r.sellerNote(), r.carrier(), r.trackingNo(),
                String.join(", ", parts), r.total(), r.requestedAt(), r.answeredAt(), r.doneAt(),
                !"cancel".equals(r.claimType()) && "approved".equals(r.status()) ? r.returnAddress() : null);
    }

    public Claim find(long claimId) {
        Row r = row(claimId);
        return r == null ? null : view(r);
    }

    /** 판매자별 주문의 가장 최근 신청. 없으면 null */
    public Claim latestForSub(long subOrderId) {
        Row r = db.sql(SELECT + " WHERE c.sub_order_id = :s ORDER BY c.id DESC LIMIT 1").param("s", subOrderId)
                .query(Row.class).optional().orElse(null);
        return r == null ? null : view(r);
    }

    /** 진행 중(requested, approved)인 신청이 있는지 */
    public boolean hasOpen(long subOrderId) {
        return db.sql("SELECT COUNT(*) FROM order_claim WHERE sub_order_id = :s AND status IN ('requested', 'approved')")
                .param("s", subOrderId).query(Integer.class).single() > 0;
    }

    /**
     * 상태 거르기 값. 비어 있으면 null(전체). 화면의 "거절·철회" 탭처럼 쉼표로 여러 상태를 줄 수 있다.
     */
    static List<String> statuses(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (String s : status.split(",")) {
            String v = s.trim();
            if (!Text.in(v, STATUSES)) {
                throw ApiException.bad("신청 상태 값이 올바르지 않습니다.");
            }
            result.add(v);
        }
        return result;
    }

    public Paged<Claim> listForBuyer(long memberId, int page) {
        return list(" WHERE c.member_id = :m", "m", memberId, null, page, MY_PAGE_SIZE);
    }

    public Paged<Claim> listForSeller(long sellerId, String status, int page) {
        return list(" WHERE so.seller_id = :s", "s", sellerId, statuses(status), page, PAGE_SIZE);
    }

    public Paged<Claim> listAll(String status, int page) {
        return list(" WHERE c.tenant_id = :t", "t", tenant.id(), statuses(status), page, PAGE_SIZE);
    }

    private Paged<Claim> list(String where, String key, long value, List<String> statuses, int page, int size) {
        String w = where + (statuses == null ? "" : " AND c.status IN (:sts)");
        String from = """
                 FROM order_claim c JOIN sub_order so ON so.id = c.sub_order_id
                """;
        var count = db.sql("SELECT COUNT(*)" + from + w).param(key, value);
        var query = db.sql(SELECT + w + " ORDER BY c.id DESC LIMIT :limit OFFSET :offset").param(key, value)
                .param("limit", size).param("offset", Paged.offset(page, size));
        if (statuses != null) {
            count = count.param("sts", statuses);
            query = query.param("sts", statuses);
        }
        List<Claim> items = new ArrayList<>();
        for (Row r : query.query(Row.class).list()) {
            items.add(view(r));
        }
        return new Paged<>(items, page, size, count.query(Long.class).single());
    }
}
