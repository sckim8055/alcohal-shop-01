package me.nexthub.sool.seller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import me.nexthub.sool.admin.InvoiceService;
import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.auth.Member;
import me.nexthub.sool.auth.Seller;
import me.nexthub.sool.catalog.Catalog;
import me.nexthub.sool.catalog.Category;
import me.nexthub.sool.catalog.ProductView;
import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.AppClock;
import me.nexthub.sool.common.Db;
import me.nexthub.sool.common.Tenant;
import me.nexthub.sool.common.Text;
import me.nexthub.sool.order.OrderService;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 판매자: 입점 신청, 가게 설정, 상품 관리, 주문 처리. */
@RestController
public class SellerController {

    public record ApplyRequest(String shopName, String bizNo, String intro, String licenseType, String licenseNo) {
    }

    public record SettingsRequest(String intro, String bankName, String bankAccount, String bankHolder, String payLink,
                                  Integer baseShipFee, Integer freeShipMin, String freeShipStart, String freeShipEnd) {
    }

    public record ProductRequest(Long categoryId, String name, String description, String productType,
                                 Long price, Long salePrice, String saleStart, String saleEnd,
                                 Integer stock, String status) {
    }

    public record ConfirmRequest(String cashReceiptApproval) {
    }

    public record ShipRequest(String carrier, String trackingNo) {
    }

    public record StatusCount(String status, int count) {
    }

    public record Dashboard(Seller seller, List<ProductView> products, List<StatusCount> counts,
                            List<InvoiceService.Invoice> invoices) {
    }

    private static final Map<String, Boolean> OK = Map.of("ok", true);

    private final JdbcClient db;
    private final Auth auth;
    private final Tenant tenant;
    private final AppClock clock;
    private final Catalog catalog;
    private final OrderService orders;
    private final InvoiceService invoices;

    public SellerController(JdbcClient db, Auth auth, Tenant tenant, AppClock clock, Catalog catalog,
                            OrderService orders, InvoiceService invoices) {
        this.db = db;
        this.auth = auth;
        this.tenant = tenant;
        this.clock = clock;
        this.catalog = catalog;
        this.orders = orders;
        this.invoices = invoices;
    }

    @PostMapping("/api/seller/apply")
    @Transactional
    public Seller apply(@RequestBody ApplyRequest body, HttpServletRequest request) {
        Member user = auth.requireUser(request);
        if (auth.sellerOf(user.id()) != null) {
            throw ApiException.conflict("이미 입점 신청을 했습니다.");
        }
        String shop = Text.clean(body.shopName(), 40);
        String biz = Text.clean(body.bizNo(), 20);
        if (shop.isEmpty() || biz.isEmpty()) {
            throw ApiException.bad("가게 이름과 사업자등록번호를 입력해 주세요.");
        }
        db.sql("""
                INSERT INTO seller (tenant_id, member_id, shop_name, biz_no, intro, license_type, license_no, created_at)
                VALUES (:t, :m, :shop, :biz, :intro, :lt, :ln, :now)
                """)
                .param("t", tenant.id()).param("m", user.id()).param("shop", shop).param("biz", biz)
                .param("intro", Text.clean(body.intro(), 500)).param("lt", Text.clean(body.licenseType(), 40))
                .param("ln", Text.clean(body.licenseNo(), 40)).param("now", clock.now()).update();
        if ("buyer".equals(user.role())) {
            db.sql("UPDATE member SET role = 'seller' WHERE id = :id").param("id", user.id()).update();
        }
        return auth.sellerOf(user.id());
    }

    /** 내 가게 현황. 심사 중인 판매자도 볼 수 있다. */
    @GetMapping("/api/seller")
    public Dashboard dashboard(HttpServletRequest request) {
        Seller seller = auth.sellerOf(auth.requireUser(request).id());
        if (seller == null) {
            throw ApiException.notFound();
        }
        List<ProductView> products = db.sql(Catalog.PRODUCT_SELECT + " WHERE p.seller_id = :s ORDER BY p.id DESC")
                .param("today", clock.today()).param("s", seller.id()).query(ProductView.class).list();
        List<StatusCount> counts = db.sql("SELECT status, COUNT(*) AS count FROM sub_order WHERE seller_id = :s GROUP BY status")
                .param("s", seller.id()).query(StatusCount.class).list();
        return new Dashboard(seller, products, counts, invoices.listForSeller(seller.id()));
    }

    @PutMapping("/api/seller/settings")
    public Seller settings(@RequestBody SettingsRequest body, HttpServletRequest request) {
        Seller seller = auth.requireSeller(request);
        String link = Text.clean(body.payLink(), 300);
        Integer fee = body.baseShipFee();
        Integer freeMin = body.freeShipMin();
        if (fee == null || fee < 0 || fee > 100_000 || (freeMin != null && (freeMin < 0 || freeMin > 100_000_000))
                || (!link.isEmpty() && !link.startsWith("https://"))) {
            throw ApiException.bad("배송비는 숫자로, 송금 링크는 https:// 로 시작하게 입력해 주세요.");
        }
        db.sql("""
                UPDATE seller SET intro = :intro, bank_name = :bn, bank_account = :ba, bank_holder = :bh, pay_link = :link,
                       base_ship_fee = :fee, free_ship_min = :min, free_ship_start = :fs, free_ship_end = :fe
                 WHERE id = :id
                """)
                .param("intro", Text.clean(body.intro(), 500)).param("bn", Text.clean(body.bankName(), 30))
                .param("ba", Text.clean(body.bankAccount(), 40)).param("bh", Text.clean(body.bankHolder(), 40))
                .param("link", link).param("fee", fee).param("min", freeMin)
                .param("fs", Text.date(body.freeShipStart())).param("fe", Text.date(body.freeShipEnd()))
                .param("id", seller.id()).update();
        return auth.sellerOf(seller.memberId());
    }

    // ---- 상품 ----

    @GetMapping("/api/seller/products/{id}")
    public ProductView product(@PathVariable("id") long id, HttpServletRequest request) {
        Seller seller = auth.requireSeller(request);
        ProductView p = catalog.find(id);
        if (p == null || p.sellerId() != seller.id()) {
            throw ApiException.notFound();
        }
        return p;
    }

    @PostMapping("/api/seller/products")
    public ProductView create(@RequestBody ProductRequest body, HttpServletRequest request) {
        Seller seller = auth.requireSeller(request);
        Checked c = check(body, seller);
        long id = Db.insert(db.sql("""
                INSERT INTO product (tenant_id, seller_id, category_id, name, description, product_type, price,
                                     sale_price, sale_start, sale_end, stock, status, created_at)
                VALUES (:t, :s, :c, :n, :d, :type, :price, :sale, :ss, :se, :stock, :status, :now)
                """)
                .param("t", tenant.id()).param("s", seller.id()).param("c", c.categoryId).param("n", c.name)
                .param("d", c.description).param("type", c.type).param("price", c.price).param("sale", c.salePrice)
                .param("ss", c.saleStart).param("se", c.saleEnd).param("stock", c.stock).param("status", c.status)
                .param("now", clock.now()));
        return catalog.find(id);
    }

    @PutMapping("/api/seller/products/{id}")
    public ProductView update(@PathVariable("id") long id, @RequestBody ProductRequest body,
                              HttpServletRequest request) {
        Seller seller = auth.requireSeller(request);
        ProductView existing = catalog.find(id);
        if (existing == null || existing.sellerId() != seller.id()) {
            throw ApiException.notFound();
        }
        Checked c = check(body, seller);
        db.sql("""
                UPDATE product SET category_id = :c, name = :n, description = :d, product_type = :type, price = :price,
                       sale_price = :sale, sale_start = :ss, sale_end = :se, stock = :stock, status = :status
                 WHERE id = :id AND seller_id = :s
                """)
                .param("c", c.categoryId).param("n", c.name).param("d", c.description).param("type", c.type)
                .param("price", c.price).param("sale", c.salePrice).param("ss", c.saleStart).param("se", c.saleEnd)
                .param("stock", c.stock).param("status", c.status).param("id", id).param("s", seller.id()).update();
        return catalog.find(id);
    }

    /** 검증을 통과한 상품 입력값 */
    private static final class Checked {
        long categoryId;
        String name;
        String description;
        String type;
        long price;
        Long salePrice;
        LocalDate saleStart;
        LocalDate saleEnd;
        int stock;
        String status;
    }

    private Checked check(ProductRequest body, Seller seller) {
        Category cat = body.categoryId() == null ? null : catalog.category(body.categoryId());
        String name = Text.clean(body.name(), 80);
        Long price = body.price();
        Long sale = body.salePrice();
        Integer stock = body.stock();
        if (cat == null || name.isEmpty() || price == null || price < 0 || price > 2_000_000_000L
                || stock == null || stock < 0 || stock > 1_000_000 || (sale != null && sale < 0)) {
            throw ApiException.bad("상품명, 분류, 가격, 재고를 확인해 주세요.");
        }
        if (sale != null && sale >= price) {
            throw ApiException.bad("할인가는 정가보다 낮아야 합니다.");
        }
        String type = "ticket".equals(body.productType()) ? "ticket" : "physical";
        // 주류는 면허가 확인된 판매자만 등록할 수 있다.
        if (cat.alcohol() && !seller.licenseVerified()) {
            throw ApiException.forbidden(
                    "주류는 제조 면허와 통신판매 승인이 확인된 판매자만 등록할 수 있습니다. 운영자에게 면허 확인을 요청해 주세요.");
        }
        if (cat.alcohol() && !"physical".equals(type)) {
            throw ApiException.bad("주류는 실물 상품으로만 등록할 수 있습니다.");
        }
        Checked c = new Checked();
        c.categoryId = cat.id();
        c.name = name;
        c.description = Text.clean(body.description(), 2000);
        c.type = type;
        c.price = price;
        c.salePrice = sale;
        c.saleStart = Text.date(body.saleStart());
        c.saleEnd = Text.date(body.saleEnd());
        c.stock = stock;
        c.status = "hidden".equals(body.status()) ? "hidden" : "active";
        return c;
    }

    // ---- 주문 처리 ----

    @GetMapping("/api/seller/orders")
    public List<OrderService.SellerOrder> orders(HttpServletRequest request) {
        return orders.listForSeller(auth.requireSeller(request).id());
    }

    @PostMapping("/api/seller/suborders/{id}/confirm")
    public Map<String, Boolean> confirm(@PathVariable("id") long id, @RequestBody(required = false) ConfirmRequest body,
                                        HttpServletRequest request) {
        orders.confirmDeposit(id, auth.requireSeller(request).id(), body == null ? null : body.cashReceiptApproval());
        return OK;
    }

    @PostMapping("/api/seller/suborders/{id}/ship")
    public Map<String, Boolean> ship(@PathVariable("id") long id, @RequestBody ShipRequest body,
                                     HttpServletRequest request) {
        orders.ship(id, auth.requireSeller(request).id(), body.carrier(), body.trackingNo());
        return OK;
    }

    @PostMapping("/api/seller/suborders/{id}/deliver")
    public Map<String, Boolean> deliver(@PathVariable("id") long id, HttpServletRequest request) {
        orders.deliverTicket(id, auth.requireSeller(request).id());
        return OK;
    }

    @PostMapping("/api/seller/suborders/{id}/refund")
    public Map<String, Boolean> refund(@PathVariable("id") long id, HttpServletRequest request) {
        orders.refund(id, auth.requireSeller(request).id());
        return OK;
    }
}
