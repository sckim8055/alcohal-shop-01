package me.nexthub.sool.admin;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.auth.Member;
import me.nexthub.sool.catalog.Catalog;
import me.nexthub.sool.catalog.Category;
import me.nexthub.sool.catalog.ProductView;
import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.AppClock;
import me.nexthub.sool.common.Db;
import me.nexthub.sool.common.Paged;
import me.nexthub.sool.common.Tenant;
import me.nexthub.sool.common.Text;
import me.nexthub.sool.notify.NotificationService;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 운영자: 회원 관리, 상품 관리(판매 차단), 분류 관리, 전체 주문 조회. */
@RestController
public class AdminManageController {

    public record MemberRow(long id, String email, String name, String role, String status, String shopName,
                            LocalDateTime adultVerifiedAt, LocalDateTime lastLoginAt, LocalDateTime createdAt) {
    }

    public record AdminFlag(Boolean admin) {
    }

    public record MemberStatus(String status) {
    }

    public record BlockRequest(Boolean blocked) {
    }

    public record CategoryRequest(Long parentId, String name, Boolean alcohol, Integer sortOrder, Boolean active) {
    }

    public record OrderRow(long id, long orderId, String orderNo, String buyerName, String shopName, long total,
                           String status, LocalDateTime createdAt) {
    }

    public record Summary(long members, long pendingSellers, long productsOnSale, long awaitingDeposit,
                          long toShip, long unpaidInvoices, long claimsOpen) {
    }

    private static final Map<String, Boolean> OK = Map.of("ok", true);

    private final JdbcClient db;
    private final Auth auth;
    private final Tenant tenant;
    private final AppClock clock;
    private final Catalog catalog;
    private final NotificationService notifications;

    public AdminManageController(JdbcClient db, Auth auth, Tenant tenant, AppClock clock, Catalog catalog,
                                 NotificationService notifications) {
        this.db = db;
        this.auth = auth;
        this.tenant = tenant;
        this.clock = clock;
        this.catalog = catalog;
        this.notifications = notifications;
    }

    @GetMapping("/api/admin/summary")
    public Summary summary(HttpServletRequest request) {
        auth.requireAdmin(request);
        return new Summary(
                count("SELECT COUNT(*) FROM member WHERE tenant_id = :t AND status = 'active'"),
                count("SELECT COUNT(*) FROM seller WHERE tenant_id = :t AND status = 'pending'"),
                count("SELECT COUNT(*) FROM product WHERE tenant_id = :t AND status = 'on_sale'"),
                count("SELECT COUNT(*) FROM sub_order WHERE tenant_id = :t AND status = 'awaiting_deposit'"),
                count("SELECT COUNT(*) FROM sub_order WHERE tenant_id = :t AND status = 'paid'"),
                count("SELECT COUNT(*) FROM commission_invoice WHERE tenant_id = :t AND status = 'issued'"),
                count("SELECT COUNT(*) FROM order_claim WHERE tenant_id = :t AND status IN ('requested', 'approved')"));
    }

    private long count(String sql) {
        return db.sql(sql).param("t", tenant.id()).query(Long.class).single();
    }

    // ---- 회원 ----

    @GetMapping("/api/admin/members")
    public Paged<MemberRow> members(@RequestParam(name = "q", required = false) String q,
                                    @RequestParam(name = "role", required = false) String role,
                                    @RequestParam(name = "status", required = false) String status,
                                    @RequestParam(name = "page", required = false) Integer page,
                                    HttpServletRequest request) {
        auth.requireAdmin(request);
        StringBuilder where = new StringBuilder(" WHERE m.tenant_id = :t");
        String keyword = q == null || q.isBlank() ? null : "%" + Text.clean(q, 50).replaceAll("([!%_])", "!$1") + "%";
        boolean byRole = Text.in(role, List.of("buyer", "seller", "admin"));
        boolean byStatus = Text.in(status, List.of("active", "suspended", "withdrawn"));
        if (keyword != null) {
            where.append(" AND (m.email LIKE :q ESCAPE '!' OR m.name LIKE :q ESCAPE '!')");
        }
        if (byRole) {
            where.append(" AND m.role = :role");
        }
        if (byStatus) {
            where.append(" AND m.status = :status");
        }
        int pageNo = Paged.page(page);
        int size = 20;
        var count = db.sql("SELECT COUNT(*) FROM member m" + where).param("t", tenant.id());
        var query = db.sql("""
                SELECT m.id, m.email, m.name, m.role, m.status, s.shop_name, m.adult_verified_at, m.last_login_at, m.created_at
                  FROM member m LEFT JOIN seller s ON s.member_id = m.id
                """ + where + " ORDER BY m.id DESC LIMIT :limit OFFSET :offset")
                .param("t", tenant.id()).param("limit", size).param("offset", Paged.offset(pageNo, size));
        if (keyword != null) {
            count = count.param("q", keyword);
            query = query.param("q", keyword);
        }
        if (byRole) {
            count = count.param("role", role);
            query = query.param("role", role);
        }
        if (byStatus) {
            count = count.param("status", status);
            query = query.param("status", status);
        }
        return new Paged<>(query.query(MemberRow.class).list(), pageNo, size, count.query(Long.class).single());
    }

    /** 운영자로 지정하거나 해제한다. 해제하면 가게가 있는 회원은 판매자, 없으면 구매자가 된다. */
    @PostMapping("/api/admin/members/{id}/admin")
    @Transactional
    public Map<String, Boolean> setAdmin(@PathVariable("id") long id, @RequestBody AdminFlag body,
                                         HttpServletRequest request) {
        Member me = auth.requireAdmin(request);
        if (me.id() == id) {
            throw ApiException.bad("자기 자신의 운영자 권한은 바꿀 수 없습니다.");
        }
        boolean admin = Boolean.TRUE.equals(body.admin());
        String role = admin ? "admin" : (auth.sellerOf(id) != null ? "seller" : "buyer");
        int changed = db.sql("UPDATE member SET role = :r WHERE id = :id AND tenant_id = :t")
                .param("r", role).param("id", id).param("t", tenant.id()).update();
        if (changed != 1) {
            throw ApiException.notFound();
        }
        return OK;
    }

    /** 이용 정지와 해제. 정지하면 그 회원의 로그인 세션이 바로 끊긴다. */
    @PostMapping("/api/admin/members/{id}/status")
    @Transactional
    public Map<String, Boolean> setStatus(@PathVariable("id") long id, @RequestBody MemberStatus body,
                                          HttpServletRequest request) {
        Member me = auth.requireAdmin(request);
        if (me.id() == id) {
            throw ApiException.bad("자기 자신은 정지할 수 없습니다.");
        }
        if (!Text.in(body.status(), List.of("active", "suspended"))) {
            throw ApiException.bad("회원 상태 값이 올바르지 않습니다.");
        }
        int changed = db.sql("UPDATE member SET status = :st WHERE id = :id AND tenant_id = :t AND status <> 'withdrawn'")
                .param("st", body.status()).param("id", id).param("t", tenant.id()).update();
        if (changed != 1) {
            throw ApiException.notFound();
        }
        auth.expireSessions(id);
        return OK;
    }

    // ---- 상품 ----

    @GetMapping("/api/admin/products")
    public Paged<ProductView> products(@RequestParam(name = "q", required = false) String q,
                                       @RequestParam(name = "status", required = false) String status,
                                       @RequestParam(name = "page", required = false) Integer page,
                                       HttpServletRequest request) {
        auth.requireAdmin(request);
        String keyword = q == null || q.isBlank() ? null : "%" + Text.clean(q, 50).replaceAll("([!%_])", "!$1") + "%";
        boolean byStatus = Text.in(status, List.of("draft", "on_sale", "paused", "ended", "blocked"));
        String where = " WHERE p.tenant_id = :t" + (byStatus ? " AND p.status = :st" : "")
                + (keyword == null ? "" : " AND p.name LIKE :q ESCAPE '!'");
        int pageNo = Paged.page(page);
        int size = 20;
        var count = db.sql("SELECT COUNT(*) FROM product p" + where).param("t", tenant.id());
        var query = db.sql(Catalog.PRODUCT_SELECT + where + " ORDER BY p.id DESC LIMIT :limit OFFSET :offset")
                .param("t", tenant.id()).param("today", clock.today())
                .param("limit", size).param("offset", Paged.offset(pageNo, size));
        if (byStatus) {
            count = count.param("st", status);
            query = query.param("st", status);
        }
        if (keyword != null) {
            count = count.param("q", keyword);
            query = query.param("q", keyword);
        }
        return new Paged<>(query.query(ProductView.class).list(), pageNo, size, count.query(Long.class).single());
    }

    /** 문제가 있는 상품의 판매를 막거나 푼다. 풀면 판매 중지 상태로 돌아가고, 판매자가 다시 판매를 시작한다. */
    @PostMapping("/api/admin/products/{id}/block")
    public Map<String, Boolean> block(@PathVariable("id") long id, @RequestBody BlockRequest body,
                                      HttpServletRequest request) {
        auth.requireAdmin(request);
        ProductView p = catalog.find(id);
        if (p == null) {
            throw ApiException.notFound();
        }
        boolean block = Boolean.TRUE.equals(body.blocked());
        int changed = block
                ? db.sql("UPDATE product SET status = 'blocked' WHERE id = :id AND status <> 'blocked'").param("id", id).update()
                : db.sql("UPDATE product SET status = 'paused' WHERE id = :id AND status = 'blocked'").param("id", id).update();
        if (changed == 1) {
            long memberId = db.sql("SELECT member_id FROM seller WHERE id = :id").param("id", p.sellerId())
                    .query(Long.class).single();
            notifications.notifyWithMail(memberId, block ? "상품 판매가 중단되었습니다" : "상품 판매 중단이 풀렸습니다",
                    "'" + p.name() + "' " + (block ? "상품을 운영자가 판매 중단했습니다. 운영자에게 문의해 주세요."
                            : "상품을 다시 판매할 수 있습니다. 상품 관리에서 판매를 시작해 주세요."), "/seller/products");
        }
        return OK;
    }

    // ---- 분류 ----

    /** 쓰지 않는 분류까지 모두 */
    @GetMapping("/api/admin/categories")
    public List<Category> categories(HttpServletRequest request) {
        auth.requireAdmin(request);
        return catalog.categories(true);
    }

    @PostMapping("/api/admin/categories")
    public Category createCategory(@RequestBody CategoryRequest body, HttpServletRequest request) {
        auth.requireAdmin(request);
        String name = Text.clean(body.name(), 100);
        Category parent = body.parentId() == null ? null : catalog.category(body.parentId());
        if (name.isEmpty() || (body.parentId() != null && (parent == null || parent.parentId() != null))) {
            throw ApiException.bad("분류 이름을 입력해 주세요. 분류는 대분류와 중분류 두 단계까지 만들 수 있습니다.");
        }
        checkDuplicate(name, body.parentId(), null);
        // 중분류의 주류 여부는 대분류를 따른다.
        boolean alcohol = parent != null ? parent.alcohol() : Boolean.TRUE.equals(body.alcohol());
        long id = Db.insert(db.sql("""
                INSERT INTO category (tenant_id, parent_id, name, is_alcohol, sort_order, active)
                VALUES (:t, :p, :n, :a, :o, TRUE)
                """)
                .param("t", tenant.id()).param("p", body.parentId()).param("n", name).param("a", alcohol)
                .param("o", body.sortOrder() == null ? 99 : body.sortOrder()));
        return catalog.category(id);
    }

    /** 이름, 순서, 사용 여부, (대분류만) 주류 여부를 바꾼다. 상품이 걸려 있을 수 있어 지우지는 않고 쓰지 않음으로 둔다. */
    @PutMapping("/api/admin/categories/{id}")
    @Transactional
    public Category updateCategory(@PathVariable("id") long id, @RequestBody CategoryRequest body,
                                   HttpServletRequest request) {
        auth.requireAdmin(request);
        Category c = catalog.category(id);
        if (c == null) {
            throw ApiException.notFound();
        }
        String name = body.name() == null ? c.name() : Text.clean(body.name(), 100);
        if (name.isEmpty()) {
            throw ApiException.bad("분류 이름을 입력해 주세요.");
        }
        checkDuplicate(name, c.parentId(), id);
        boolean alcohol = c.parentId() == null && body.alcohol() != null ? body.alcohol() : c.alcohol();
        db.sql("UPDATE category SET name = :n, is_alcohol = :a, sort_order = :o, active = :act WHERE id = :id AND tenant_id = :t")
                .param("n", name).param("a", alcohol)
                .param("o", body.sortOrder() == null ? c.sortOrder() : body.sortOrder())
                .param("act", body.active() == null ? c.active() : body.active())
                .param("id", id).param("t", tenant.id()).update();
        if (c.parentId() == null) {
            db.sql("UPDATE category SET is_alcohol = :a WHERE parent_id = :id").param("a", alcohol).param("id", id).update();
        }
        return catalog.category(id);
    }

    private void checkDuplicate(String name, Long parentId, Long selfId) {
        for (Category other : catalog.categories(true)) {
            boolean sameParent = parentId == null ? other.parentId() == null : parentId.equals(other.parentId());
            if (sameParent && other.name().equals(name) && (selfId == null || other.id() != selfId)) {
                throw ApiException.conflict("같은 이름의 분류가 이미 있습니다.");
            }
        }
    }

    // ---- 주문 ----

    @GetMapping("/api/admin/orders")
    public Paged<OrderRow> orders(@RequestParam(name = "status", required = false) String status,
                                  @RequestParam(name = "page", required = false) Integer page,
                                  HttpServletRequest request) {
        auth.requireAdmin(request);
        boolean byStatus = Text.in(status, List.of("awaiting_deposit", "paid", "shipped", "delivered", "completed", "cancelled", "refunded"));
        String where = " WHERE so.tenant_id = :t" + (byStatus ? " AND so.status = :st" : "");
        int pageNo = Paged.page(page);
        int size = 20;
        var count = db.sql("SELECT COUNT(*) FROM sub_order so" + where).param("t", tenant.id());
        var query = db.sql("""
                SELECT so.id, o.id AS order_id, o.order_no, m.name AS buyer_name, s.shop_name, so.total, so.status, o.created_at
                  FROM sub_order so JOIN shop_order o ON o.id = so.order_id JOIN seller s ON s.id = so.seller_id
                  JOIN member m ON m.id = o.buyer_id
                """ + where + " ORDER BY so.id DESC LIMIT :limit OFFSET :offset")
                .param("t", tenant.id()).param("limit", size).param("offset", Paged.offset(pageNo, size));
        if (byStatus) {
            count = count.param("st", status);
            query = query.param("st", status);
        }
        return new Paged<>(query.query(OrderRow.class).list(), pageNo, size, count.query(Long.class).single());
    }
}
