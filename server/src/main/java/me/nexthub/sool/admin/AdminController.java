package me.nexthub.sool.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.auth.Member;
import me.nexthub.sool.catalog.Catalog;
import me.nexthub.sool.catalog.Category;
import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.AppClock;
import me.nexthub.sool.common.Tenant;
import me.nexthub.sool.order.OrderService;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 운영자: 입점 심사, 주류 면허 확인, 수수료율 설정, 수수료 청구. */
@RestController
public class AdminController {

    public record SellerRow(long id, long memberId, String email, String shopName, String bizNo, String status,
                            String licenseType, String licenseNo, boolean licenseVerified, Integer commissionRateBp) {
    }

    public record RateLog(long id, String scope, Long targetId, Integer rateBp, String changedByName,
                          LocalDateTime changedAt) {
    }

    public record RecentSub(long id, String orderNo, String shopName, long total, String status,
                            LocalDateTime depositDueAt) {
    }

    public record Dashboard(List<SellerRow> sellers, List<Category> categories, int defaultRateBp,
                            List<InvoiceService.Invoice> invoices, List<RateLog> rateLog, List<RecentSub> recentSubs,
                            String currentPeriod) {
    }

    public record StatusRequest(String status) {
    }

    public record LicenseRequest(Boolean verified) {
    }

    /** 수수료율(%). 소수 둘째 자리까지. null이면 "따로 정하지 않음" */
    public record RateRequest(BigDecimal ratePercent) {
    }

    public record PeriodRequest(String period) {
    }

    private static final Map<String, Boolean> OK = Map.of("ok", true);

    private final JdbcClient db;
    private final Auth auth;
    private final Tenant tenant;
    private final AppClock clock;
    private final Catalog catalog;
    private final OrderService orders;
    private final InvoiceService invoices;

    public AdminController(JdbcClient db, Auth auth, Tenant tenant, AppClock clock, Catalog catalog,
                           OrderService orders, InvoiceService invoices) {
        this.db = db;
        this.auth = auth;
        this.tenant = tenant;
        this.clock = clock;
        this.catalog = catalog;
        this.orders = orders;
        this.invoices = invoices;
    }

    @GetMapping("/api/admin")
    public Dashboard dashboard(HttpServletRequest request) {
        auth.requireAdmin(request);
        List<SellerRow> sellers = db.sql("""
                SELECT s.id, s.member_id, m.email, s.shop_name, s.biz_no, s.status, s.license_type, s.license_no,
                       s.license_verified, s.commission_rate_bp
                  FROM seller s JOIN member m ON m.id = s.member_id
                 WHERE s.tenant_id = :t ORDER BY s.id
                """).param("t", tenant.id()).query(SellerRow.class).list();
        List<RateLog> log = db.sql("""
                SELECT l.id, l.scope, l.target_id, l.rate_bp, m.name AS changed_by_name, l.changed_at
                  FROM commission_rate_log l JOIN member m ON m.id = l.changed_by
                 WHERE l.tenant_id = :t ORDER BY l.id DESC LIMIT 10
                """).param("t", tenant.id()).query(RateLog.class).list();
        List<RecentSub> subs = db.sql("""
                SELECT so.id, o.order_no, s.shop_name, so.total, so.status, so.deposit_due_at
                  FROM sub_order so JOIN shop_order o ON o.id = so.order_id JOIN seller s ON s.id = so.seller_id
                 WHERE so.tenant_id = :t ORDER BY so.id DESC LIMIT 30
                """).param("t", tenant.id()).query(RecentSub.class).list();
        return new Dashboard(sellers, catalog.categories(), orders.defaultRateBp(), invoices.listAll(), log, subs,
                YearMonth.from(clock.today()).toString());
    }

    @PostMapping("/api/admin/sellers/{id}/status")
    public Map<String, Boolean> sellerStatus(@PathVariable("id") long id, @RequestBody StatusRequest body,
                                             HttpServletRequest request) {
        auth.requireAdmin(request);
        if (!List.of("approved", "rejected", "pending").contains(body.status())) {
            throw ApiException.bad("입점 상태 값이 올바르지 않습니다.");
        }
        db.sql("UPDATE seller SET status = :st WHERE id = :id AND tenant_id = :t")
                .param("st", body.status()).param("id", id).param("t", tenant.id()).update();
        return OK;
    }

    @PostMapping("/api/admin/sellers/{id}/license")
    @Transactional
    public Map<String, Boolean> license(@PathVariable("id") long id, @RequestBody LicenseRequest body,
                                        HttpServletRequest request) {
        auth.requireAdmin(request);
        boolean verified = Boolean.TRUE.equals(body.verified());
        db.sql("UPDATE seller SET license_verified = :v WHERE id = :id AND tenant_id = :t")
                .param("v", verified).param("id", id).param("t", tenant.id()).update();
        if (!verified) {
            // 면허 확인을 취소하면 그 판매자의 주류 상품을 숨긴다.
            db.sql("""
                    UPDATE product SET status = 'hidden'
                     WHERE seller_id = :id AND tenant_id = :t
                       AND category_id IN (SELECT id FROM category WHERE is_alcohol = TRUE)
                    """).param("id", id).param("t", tenant.id()).update();
        }
        return OK;
    }

    @PutMapping("/api/admin/commission/default")
    @Transactional
    public Map<String, Boolean> defaultRate(@RequestBody RateRequest body, HttpServletRequest request) {
        Member admin = auth.requireAdmin(request);
        Integer bp = toBp(body.ratePercent());
        if (bp == null) {
            throw ApiException.bad("기본 수수료율은 비워 둘 수 없습니다.");
        }
        db.sql("UPDATE app_setting SET setting_value = :v WHERE tenant_id = :t AND setting_key = 'default_commission_rate_bp'")
                .param("v", String.valueOf(bp)).param("t", tenant.id()).update();
        log(admin, "default", null, bp);
        return OK;
    }

    @PutMapping("/api/admin/categories/{id}/commission")
    @Transactional
    public Map<String, Boolean> categoryRate(@PathVariable("id") long id, @RequestBody RateRequest body,
                                             HttpServletRequest request) {
        Member admin = auth.requireAdmin(request);
        Integer bp = toBp(body.ratePercent());
        db.sql("UPDATE category SET commission_rate_bp = :bp WHERE id = :id AND tenant_id = :t")
                .param("bp", bp).param("id", id).param("t", tenant.id()).update();
        log(admin, "category", id, bp);
        return OK;
    }

    @PutMapping("/api/admin/sellers/{id}/commission")
    @Transactional
    public Map<String, Boolean> sellerRate(@PathVariable("id") long id, @RequestBody RateRequest body,
                                           HttpServletRequest request) {
        Member admin = auth.requireAdmin(request);
        Integer bp = toBp(body.ratePercent());
        db.sql("UPDATE seller SET commission_rate_bp = :bp WHERE id = :id AND tenant_id = :t")
                .param("bp", bp).param("id", id).param("t", tenant.id()).update();
        log(admin, "seller", id, bp);
        return OK;
    }

    @PostMapping("/api/admin/invoices/generate")
    public Map<String, Integer> generate(@RequestBody PeriodRequest body, HttpServletRequest request) {
        auth.requireAdmin(request);
        return Map.of("count", invoices.generate(body.period()));
    }

    @PostMapping("/api/admin/invoices/{id}/paid")
    public Map<String, Boolean> paid(@PathVariable("id") long id, HttpServletRequest request) {
        auth.requireAdmin(request);
        invoices.markPaid(id);
        return OK;
    }

    /** 0~30% 범위만 허용. 바꾼 수수료율은 이후 들어오는 주문부터 적용된다. */
    private static Integer toBp(BigDecimal percent) {
        if (percent == null) {
            return null;
        }
        if (percent.signum() < 0 || percent.compareTo(BigDecimal.valueOf(30)) > 0) {
            throw ApiException.bad("수수료율은 0~30 사이 숫자로 입력해 주세요.");
        }
        return percent.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private void log(Member admin, String scope, Long targetId, Integer bp) {
        db.sql("""
                INSERT INTO commission_rate_log (tenant_id, scope, target_id, rate_bp, changed_by, changed_at)
                VALUES (:t, :scope, :target, :bp, :by, :now)
                """)
                .param("t", tenant.id()).param("scope", scope).param("target", targetId).param("bp", bp)
                .param("by", admin.id()).param("now", clock.now()).update();
    }
}
