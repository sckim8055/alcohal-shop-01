package me.nexthub.sool.seller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import me.nexthub.sool.admin.InvoiceService;
import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.auth.Member;
import me.nexthub.sool.auth.Seller;
import me.nexthub.sool.catalog.Catalog;
import me.nexthub.sool.catalog.Category;
import me.nexthub.sool.catalog.ProductDetail;
import me.nexthub.sool.catalog.ProductDoc;
import me.nexthub.sool.catalog.ProductView;
import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.AppClock;
import me.nexthub.sool.common.Db;
import me.nexthub.sool.common.Paged;
import me.nexthub.sool.common.Tenant;
import me.nexthub.sool.common.Text;
import me.nexthub.sool.file.FileController;
import me.nexthub.sool.order.OrderService;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;

/** 판매자: 입점 신청, 가게 설정, 상품 관리, 주문 처리. */
@RestController
public class SellerController {

    /** 판매자가 직접 고를 수 있는 상품 상태. blocked(운영자 차단)는 운영자만 정한다. */
    public static final List<String> SELLER_STATUSES = List.of("draft", "on_sale", "paused", "ended");
    private static final List<String> ORDER_STATUSES = List.of(
            "awaiting_deposit", "paid", "shipped", "delivered", "completed", "cancelled", "refunded");
    private static final Pattern YOUTUBE = Pattern.compile(
            "(?:youtu\\.be/|youtube\\.com/(?:watch\\?(?:.*&)?v=|shorts/|embed/|live/))([A-Za-z0-9_-]{11})");
    private static final Pattern YOUTUBE_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final int MAX_MEDIA = 12;

    public record ApplyRequest(String shopName, String bizNo, String intro, String licenseType, String licenseNo) {
    }

    public record SettingsRequest(String intro, String bankName, String bankAccount, String bankHolder, String payLink,
                                  Integer baseShipFee, Integer freeShipMin, String freeShipStart, String freeShipEnd) {
    }

    public record MediaRequest(String kind, String url) {
    }

    /**
     * descriptionDoc: 편집기 문서(JSON). media: 순서대로, 첫 사진이 대표 사진.
     * media 의 url 은 사진·동영상이면 올린 파일 주소, youtube 면 유튜브 주소나 영상 번호.
     */
    public record ProductRequest(Long categoryId, String name, String description, JsonNode descriptionDoc,
                                 String productType, Long price, Long salePrice, String saleStart, String saleEnd,
                                 Integer stock, String status, BigDecimal abv, Integer volumeMl, String origin,
                                 List<MediaRequest> media) {
    }

    public record ConfirmRequest(String cashReceiptApproval) {
    }

    public record ShipRequest(String carrier, String trackingNo) {
    }

    public record StatusRequest(String status) {
    }

    public record StatusCount(String status, int count) {
    }

    public record Dashboard(Seller seller, List<StatusCount> orderCounts, List<StatusCount> productCounts,
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
    private final FileController files;

    public SellerController(JdbcClient db, Auth auth, Tenant tenant, AppClock clock, Catalog catalog,
                            OrderService orders, InvoiceService invoices, FileController files) {
        this.db = db;
        this.auth = auth;
        this.tenant = tenant;
        this.clock = clock;
        this.catalog = catalog;
        this.orders = orders;
        this.invoices = invoices;
        this.files = files;
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
        List<StatusCount> orderCounts = db.sql("SELECT status, COUNT(*) AS count FROM sub_order WHERE seller_id = :s GROUP BY status")
                .param("s", seller.id()).query(StatusCount.class).list();
        List<StatusCount> productCounts = db.sql("SELECT status, COUNT(*) AS count FROM product WHERE seller_id = :s GROUP BY status")
                .param("s", seller.id()).query(StatusCount.class).list();
        return new Dashboard(seller, orderCounts, productCounts, invoices.listForSeller(seller.id()));
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

    /** 내 상품 목록. status 를 주면 그 상태만. */
    @GetMapping("/api/seller/products")
    public Paged<ProductView> products(@RequestParam(name = "status", required = false) String status,
                                       @RequestParam(name = "q", required = false) String q,
                                       @RequestParam(name = "page", required = false) Integer page,
                                       HttpServletRequest request) {
        Seller seller = auth.requireSeller(request);
        String st = status == null || status.isBlank() ? null : status;
        String keyword = q == null || q.isBlank() ? null : "%" + Text.clean(q, 50).replaceAll("([!%_])", "!$1") + "%";
        String where = " WHERE p.seller_id = :s" + (st == null ? "" : " AND p.status = :st")
                + (keyword == null ? "" : " AND p.name LIKE :q ESCAPE '!'");
        int pageNo = Paged.page(page);
        int size = 20;
        var count = db.sql("SELECT COUNT(*) FROM product p" + where).param("s", seller.id());
        var query = db.sql(Catalog.PRODUCT_SELECT + where + " ORDER BY p.id DESC LIMIT :limit OFFSET :offset")
                .param("today", clock.today()).param("s", seller.id())
                .param("limit", size).param("offset", Paged.offset(pageNo, size));
        if (st != null) {
            count = count.param("st", st);
            query = query.param("st", st);
        }
        if (keyword != null) {
            count = count.param("q", keyword);
            query = query.param("q", keyword);
        }
        return new Paged<>(query.query(ProductView.class).list(), pageNo, size, count.query(Long.class).single());
    }

    @GetMapping("/api/seller/products/{id}")
    public ProductDetail product(@PathVariable("id") long id, HttpServletRequest request) {
        Seller seller = auth.requireSeller(request);
        return catalog.detail(own(id, seller));
    }

    @PostMapping("/api/seller/products")
    @Transactional
    public ProductDetail create(@RequestBody ProductRequest body, HttpServletRequest request) {
        Seller seller = auth.requireSeller(request);
        Checked c = check(body, seller, null);
        long id = Db.insert(db.sql("""
                INSERT INTO product (tenant_id, seller_id, category_id, name, description, description_doc, product_type,
                                     price, sale_price, sale_start, sale_end, stock, status, abv, volume_ml, origin, created_at)
                VALUES (:t, :s, :c, :n, :d, :doc, :type, :price, :sale, :ss, :se, :stock, :status, :abv, :vol, :origin, :now)
                """)
                .param("t", tenant.id()).param("s", seller.id()).param("c", c.categoryId).param("n", c.name)
                .param("d", c.description).param("doc", c.doc).param("type", c.type).param("price", c.price)
                .param("sale", c.salePrice).param("ss", c.saleStart).param("se", c.saleEnd).param("stock", c.stock)
                .param("status", c.status).param("abv", c.abv).param("vol", c.volumeMl).param("origin", c.origin)
                .param("now", clock.now()));
        saveMedia(id, c.media);
        return catalog.detail(catalog.find(id));
    }

    @PutMapping("/api/seller/products/{id}")
    @Transactional
    public ProductDetail update(@PathVariable("id") long id, @RequestBody ProductRequest body,
                                HttpServletRequest request) {
        Seller seller = auth.requireSeller(request);
        ProductView existing = own(id, seller);
        Checked c = check(body, seller, existing);
        db.sql("""
                UPDATE product SET category_id = :c, name = :n, description = :d, description_doc = :doc, product_type = :type,
                       price = :price, sale_price = :sale, sale_start = :ss, sale_end = :se, stock = :stock, status = :status,
                       abv = :abv, volume_ml = :vol, origin = :origin
                 WHERE id = :id AND seller_id = :s
                """)
                .param("c", c.categoryId).param("n", c.name).param("d", c.description).param("doc", c.doc)
                .param("type", c.type).param("price", c.price).param("sale", c.salePrice).param("ss", c.saleStart)
                .param("se", c.saleEnd).param("stock", c.stock).param("status", c.status).param("abv", c.abv)
                .param("vol", c.volumeMl).param("origin", c.origin).param("id", id).param("s", seller.id()).update();
        saveMedia(id, c.media);
        return catalog.detail(catalog.find(id));
    }

    /** 목록에서 바로 판매 상태만 바꾼다(판매 시작, 판매 중지, 판매 종료). */
    @PostMapping("/api/seller/products/{id}/status")
    public Map<String, Boolean> changeStatus(@PathVariable("id") long id, @RequestBody StatusRequest body,
                                             HttpServletRequest request) {
        Seller seller = auth.requireSeller(request);
        ProductView p = own(id, seller);
        if (!Text.in(body.status(), SELLER_STATUSES)) {
            throw ApiException.bad("상품 상태 값이 올바르지 않습니다.");
        }
        if ("blocked".equals(p.status())) {
            throw ApiException.forbidden("운영자가 판매를 막은 상품입니다. 운영자에게 문의해 주세요.");
        }
        if ("on_sale".equals(body.status()) && p.alcohol() && !seller.licenseVerified()) {
            throw ApiException.forbidden("주류 면허가 확인되지 않아 술은 판매할 수 없습니다.");
        }
        db.sql("UPDATE product SET status = :st WHERE id = :id AND seller_id = :s")
                .param("st", body.status()).param("id", id).param("s", seller.id()).update();
        return OK;
    }

    private ProductView own(long id, Seller seller) {
        ProductView p = catalog.find(id);
        if (p == null || p.sellerId() != seller.id()) {
            throw ApiException.notFound();
        }
        return p;
    }

    /** 검증을 통과한 상품 입력값 */
    private static final class Checked {
        long categoryId;
        String name;
        String description;
        String doc;
        String type;
        long price;
        Long salePrice;
        LocalDate saleStart;
        LocalDate saleEnd;
        int stock;
        String status;
        BigDecimal abv;
        Integer volumeMl;
        String origin;
        List<MediaRequest> media = new ArrayList<>();
    }

    private Checked check(ProductRequest body, Seller seller, ProductView existing) {
        Category cat = body.categoryId() == null ? null : catalog.category(body.categoryId());
        String name = Text.clean(body.name(), 80);
        Long price = body.price();
        Long sale = body.salePrice();
        Integer stock = body.stock();
        if (cat == null || !cat.active() || name.isEmpty() || price == null || price < 0 || price > 2_000_000_000L
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
        BigDecimal abv = body.abv();
        if (abv != null && (abv.signum() < 0 || abv.compareTo(BigDecimal.valueOf(99)) > 0)) {
            throw ApiException.bad("도수는 0~99 사이로 입력해 주세요.");
        }
        Integer volume = body.volumeMl();
        if (volume != null && (volume < 1 || volume > 1_000_000)) {
            throw ApiException.bad("용량(ml)을 확인해 주세요.");
        }

        Checked c = new Checked();
        c.categoryId = cat.id();
        c.name = name;
        c.doc = ProductDoc.normalize(body.descriptionDoc());
        // 꾸민 설명이 있으면 요약 글은 거기서 뽑는다.
        c.description = c.doc != null ? ProductDoc.plainText(body.descriptionDoc(), 2000) : Text.clean(body.description(), 2000);
        c.type = type;
        c.price = price;
        c.salePrice = sale;
        c.saleStart = Text.date(body.saleStart());
        c.saleEnd = Text.date(body.saleEnd());
        c.stock = stock;
        c.abv = abv;
        c.volumeMl = volume;
        c.origin = Text.clean(body.origin(), 60);

        // 상태: 운영자가 막은 상품은 판매자가 풀 수 없다. 값을 주지 않으면 새 상품은 판매 중, 기존 상품은 그대로.
        if (existing != null && "blocked".equals(existing.status())) {
            c.status = "blocked";
        } else if (body.status() == null || body.status().isBlank()) {
            c.status = existing == null ? "on_sale" : existing.status();
        } else if (Text.in(body.status(), SELLER_STATUSES)) {
            c.status = body.status();
        } else {
            throw ApiException.bad("상품 상태 값이 올바르지 않습니다.");
        }

        if (body.media() != null) {
            if (body.media().size() > MAX_MEDIA) {
                throw ApiException.bad("사진과 동영상은 모두 합쳐 " + MAX_MEDIA + "개까지 넣을 수 있습니다.");
            }
            for (MediaRequest m : body.media()) {
                c.media.add(checkMedia(m, seller));
            }
        }
        return c;
    }

    private MediaRequest checkMedia(MediaRequest m, Seller seller) {
        String url = m == null || m.url() == null ? "" : m.url().trim();
        if (m != null && "youtube".equals(m.kind())) {
            Matcher matcher = YOUTUBE.matcher(url);
            String id = matcher.find() ? matcher.group(1) : url;
            if (!YOUTUBE_ID.matcher(id).matches()) {
                throw ApiException.bad("유튜브 주소를 확인해 주세요.");
            }
            return new MediaRequest("youtube", id);
        }
        // 사진·동영상은 이 판매자가 직접 올린 파일만 쓸 수 있다. 종류는 올릴 때 서버가 판단한 값을 따른다.
        String kind = files.kindOfOwned(url, seller.memberId());
        if (kind == null) {
            throw ApiException.bad("올린 파일을 찾을 수 없습니다. 사진이나 동영상을 다시 올려 주세요.");
        }
        return new MediaRequest(kind, url);
    }

    private void saveMedia(long productId, List<MediaRequest> media) {
        db.sql("DELETE FROM product_media WHERE product_id = :p").param("p", productId).update();
        int order = 0;
        for (MediaRequest m : media) {
            db.sql("INSERT INTO product_media (product_id, kind, url, sort_order) VALUES (:p, :k, :u, :o)")
                    .param("p", productId).param("k", m.kind()).param("u", m.url()).param("o", order++).update();
        }
    }

    // ---- 주문 처리 ----

    @GetMapping("/api/seller/orders")
    public Paged<OrderService.SellerOrder> orders(@RequestParam(name = "status", required = false) String status,
                                                  @RequestParam(name = "page", required = false) Integer page,
                                                  HttpServletRequest request) {
        Seller seller = auth.requireSeller(request);
        String st = Text.in(status, ORDER_STATUSES) ? status : null;
        return orders.listForSeller(seller.id(), st, Paged.page(page), 10);
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
