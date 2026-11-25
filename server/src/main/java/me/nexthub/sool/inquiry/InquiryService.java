package me.nexthub.sool.inquiry;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import me.nexthub.sool.catalog.Catalog;
import me.nexthub.sool.catalog.ProductView;
import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.AppClock;
import me.nexthub.sool.common.Db;
import me.nexthub.sool.common.Names;
import me.nexthub.sool.common.Paged;
import me.nexthub.sool.common.Tenant;
import me.nexthub.sool.common.Text;
import me.nexthub.sool.notify.NotificationService;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * 문의 규칙(docs/adr/0003 B). 받는 쪽은 target 으로 정한다: seller 면 그 가게 판매자, admin 이면 운영자.
 * 주문 문의와 1:1 문의는 주문·개인 사정이 담기므로 늘 비밀글로 저장한다. 상태 변경은 조건부 UPDATE 다.
 */
@Service
public class InquiryService {

    public static final List<String> TARGETS = List.of("seller", "admin");
    public static final String SECRET_TITLE = "비밀글입니다";
    private static final int PRODUCT_PAGE_SIZE = 10;
    private static final int MY_PAGE_SIZE = 10;
    private static final int PAGE_SIZE = 20;

    public record WriteRequest(String target, Long productId, Long subOrderId, String title, String body,
                               Boolean secret) {
    }

    /**
     * 지금 보는 사람. 비밀글 내용을 보여 줄지 정할 때 쓴다. 로그인하지 않았으면 memberId 가 null,
     * 가게가 없으면 sellerId 가 null 이다.
     */
    public record Viewer(Long memberId, Long sellerId, boolean admin) {

        public static final Viewer ANONYMOUS = new Viewer(null, null, false);
    }

    /** 권한 확인에 필요한 값까지 담은 내부용 줄. 응답으로는 {@link Inquiry} 만 내보낸다. */
    record Row(long id, long memberId, String memberName, String target, Long sellerId, Long productId,
               String productName, Long subOrderId, Long orderId, String orderNo, String shopName, String title,
               String body, boolean secret, String status, String answer, LocalDateTime answeredAt,
               LocalDateTime createdAt, Long sellerMemberId) {
    }

    private record SubOwner(long sellerId, long buyerId) {
    }

    private static final String SELECT = """
            SELECT i.id, i.member_id, m.name AS member_name, i.target, i.seller_id, i.product_id, p.name AS product_name,
                   i.sub_order_id, so.order_id, o.order_no, s.shop_name, i.title, i.body, i.secret, i.status, i.answer,
                   i.answered_at, i.created_at, s.member_id AS seller_member_id
              FROM inquiry i
              JOIN member m ON m.id = i.member_id
              LEFT JOIN product p ON p.id = i.product_id
              LEFT JOIN sub_order so ON so.id = i.sub_order_id
              LEFT JOIN shop_order o ON o.id = so.order_id
              LEFT JOIN seller s ON s.id = i.seller_id
            """;

    private final JdbcClient db;
    private final Tenant tenant;
    private final AppClock clock;
    private final Catalog catalog;
    private final NotificationService notifications;

    public InquiryService(JdbcClient db, Tenant tenant, AppClock clock, Catalog catalog,
                          NotificationService notifications) {
        this.db = db;
        this.tenant = tenant;
        this.clock = clock;
        this.catalog = catalog;
        this.notifications = notifications;
    }

    // ---- 읽기 ----

    Row row(long id) {
        return db.sql(SELECT + " WHERE i.id = :id AND i.tenant_id = :t").param("id", id).param("t", tenant.id())
                .query(Row.class).optional().orElse(null);
    }

    private static String kind(Row r) {
        if ("admin".equals(r.target())) {
            return "admin";
        }
        return r.subOrderId() != null ? "order" : "product";
    }

    /** 비밀글은 쓴 사람, 받는 가게의 판매자, 운영자에게만 내용이 보인다. */
    static boolean canRead(Row r, Viewer v) {
        if (!r.secret() || v.admin()) {
            return true;
        }
        if (v.memberId() != null && v.memberId() == r.memberId()) {
            return true;
        }
        return v.sellerId() != null && r.sellerId() != null && v.sellerId().longValue() == r.sellerId();
    }

    static Inquiry view(Row r, Viewer v, boolean masked) {
        boolean open = canRead(r, v);
        return new Inquiry(r.id(), r.target(), kind(r), r.productId(), r.productName(), r.subOrderId(), r.orderId(),
                r.orderNo(), r.shopName(), open ? r.title() : SECRET_TITLE, open ? r.body() : null, r.secret(),
                masked ? Names.mask(r.memberName()) : r.memberName(),
                v.memberId() != null && v.memberId() == r.memberId(), r.status(), open ? r.answer() : null,
                r.answeredAt(), r.createdAt());
    }

    private Paged<Inquiry> list(String where, Map<String, ?> params, int page, int size, Viewer v, boolean masked) {
        var count = db.sql("SELECT COUNT(*) FROM inquiry i" + where).params(params);
        var query = db.sql(SELECT + where + " ORDER BY i.id DESC LIMIT :limit OFFSET :offset").params(params)
                .param("limit", size).param("offset", Paged.offset(page, size));
        List<Inquiry> items = new ArrayList<>();
        for (Row r : query.query(Row.class).list()) {
            items.add(view(r, v, masked));
        }
        return new Paged<>(items, page, size, count.query(Long.class).single());
    }

    /** 상품 화면: 누구나. 비밀글은 가리고, 쓴 사람 이름은 가운데를 가린다. */
    public Paged<Inquiry> forProduct(long productId, Viewer v, int page) {
        return list(" WHERE i.product_id = :p AND i.tenant_id = :t AND i.status IN ('open', 'answered')",
                Map.of("p", productId, "t", tenant.id()), page, PRODUCT_PAGE_SIZE, v, true);
    }

    public Paged<Inquiry> mine(long memberId, int page) {
        return list(" WHERE i.member_id = :m AND i.status IN ('open', 'answered')", Map.of("m", memberId),
                page, MY_PAGE_SIZE, new Viewer(memberId, null, false), false);
    }

    /** 판매자: 내 가게로 온 상품·주문 문의. unanswered 면 답하지 않은 것만 */
    public Paged<Inquiry> forSeller(long sellerId, boolean unanswered, int page) {
        return list(" WHERE i.target = 'seller' AND i.seller_id = :s AND " + statusWhere(unanswered),
                Map.of("s", sellerId), page, PAGE_SIZE, new Viewer(null, sellerId, false), false);
    }

    /** 운영자: 1:1 문의만. 판매자에게 간 문의는 그 판매자가 답한다. */
    public Paged<Inquiry> forAdmin(boolean unanswered, int page) {
        return list(" WHERE i.tenant_id = :t AND i.target = 'admin' AND " + statusWhere(unanswered),
                Map.of("t", tenant.id()), page, PAGE_SIZE, new Viewer(null, null, true), false);
    }

    private static String statusWhere(boolean unanswered) {
        return unanswered ? "i.status = 'open'" : "i.status IN ('open', 'answered')";
    }

    // ---- 쓰기 ----

    public Inquiry write(long memberId, WriteRequest body) {
        String target = body == null ? null : body.target();
        if (!Text.in(target, TARGETS)) {
            throw ApiException.bad("문의할 곳(판매자 또는 운영자)을 골라 주세요.");
        }
        String title = body.title() == null ? "" : body.title().trim();
        String text = body.body() == null ? "" : body.body().trim();
        if (title.length() < 2 || title.length() > 100) {
            throw ApiException.bad("제목은 2자 이상 100자 이하로 써 주세요.");
        }
        if (text.length() < 5 || text.length() > 2000) {
            throw ApiException.bad("내용은 5자 이상 2,000자 이하로 써 주세요.");
        }
        Long sellerId = null;
        Long productId = null;
        Long subOrderId = null;
        boolean secret;
        String about;
        if ("admin".equals(target)) {
            secret = true;
            about = "1:1 문의";
        } else if (body.subOrderId() != null && body.productId() != null) {
            throw ApiException.bad("상품 문의와 주문 문의 가운데 하나만 골라 주세요.");
        } else if (body.subOrderId() != null) {
            SubOwner owner = db.sql("""
                    SELECT so.seller_id, o.buyer_id FROM sub_order so JOIN shop_order o ON o.id = so.order_id
                     WHERE so.id = :id AND so.tenant_id = :t
                    """).param("id", body.subOrderId()).param("t", tenant.id()).query(SubOwner.class)
                    .optional().orElse(null);
            // 내 주문이 아니면 있는지도 알려 주지 않는다
            if (owner == null || owner.buyerId() != memberId) {
                throw ApiException.notFound();
            }
            sellerId = owner.sellerId();
            subOrderId = body.subOrderId();
            secret = true;
            about = "주문 문의";
        } else if (body.productId() != null) {
            ProductView p = catalog.find(body.productId());
            if (p == null || !p.onSale()) {
                throw ApiException.notFound();
            }
            sellerId = p.sellerId();
            productId = p.id();
            secret = Boolean.TRUE.equals(body.secret());
            about = p.name() + " 상품 문의";
        } else {
            throw ApiException.bad("문의할 상품이나 주문을 골라 주세요.");
        }
        long id = Db.insert(db.sql("""
                INSERT INTO inquiry (tenant_id, member_id, target, seller_id, product_id, sub_order_id, title, body,
                                     secret, status, created_at)
                VALUES (:t, :m, :target, :s, :p, :so, :title, :body, :secret, 'open', :now)
                """)
                .param("t", tenant.id()).param("m", memberId).param("target", target).param("s", sellerId)
                .param("p", productId).param("so", subOrderId).param("title", title).param("body", text)
                .param("secret", secret).param("now", clock.now()));
        Row r = row(id);
        if ("admin".equals(target)) {
            for (Long admin : db.sql("""
                    SELECT id FROM member WHERE tenant_id = :t AND role = 'admin' AND status = 'active' ORDER BY id
                    """).param("t", tenant.id()).query(Long.class).list()) {
                notifications.notify(admin, "새 1:1 문의가 들어왔습니다", title, "/admin/inquiries");
            }
        } else {
            notifications.notify(r.sellerMemberId(), "새 문의가 들어왔습니다", about + ": " + title, "/seller/inquiries");
        }
        return view(r, new Viewer(memberId, null, false), false);
    }

    /** 쓴 사람은 답변이 달리기 전에만 지울 수 있다(답변이 달린 뒤에는 주고받은 기록으로 남긴다). */
    public void delete(long memberId, long inquiryId) {
        Row r = row(inquiryId);
        if (r == null || r.memberId() != memberId || "deleted".equals(r.status())) {
            throw ApiException.notFound();
        }
        int changed = db.sql("UPDATE inquiry SET status = 'deleted' WHERE id = :id AND member_id = :m AND status = 'open'")
                .param("id", inquiryId).param("m", memberId).update();
        if (changed != 1) {
            throw ApiException.conflict("답변이 달린 문의는 지울 수 없습니다.");
        }
    }

    /** 판매자 답변: 내 가게로 온 문의만. 남의 가게 문의와 1:1 문의는 404 */
    public Inquiry answerAsSeller(long sellerId, long answererId, long inquiryId, String answer) {
        Row r = row(inquiryId);
        if (r == null || !"seller".equals(r.target()) || r.sellerId() == null || r.sellerId() != sellerId
                || "deleted".equals(r.status())) {
            throw ApiException.notFound();
        }
        return answer(r, answererId, answer, new Viewer(null, sellerId, false));
    }

    /** 운영자 답변: 1:1 문의만. 판매자에게 간 문의는 판매자가 답한다. */
    public Inquiry answerAsAdmin(long answererId, long inquiryId, String answer) {
        Row r = row(inquiryId);
        if (r == null || !"admin".equals(r.target()) || "deleted".equals(r.status())) {
            throw ApiException.notFound();
        }
        return answer(r, answererId, answer, new Viewer(null, null, true));
    }

    /** 답변은 한 번 달고 그 뒤로는 고칠 수 있다. 알림(메일 포함)은 처음 달 때만 보낸다. */
    private Inquiry answer(Row r, long answererId, String answerIn, Viewer v) {
        String answer = answerIn == null ? "" : answerIn.trim();
        if (answer.isEmpty()) {
            throw ApiException.bad("답변을 입력해 주세요.");
        }
        if (answer.length() > 2000) {
            throw ApiException.bad("답변은 2,000자까지 쓸 수 있습니다.");
        }
        int changed = db.sql("""
                UPDATE inquiry SET answer = :a, answered_by = :by, answered_at = :now, status = 'answered'
                 WHERE id = :id AND status IN ('open', 'answered')
                """).param("a", answer).param("by", answererId).param("now", clock.now()).param("id", r.id()).update();
        if (changed != 1) {
            throw ApiException.conflict("쓴 사람이 지운 문의입니다.");
        }
        if ("open".equals(r.status())) {
            notifications.notifyWithMail(r.memberId(), "문의에 답변이 달렸습니다",
                    "'" + r.title() + "' 문의에 답변이 달렸습니다. 문의 내역에서 확인해 주세요.", "/my/inquiries");
        }
        return view(row(r.id()), v, false);
    }
}
