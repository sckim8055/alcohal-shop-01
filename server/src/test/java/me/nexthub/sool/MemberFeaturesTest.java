package me.nexthub.sool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.nexthub.sool.common.AppClock;
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

/** 찜, 배송지, 다시 담기, 회원 탈퇴, 판매자 정보, 가게 현황의 할 일, 금액 쿠폰 보완(docs/adr/0002 B–G). */
@SpringBootTest
class MemberFeaturesTest {

    static final String NURUK = "전통 누룩 1kg";        // 누룩과 쌀 12,000원, 재고 100
    static final String JAR = "유리 발효병 5L";         // 누룩과 쌀 14,000원
    static final String CUP = "백자 술잔 2개 세트";      // 도담 공방
    static final String LESSON = "막걸리 빚기 하루 수업";
    static final String WINE = "달빛 오미자주 500ml";
    static final String PAUSED = "준비 중인 상품";
    static final Map<String, String> ADDRESS = Map.of("receiver", "김구매", "phone", "010-0000-0000", "address", "시범시 시범로 1");

    @Autowired
    WebApplicationContext context;
    @Autowired
    SampleData sample;
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

    JsonNode order(Api api) {
        Api.Res r = api.post("/api/orders", ADDRESS);
        assertEquals(200, r.status(), String.valueOf(r.body()));
        return r.body();
    }

    long firstSub(JsonNode order) {
        return order.get("subs").get(0).get("sub").get("id").asLong();
    }

    List<Long> ids(JsonNode array) {
        List<Long> result = new ArrayList<>();
        for (JsonNode n : array) {
            result.add(n.asLong());
        }
        return result;
    }

    Map<String, Object> address(String label, String receiver, boolean isDefault) {
        Map<String, Object> b = new HashMap<>();
        b.put("label", label);
        b.put("receiver", receiver);
        b.put("phone", "010-1111-2222");
        b.put("address", "시범시 " + label + "로 1");
        b.put("isDefault", isDefault);
        return b;
    }

    long createAddress(Api api, String label, boolean isDefault) {
        Api.Res r = api.post("/api/my/addresses", address(label, "이단골", isDefault));
        assertEquals(200, r.status(), String.valueOf(r.body()));
        clock.advance(Duration.ofSeconds(1));   // "가장 최근" 순서를 분명히 하려고
        return r.body().get("id").asLong();
    }

    int cartQty(long memberId, String product) {
        return db.sql("SELECT COALESCE(SUM(qty), 0) FROM cart_item WHERE member_id = :m AND product_id = :p")
                .param("m", memberId).param("p", sample.productId(product)).query(Integer.class).single();
    }

    int rows(String sql, long memberId) {
        return db.sql(sql).param("m", memberId).query(Integer.class).single();
    }

    JsonNode todo(Api seller) {
        return seller.get("/api/seller").body().get("todo");
    }

    // ---- 찜 ----

    @Test
    @DisplayName("찜: 두 번 눌러도 하나, 판매 중이 아니면 404, 판매가 끝나도 목록에 남고 풀 수 있다")
    void wishlist() {
        Api buyer = as("buyer@test.local");
        long wine = sample.productId(WINE);
        long nuruk = sample.productId(NURUK);
        assertEquals(401, new Api(mvc).open().get("/api/wishlist/ids").status());
        assertEquals(0, buyer.get("/api/wishlist/ids").body().size());

        Api.Res r = buyer.post("/api/wishlist/" + wine);
        assertEquals(200, r.status());
        assertTrue(r.body().get("wished").asBoolean());
        assertEquals(200, buyer.post("/api/wishlist/" + wine).status());
        assertEquals(List.of(wine), ids(buyer.get("/api/wishlist/ids").body()));
        assertEquals(404, buyer.post("/api/wishlist/" + sample.productId(PAUSED)).status());
        assertEquals(404, buyer.post("/api/wishlist/999999").status());

        clock.advance(Duration.ofSeconds(1));
        buyer.post("/api/wishlist/" + nuruk);
        JsonNode mine = buyer.get("/api/my/wishlist").body();
        assertEquals(2, mine.get("total").asLong());
        assertEquals(24, mine.get("size").asInt());
        assertEquals(nuruk, mine.get("items").get(0).get("id").asLong());   // 최근에 찜한 것 먼저
        assertEquals(List.of(nuruk, wine), ids(buyer.get("/api/wishlist/ids").body()));

        // 판매를 끝내도 찜 목록에는 남고 status 로 알 수 있다
        assertEquals(200, as("supplies@test.local").post("/api/seller/products/" + nuruk + "/status",
                Map.of("status", "ended")).status());
        JsonNode ended = buyer.get("/api/my/wishlist").body().get("items").get(0);
        assertEquals("ended", ended.get("status").asString());

        Api.Res off = buyer.post("/api/wishlist/" + nuruk + "/remove");
        assertEquals(200, off.status());
        assertFalse(off.body().get("wished").asBoolean());
        assertEquals(200, buyer.post("/api/wishlist/" + nuruk + "/remove").status());
        assertEquals(List.of(wine), ids(buyer.get("/api/wishlist/ids").body()));
        assertEquals(0, as("buyer3@test.local").get("/api/my/wishlist").body().get("total").asLong());
    }

    // ---- 배송지 ----

    @Test
    @DisplayName("배송지: 첫 배송지는 기본, 기본은 하나, 기본을 지우면 가장 최근 것이 기본, 남의 배송지는 404")
    void addresses() {
        Api api = as("buyer3@test.local");
        assertEquals(0, api.get("/api/my/addresses").body().size());
        assertEquals(400, api.post("/api/my/addresses", address("빈칸", " ", false)).status());
        Map<String, Object> noAddress = address("주소 없음", "이단골", false);
        noAddress.put("address", "");
        assertEquals(400, api.post("/api/my/addresses", noAddress).status());

        long home = createAddress(api, "집", false);
        JsonNode list = api.get("/api/my/addresses").body();
        assertEquals(1, list.size());
        assertTrue(list.get(0).get("isDefault").asBoolean());   // 첫 배송지는 저절로 기본
        assertEquals("이단골", list.get(0).get("receiver").asString());

        long office = createAddress(api, "회사", false);
        long parents = createAddress(api, "본가", true);
        list = api.get("/api/my/addresses").body();
        assertEquals(parents, list.get(0).get("id").asLong());   // 기본 먼저
        int defaults = 0;
        for (JsonNode a : list) {
            defaults += a.get("isDefault").asBoolean() ? 1 : 0;
        }
        assertEquals(1, defaults);

        // 고치기: 이름과 기본 여부
        Map<String, Object> change = address("새 회사", "이단골", true);
        Api.Res u = api.put("/api/my/addresses/" + office, change);
        assertEquals(200, u.status(), String.valueOf(u.body()));
        assertEquals("새 회사", u.body().get("label").asString());
        assertTrue(u.body().get("isDefault").asBoolean());
        assertEquals(office, api.get("/api/my/addresses").body().get(0).get("id").asLong());

        // 기본으로
        JsonNode afterDefault = api.post("/api/my/addresses/" + home + "/default").body();
        assertEquals(home, afterDefault.get(0).get("id").asLong());
        assertTrue(afterDefault.get(0).get("isDefault").asBoolean());

        // 남의 배송지
        Api other = as("buyer@test.local");
        assertEquals(404, other.put("/api/my/addresses/" + home, change).status());
        assertEquals(404, other.post("/api/my/addresses/" + home + "/default").status());
        assertEquals(404, other.post("/api/my/addresses/" + home + "/delete").status());
        assertEquals(2, other.get("/api/my/addresses").body().size());   // 시범 배송지(집, 회사)
        assertEquals("집", other.get("/api/my/addresses").body().get(0).get("label").asString());

        // 기본 배송지(집)를 지우면 남은 것 가운데 가장 최근에 넣은 본가가 기본이 된다
        JsonNode afterDelete = api.post("/api/my/addresses/" + home + "/delete").body();
        assertEquals(2, afterDelete.size());
        assertEquals(parents, afterDelete.get(0).get("id").asLong());
        assertTrue(afterDelete.get(0).get("isDefault").asBoolean());
        assertEquals(404, api.post("/api/my/addresses/" + home + "/delete").status());

        // 10개까지
        for (int i = 0; i < 8; i++) {
            createAddress(api, "추가" + i, false);
        }
        assertEquals(10, api.get("/api/my/addresses").body().size());
        Api.Res over = api.post("/api/my/addresses", address("열한째", "이단골", false));
        assertEquals(409, over.status());
    }

    // ---- 다시 담기 ----

    @Test
    @DisplayName("다시 담기: 판매 중이고 재고 있는 상품만 같은 수량으로(재고·99개 한도) 담고, 못 담은 이름을 알려 준다")
    void reorder() {
        Api buyer = as("buyer@test.local");
        long me = sample.memberId("buyer@test.local");
        add(buyer, NURUK, 3);
        add(buyer, JAR, 2);
        add(buyer, CUP, 1);
        JsonNode o = order(buyer);
        long orderId = o.get("order").get("id").asLong();
        assertEquals(0, cartQty(me, NURUK));

        db.sql("UPDATE product SET stock = 1 WHERE name = :n").param("n", JAR).update();
        db.sql("UPDATE product SET status = 'paused' WHERE name = :n").param("n", CUP).update();
        Api.Res r = buyer.post("/api/orders/" + orderId + "/reorder");
        assertEquals(200, r.status(), String.valueOf(r.body()));
        assertEquals(2, r.body().get("added").asInt());
        assertEquals(1, r.body().get("skipped").size());
        assertEquals(CUP, r.body().get("skipped").get(0).asString());
        assertEquals(3, cartQty(me, NURUK));
        assertEquals(1, cartQty(me, JAR));     // 재고 한도
        assertEquals(0, cartQty(me, CUP));

        // 이미 담긴 수량과 합쳐도 99개를 넘지 않는다
        db.sql("UPDATE product SET stock = 500 WHERE name = :n").param("n", NURUK).update();
        db.sql("UPDATE cart_item SET qty = 98 WHERE member_id = :m AND product_id = :p")
                .param("m", me).param("p", sample.productId(NURUK)).update();
        buyer.post("/api/orders/" + orderId + "/reorder");
        assertEquals(99, cartQty(me, NURUK));

        // 품절이면 건너뛴다
        db.sql("UPDATE product SET stock = 0 WHERE name = :n").param("n", JAR).update();
        JsonNode again = buyer.post("/api/orders/" + orderId + "/reorder").body();
        assertTrue(again.get("skipped").toString().contains(JAR));

        assertEquals(404, as("buyer3@test.local").post("/api/orders/" + orderId + "/reorder").status());
        assertEquals(404, buyer.post("/api/orders/999999/reorder").status());
    }

    // ---- 탈퇴 ----

    @Test
    @DisplayName("탈퇴: 비밀번호를 확인하고, 이메일·이름을 지우고, 장바구니·찜·배송지·쓰지 않은 쿠폰은 지우되 주문은 남긴다")
    void withdraw() {
        Api api = as("buyer3@test.local");
        Api otherDevice = as("buyer3@test.local");
        long id = sample.memberId("buyer3@test.local");
        // 끝난 주문 하나(구매자 취소), 쿠폰, 찜, 배송지, 장바구니
        add(api, NURUK, 1);
        long sub = firstSub(order(api));
        assertEquals(200, api.post("/api/suborders/" + sub + "/cancel").status());
        long coupon = db.sql("SELECT id FROM coupon WHERE name = '누룩과 쌀 10% 할인'").query(Long.class).single();
        assertEquals(200, api.post("/api/coupons/" + coupon + "/claim").status());
        assertEquals(200, api.post("/api/wishlist/" + sample.productId(WINE)).status());
        createAddress(api, "집", true);
        add(api, JAR, 1);

        assertEquals(400, api.post("/api/me/withdraw", Map.of("password", "wrong-password")).status());
        assertEquals(400, api.post("/api/me/withdraw").status());
        Api.Res r = api.post("/api/me/withdraw", Map.of("password", SampleData.PASSWORD));
        assertEquals(200, r.status(), String.valueOf(r.body()));
        assertTrue(r.body().get("ok").asBoolean());

        assertEquals("withdrawn-" + id + "@deleted.local", db.sql("SELECT email FROM member WHERE id = :m").param("m", id)
                .query(String.class).single());
        assertEquals("탈퇴한 회원", db.sql("SELECT name FROM member WHERE id = :m").param("m", id).query(String.class).single());
        assertEquals("withdrawn", db.sql("SELECT status FROM member WHERE id = :m").param("m", id).query(String.class).single());
        assertEquals(0, rows("SELECT COUNT(*) FROM cart_item WHERE member_id = :m", id));
        assertEquals(0, rows("SELECT COUNT(*) FROM wishlist WHERE member_id = :m", id));
        assertEquals(0, rows("SELECT COUNT(*) FROM member_address WHERE member_id = :m", id));
        assertEquals(0, rows("SELECT COUNT(*) FROM member_coupon WHERE member_id = :m AND status = 'issued'", id));
        assertEquals(1, rows("SELECT COUNT(*) FROM shop_order WHERE buyer_id = :m", id));

        // 이 브라우저도, 다른 기기도 로그아웃되고, 다시 로그인할 수 없다
        assertTrue(otherDevice.get("/api/me").body().get("user").isNull());
        assertEquals(401, otherDevice.get("/api/orders").status());
        Api fresh = new Api(mvc).open();
        assertEquals(401, fresh.post("/api/auth/login", Map.of("email", "buyer3@test.local", "password", SampleData.PASSWORD)).status());
        assertEquals(401, fresh.post("/api/auth/login", Map.of("email", "withdrawn-" + id + "@deleted.local",
                "password", SampleData.PASSWORD)).status());
    }

    @Test
    @DisplayName("탈퇴할 수 없는 때: 끝나지 않은 주문, 진행 중인 신청, 판매자, 운영자")
    void withdrawBlocked() {
        Map<String, String> pw = Map.of("password", SampleData.PASSWORD);
        Api buyer = as("buyer@test.local");
        add(buyer, NURUK, 1);
        order(buyer);
        Api.Res r = buyer.post("/api/me/withdraw", pw);
        assertEquals(409, r.status());
        assertTrue(r.body().get("message").asString().contains("주문"));

        // 구매확정된 주문에 반품 신청이 진행 중이면 막고, 철회하면 탈퇴할 수 있다
        Api buyer2 = as("buyer2@test.local");
        Api seller = as("supplies@test.local");
        add(buyer2, NURUK, 1);
        long sub = firstSub(order(buyer2));
        seller.post("/api/seller/suborders/" + sub + "/confirm");
        seller.post("/api/seller/suborders/" + sub + "/ship", Map.of("carrier", "cj", "trackingNo", "1234"));
        assertEquals(200, buyer2.post("/api/suborders/" + sub + "/receive").status());
        Api.Res c = buyer2.post("/api/suborders/" + sub + "/claims", Map.of("claimType", "return", "reasonCode", "defect"));
        assertEquals(200, c.status(), String.valueOf(c.body()));
        Api.Res blocked = buyer2.post("/api/me/withdraw", pw);
        assertEquals(409, blocked.status());
        assertTrue(blocked.body().get("message").asString().contains("신청"));
        buyer2.post("/api/claims/" + c.body().get("id").asLong() + "/withdraw");
        assertEquals(200, buyer2.post("/api/me/withdraw", pw).status());

        Api.Res s = as("cups@test.local").post("/api/me/withdraw", pw);
        assertEquals(409, s.status());
        assertTrue(s.body().get("message").asString().contains("운영자에게 문의"));
        assertEquals(409, as("admin@test.local").post("/api/me/withdraw", pw).status());
        assertEquals(400, as("admin@test.local").post("/api/me/withdraw", Map.of("password", "nope")).status());
    }

    // ---- 판매자 정보와 가게 현황 ----

    @Test
    @DisplayName("상품 상세에 판매자 정보가 나오고 계좌 정보는 나오지 않으며, 가게 설정에서 고칠 수 있다")
    void sellerInfo() {
        long id = sample.productId(NURUK);
        JsonNode detail = new Api(mvc).open().get("/api/products/" + id).body();
        JsonNode info = detail.get("sellerInfo");
        assertEquals("누룩과 쌀", info.get("shopName").asString());
        assertEquals("최누룩", info.get("ceoName").asString());
        assertEquals("000-00-00003", info.get("bizNo").asString());
        assertEquals("제2026-시범-0004호", info.get("mailOrderNo").asString());
        assertEquals("000-0000-0004", info.get("csPhone").asString());
        assertTrue(info.get("bizAddress").asString().startsWith("시범시"));
        assertFalse(info.get("returnAddress").asString().isEmpty());
        assertEquals(7, info.size());
        String all = detail.toString();
        assertFalse(all.contains("bankAccount") || all.contains("bankName") || all.contains("000-0000-0003"), all);

        Api seller = as("supplies@test.local");
        Map<String, Object> settings = new HashMap<>();
        settings.put("baseShipFee", 3000);
        settings.put("ceoName", "  새 대표  ");
        settings.put("bizAddress", "시범시 새주소 5");
        settings.put("mailOrderNo", "제2026-시범-0099호");
        settings.put("returnAddress", "시범시 반품로 9");
        settings.put("csPhone", "000-1111-2222");
        Api.Res r = seller.put("/api/seller/settings", settings);
        assertEquals(200, r.status(), String.valueOf(r.body()));
        assertEquals("새 대표", r.body().get("ceoName").asString());
        assertEquals("시범시 반품로 9", r.body().get("returnAddress").asString());
        assertEquals("000-1111-2222", seller.get("/api/me").body().get("seller").get("csPhone").asString());
        assertEquals("제2026-시범-0099호", new Api(mvc).open().get("/api/products/" + id).body()
                .get("sellerInfo").get("mailOrderNo").asString());
    }

    @Test
    @DisplayName("가게 현황의 할 일: 답할 신청, 승인한 반품·교환, 품절 상품, 발송이 늦은 주문")
    void dashboardTodo() {
        Api seller = as("supplies@test.local");
        JsonNode t = todo(seller);
        assertEquals(0, t.get("claimsRequested").asLong());
        assertEquals(0, t.get("returnsApproved").asLong());
        assertEquals(1, t.get("soldOut").asLong());   // 쌀누룩(판매 중, 재고 0). 임시저장 당도계는 세지 않는다
        assertEquals(0, t.get("shipLate").asLong());

        Api buyer = as("buyer@test.local");
        add(buyer, NURUK, 1);
        long late = firstSub(order(buyer));
        seller.post("/api/seller/suborders/" + late + "/confirm");
        add(buyer, LESSON, 1);
        long lesson = firstSub(buyer.post("/api/orders", Map.of("receiver", "김구매", "phone", "010-0000-0000")).body());
        seller.post("/api/seller/suborders/" + lesson + "/confirm");
        assertEquals(0, todo(seller).get("shipLate").asLong());
        clock.advance(Duration.ofDays(4));
        assertEquals(1, todo(seller).get("shipLate").asLong());   // 배송이 없는 수업은 세지 않는다

        buyer.post("/api/suborders/" + late + "/claims", Map.of("claimType", "cancel", "reasonCode", "late"));
        assertEquals(1, todo(seller).get("claimsRequested").asLong());

        add(buyer, NURUK, 1);
        long ret = firstSub(order(buyer));
        seller.post("/api/seller/suborders/" + ret + "/confirm");
        seller.post("/api/seller/suborders/" + ret + "/ship", Map.of("carrier", "cj", "trackingNo", "1234"));
        long claim = buyer.post("/api/suborders/" + ret + "/claims", Map.of("claimType", "return", "reasonCode", "defect"))
                .body().get("id").asLong();
        assertEquals(2, todo(seller).get("claimsRequested").asLong());
        seller.post("/api/seller/claims/" + claim + "/approve");
        t = todo(seller);
        assertEquals(1, t.get("claimsRequested").asLong());
        assertEquals(1, t.get("returnsApproved").asLong());
        assertEquals(0, todo(as("cups@test.local")).get("claimsRequested").asLong());
        assertEquals(2, as("admin@test.local").get("/api/admin/summary").body().get("claimsOpen").asLong());
    }

    // ---- 쿠폰 보완 ----

    @Test
    @DisplayName("금액 쿠폰은 최소 주문 금액이 할인 금액보다 작으면 만들 수 없다")
    void amountCouponNeedsMinOrder() {
        Api admin = as("admin@test.local");
        Map<String, Object> b = new HashMap<>();
        b.put("name", "3,000원 할인");
        b.put("discountType", "amount");
        b.put("discountValue", 3000);
        b.put("minOrder", 2999);
        b.put("issueType", "download");
        b.put("validFrom", clock.today().toString());
        b.put("validTo", clock.today().plusDays(30).toString());
        Api.Res r = admin.post("/api/admin/coupons", b);
        assertEquals(400, r.status());
        assertEquals("최소 주문 금액은 할인 금액 이상이어야 합니다.", r.body().get("message").asString());
        b.put("minOrder", 3000);
        assertEquals(200, admin.post("/api/admin/coupons", b).status());
        b.put("name", "5% 할인");
        b.put("discountType", "percent");
        b.put("discountValue", 5);
        b.put("minOrder", 0);
        assertEquals(200, admin.post("/api/admin/coupons", b).status());   // 비율 할인은 이 규칙과 상관없다
    }
}
