package me.nexthub.sool.catalog;

import java.util.List;

import me.nexthub.sool.common.AppClock;
import me.nexthub.sool.common.Paged;
import me.nexthub.sool.common.Tenant;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** 상품·분류 조회. 현재 판매가와 수수료율을 구하는 식은 여기 한 곳에만 둔다. */
@Component
public class Catalog {

    /**
     * 상품 + 분류(중분류와 그 위 대분류) + 판매자. :today 값이 필요하다.
     * category_rate_bp 는 중분류에 값이 없으면 대분류 값을 쓴다.
     */
    public static final String PRODUCT_SELECT = """
            SELECT p.id, p.seller_id, p.category_id, p.name, p.description, p.product_type,
                   p.price, p.sale_price, p.sale_start, p.sale_end, p.stock, p.status,
                   p.abv, p.volume_ml, p.origin,
                   CASE WHEN p.sale_price IS NOT NULL
                             AND (p.sale_start IS NULL OR p.sale_start <= :today)
                             AND (p.sale_end IS NULL OR :today <= p.sale_end)
                        THEN p.sale_price ELSE p.price END AS current_price,
                   (SELECT pm.url FROM product_media pm
                     WHERE pm.product_id = p.id AND pm.kind = 'image' ORDER BY pm.sort_order LIMIT 1) AS main_image,
                   c.name AS category_name, COALESCE(pc.name, c.name) AS top_category_name, c.is_alcohol AS alcohol,
                   COALESCE(c.commission_rate_bp, pc.commission_rate_bp) AS category_rate_bp,
                   s.shop_name, s.status AS seller_status, s.intro AS seller_intro,
                   s.base_ship_fee, s.free_ship_min, s.free_ship_start, s.free_ship_end,
                   s.commission_rate_bp AS seller_rate_bp
              FROM product p
              JOIN category c ON c.id = p.category_id
              LEFT JOIN category pc ON pc.id = c.parent_id
              JOIN seller s ON s.id = p.seller_id
            """;

    private static final String CATEGORY_SELECT = """
            SELECT id, parent_id, name, is_alcohol AS alcohol, commission_rate_bp, sort_order, active
              FROM category
            """;

    private final JdbcClient db;
    private final Tenant tenant;
    private final AppClock clock;

    public Catalog(JdbcClient db, Tenant tenant, AppClock clock) {
        this.db = db;
        this.tenant = tenant;
        this.clock = clock;
    }

    /** 대분류가 먼저, 그 안에서는 정한 순서대로. includeInactive 가 false 면 쓰지 않는 분류는 뺀다. */
    public List<Category> categories(boolean includeInactive) {
        return db.sql(CATEGORY_SELECT + " WHERE tenant_id = :t" + (includeInactive ? "" : " AND active = TRUE")
                        + " ORDER BY CASE WHEN parent_id IS NULL THEN 0 ELSE 1 END, sort_order, id")
                .param("t", tenant.id()).query(Category.class).list();
    }

    public Category category(long id) {
        return db.sql(CATEGORY_SELECT + " WHERE tenant_id = :t AND id = :id")
                .param("t", tenant.id()).param("id", id).query(Category.class).optional().orElse(null);
    }

    /** 판매 중인 상품 목록(승인된 판매자의 판매 중 상품만). 분류는 대분류를 주면 그 아래 중분류까지 포함한다. */
    public Paged<ProductView> list(Long categoryId, String keyword, String sort, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE p.tenant_id = :t AND p.status = 'on_sale' AND s.status = 'approved'");
        if (categoryId != null) {
            where.append(" AND (p.category_id = :cat OR c.parent_id = :cat)");
        }
        String k = keyword == null ? "" : keyword.trim();
        if (!k.isEmpty()) {
            where.append(" AND p.name LIKE :q ESCAPE '!'");
            k = "%" + (k.length() > 50 ? k.substring(0, 50) : k).replaceAll("([!%_])", "!$1") + "%";
        }
        String order = switch (sort == null ? "" : sort) {
            case "price_asc" -> " ORDER BY current_price ASC, p.id DESC";
            case "price_desc" -> " ORDER BY current_price DESC, p.id DESC";
            default -> " ORDER BY p.id DESC";
        };
        String from = " FROM product p JOIN category c ON c.id = p.category_id JOIN seller s ON s.id = p.seller_id";
        var count = db.sql("SELECT COUNT(*)" + from + where).param("t", tenant.id());
        var query = db.sql(PRODUCT_SELECT + where + order + " LIMIT :limit OFFSET :offset")
                .param("t", tenant.id()).param("today", clock.today())
                .param("limit", size).param("offset", Paged.offset(page, size));
        if (categoryId != null) {
            count = count.param("cat", categoryId);
            query = query.param("cat", categoryId);
        }
        if (!k.isEmpty()) {
            count = count.param("q", k);
            query = query.param("q", k);
        }
        return new Paged<>(query.query(ProductView.class).list(), page, size, count.query(Long.class).single());
    }

    /** 판매 상태와 상관없이 상품 하나를 읽는다. 없으면 null */
    public ProductView find(long id) {
        return db.sql(PRODUCT_SELECT + " WHERE p.tenant_id = :t AND p.id = :id")
                .param("t", tenant.id()).param("today", clock.today()).param("id", id)
                .query(ProductView.class).optional().orElse(null);
    }

    public ProductDetail detail(ProductView p) {
        String doc = db.sql("SELECT description_doc FROM product WHERE id = :id").param("id", p.id())
                .query(String.class).optional().orElse(null);
        List<ProductDetail.Media> media = db.sql("SELECT kind, url FROM product_media WHERE product_id = :id ORDER BY sort_order")
                .param("id", p.id()).query(ProductDetail.Media.class).list();
        ProductDetail.SellerInfo info = db.sql("""
                SELECT shop_name, ceo_name, biz_no, biz_address, mail_order_no, cs_phone, return_address
                  FROM seller WHERE id = :s
                """).param("s", p.sellerId()).query(ProductDetail.SellerInfo.class).single();
        return new ProductDetail(p, doc, media, info);
    }
}
