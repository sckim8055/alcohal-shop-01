package me.nexthub.sool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

import me.nexthub.sool.common.AppClock;
import me.nexthub.sool.order.OrderService;
import me.nexthub.sool.sample.SampleData;
import me.nexthub.sool.support.Api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.JsonNode;

/** 술장터 1단계의 업무 규칙을 API로 확인한다. 테스트마다 기본 시범 데이터(회원·판매자·상품)에서 시작한다. */
@SpringBootTest
class FlowTest {

    static final String MAKGEOLLI = "한울 생막걸리 750ml";
    static final String YAKJU = "한울 약주 500ml";
    static final String NURUK = "전통 누룩 1kg";
    static final String CUP = "백자 술잔 2개 세트";
    static final String LESSON = "막걸리 빚기 하루 수업";
    static final Map<String, String> ADDRESS = Map.of("receiver", "김구매", "phone", "010-0000-0000", "address", "시범시 시범로 1");

    @Autowired
    WebApplicationContext context;
    @Autowired
    SampleData sample;
    @Autowired
    OrderService orders;
    @Autowired
    AppClock clock;
    @Autowired
    JdbcClient db;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        sample.reset();
        sample.loadBase();
    }

    // ---- 도우미 ----

    Api as(String email) {
        return new Api(mvc).login(email);
    }

    void add(Api api, String product, int qty) {
        assertEquals(200, api.post("/api/cart/items", Map.of("productId", sample.productId(product), "qty", qty)).status());
    }

    JsonNode order(Api api, Map<String, String> info) {
        Api.Res r = api.post("/api/orders", info);
        assertEquals(200, r.status(), String.valueOf(r.body()));
        return r.body();
    }

    /** 주문 안에서 해당 가게의 판매자별 주문을 찾는다. */
    JsonNode sub(JsonNode order, String shop) {
        for (JsonNode s : order.get("subs")) {
            if (shop.equals(s.get("sub").get("shopName").asString())) {
                return s;
            }
        }
        throw new AssertionError("판매자별 주문이 없습니다: " + shop);
    }

    long subId(JsonNode order, String shop) {
        return sub(order, shop).get("sub").get("id").asLong();
    }

    String subStatus(Api buyer, long orderId, String shop) {
        return sub(buyer.get("/api/orders/" + orderId).body(), shop).get("sub").get("status").asString();
    }

    int stock(String product) {
        return db.sql("SELECT stock FROM product WHERE name = :n").param("n", product).query(Integer.class).single();
    }

    // ---- 보안 ----

    @Test
    @DisplayName("토큰 없는 변경 요청은 거부된다")
    void csrfTokenRequired() {
        Api api = new Api(mvc).open();
        Api.Res r = api.postWithoutToken("/api/auth/login", Map.of("email", "buyer@test.local", "password", SampleData.PASSWORD));
        assertEquals(403, r.status());
    }

    @Test
    @DisplayName("틀린 비밀번호는 거부되고, 로그인하면 토큰이 새로 발급된다")
    void loginRotatesToken() {
        Api api = new Api(mvc).open();
        String before = api.get("/api/me").body().get("csrfToken").asString();
        assertEquals(401, api.post("/api/auth/login", Map.of("email", "buyer@test.local", "password", "wrong-password")).status());
        assertEquals(401, api.post("/api/auth/login", Map.of("email", "nobody@test.local", "password", "wrong-password")).status());
        Api.Res ok = api.post("/api/auth/login", Map.of("email", "buyer@test.local", "password", SampleData.PASSWORD));
        assertEquals(200, ok.status());
        assertNotEquals(before, ok.body().get("csrfToken").asString());
        assertEquals("김구매", ok.body().get("user").get("name").asString());
        assertFalse(ok.body().toString().contains("pbkdf2"), "비밀번호 해시가 응답에 나오면 안 된다");
    }

    @Test
    @DisplayName("로그인하지 않았거나 권한이 없으면 판매자·운영자 기능을 쓸 수 없다")
    void roleGates() {
        assertEquals(401, new Api(mvc).open().get("/api/cart").status());
        assertEquals(403, as("buyer@test.local").get("/api/admin").status());
        Map<String, Object> product = Map.of("categoryId", 2, "name", "심사 중 상품", "price", 1000, "stock", 1);
        assertEquals(403, as("newseller@test.local").post("/api/seller/products", product).status());
        assertEquals(403, as("buyer@test.local").post("/api/seller/products", product).status());
        assertEquals(200, as("admin@test.local").get("/api/admin").status());
    }

    // ---- 성인인증 ----

    @Test
    @DisplayName("성인인증 전에는 주류 주문이 막히고, 보는 것은 막지 않는다")
    void alcoholNeedsAdultVerification() {
        Api api = as("buyer2@test.local");
        assertEquals(200, api.get("/api/products/" + sample.productId(MAKGEOLLI)).status());
        add(api, MAKGEOLLI, 1);
        assertTrue(api.get("/api/cart").body().get("hasAlcohol").asBoolean());
        assertEquals(403, api.post("/api/orders", ADDRESS).status());
        assertEquals(50, stock(MAKGEOLLI));
    }

    @Test
    @DisplayName("미성년 생년월일은 인증되지 않고, 성인은 인증 후 주류를 주문할 수 있다")
    void adultVerification() {
        Api api = as("buyer2@test.local");
        int year = clock.today().getYear();
        assertEquals(403, api.post("/api/auth/verify-adult", Map.of("birth", (year - 18) + "-01-01")).status());
        assertEquals(403, api.post("/api/auth/verify-adult", Map.of("birth", "잘못된 값")).status());
        assertEquals(200, api.post("/api/auth/verify-adult", Map.of("birth", (year - 19) + "-12-31")).status());
        add(api, MAKGEOLLI, 1);
        order(api, ADDRESS);
    }

    // ---- 주문 ----

    @Test
    @DisplayName("주문하면 판매자별로 나뉘고 금액·재고·수수료율이 맞다")
    void orderSplitsBySeller() {
        Api api = as("buyer@test.local");
        add(api, MAKGEOLLI, 2);
        add(api, NURUK, 1);
        add(api, CUP, 1);
        JsonNode o = order(api, ADDRESS);

        assertEquals(3, o.get("subs").size());
        JsonNode brew = sub(o, "한울 양조장");
        assertEquals(16000, brew.get("sub").get("itemsTotal").asLong());
        assertEquals(4000, brew.get("sub").get("shipFee").asLong());
        assertEquals(100, brew.get("items").get(0).get("commissionRateBp").asInt());   // 전통주 1%
        JsonNode supplies = sub(o, "누룩과 쌀");
        assertEquals(15000, supplies.get("sub").get("total").asLong());
        assertEquals(300, supplies.get("items").get(0).get("commissionRateBp").asInt()); // 기본 3%
        assertEquals(21500, sub(o, "도담 공방").get("sub").get("total").asLong());
        assertEquals(56500, o.get("order").get("total").asLong());

        assertEquals(48, stock(MAKGEOLLI));
        assertEquals(0, api.get("/api/cart").body().get("groups").size());
        assertEquals(1, api.get("/api/orders").body().size());
    }

    @Test
    @DisplayName("남의 주문은 볼 수 없고, 다른 판매자의 주문은 처리할 수 없다")
    void accessControl() {
        Api buyer = as("buyer@test.local");
        add(buyer, MAKGEOLLI, 1);
        JsonNode o = order(buyer, ADDRESS);
        long orderId = o.get("order").get("id").asLong();
        long brewSub = subId(o, "한울 양조장");

        assertEquals(404, as("buyer3@test.local").get("/api/orders/" + orderId).status());
        assertEquals(409, as("buyer3@test.local").post("/api/suborders/" + brewSub + "/cancel").status());
        assertEquals(409, as("cups@test.local").post("/api/seller/suborders/" + brewSub + "/confirm").status());
        assertEquals("awaiting_deposit", subStatus(buyer, orderId, "한울 양조장"));
    }

    @Test
    @DisplayName("입금 신고 → 입금 확인 → 발송 → 배송 완료 → 구매확정")
    void fullFlow() {
        Api buyer = as("buyer@test.local");
        Api seller = as("brewery@test.local");
        add(buyer, MAKGEOLLI, 1);
        JsonNode o = order(buyer, ADDRESS);
        long orderId = o.get("order").get("id").asLong();
        long id = subId(o, "한울 양조장");

        assertEquals(409, seller.post("/api/seller/suborders/" + id + "/ship", Map.of("carrier", "cj", "trackingNo", "1234")).status());
        assertEquals(200, buyer.post("/api/suborders/" + id + "/report", Map.of("depositor", "김구매")).status());
        assertEquals(409, buyer.post("/api/suborders/" + id + "/report", Map.of("depositor", "김구매")).status());
        assertEquals(409, buyer.post("/api/suborders/" + id + "/cancel").status());
        assertEquals(200, seller.post("/api/seller/suborders/" + id + "/confirm", Map.of("cashReceiptApproval", "A-1")).status());
        assertEquals(409, seller.post("/api/seller/suborders/" + id + "/confirm").status());
        assertEquals(400, seller.post("/api/seller/suborders/" + id + "/ship", Map.of("carrier", "없는택배", "trackingNo", "1234")).status());
        assertEquals(200, seller.post("/api/seller/suborders/" + id + "/ship", Map.of("carrier", "cj", "trackingNo", "1234-5678")).status());

        JsonNode shipped = sub(buyer.get("/api/orders/" + orderId).body(), "한울 양조장");
        assertEquals("shipped", shipped.get("sub").get("status").asString());
        assertFalse(shipped.get("tracking").get("delivered").asBoolean());

        clock.advance(Duration.ofMinutes(5));
        orders.sweep();
        assertEquals("delivered", subStatus(buyer, orderId, "한울 양조장"));

        assertEquals(200, buyer.post("/api/suborders/" + id + "/receive").status());
        assertEquals("completed", subStatus(buyer, orderId, "한울 양조장"));
        assertEquals(409, seller.post("/api/seller/suborders/" + id + "/refund").status());
    }

    @Test
    @DisplayName("배송 완료 후 7일이 지나면 자동으로 구매확정된다")
    void autoComplete() {
        Api buyer = as("buyer@test.local");
        Api seller = as("brewery@test.local");
        add(buyer, MAKGEOLLI, 1);
        JsonNode o = order(buyer, ADDRESS);
        long id = subId(o, "한울 양조장");
        seller.post("/api/seller/suborders/" + id + "/confirm");
        seller.post("/api/seller/suborders/" + id + "/ship", Map.of("carrier", "cj", "trackingNo", "1234"));
        clock.advance(Duration.ofMinutes(5));
        orders.sweep();
        clock.advance(Duration.ofDays(8));
        orders.sweep();
        assertEquals("completed", subStatus(buyer, o.get("order").get("id").asLong(), "한울 양조장"));
    }

    @Test
    @DisplayName("판매자가 환불로 기록하면 발송 전 재고가 돌아온다")
    void refundRestoresStock() {
        Api buyer = as("buyer@test.local");
        Api seller = as("brewery@test.local");
        add(buyer, MAKGEOLLI, 3);
        JsonNode o = order(buyer, ADDRESS);
        long id = subId(o, "한울 양조장");
        assertEquals(47, stock(MAKGEOLLI));
        seller.post("/api/seller/suborders/" + id + "/confirm");
        assertEquals(200, seller.post("/api/seller/suborders/" + id + "/refund").status());
        assertEquals(50, stock(MAKGEOLLI));
        assertEquals("refunded", subStatus(buyer, o.get("order").get("id").asLong(), "한울 양조장"));
        assertEquals(409, seller.post("/api/seller/suborders/" + id + "/refund").status());
        assertEquals(50, stock(MAKGEOLLI));
    }

    @Test
    @DisplayName("입금 신고 없이 기한이 지나면 취소되고 재고가 돌아온다")
    void depositDeadline() {
        Api buyer = as("buyer@test.local");
        add(buyer, MAKGEOLLI, 2);
        add(buyer, CUP, 1);
        JsonNode o = order(buyer, ADDRESS);
        long orderId = o.get("order").get("id").asLong();
        buyer.post("/api/suborders/" + subId(o, "도담 공방") + "/report", Map.of("depositor", "김구매"));

        clock.advance(Duration.ofDays(OrderService.DEPOSIT_DAYS).plusHours(1));
        assertEquals(1, orders.sweep());
        assertEquals("cancelled", subStatus(buyer, orderId, "한울 양조장"));
        assertEquals("awaiting_deposit", subStatus(buyer, orderId, "도담 공방"));   // 신고한 주문은 자동 취소하지 않는다
        assertEquals(50, stock(MAKGEOLLI));
    }

    @Test
    @DisplayName("영수증: 통합은 구매자와 운영자만, 판매자별은 해당 판매자도 볼 수 있다")
    void receipts() {
        Api buyer = as("buyer@test.local");
        add(buyer, MAKGEOLLI, 1);
        add(buyer, CUP, 1);
        JsonNode o = order(buyer, ADDRESS);
        long orderId = o.get("order").get("id").asLong();
        long brewSub = subId(o, "한울 양조장");
        String split = "/api/orders/" + orderId + "/receipt/" + brewSub;

        assertEquals(200, buyer.get("/api/orders/" + orderId).status());
        assertEquals(200, as("admin@test.local").get("/api/orders/" + orderId).status());
        assertEquals(404, as("brewery@test.local").get("/api/orders/" + orderId).status());

        Api.Res own = as("brewery@test.local").get(split);
        assertEquals(200, own.status());
        assertEquals(1, own.body().get("subs").size());
        assertEquals(404, as("cups@test.local").get(split).status());
        assertEquals(200, buyer.get(split).status());
    }

    @Test
    @DisplayName("재고 1개를 두 사람이 주문하면 한 사람만 성공하고, 할인가가 적용된다")
    void noOversellAndSalePrice() {
        db.sql("UPDATE product SET stock = 1 WHERE name = :n").param("n", YAKJU).update();
        Api first = as("buyer@test.local");
        Api second = as("buyer3@test.local");
        add(first, YAKJU, 1);
        add(second, YAKJU, 1);

        JsonNode o = order(first, ADDRESS);
        assertEquals(19000, sub(o, "한울 양조장").get("items").get(0).get("unitPrice").asLong());
        assertEquals(409, second.post("/api/orders", ADDRESS).status());
        assertEquals(0, stock(YAKJU));
        assertEquals(1, second.get("/api/cart").body().get("groups").size());   // 실패한 주문의 장바구니는 그대로
    }

    @Test
    @DisplayName("할인 기간이 지나면 정가로 돌아간다")
    void salePeriod() {
        String name = "달빛 복분자주 375ml";
        Api api = as("buyer@test.local");
        long id = sample.productId(name);
        assertEquals(14000, api.get("/api/products/" + id).body().get("currentPrice").asLong());
        clock.advance(Duration.ofDays(11));
        assertEquals(16000, api.get("/api/products/" + id).body().get("currentPrice").asLong());
    }

    @Test
    @DisplayName("판매자 기준 금액 이상이면 무료배송")
    void freeShipping() {
        Api api = as("buyer@test.local");
        add(api, YAKJU, 3);   // 19,000 x 3 = 57,000 ≥ 50,000
        JsonNode cart = api.get("/api/cart").body();
        assertEquals(0, cart.get("groups").get(0).get("shipFee").asLong());
        assertEquals(57000, cart.get("total").asLong());
    }

    @Test
    @DisplayName("수업(이용권)은 주소 없이 주문하고 배송 없이 전달 완료된다")
    void ticketProduct() {
        Api buyer = as("buyer@test.local");
        Api seller = as("supplies@test.local");
        add(buyer, LESSON, 1);
        JsonNode o = order(buyer, Map.of("receiver", "김구매", "phone", "010-0000-0000"));
        JsonNode s = sub(o, "누룩과 쌀");
        long id = s.get("sub").get("id").asLong();
        assertFalse(s.get("sub").get("hasPhysical").asBoolean());
        assertEquals(0, s.get("sub").get("shipFee").asLong());

        seller.post("/api/seller/suborders/" + id + "/confirm");
        assertEquals(409, seller.post("/api/seller/suborders/" + id + "/ship", Map.of("carrier", "cj", "trackingNo", "1")).status());
        assertEquals(200, seller.post("/api/seller/suborders/" + id + "/deliver").status());
        assertEquals("delivered", subStatus(buyer, o.get("order").get("id").asLong(), "누룩과 쌀"));
    }

    @Test
    @DisplayName("실물 상품은 주소가 없으면 주문할 수 없다")
    void addressRequired() {
        Api buyer = as("buyer@test.local");
        add(buyer, NURUK, 1);
        assertEquals(400, buyer.post("/api/orders", Map.of("receiver", "김구매", "phone", "010-0000-0000")).status());
        assertEquals(100, stock(NURUK));
    }

    // ---- 판매자 ----

    @Test
    @DisplayName("주류는 면허가 확인된 판매자만 등록할 수 있다")
    void alcoholLicenseGate() {
        long alcohol = db.sql("SELECT id FROM category WHERE name = '전통주'").query(Long.class).single();
        Map<String, Object> product = Map.of("categoryId", alcohol, "name", "새 술", "price", 10000, "stock", 5);
        assertEquals(403, as("cups@test.local").post("/api/seller/products", product).status());
        Api.Res ok = as("brewery@test.local").post("/api/seller/products", product);
        assertEquals(200, ok.status());
        assertEquals("새 술", ok.body().get("name").asString());

        Map<String, Object> ticket = new HashMap<>(product);
        ticket.put("productType", "ticket");
        assertEquals(400, as("brewery@test.local").post("/api/seller/products", ticket).status());
    }

    @Test
    @DisplayName("입점 신청 → 운영자 승인 → 상품 등록 → 목록에 보인다")
    void sellerOnboarding() {
        Api applicant = as("buyer2@test.local");
        Map<String, String> apply = Map.of("shopName", "새 가게", "bizNo", "000-00-00009");
        assertEquals(200, applicant.post("/api/seller/apply", apply).status());
        assertEquals(409, applicant.post("/api/seller/apply", apply).status());
        long raw = db.sql("SELECT id FROM category WHERE name = '원재료'").query(Long.class).single();
        Map<String, Object> product = Map.of("categoryId", raw, "name", "햇쌀 2kg", "price", 9000, "stock", 3);
        assertEquals(403, applicant.post("/api/seller/products", product).status());

        Api admin = as("admin@test.local");
        long sellerId = sample.sellerId("새 가게");
        assertEquals(200, admin.post("/api/admin/sellers/" + sellerId + "/status", Map.of("status", "approved")).status());
        assertEquals(200, applicant.post("/api/seller/products", product).status());
        assertTrue(applicant.get("/api/products?q=햇쌀").body().toString().contains("햇쌀 2kg"));

        Map<String, Object> settings = Map.of("baseShipFee", 2500, "payLink", "http://example.com/x");
        assertEquals(400, applicant.put("/api/seller/settings", settings).status());   // https 만 허용
        assertEquals(200, applicant.put("/api/seller/settings", Map.of("baseShipFee", 2500, "bankName", "시범은행")).status());
        assertEquals(2500, applicant.get("/api/seller").body().get("seller").get("baseShipFee").asInt());
    }

    @Test
    @DisplayName("남의 상품은 고칠 수 없다")
    void productOwnership() {
        long id = sample.productId(MAKGEOLLI);
        Map<String, Object> change = Map.of("categoryId", 2, "name", "바꿔치기", "price", 1, "stock", 1);
        assertEquals(404, as("cups@test.local").put("/api/seller/products/" + id, change).status());
        assertEquals(8000, as("buyer@test.local").get("/api/products/" + id).body().get("price").asLong());
    }

    // ---- 운영자 ----

    @Test
    @DisplayName("수수료율을 바꿔도 기존 주문은 그대로이고, 청구서 금액이 맞다")
    void commissionAndInvoice() {
        Api buyer = as("buyer@test.local");
        Api seller = as("brewery@test.local");
        Api admin = as("admin@test.local");
        add(buyer, MAKGEOLLI, 2);
        JsonNode first = order(buyer, ADDRESS);
        long firstSub = subId(first, "한울 양조장");

        long alcohol = db.sql("SELECT id FROM category WHERE name = '전통주'").query(Long.class).single();
        assertEquals(400, admin.put("/api/admin/categories/" + alcohol + "/commission", Map.of("ratePercent", 31)).status());
        assertEquals(200, admin.put("/api/admin/categories/" + alcohol + "/commission", Map.of("ratePercent", 5)).status());

        add(buyer, MAKGEOLLI, 1);
        JsonNode second = order(buyer, ADDRESS);
        assertEquals(500, sub(second, "한울 양조장").get("items").get(0).get("commissionRateBp").asInt());
        JsonNode firstAgain = buyer.get("/api/orders/" + first.get("order").get("id").asLong()).body();
        assertEquals(100, sub(firstAgain, "한울 양조장").get("items").get(0).get("commissionRateBp").asInt());

        // 입금 확인된 첫 주문만 청구 대상: 16,000원 x 1% = 160원 (배송비 제외)
        seller.post("/api/seller/suborders/" + firstSub + "/confirm");
        String period = YearMonth.from(clock.today()).toString();
        assertEquals(400, admin.post("/api/admin/invoices/generate", Map.of("period", "2026-13")).status());
        assertEquals(1, admin.post("/api/admin/invoices/generate", Map.of("period", period)).body().get("count").asInt());
        JsonNode invoice = admin.get("/api/admin").body().get("invoices").get(0);
        assertEquals(16000, invoice.get("salesTotal").asLong());
        assertEquals(160, invoice.get("amount").asLong());

        // 납부 완료로 기록한 청구서는 다시 계산하지 않는다
        long invoiceId = invoice.get("id").asLong();
        assertEquals(200, admin.post("/api/admin/invoices/" + invoiceId + "/paid").status());
        seller.post("/api/seller/suborders/" + subId(second, "한울 양조장") + "/confirm");
        admin.post("/api/admin/invoices/generate", Map.of("period", period));
        assertEquals(160, admin.get("/api/admin").body().get("invoices").get(0).get("amount").asLong());

        // 판매자별 수수료율이 카테고리보다 우선한다
        long sellerId = sample.sellerId("한울 양조장");
        admin.put("/api/admin/sellers/" + sellerId + "/commission", Map.of("ratePercent", 0.5));
        add(buyer, MAKGEOLLI, 1);
        assertEquals(50, sub(order(buyer, ADDRESS), "한울 양조장").get("items").get(0).get("commissionRateBp").asInt());
    }

    @Test
    @DisplayName("면허 확인을 취소하면 주류 상품이 숨겨진다")
    void licenseRevokeHidesAlcohol() {
        Api admin = as("admin@test.local");
        assertTrue(admin.get("/api/products").body().toString().contains(MAKGEOLLI));
        long sellerId = sample.sellerId("한울 양조장");
        assertEquals(200, admin.post("/api/admin/sellers/" + sellerId + "/license", Map.of("verified", false)).status());
        assertFalse(admin.get("/api/products").body().toString().contains(MAKGEOLLI));
        assertEquals(404, admin.get("/api/products/" + sample.productId(MAKGEOLLI)).status());
    }

    @Test
    @DisplayName("심사 중이거나 숨긴 상품은 목록과 장바구니에 나오지 않는다")
    void hiddenProducts() {
        Api buyer = as("buyer@test.local");
        assertFalse(buyer.get("/api/products").body().toString().contains("준비 중인 상품"));
        long hidden = sample.productId("준비 중인 상품");
        assertEquals(404, buyer.get("/api/products/" + hidden).status());
        assertEquals(400, buyer.post("/api/cart/items", Map.of("productId", hidden, "qty", 1)).status());
    }

    // ---- 게시판 ----

    @Test
    @DisplayName("게시글은 로그인해야 쓸 수 있고, 내용은 글자 그대로 저장된다")
    void board() {
        Map<String, String> post = Map.of("title", "<script>alert(1)</script>", "body", "<b>굵게</b> 쓴 글");
        assertEquals(401, new Api(mvc).open().post("/api/posts", post).status());
        Api.Res created = as("buyer@test.local").post("/api/posts", post);
        assertEquals(200, created.status());
        assertEquals("<script>alert(1)</script>", created.body().get("title").asString());
        assertEquals(1, new Api(mvc).open().get("/api/posts").body().size());
    }

    @Test
    @DisplayName("시범 데이터(주문·청구서·게시글)가 오류 없이 들어간다")
    void sampleActivityLoads() {
        sample.loadActivity();
        Api admin = as("admin@test.local");
        JsonNode dash = admin.get("/api/admin").body();
        assertTrue(dash.get("recentSubs").size() >= 9);
        assertTrue(dash.get("invoices").size() >= 2);
        assertEquals(5, admin.get("/api/posts").body().size());
        assertEquals(3, as("buyer@test.local").get("/api/orders").body().size());
    }
}
