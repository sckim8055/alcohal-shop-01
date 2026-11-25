package me.nexthub.sool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

/** 쿠폰: 받기, 할인 계산, 주문에 쓰기, 취소·환불 때 돌려주기, 수수료 청구서 반영. */
@SpringBootTest
class CouponTest {

    static final String SIGNUP = "가입 축하 3,000원";
    static final String FREE = "첫 장보기 무료배송";
    static final String NURUK_10 = "누룩과 쌀 10% 할인";
    static final String DODAM_2000 = "도담 공방 2,000원";

    static final String MAKGEOLLI = "한울 생막걸리 750ml";
    static final String NURUK = "전통 누룩 1kg";        // 누룩과 쌀 12,000원
    static final String RICE = "술빚기용 찹쌀 4kg";      // 누룩과 쌀 21,000원
    static final String FERMENTER = "발효조 20L";       // 누룩과 쌀 35,000원
    static final String JUG = "분청 주병";              // 도담 공방 35,000원

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

    Api register(String email) {
        Api api = new Api(mvc).open();
        Api.Res r = api.post("/api/auth/register", Map.of("email", email, "name", "새손님", "password", "password1"));
        assertEquals(200, r.status(), String.valueOf(r.body()));
        return api;
    }

    void add(Api api, String product, int qty) {
        assertEquals(200, api.post("/api/cart/items", Map.of("productId", sample.productId(product), "qty", qty)).status());
    }

    long couponId(String name) {
        return db.sql("SELECT id FROM coupon WHERE name = :n").param("n", name).query(Long.class).single();
    }

    long shop(String name) {
        return sample.sellerId(name);
    }

    /** 내 쿠폰(쓸 수 있는 것)에서 이름으로 찾은 회원 쿠폰 id */
    long held(Api api, String name) {
        for (JsonNode mc : api.get("/api/my/coupons?status=usable").body().get("items")) {
            if (name.equals(mc.get("coupon").get("name").asString())) {
                return mc.get("id").asLong();
            }
        }
        throw new AssertionError("쿠폰이 없습니다: " + name);
    }

    long claim(Api api, String name) {
        Api.Res r = api.post("/api/coupons/" + couponId(name) + "/claim");
        assertEquals(200, r.status(), String.valueOf(r.body()));
        return r.body().get("id").asLong();
    }

    Map<String, Object> pick(long sellerId, long memberCouponId) {
        return Map.of("sellerId", sellerId, "memberCouponId", memberCouponId);
    }

    Api.Res order(Api api, Map<?, ?>... coupons) {
        Map<String, Object> body = new HashMap<>();
        body.put("receiver", "김구매");
        body.put("phone", "010-0000-0000");
        body.put("address", "시범시 시범로 1");
        body.put("coupons", List.of(coupons));
        return api.post("/api/orders", body);
    }

    JsonNode okOrder(Api api, Map<?, ?>... coupons) {
        Api.Res r = order(api, coupons);
        assertEquals(200, r.status(), String.valueOf(r.body()));
        return r.body();
    }

    JsonNode sub(JsonNode order, String shop) {
        for (JsonNode s : order.get("subs")) {
            if (shop.equals(s.get("sub").get("shopName").asString())) {
                return s;
            }
        }
        throw new AssertionError("가게 없음: " + shop);
    }

    long itemDiscountSum(JsonNode sub) {
        long sum = 0;
        for (JsonNode i : sub.get("items")) {
            sum += i.get("couponDiscount").asLong();
        }
        return sum;
    }

    JsonNode checkoutGroup(Api api, String shop) {
        for (JsonNode g : api.get("/api/checkout/coupons").body()) {
            if (shop.equals(g.get("shopName").asString())) {
                return g;
            }
        }
        throw new AssertionError("가게 없음: " + shop);
    }

    List<String> optionNames(JsonNode group) {
        List<String> names = new ArrayList<>();
        for (JsonNode o : group.get("options")) {
            names.add(o.get("name").asString());
        }
        return names;
    }

    String heldStatus(long memberCouponId) {
        return db.sql("SELECT status FROM member_coupon WHERE id = :id").param("id", memberCouponId)
                .query(String.class).single();
    }

    Map<String, Object> couponBody(String name, String discountType, int value, String issueType) {
        Map<String, Object> b = new HashMap<>();
        b.put("name", name);
        b.put("discountType", discountType);
        b.put("discountValue", value);
        b.put("minOrder", 0);
        b.put("issueType", issueType);
        b.put("validFrom", clock.today().toString());
        b.put("validTo", clock.today().plusDays(30).toString());
        return b;
    }

    // ---- 받기 ----

    @Test
    @DisplayName("가입하면 가입 쿠폰과 알림을 받고, 주문서에서 바로 쓸 수 있다")
    void signupCoupon() {
        Api fresh = register("fresh@test.local");
        JsonNode mine = fresh.get("/api/my/coupons").body();
        assertEquals(1, mine.get("total").asLong());
        JsonNode mc = mine.get("items").get(0);
        assertEquals(SIGNUP, mc.get("coupon").get("name").asString());
        assertEquals("issued", mc.get("status").asString());
        assertFalse(mc.get("expired").asBoolean());
        assertTrue(mc.get("coupon").get("code").isNull());
        assertEquals(1, fresh.get("/api/me").body().get("unreadCount").asInt());
        assertEquals("/my/coupons", fresh.get("/api/notifications").body().get("items").get(0).get("link").asString());

        add(fresh, NURUK, 2);   // 24,000원 + 배송비 3,000원
        JsonNode group = checkoutGroup(fresh, "누룩과 쌀");
        assertEquals(1, group.get("options").size());
        JsonNode option = group.get("options").get(0);
        assertEquals(3000, option.get("discount").asLong());
        assertEquals(0, option.get("shipDiscount").asLong());
        assertEquals("platform", option.get("issuer").asString());

        JsonNode o = okOrder(fresh, pick(shop("누룩과 쌀"), option.get("memberCouponId").asLong()));
        JsonNode s = sub(o, "누룩과 쌀");
        assertEquals(24000, o.get("order").get("total").asLong());
        assertEquals(24000, s.get("sub").get("total").asLong());
        assertEquals(3000, s.get("sub").get("itemDiscount").asLong());
        assertEquals(SIGNUP, s.get("sub").get("couponName").asString());
        assertEquals("platform", s.get("sub").get("couponIssuer").asString());
        assertEquals(3000, itemDiscountSum(s));
        assertEquals(0, fresh.get("/api/my/coupons?status=usable").body().get("total").asLong());
        assertEquals(1, fresh.get("/api/my/coupons?status=used").body().get("total").asLong());
    }

    @Test
    @DisplayName("쿠폰 받기: 두 번 받으면 409, 발급 수가 다 차면 409, 비로그인도 목록은 본다")
    void downloadClaim() {
        Api buyer = as("buyer@test.local");
        JsonNode anon = new Api(mvc).open().get("/api/coupons").body();
        assertEquals(2, anon.size());
        for (JsonNode l : anon) {
            assertFalse(l.get("owned").asBoolean());
            assertTrue(l.get("coupon").get("code").isNull());
        }

        claim(buyer, NURUK_10);
        assertEquals(409, buyer.post("/api/coupons/" + couponId(NURUK_10) + "/claim").status());
        boolean owned = false;
        for (JsonNode l : buyer.get("/api/coupons").body()) {
            if (NURUK_10.equals(l.get("coupon").get("name").asString())) {
                owned = l.get("owned").asBoolean();
                assertEquals(1, l.get("coupon").get("issuedCount").asInt());
            }
        }
        assertTrue(owned);

        db.sql("UPDATE coupon SET total_limit = 1 WHERE name = :n").param("n", NURUK_10).update();
        Api other = as("buyer3@test.local");
        assertEquals(409, other.post("/api/coupons/" + couponId(NURUK_10) + "/claim").status());
        for (JsonNode l : other.get("/api/coupons").body()) {
            if (NURUK_10.equals(l.get("coupon").get("name").asString())) {
                assertTrue(l.get("soldOut").asBoolean());
            }
        }
        // 코드 쿠폰은 받기 화면으로 받을 수 없다
        assertEquals(404, other.post("/api/coupons/" + couponId(FREE) + "/claim").status());
        assertEquals(401, new Api(mvc).open().post("/api/coupons/" + couponId(DODAM_2000) + "/claim").status());
    }

    @Test
    @DisplayName("코드 입력: 대소문자와 앞뒤 공백을 가리지 않고, 없는 코드는 404, 코드는 구매자에게 보이지 않는다")
    void redeemCode() {
        Api buyer = as("buyer@test.local");
        Api.Res r = buyer.post("/api/coupons/redeem", Map.of("code", "  sool-free "));
        assertEquals(200, r.status(), String.valueOf(r.body()));
        assertEquals(FREE, r.body().get("coupon").get("name").asString());
        assertTrue(r.body().get("coupon").get("code").isNull());
        assertEquals(409, buyer.post("/api/coupons/redeem", Map.of("code", "SOOL-FREE")).status());
        assertEquals(404, buyer.post("/api/coupons/redeem", Map.of("code", "NOPE-1234")).status());
        assertFalse(buyer.get("/api/my/coupons").body().toString().contains("SOOL-FREE"));

        // 만든 사람(운영자)에게는 보인다
        assertTrue(as("admin@test.local").get("/api/admin/coupons?issuer=platform").body().toString().contains("SOOL-FREE"));
    }

    @Test
    @DisplayName("멈춘 쿠폰은 받을 수 없고 목록에서도 빠진다, 끝낸 쿠폰은 되돌릴 수 없다")
    void pausedCoupon() {
        Api seller = as("supplies@test.local");
        Api buyer = as("buyer@test.local");
        long id = couponId(NURUK_10);
        assertEquals(200, seller.post("/api/seller/coupons/" + id + "/status", Map.of("status", "paused")).status());
        assertEquals(409, buyer.post("/api/coupons/" + id + "/claim").status());
        assertEquals(1, buyer.get("/api/coupons").body().size());

        assertEquals(200, seller.post("/api/seller/coupons/" + id + "/status", Map.of("status", "active")).status());
        claim(buyer, NURUK_10);
        assertEquals(200, seller.post("/api/seller/coupons/" + id + "/status", Map.of("status", "ended")).status());
        assertEquals(409, seller.post("/api/seller/coupons/" + id + "/status", Map.of("status", "active")).status());
        assertEquals(400, seller.post("/api/seller/coupons/" + id + "/status", Map.of("status", "gone")).status());

        // 끝난 쿠폰은 가지고 있어도 주문에 쓸 수 없다
        add(buyer, NURUK, 2);
        assertEquals(0, checkoutGroup(buyer, "누룩과 쌀").get("options").size());
        assertEquals(400, order(buyer, pick(shop("누룩과 쌀"), held(buyer, NURUK_10))).status());
    }

    // ---- 계산 ----

    @Test
    @DisplayName("비율 할인: 최소 금액 미만이면 쓸 수 없고, 상한을 넘지 않으며, 줄마다 나눈 할인의 합이 정확하다")
    void percentDiscount() {
        Api buyer = as("buyer@test.local");
        long mc = claim(buyer, NURUK_10);
        long supplies = shop("누룩과 쌀");

        add(buyer, NURUK, 1);   // 12,000원 < 최소 15,000원
        assertEquals(0, checkoutGroup(buyer, "누룩과 쌀").get("options").size());
        assertEquals(400, order(buyer, pick(supplies, mc)).status());

        add(buyer, FERMENTER, 2);   // 12,000 + 70,000 = 82,000원 → 10% = 8,200원, 상한 5,000원
        JsonNode option = checkoutGroup(buyer, "누룩과 쌀").get("options").get(0);
        assertEquals(5000, option.get("discount").asLong());

        JsonNode o = okOrder(buyer, pick(supplies, mc));
        JsonNode s = sub(o, "누룩과 쌀");
        assertEquals(5000, s.get("sub").get("itemDiscount").asLong());
        assertEquals(5000, itemDiscountSum(s));
        // 12,000 × 5,000 / 82,000 = 731.7 → 731, 나머지는 마지막 줄(발효조)에
        assertEquals(731, s.get("items").get(0).get("couponDiscount").asLong());
        assertEquals(4269, s.get("items").get(1).get("couponDiscount").asLong());
        assertEquals(82000 + 3000 - 5000, s.get("sub").get("total").asLong());
        assertEquals(80000, o.get("order").get("total").asLong());
        assertEquals("seller", s.get("sub").get("couponIssuer").asString());
    }

    @Test
    @DisplayName("금액 할인과 무료배송: 할인액과 합계가 맞고, 배송비가 없으면 무료배송 쿠폰은 나오지 않는다")
    void amountAndFreeShipping() {
        Api buyer = as("buyer@test.local");
        long dodam = claim(buyer, DODAM_2000);
        long free = buyer.post("/api/coupons/redeem", Map.of("code", "SOOL-FREE")).body().get("id").asLong();

        add(buyer, JUG, 1);         // 도담 공방 35,000원 + 배송비 3,500원
        add(buyer, MAKGEOLLI, 2);   // 한울 양조장 16,000원 + 배송비 4,000원 (술)
        JsonNode cupsGroup = checkoutGroup(buyer, "도담 공방");
        assertTrue(optionNames(cupsGroup).contains(DODAM_2000));
        assertTrue(optionNames(cupsGroup).contains(FREE));
        JsonNode brewGroup = checkoutGroup(buyer, "한울 양조장");
        assertEquals(List.of(FREE), optionNames(brewGroup));   // 판매자 쿠폰은 그 가게에서만, 무료배송 쿠폰은 술 제외가 아니다
        assertEquals(4000, brewGroup.get("options").get(0).get("shipDiscount").asLong());
        assertEquals(0, brewGroup.get("options").get(0).get("discount").asLong());

        JsonNode o = okOrder(buyer, pick(shop("도담 공방"), dodam), pick(shop("한울 양조장"), free));
        JsonNode cups = sub(o, "도담 공방");
        assertEquals(2000, cups.get("sub").get("itemDiscount").asLong());
        assertEquals(36500, cups.get("sub").get("total").asLong());
        assertEquals(2000, cups.get("items").get(0).get("couponDiscount").asLong());
        JsonNode brew = sub(o, "한울 양조장");
        assertEquals(4000, brew.get("sub").get("shipDiscount").asLong());
        assertEquals(0, brew.get("sub").get("itemDiscount").asLong());
        assertEquals(4000, brew.get("sub").get("shipFee").asLong());
        assertEquals(16000, brew.get("sub").get("total").asLong());
        assertEquals(0, itemDiscountSum(brew));
        assertEquals(36500 + 16000, o.get("order").get("total").asLong());

        // 배송비가 0원인 묶음(도담 공방 40,000원 이상)에는 무료배송 쿠폰이 나오지 않는다
        Api other = as("buyer3@test.local");
        other.post("/api/coupons/redeem", Map.of("code", "sool-free"));
        add(other, JUG, 2);
        assertEquals(0, checkoutGroup(other, "도담 공방").get("options").size());
    }

    @Test
    @DisplayName("분류 쿠폰: 대분류를 고르면 그 아래 중분류 상품만 대상이고 할인도 그 줄에만 나눈다")
    void categoryScope() {
        Api admin = as("admin@test.local");
        long raw = db.sql("SELECT id FROM category WHERE name = '원재료' AND parent_id IS NULL").query(Long.class).single();
        Map<String, Object> body = couponBody("원재료 10%", "percent", 10, "download");
        body.put("categoryId", raw);
        Api.Res created = admin.post("/api/admin/coupons", body);
        assertEquals(200, created.status(), String.valueOf(created.body()));
        assertEquals("원재료", created.body().get("categoryName").asString());

        Api buyer = as("buyer@test.local");
        long mc = claim(buyer, "원재료 10%");
        add(buyer, NURUK, 1);       // 원재료 > 누룩·효모 12,000원
        add(buyer, FERMENTER, 1);   // 양조 기구 > 발효 용기 35,000원 (대상 아님)
        JsonNode s = sub(okOrder(buyer, pick(shop("누룩과 쌀"), mc)), "누룩과 쌀");
        assertEquals(1200, s.get("sub").get("itemDiscount").asLong());
        assertEquals(1200, s.get("items").get(0).get("couponDiscount").asLong());
        assertEquals(0, s.get("items").get(1).get("couponDiscount").asLong());
    }

    @Test
    @DisplayName("술 제외 쿠폰은 술만 담긴 묶음에 쓸 수 없다")
    void alcoholExcluded() {
        Api fresh = register("fresh2@test.local");
        db.sql("UPDATE member SET adult_verified_at = created_at WHERE email = 'fresh2@test.local'").update();
        add(fresh, MAKGEOLLI, 3);   // 24,000원이지만 모두 술이라 대상 금액 0
        assertEquals(0, checkoutGroup(fresh, "한울 양조장").get("options").size());
        Api.Res r = order(fresh, pick(shop("한울 양조장"), held(fresh, SIGNUP)));
        assertEquals(400, r.status());
        assertEquals("issued", heldStatus(held(fresh, SIGNUP)));
        // 실패한 주문은 재고를 줄이지 않는다
        assertEquals(50, db.sql("SELECT stock FROM product WHERE name = :n").param("n", MAKGEOLLI)
                .query(Integer.class).single());
    }

    @Test
    @DisplayName("판매자 쿠폰은 그 가게에만, 같은 쿠폰을 두 가게에, 쓴 쿠폰을 다시 쓸 수 없다")
    void usageRules() {
        Api buyer = as("buyer@test.local");
        long dodam = claim(buyer, DODAM_2000);
        long free = buyer.post("/api/coupons/redeem", Map.of("code", "SOOL-FREE")).body().get("id").asLong();
        add(buyer, JUG, 1);
        add(buyer, FERMENTER, 1);

        assertEquals(400, order(buyer, pick(shop("누룩과 쌀"), dodam)).status());
        assertEquals(400, order(buyer, pick(shop("도담 공방"), free), pick(shop("누룩과 쌀"), free)).status());
        assertEquals(400, order(buyer, pick(shop("한울 양조장"), free)).status());   // 장바구니에 없는 가게
        Api other = as("buyer3@test.local");
        add(other, JUG, 1);
        assertEquals(400, order(other, pick(shop("도담 공방"), dodam)).status());   // 남의 쿠폰

        okOrder(buyer, pick(shop("누룩과 쌀"), free));
        assertEquals("used", heldStatus(free));
        add(buyer, FERMENTER, 1);
        assertEquals(400, order(buyer, pick(shop("누룩과 쌀"), free)).status());
        // 쿠폰 없이 주문하면 예전과 같다
        JsonNode plain = okOrder(buyer);
        assertEquals(35000 + 3000, plain.get("order").get("total").asLong());
        assertEquals(0, sub(plain, "누룩과 쌀").get("sub").get("itemDiscount").asLong());
        assertTrue(sub(plain, "누룩과 쌀").get("sub").get("couponName").isNull());
    }

    @Test
    @DisplayName("기한이 지난 쿠폰은 쓸 수 없고 기한 지난 쿠폰 목록으로 간다")
    void expiredCoupon() {
        Api fresh = register("late@test.local");
        long mc = held(fresh, SIGNUP);
        clock.advance(Duration.ofDays(31));
        assertEquals(0, fresh.get("/api/my/coupons?status=usable").body().get("total").asLong());
        JsonNode expired = fresh.get("/api/my/coupons?status=expired").body();
        assertEquals(1, expired.get("total").asLong());
        assertTrue(expired.get("items").get(0).get("expired").asBoolean());
        add(fresh, NURUK, 2);
        assertEquals(0, checkoutGroup(fresh, "누룩과 쌀").get("options").size());
        assertEquals(400, order(fresh, pick(shop("누룩과 쌀"), mc)).status());
        assertEquals(400, fresh.get("/api/my/coupons?status=nope").status());
    }

    // ---- 돌려주기 ----

    @Test
    @DisplayName("구매자 취소, 입금 기한 자동 취소, 환불하면 쿠폰을 돌려준다")
    void restoreCoupon() {
        Api buyer = as("buyer@test.local");
        Api seller = as("supplies@test.local");
        long free = buyer.post("/api/coupons/redeem", Map.of("code", "SOOL-FREE")).body().get("id").asLong();
        long supplies = shop("누룩과 쌀");

        // 1) 구매자 취소
        add(buyer, FERMENTER, 1);
        JsonNode o1 = okOrder(buyer, pick(supplies, free));
        assertEquals("used", heldStatus(free));
        assertEquals(200, buyer.post("/api/suborders/" + sub(o1, "누룩과 쌀").get("sub").get("id").asLong() + "/cancel").status());
        assertEquals("issued", heldStatus(free));
        assertEquals(1, db.sql("SELECT COUNT(*) FROM member_coupon WHERE id = :id AND used_at IS NULL AND sub_order_id IS NULL")
                .param("id", free).query(Integer.class).single());

        // 2) 입금 기한 경과
        add(buyer, FERMENTER, 1);
        okOrder(buyer, pick(supplies, free));
        assertEquals("used", heldStatus(free));
        clock.advance(Duration.ofDays(OrderService.DEPOSIT_DAYS).plusHours(1));
        assertEquals(1, orders.sweep());
        assertEquals("issued", heldStatus(free));
        assertEquals(1, buyer.get("/api/my/coupons?status=usable").body().get("total").asLong());

        // 3) 입금 확인 후 환불
        add(buyer, FERMENTER, 1);
        JsonNode o3 = okOrder(buyer, pick(supplies, free));
        long sub3 = sub(o3, "누룩과 쌀").get("sub").get("id").asLong();
        assertEquals(200, seller.post("/api/seller/suborders/" + sub3 + "/confirm").status());
        assertEquals(200, seller.post("/api/seller/suborders/" + sub3 + "/refund").status());
        assertEquals("issued", heldStatus(free));

        // 돌려받은 쿠폰으로 다시 주문할 수 있다
        add(buyer, FERMENTER, 1);
        okOrder(buyer, pick(supplies, free));
        assertEquals("used", heldStatus(free));
    }

    // ---- 판매자·운영자 ----

    @Test
    @DisplayName("판매자는 받기·코드 쿠폰만 만들고, 술 제외가 고정되며, 남의 쿠폰은 건드릴 수 없다")
    void sellerCoupons() {
        Api seller = as("cups@test.local");
        Api buyer = as("buyer@test.local");
        Map<String, Object> signup = couponBody("가입 쿠폰", "amount", 1000, "signup");
        signup.put("minOrder", 1000);
        assertEquals(400, seller.post("/api/seller/coupons", signup).status());
        Map<String, Object> platform = couponBody("플랫폼인 척", "amount", 1000, "download");
        platform.put("issuer", "platform");
        assertEquals(403, seller.post("/api/seller/coupons", platform).status());
        assertEquals(403, buyer.post("/api/seller/coupons", couponBody("구매자", "amount", 1000, "download")).status());

        Map<String, Object> code = couponBody("공방 코드 쿠폰", "percent", 5, "code");
        code.put("code", "dodam-5");
        code.put("excludeAlcohol", false);
        Api.Res r = seller.post("/api/seller/coupons", code);
        assertEquals(200, r.status(), String.valueOf(r.body()));
        assertEquals("seller", r.body().get("issuer").asString());
        assertEquals("DODAM-5", r.body().get("code").asString());
        assertTrue(r.body().get("excludeAlcohol").asBoolean());
        assertEquals(shop("도담 공방"), r.body().get("sellerId").asLong());

        Map<String, Object> dup = couponBody("겹치는 코드", "amount", 1000, "code");
        dup.put("code", "Sool-Free");
        dup.put("minOrder", 1000);
        assertEquals(409, seller.post("/api/seller/coupons", dup).status());
        assertEquals(400, seller.post("/api/seller/coupons", couponBody("", "amount", 1000, "download")).status());
        assertEquals(400, seller.post("/api/seller/coupons", couponBody("너무 큼", "percent", 91, "download")).status());
        assertEquals(400, seller.post("/api/seller/coupons", couponBody("너무 작음", "amount", 99, "download")).status());
        Map<String, Object> badDates = couponBody("거꾸로", "amount", 1000, "download");
        badDates.put("validTo", clock.today().minusDays(1).toString());
        badDates.put("minOrder", 1000);
        assertEquals(400, seller.post("/api/seller/coupons", badDates).status());
        Map<String, Object> noCode = couponBody("코드 없음", "amount", 1000, "code");
        noCode.put("minOrder", 1000);
        assertEquals(400, seller.post("/api/seller/coupons", noCode).status());

        JsonNode list = seller.get("/api/seller/coupons").body();
        assertEquals(2, list.get("total").asLong());   // 시범 쿠폰 + 새 쿠폰
        assertEquals(404, seller.post("/api/seller/coupons/" + couponId(NURUK_10) + "/status", Map.of("status", "paused")).status());
        assertEquals(404, seller.post("/api/seller/coupons/" + couponId(FREE) + "/status", Map.of("status", "paused")).status());

        // 코드로 받은 판매자 쿠폰도 구매자에게는 코드가 보이지 않는다
        Api.Res got = buyer.post("/api/coupons/redeem", Map.of("code", "DODAM-5"));
        assertEquals(200, got.status());
        assertTrue(got.body().get("coupon").get("code").isNull());
    }

    @Test
    @DisplayName("운영자: 플랫폼 쿠폰 만들기, 판매자 쿠폰 멈추기, 회원에게 직접 지급")
    void adminCoupons() {
        Api admin = as("admin@test.local");
        assertEquals(403, as("buyer@test.local").get("/api/admin/coupons").status());
        assertEquals(4, admin.get("/api/admin/coupons").body().get("total").asLong());
        assertEquals(2, admin.get("/api/admin/coupons?issuer=seller").body().get("total").asLong());
        assertEquals(400, admin.get("/api/admin/coupons?issuer=nobody").status());

        Map<String, Object> body = couponBody("단골 감사 1,000원", "amount", 1000, "grant");
        body.put("minOrder", 1000);
        body.put("excludeAlcohol", false);
        Api.Res r = admin.post("/api/admin/coupons", body);
        assertEquals(200, r.status(), String.valueOf(r.body()));
        assertEquals("platform", r.body().get("issuer").asString());
        assertFalse(r.body().get("excludeAlcohol").asBoolean());
        long id = r.body().get("id").asLong();

        Api.Res g = admin.post("/api/admin/coupons/" + id + "/grant", Map.of("email", "buyer2@test.local"));
        assertEquals(200, g.status(), String.valueOf(g.body()));
        assertEquals("issued", g.body().get("status").asString());
        assertEquals(409, admin.post("/api/admin/coupons/" + id + "/grant", Map.of("email", "buyer2@test.local")).status());
        assertEquals(404, admin.post("/api/admin/coupons/" + id + "/grant", Map.of("email", "nobody@test.local")).status());
        assertEquals(400, admin.post("/api/admin/coupons/" + couponId(FREE) + "/grant", Map.of("email", "buyer2@test.local")).status());

        Api buyer2 = as("buyer2@test.local");
        assertEquals("단골 감사 1,000원", buyer2.get("/api/my/coupons").body().get("items").get(0).get("coupon").get("name").asString());
        assertEquals(1, buyer2.get("/api/me").body().get("unreadCount").asInt());

        assertEquals(200, admin.post("/api/admin/coupons/" + couponId(DODAM_2000) + "/status", Map.of("status", "paused")).status());
        assertEquals(1, buyer2.get("/api/coupons").body().size());
    }

    // ---- 청구서 ----

    @Test
    @DisplayName("청구서: 판매자 쿠폰 할인은 수수료 기준에서 빼고, 플랫폼 쿠폰 할인은 차감액으로 따로 보여 준다")
    void invoiceWithCoupons() {
        Api admin = as("admin@test.local");
        // 판매자 부담: 누룩 12,000 + 찹쌀 21,000 = 33,000원, 10% = 3,300원 (1,200 / 2,100)
        Api buyer = as("buyer@test.local");
        long mc = claim(buyer, NURUK_10);
        add(buyer, NURUK, 1);
        add(buyer, RICE, 1);
        JsonNode o1 = okOrder(buyer, pick(shop("누룩과 쌀"), mc));
        assertEquals(1200, sub(o1, "누룩과 쌀").get("items").get(0).get("couponDiscount").asLong());
        as("supplies@test.local").post("/api/seller/suborders/" + sub(o1, "누룩과 쌀").get("sub").get("id").asLong() + "/confirm");

        // 플랫폼 부담: 분청 주병 35,000원에 가입 쿠폰 3,000원
        Api fresh = register("payer@test.local");
        add(fresh, JUG, 1);
        JsonNode o2 = okOrder(fresh, pick(shop("도담 공방"), held(fresh, SIGNUP)));
        as("cups@test.local").post("/api/seller/suborders/" + sub(o2, "도담 공방").get("sub").get("id").asLong() + "/confirm");

        String period = YearMonth.from(clock.today()).toString();
        assertEquals(200, admin.post("/api/admin/invoices/generate", Map.of("period", period)).status());
        JsonNode supplies = null;
        JsonNode cups = null;
        for (JsonNode i : admin.get("/api/admin").body().get("invoices")) {
            if ("누룩과 쌀".equals(i.get("shopName").asString())) {
                supplies = i;
            }
            if ("도담 공방".equals(i.get("shopName").asString())) {
                cups = i;
            }
        }
        // (12,000 − 1,200) × 3% + (21,000 − 2,100) × 3% = 324 + 567
        assertEquals(33000, supplies.get("salesTotal").asLong());
        assertEquals(891, supplies.get("amount").asLong());
        assertEquals(0, supplies.get("platformCouponCredit").asLong());
        assertEquals(891, supplies.get("net").asLong());
        // 35,000 × 3% = 1,050 (플랫폼 쿠폰은 빼지 않음), 차감 3,000 → 플랫폼이 1,950원을 보낸다
        assertEquals(1050, cups.get("amount").asLong());
        assertEquals(3000, cups.get("platformCouponCredit").asLong());
        assertEquals(-1950, cups.get("net").asLong());
        assertEquals(3000, db.sql("SELECT platform_coupon_credit FROM commission_invoice WHERE id = :id")
                .param("id", cups.get("id").asLong()).query(Long.class).single());

        // 판매자 화면에도 같은 값이 나온다
        JsonNode mine = as("cups@test.local").get("/api/seller").body().get("invoices").get(0);
        assertEquals(-1950, mine.get("net").asLong());
    }
}
