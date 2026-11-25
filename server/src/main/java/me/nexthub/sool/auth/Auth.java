package me.nexthub.sool.auth;

import jakarta.servlet.http.HttpServletRequest;

import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.Tenant;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** 요청을 보낸 사람이 누구인지, 무엇을 할 수 있는지 확인한다. */
@Component
public class Auth {

    static final String USER_ATTR = "sool.user";

    public static final String SELLER_SELECT = """
            SELECT id, member_id, shop_name, biz_no, intro, status, bank_name, bank_account, bank_holder, pay_link,
                   license_type, license_no, license_verified, base_ship_fee, free_ship_min,
                   free_ship_start, free_ship_end, commission_rate_bp
              FROM seller
            """;

    private final JdbcClient db;
    private final Tenant tenant;

    public Auth(JdbcClient db, Tenant tenant) {
        this.db = db;
        this.tenant = tenant;
    }

    /** 로그인하지 않았으면 null */
    public Member user(HttpServletRequest request) {
        return (Member) request.getAttribute(USER_ATTR);
    }

    public Member requireUser(HttpServletRequest request) {
        Member m = user(request);
        if (m == null) {
            throw ApiException.unauthorized();
        }
        return m;
    }

    public Member requireAdmin(HttpServletRequest request) {
        Member m = requireUser(request);
        if (!m.admin()) {
            throw ApiException.forbidden("운영자만 이용할 수 있습니다.");
        }
        return m;
    }

    /** 입점 신청을 한 적이 없으면 null */
    public Seller sellerOf(long memberId) {
        return db.sql(SELLER_SELECT + " WHERE member_id = :m AND tenant_id = :t")
                .param("m", memberId).param("t", tenant.id())
                .query(Seller.class).optional().orElse(null);
    }

    /** 입점 승인이 끝난 판매자만 통과한다. */
    public Seller requireSeller(HttpServletRequest request) {
        Seller s = sellerOf(requireUser(request).id());
        if (s == null || !s.approved()) {
            throw ApiException.forbidden("입점 승인이 끝난 판매자만 이용할 수 있습니다.");
        }
        return s;
    }

    public Member load(long memberId) {
        return db.sql("SELECT id, email, name, role, adult_verified_at FROM member WHERE id = :id AND tenant_id = :t")
                .param("id", memberId).param("t", tenant.id())
                .query(Member.class).optional().orElse(null);
    }
}
