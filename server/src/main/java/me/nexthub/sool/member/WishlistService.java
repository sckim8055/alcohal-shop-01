package me.nexthub.sool.member;

import java.util.List;

import me.nexthub.sool.catalog.Catalog;
import me.nexthub.sool.catalog.ProductView;
import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.AppClock;
import me.nexthub.sool.common.Paged;
import me.nexthub.sool.common.Tenant;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** 찜. 찜하기는 판매 중인 상품만, 풀기와 보기는 판매가 끝난 상품도 된다. */
@Service
public class WishlistService {

    public static final int PAGE_SIZE = 24;

    private final JdbcClient db;
    private final Tenant tenant;
    private final AppClock clock;
    private final Catalog catalog;

    public WishlistService(JdbcClient db, Tenant tenant, AppClock clock, Catalog catalog) {
        this.db = db;
        this.tenant = tenant;
        this.clock = clock;
        this.catalog = catalog;
    }

    /** 이미 찜했으면 그대로 둔다(두 번 눌러도 같은 결과). */
    public void add(long memberId, long productId) {
        ProductView p = catalog.find(productId);
        if (p == null || !p.onSale()) {
            throw ApiException.notFound();
        }
        int exists = db.sql("SELECT COUNT(*) FROM wishlist WHERE member_id = :m AND product_id = :p")
                .param("m", memberId).param("p", productId).query(Integer.class).single();
        if (exists > 0) {
            return;
        }
        try {
            db.sql("INSERT INTO wishlist (tenant_id, member_id, product_id, created_at) VALUES (:t, :m, :p, :now)")
                    .param("t", tenant.id()).param("m", memberId).param("p", productId).param("now", clock.now())
                    .update();
        } catch (DuplicateKeyException e) {
            // 동시에 두 번 눌렀을 때. 이미 찜한 상태이므로 그대로 성공으로 본다.
        }
    }

    public void remove(long memberId, long productId) {
        db.sql("DELETE FROM wishlist WHERE member_id = :m AND product_id = :p")
                .param("m", memberId).param("p", productId).update();
    }

    public List<Long> ids(long memberId) {
        return db.sql("SELECT product_id FROM wishlist WHERE member_id = :m ORDER BY created_at DESC, product_id DESC")
                .param("m", memberId).query(Long.class).list();
    }

    /** 최근에 찜한 것부터. 판매가 끝난 상품도 status 로 구분해 보여 준다. */
    public Paged<ProductView> list(long memberId, int page) {
        long total = db.sql("SELECT COUNT(*) FROM wishlist WHERE member_id = :m").param("m", memberId)
                .query(Long.class).single();
        List<ProductView> items = db.sql(Catalog.PRODUCT_SELECT + """
                 JOIN wishlist w ON w.product_id = p.id
                 WHERE w.member_id = :m ORDER BY w.created_at DESC, p.id DESC LIMIT :limit OFFSET :offset
                """).param("today", clock.today()).param("m", memberId).param("limit", PAGE_SIZE)
                .param("offset", Paged.offset(page, PAGE_SIZE)).query(ProductView.class).list();
        return new Paged<>(items, page, PAGE_SIZE, total);
    }
}
