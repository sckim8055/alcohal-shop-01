package me.nexthub.sool.catalog;

import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.Paged;
import me.nexthub.sool.common.Tenant;
import me.nexthub.sool.review.ReviewService;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 가게 화면(docs/adr/0003 D). 승인된 가게만 보여 주고, 계좌 정보는 넣지 않는다(입금 안내는 주문한 사람에게만). */
@RestController
public class ShopController {

    private static final int PAGE_SIZE = 24;

    /** productCount 는 지금 판매 중인 상품 수, reviewCount·ratingAvg 는 가게 상품 전체의 보이는 리뷰 기준 */
    public record Shop(long id, String shopName, String intro, String ceoName, String bizNo, String mailOrderNo,
                       String csPhone, long productCount, long reviewCount, Double ratingAvg) {
    }

    public record ShopPage(Shop shop, Paged<ProductView> products) {
    }

    private record Info(long id, String shopName, String intro, String ceoName, String bizNo, String mailOrderNo,
                        String csPhone) {
    }

    private final JdbcClient db;
    private final Tenant tenant;
    private final Catalog catalog;
    private final ReviewService reviews;

    public ShopController(JdbcClient db, Tenant tenant, Catalog catalog, ReviewService reviews) {
        this.db = db;
        this.tenant = tenant;
        this.catalog = catalog;
        this.reviews = reviews;
    }

    @GetMapping("/api/shops/{sellerId}")
    public ShopPage shop(@PathVariable("sellerId") long sellerId,
                         @RequestParam(name = "page", required = false) Integer page) {
        Info info = db.sql("""
                SELECT id, shop_name, intro, ceo_name, biz_no, mail_order_no, cs_phone
                  FROM seller WHERE id = :id AND tenant_id = :t AND status = 'approved'
                """).param("id", sellerId).param("t", tenant.id()).query(Info.class).optional().orElse(null);
        if (info == null) {
            throw ApiException.notFound();
        }
        Paged<ProductView> products = catalog.list(null, null, null, sellerId, Paged.page(page), PAGE_SIZE);
        ReviewService.Stats stats = reviews.statsForSeller(sellerId);
        return new ShopPage(new Shop(info.id(), info.shopName(), info.intro(), info.ceoName(), info.bizNo(),
                info.mailOrderNo(), info.csPhone(), products.total(), stats.reviewCount(), stats.ratingAvg()), products);
    }
}
