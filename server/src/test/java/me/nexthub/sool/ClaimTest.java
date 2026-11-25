package me.nexthub.sool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
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

/** 취소·반품·교환 신청(docs/adr/0002 A): 신청할 수 있는 때, 처리 순서, 환불 연결, 구매확정 막기, 알림. */
@SpringBootTest
class ClaimTest {

    static final String NURUK = "전통 누룩 1kg";            // 누룩과 쌀 12,000원, 재고 100
    static final String LESSON = "막걸리 빚기 하루 수업";     // 누룩과 쌀, 이용권
    static final String SHOP = "누룩과 쌀";
    static final String NURUK_10 = "누룩과 쌀 10% 할인";
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
    Api buyer;
    Api seller;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        sample.reset();
        sample.loadBase();
        buyer = as("buyer@test.local");
        seller = as("supplies@test.local");
    }

    // ---- 도우미 ----

    Api as(String email) {
        return new Api(mvc).login(email);
    }

    /** 누룩과 쌀에서 상품 하나를 주문하고 판매자별 주문 번호를 돌려준다. */
    long orderOne(String product, int qty) {
        assertEquals(200, buyer.post("/api/cart/items", Map.of("productId", sample.productId(product), "qty", qty)).status());
        Api.Res r = buyer.post("/api/orders", ADDRESS);
        assertEquals(200, r.status(), String.valueOf(r.body()));
        return r.body().get("subs").get(0).get("sub").get("id").asLong();
    }

    long orderId(long subId) {
        return db.sql("SELECT order_id FROM sub_order WHERE id = :id").param("id", subId).query(Long.class).single();
    }

    long paid(long subId) {
        assertEquals(200, seller.post("/api/seller/suborders/" + subId + "/confirm").status());
        return subId;
    }

    long shipped(long subId) {
        paid(subId);
        Api.Res r = seller.post("/api/seller/suborders/" + subId + "/ship", Map.of("carrier", "cj", "trackingNo", "1234"));
        assertEquals(200, r.status(), String.valueOf(r.body()));
        return subId;
    }

    Api.Res claim(Api api, long subId, String type, String reason, String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("claimType", type);
        body.put("reasonCode", reason);
        body.put("reasonText", text);
        return api.post("/api/suborders/" + subId + "/claims", body);
    }

    long okClaim(long subId, String type) {
        Api.Res r = claim(buyer, subId, type, "changed_mind", "");
        assertEquals(200, r.status(), String.valueOf(r.body()));
        assertEquals("requested", r.body().get("status").asString());
        return r.body().get("id").asLong();
    }

    String subStatus(long subId) {
        return db.sql("SELECT status FROM sub_order WHERE id = :id").param("id", subId).query(String.class).single();
    }

    String claimStatus(long claimId) {
        return db.sql("SELECT status FROM order_claim WHERE id = :id").param("id", claimId).query(String.class).single();
    }

    int stock(String product) {
        return db.sql("SELECT stock FROM product WHERE name = :n").param("n", product).query(Integer.class).single();
    }

    int notes(String email, String titlePart) {
        return db.sql("SELECT COUNT(*) FROM notification WHERE member_id = :m AND title LIKE :t")
                .param("m", sample.memberId(email)).param("t", "%" + titlePart + "%").query(Integer.class).single();
    }

    int mails(String email, String subjectPart) {
        return db.sql("SELECT COUNT(*) FROM message_outbox WHERE member_id = :m AND subject LIKE :t")
                .param("m", sample.memberId(email)).param("t", "%" + subjectPart + "%").query(Integer.class).single();
    }

    JsonNode subInOrder(long subId) {
        for (JsonNode s : buyer.get("/api/orders/" + orderId(subId)).body().get("subs")) {
            if (s.get("sub").get("id").asLong() == subId) {
                return s;
            }
        }
        throw new AssertionError("판매자별 주문이 없습니다");
    }

    // ---- 취소 ----

    @Test
    @DisplayName("취소: 입금 신고 전에는 신청할 수 없고, 신고 후 신청해 승인하면 바로 환불되어 재고와 쿠폰이 돌아온다")
    void cancelApproveRefunds() {
        long coupon = buyer.post("/api/coupons/" + db.sql("SELECT id FROM coupon WHERE name = :n").param("n", NURUK_10)
                .query(Long.class).single() + "/claim").body().get("id").asLong();
        assertEquals(200, buyer.post("/api/cart/items", Map.of("productId", sample.productId(NURUK), "qty", 2)).status());
        Map<String, Object> body = new HashMap<>(ADDRESS);
        body.put("coupons", List.of(Map.of("sellerId", sample.sellerId(SHOP), "memberCouponId", coupon)));
        Api.Res o = buyer.post("/api/orders", body);
        assertEquals(200, o.status(), String.valueOf(o.body()));
        long sub = o.body().get("subs").get(0).get("sub").get("id").asLong();
        assertEquals(98, stock(NURUK));

        // 입금 신고 전: 기존 "주문 취소"를 쓰므로 신청은 409, 반품·교환도 아직 안 된다
        assertEquals(409, claim(buyer, sub, "cancel", "changed_mind", "").status());
        assertEquals(409, claim(buyer, sub, "return", "changed_mind", "").status());
        assertEquals(200, buyer.post("/api/suborders/" + sub + "/report", Map.of("depositor", "김구매")).status());
        assertEquals(409, claim(buyer, sub, "exchange", "changed_mind", "").status());

        Api.Res r = claim(buyer, sub, "cancel", "changed_mind", "  잘못 골랐습니다  ");
        assertEquals(200, r.status(), String.valueOf(r.body()));
        JsonNode c = r.body();
        long id = c.get("id").asLong();
        assertEquals("cancel", c.get("claimType").asString());
        assertEquals("잘못 골랐습니다", c.get("reasonText").asString());
        assertEquals(SHOP, c.get("shopName").asString());
        assertEquals("김구매", c.get("buyerName").asString());
        assertEquals(NURUK + " × 2", c.get("items").asString());
        assertEquals(24000 + 3000 - 2400, c.get("total").asLong());
        assertEquals(sub, c.get("subOrderId").asLong());
        assertEquals(orderId(sub), c.get("orderId").asLong());
        assertTrue(c.get("sellerNote").isNull());
        assertTrue(c.get("answeredAt").isNull());
        assertEquals(1, notes("supplies@test.local", "취소 신청이 들어왔습니다"));
        assertEquals(1, mails("supplies@test.local", "취소 신청이 들어왔습니다"));

        // 진행 중인 신청은 하나만
        assertEquals(409, claim(buyer, sub, "cancel", "changed_mind", "").status());
        // 남의 주문에는 신청할 수 없고, 다른 가게는 처리할 수 없다
        assertEquals(404, claim(as("buyer3@test.local"), sub, "cancel", "changed_mind", "").status());
        assertEquals(404, as("cups@test.local").post("/api/seller/claims/" + id + "/approve", Map.of("sellerNote", "")).status());
        assertEquals(404, as("buyer3@test.local").post("/api/claims/" + id + "/withdraw").status());

        Api.Res a = seller.post("/api/seller/claims/" + id + "/approve", Map.of("sellerNote", "오늘 돌려드렸습니다"));
        assertEquals(200, a.status(), String.valueOf(a.body()));
        assertEquals("done", a.body().get("status").asString());
        assertEquals("오늘 돌려드렸습니다", a.body().get("sellerNote").asString());
        assertTrue(!a.body().get("answeredAt").isNull() && !a.body().get("doneAt").isNull());
        assertEquals("refunded", subStatus(sub));
        assertEquals(100, stock(NURUK));
        assertEquals("issued", db.sql("SELECT status FROM member_coupon WHERE id = :id").param("id", coupon)
                .query(String.class).single());
        assertEquals(1, notes("buyer@test.local", "취소 신청이 승인되었습니다"));
        assertEquals(1, mails("buyer@test.local", "취소 신청이 승인되었습니다"));
        assertEquals(409, seller.post("/api/seller/claims/" + id + "/approve").status());

        // 주문 상세에 가장 최근 신청이 붙는다
        JsonNode s = subInOrder(sub);
        assertEquals(id, s.get("claim").get("id").asLong());
        assertEquals("done", s.get("claim").get("status").asString());
    }

    @Test
    @DisplayName("취소: 입금 확인 후에도 신청할 수 있고, 철회는 답하기 전에만, 거절에는 사유가 필요하다")
    void withdrawAndReject() {
        long sub = paid(orderOne(NURUK, 1));
        assertTrue(subInOrder(sub).get("claim").isNull());
        long first = okClaim(sub, "cancel");
        Api.Res w = buyer.post("/api/claims/" + first + "/withdraw");
        assertEquals(200, w.status());
        assertEquals("withdrawn", w.body().get("status").asString());
        assertEquals(409, buyer.post("/api/claims/" + first + "/withdraw").status());
        assertEquals(1, notes("supplies@test.local", "철회"));
        assertEquals(409, seller.post("/api/seller/claims/" + first + "/approve").status());

        // 철회한 뒤에는 다시 신청할 수 있다
        long second = okClaim(sub, "cancel");
        assertEquals(400, seller.post("/api/seller/claims/" + second + "/reject", Map.of("sellerNote", "  ")).status());
        assertEquals(400, seller.post("/api/seller/claims/" + second + "/reject").status());
        Api.Res rj = seller.post("/api/seller/claims/" + second + "/reject", Map.of("sellerNote", "이미 포장을 마쳤습니다"));
        assertEquals(200, rj.status());
        assertEquals("rejected", rj.body().get("status").asString());
        assertEquals("이미 포장을 마쳤습니다", rj.body().get("sellerNote").asString());
        assertEquals(409, buyer.post("/api/claims/" + second + "/withdraw").status());
        assertEquals(409, seller.post("/api/seller/claims/" + second + "/approve").status());
        assertEquals(1, mails("buyer@test.local", "거절"));
        assertEquals("paid", subStatus(sub));
        assertEquals(second, subInOrder(sub).get("claim").get("id").asLong());

        // 보낸 뒤에는 취소가 아니라 반품으로
        seller.post("/api/seller/suborders/" + sub + "/ship", Map.of("carrier", "cj", "trackingNo", "1234"));
        assertEquals(409, claim(buyer, sub, "cancel", "changed_mind", "").status());
    }

    @Test
    @DisplayName("입력 확인: 종류·사유 값, 기타 사유의 설명, 500자 제한, 로그인")
    void inputChecks() {
        long sub = shipped(orderOne(NURUK, 1));
        assertEquals(400, claim(buyer, sub, "refund", "changed_mind", "").status());
        assertEquals(400, claim(buyer, sub, "return", "bored", "").status());
        assertEquals(400, claim(buyer, sub, "return", null, "").status());
        assertEquals(400, claim(buyer, sub, "return", "other", " ").status());
        assertEquals(400, claim(buyer, sub, "return", "defect", "가".repeat(501)).status());
        assertEquals(200, claim(buyer, sub, "return", "other", "가".repeat(500)).status());
        assertEquals(404, claim(buyer, 999_999, "return", "defect", "").status());
        assertEquals(401, new Api(mvc).open().post("/api/suborders/" + sub + "/claims",
                Map.of("claimType", "return", "reasonCode", "defect")).status());
    }

    // ---- 반품·교환 ----

    @Test
    @DisplayName("반품: 승인하면 반품 주소를 알리고, 받은 뒤 완료하면 환불로 기록된다")
    void returnFlow() {
        long sub = shipped(orderOne(NURUK, 2));
        long id = okClaim(sub, "return");
        assertEquals(409, seller.post("/api/seller/claims/" + id + "/complete").status());   // 승인 전에는 완료할 수 없다
        Api.Res a = seller.post("/api/seller/claims/" + id + "/approve");
        assertEquals(200, a.status(), String.valueOf(a.body()));
        assertEquals("approved", a.body().get("status").asString());
        assertTrue(a.body().get("doneAt").isNull());
        assertEquals("shipped", subStatus(sub));
        String body = db.sql("SELECT body FROM notification WHERE member_id = :m AND title LIKE '%반품 신청이 승인%'")
                .param("m", sample.memberId("buyer@test.local")).query(String.class).single();
        assertTrue(body.contains("시범시 누룩읍 재료로 4"), body);
        assertEquals(409, buyer.post("/api/claims/" + id + "/withdraw").status());
        assertEquals(409, seller.post("/api/seller/claims/" + id + "/reject", Map.of("sellerNote", "안 됩니다")).status());
        // 진행 중이므로 새 신청은 받지 않는다
        assertEquals(409, claim(buyer, sub, "exchange", "defect", "").status());

        Api.Res done = seller.post("/api/seller/claims/" + id + "/complete");
        assertEquals(200, done.status(), String.valueOf(done.body()));
        assertEquals("done", done.body().get("status").asString());
        assertEquals("refunded", subStatus(sub));
        assertEquals(98, stock(NURUK));   // 보낸 뒤의 환불은 재고를 되돌리지 않는다(기존 환불 규칙)
        assertEquals(1, mails("buyer@test.local", "반품이 끝났습니다"));
        assertEquals(409, seller.post("/api/seller/claims/" + id + "/complete").status());
    }

    @Test
    @DisplayName("교환: 완료에는 올바른 택배사와 송장번호가 필요하고, 판매자별 주문 상태는 그대로다")
    void exchangeFlow() {
        long sub = shipped(orderOne(NURUK, 1));
        clock.advance(Duration.ofMinutes(5));
        orders.sweep();
        assertEquals("delivered", subStatus(sub));
        long id = okClaim(sub, "exchange");
        assertEquals(200, seller.post("/api/seller/claims/" + id + "/approve", Map.of("sellerNote", "새 상품 보내 드립니다")).status());
        assertEquals(400, seller.post("/api/seller/claims/" + id + "/complete").status());
        assertEquals(400, seller.post("/api/seller/claims/" + id + "/complete", Map.of("carrier", "없는택배", "trackingNo", "1")).status());
        assertEquals(400, seller.post("/api/seller/claims/" + id + "/complete", Map.of("carrier", "cj", "trackingNo", "  ")).status());
        assertEquals("approved", claimStatus(id));
        Api.Res done = seller.post("/api/seller/claims/" + id + "/complete", Map.of("carrier", "hanjin", "trackingNo", "9999-0000"));
        assertEquals(200, done.status(), String.valueOf(done.body()));
        assertEquals("done", done.body().get("status").asString());
        assertEquals("hanjin", done.body().get("carrier").asString());
        assertEquals("9999-0000", done.body().get("trackingNo").asString());
        assertEquals("delivered", subStatus(sub));
        assertEquals(1, notes("buyer@test.local", "교환이 끝났습니다"));
        // 끝난 뒤에는 구매확정할 수 있다
        assertEquals(200, buyer.post("/api/suborders/" + sub + "/receive").status());
    }

    @Test
    @DisplayName("배송이 없는 주문(수업)은 반품·교환을 신청할 수 없고 취소만 된다")
    void ticketOnlyCancel() {
        long sub = paid(orderOne(LESSON, 1));
        assertEquals(200, seller.post("/api/seller/suborders/" + sub + "/deliver").status());
        assertEquals(409, claim(buyer, sub, "return", "changed_mind", "").status());
        assertEquals(409, claim(buyer, sub, "exchange", "changed_mind", "").status());
        assertEquals(409, claim(buyer, sub, "cancel", "changed_mind", "").status());   // 전달이 끝나면 취소도 안 된다

        long other = paid(orderOne(LESSON, 1));
        assertEquals(409, claim(buyer, other, "return", "changed_mind", "").status());
        long id = okClaim(other, "cancel");
        assertEquals(200, seller.post("/api/seller/claims/" + id + "/approve").status());
        assertEquals("refunded", subStatus(other));
    }

    @Test
    @DisplayName("구매확정 뒤 7일 안에만 반품·교환을 신청할 수 있고, 확정된 주문의 반품도 완료하면 환불된다")
    void completedWindow() {
        long sub = shipped(orderOne(NURUK, 1));
        assertEquals(200, buyer.post("/api/suborders/" + sub + "/receive").status());
        clock.advance(Duration.ofDays(6));
        long id = okClaim(sub, "return");
        assertEquals(200, seller.post("/api/seller/claims/" + id + "/approve").status());
        Api.Res done = seller.post("/api/seller/claims/" + id + "/complete");
        assertEquals(200, done.status(), String.valueOf(done.body()));
        assertEquals("refunded", subStatus(sub));

        long late = shipped(orderOne(NURUK, 1));
        assertEquals(200, buyer.post("/api/suborders/" + late + "/receive").status());
        clock.advance(Duration.ofDays(6));
        long w = okClaim(late, "exchange");
        assertEquals(200, buyer.post("/api/claims/" + w + "/withdraw").status());
        clock.advance(Duration.ofDays(2));
        assertEquals(409, claim(buyer, late, "return", "changed_mind", "").status());
        assertEquals(409, claim(buyer, late, "exchange", "changed_mind", "").status());
        // 일반 환불 단추는 구매확정된 주문을 환불하지 못한다(기존 규칙 유지)
        assertEquals(409, seller.post("/api/seller/suborders/" + late + "/refund").status());
    }

    @Test
    @DisplayName("진행 중인 신청이 있으면 구매확정과 7일 자동 구매확정이 막힌다")
    void openClaimBlocksCompletion() {
        long sub = shipped(orderOne(NURUK, 1));
        long id = okClaim(sub, "exchange");
        Api.Res r = buyer.post("/api/suborders/" + sub + "/receive");
        assertEquals(409, r.status());
        assertTrue(r.body().get("message").asString().contains("신청"));
        assertEquals(200, buyer.post("/api/claims/" + id + "/withdraw").status());
        assertEquals(200, buyer.post("/api/suborders/" + sub + "/receive").status());

        long auto = shipped(orderOne(NURUK, 1));
        clock.advance(Duration.ofMinutes(5));
        orders.sweep();
        assertEquals("delivered", subStatus(auto));
        long open = okClaim(auto, "return");
        assertEquals(200, seller.post("/api/seller/claims/" + open + "/approve").status());   // approved 도 진행 중
        clock.advance(Duration.ofDays(8));
        orders.sweep();
        assertEquals("delivered", subStatus(auto));
        // 판매자가 반품을 받지 않고 바로 환불하면 남은 신청은 닫힌다
        assertEquals(200, seller.post("/api/seller/suborders/" + auto + "/refund").status());
        assertEquals("done", claimStatus(open));

        long third = shipped(orderOne(NURUK, 1));
        clock.advance(Duration.ofMinutes(5));
        orders.sweep();
        long rejected = okClaim(third, "return");
        clock.advance(Duration.ofDays(8));
        orders.sweep();
        assertEquals("delivered", subStatus(third));
        assertEquals(200, seller.post("/api/seller/claims/" + rejected + "/reject", Map.of("sellerNote", "사용한 흔적이 있습니다")).status());
        orders.sweep();
        assertEquals("completed", subStatus(third));
    }

    // ---- 목록 ----

    @Test
    @DisplayName("목록: 내 신청, 가게별 신청(상태 거르기), 운영자 전체 보기, 판매자 주문 목록의 신청 표시")
    void lists() {
        long a = okClaim(shipped(orderOne(NURUK, 1)), "return");
        long b = okClaim(paid(orderOne(NURUK, 1)), "cancel");
        long cSub = paid(orderOne(NURUK, 1));
        long c = okClaim(cSub, "cancel");
        buyer.post("/api/claims/" + c + "/withdraw");
        seller.post("/api/seller/claims/" + b + "/reject", Map.of("sellerNote", "안 됩니다"));

        JsonNode mine = buyer.get("/api/my/claims").body();
        assertEquals(3, mine.get("total").asLong());
        assertEquals(10, mine.get("size").asInt());
        assertEquals(c, mine.get("items").get(0).get("id").asLong());   // 최근 것 먼저
        assertEquals(0, as("buyer3@test.local").get("/api/my/claims").body().get("total").asLong());

        assertEquals(3, seller.get("/api/seller/claims").body().get("total").asLong());
        JsonNode requested = seller.get("/api/seller/claims?status=requested").body();
        assertEquals(1, requested.get("total").asLong());
        assertEquals(a, requested.get("items").get(0).get("id").asLong());
        assertEquals(2, seller.get("/api/seller/claims?status=rejected,withdrawn").body().get("total").asLong());
        assertEquals(400, seller.get("/api/seller/claims?status=nope").status());
        assertEquals(0, as("cups@test.local").get("/api/seller/claims").body().get("total").asLong());
        assertEquals(403, buyer.get("/api/seller/claims").status());

        Api admin = as("admin@test.local");
        assertEquals(3, admin.get("/api/admin/claims").body().get("total").asLong());
        assertEquals(1, admin.get("/api/admin/claims?status=withdrawn").body().get("total").asLong());
        assertEquals(403, buyer.get("/api/admin/claims").status());
        assertEquals(1, admin.get("/api/admin/summary").body().get("claimsOpen").asLong());

        boolean found = false;
        for (JsonNode so : seller.get("/api/seller/orders").body().get("items")) {
            if (so.get("sub").get("sub").get("id").asLong() == cSub) {
                assertEquals("withdrawn", so.get("sub").get("claim").get("status").asString());
                found = true;
            }
        }
        assertTrue(found);
    }

    @Test
    @DisplayName("시범 데이터에 취소 신청과 교환 신청이 하나씩 들어간다")
    void sampleClaims() {
        sample.loadActivity();
        Api admin = as("admin@test.local");
        JsonNode all = admin.get("/api/admin/claims?status=requested").body();
        assertEquals(2, all.get("total").asLong());
        assertEquals(2, admin.get("/api/admin/summary").body().get("claimsOpen").asLong());
        assertEquals(1, seller.get("/api/seller/claims").body().get("total").asLong());
        assertEquals("exchange", buyer.get("/api/my/claims").body().get("items").get(0).get("claimType").asString());
    }
}
