package me.nexthub.sool.catalog;

import java.util.List;

import me.nexthub.sool.common.AppClock;
import me.nexthub.sool.common.Tenant;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** 상품·카테고리 조회. 현재 판매가 계산식은 여기 한 곳에만 둔다. */
@Component
public class Catalog {

    /** 상품 + 카테고리 + 판매자. :today 값이 필요하다. */
    public static final String PRODUCT_SELECT = """
            SELECT p.id, p.seller_id, p.category_id, p.name, p.description, p.product_type,
                   p.price, p.sale_price, p.sale_start, p.sale_end, p.stock, p.status,
                   CASE WHEN p.sale_price IS NOT NULL
                             AND (p.sale_start IS NULL OR p.sale_start <= :today)
                             AND (p.sale_end IS NULL OR :today <= p.sale_end)
                        THEN p.sale_price ELSE p.price END AS current_price,
                   c.name AS category_name, c.is_alcohol AS alcohol, c.commission_rate_bp AS category_rate_bp,
                   s.shop_name, s.status AS seller_status, s.intro AS seller_intro,
                   s.base_ship_fee, s.free_ship_min, s.free_ship_start, s.free_ship_end,
                   s.commission_rate_bp AS seller_rate_bp
              FROM product p
              JOIN category c ON c.id = p.category_id
              JOIN seller s ON s.id = p.seller_id
            """;

    private final JdbcClient db;
    private final Tenant tenant;
    private final AppClock clock;

    public Catalog(JdbcClient db, Tenant tenant, AppClock clock) {
        this.db = db;
        this.tenant = tenant;
        this.clock = clock;
    }

    public List<Category> categories() {
        return db.sql("""
                SELECT id, name, is_alcohol AS alcohol, commission_rate_bp
                  FROM category WHERE tenant_id = :t ORDER BY id
                """).param("t", tenant.id()).query(Category.class).list();
    }

    public Category category(long id) {
        return db.sql("""
                SELECT id, name, is_alcohol AS alcohol, commission_rate_bp
                  FROM category WHERE tenant_id = :t AND id = :id
                """).param("t", tenant.id()).param("id", id).query(Category.class).optional().orElse(null);
    }

    /** 판매 중인 상품 목록(승인된 판매자의 공개 상품만). */
    public List<ProductView> list(Long categoryId, String keyword) {
        StringBuilder sql = new StringBuilder(PRODUCT_SELECT)
                .append(" WHERE p.tenant_id = :t AND p.status = 'active' AND s.status = 'approved'");
        if (categoryId != null) {
            sql.append(" AND p.category_id = :cat");
        }
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        if (hasKeyword) {
            sql.append(" AND p.name LIKE :q ESCAPE '!'");
        }
        sql.append(" ORDER BY p.id DESC LIMIT 60");
        var spec = db.sql(sql.toString()).param("t", tenant.id()).param("today", clock.today());
        if (categoryId != null) {
            spec = spec.param("cat", categoryId);
        }
        if (hasKeyword) {
            String k = keyword.trim();
            k = k.length() > 50 ? k.substring(0, 50) : k;
            spec = spec.param("q", "%" + k.replaceAll("([!%_])", "!$1") + "%");
        }
        return spec.query(ProductView.class).list();
    }

    /** 공개 여부와 상관없이 상품 하나를 읽는다. 없으면 null */
    public ProductView find(long id) {
        return db.sql(PRODUCT_SELECT + " WHERE p.tenant_id = :t AND p.id = :id")
                .param("t", tenant.id()).param("today", clock.today()).param("id", id)
                .query(ProductView.class).optional().orElse(null);
    }
}
