package me.nexthub.sool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

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

/** 문의(docs/adr/0003 B): 세 종류, 비밀글을 누가 볼 수 있는지, 답변 권한, 지우기, 알림, 답 안 한 문의 수. */
@SpringBootTest
class InquiryTest {

    static final String NURUK = "전통 누룩 1kg";      // 누룩과 쌀
    static final String SHOP = "누룩과 쌀";
    static final Map<String, String> ADDRESS = Map.of("receiver", "김구매", "phone", "010-0000-0000", "address", "시범시 시범로 1");

    @Autowired
    WebApplicationContext context;
    @Autowired
    SampleData sample;
    @Autowired
    JdbcClient db;

    MockMvc mvc;
    Api buyer;
    Api seller;
    Api admin;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        sample.reset();
        sample.loadBase();
        buyer = as("buyer@test.local");
        seller = as("supplies@test.local");
        admin = as("admin@test.local");
    }

    // ---- 도우미 ----

    Api as(String email) {
        return new Api(mvc).login(email);
    }

    Api.Res ask(Api who, String target, Long productId, Long subOrderId, String title, String body, Boolean secret) {
        Map<String, Object> m = new HashMap<>();
        m.put("target", target);
        m.put("productId", productId);
        m.put("subOrderId", subOrderId);
        m.put("title", title);
        m.put("body", body);
        m.put("secret", secret);
        return who.post("/api/inquiries", m);
    }

    long productInquiry(Api who, boolean secret) {
        Api.Res r = ask(who, "seller", sample.productId(NURUK), null, "얼마나 쓰나요?", "쌀 몇 킬로그램에 쓰면 되나요?", secret);
        assertEquals(200, r.status(), String.valueOf(r.body()));
        return r.body().get("id").asLong();
    }

    long adminInquiry(Api who) {
        Api.Res r = ask(who, "admin", null, null, "탈퇴 문의", "판매자 탈퇴는 어떻게 하나요?", false);
        assertEquals(200, r.status(), String.valueOf(r.body()));
        return r.body().get("id").asLong();
    }

    long orderSub(Api who) {
        assertEquals(200, who.post("/api/cart/items", Map.of("productId", sample.productId(NURUK), "qty", 1)).status());
        Api.Res r = who.post("/api/orders", ADDRESS);
        assertEquals(200, r.status(), String.valueOf(r.body()));
        return r.body().get("subs").get(0).get("sub").get("id").asLong();
    }

    JsonNode productList(Api who) {
        return who.get("/api/products/" + sample.productId(NURUK) + "/inquiries").body();
    }

    int notes(String email, String titlePart) {
        return db.sql("SELECT COUNT(*) FROM notification WHERE member_id = :m AND title LIKE :t")
                .param("m", sample.memberId(email)).param("t", "%" + titlePart + "%").query(Integer.class).single();
    }

    int mails(String email, String subjectPart) {
        return db.sql("SELECT COUNT(*) FROM message_outbox WHERE member_id = :m AND subject LIKE :t")
                .param("m", sample.memberId(email)).param("t", "%" + subjectPart + "%").query(Integer.class).single();
    }

    // ---- 종류와 입력 ----

    @Test
    @DisplayName("세 종류: 상품 문의(공개·비밀 선택), 주문 문의(늘 비밀), 1:1 문의(늘 비밀)")
    void kinds() {
        long product = sample.productId(NURUK);
        Api.Res p = ask(buyer, "seller", product, null, "  얼마나 쓰나요?  ", "  쌀 몇 킬로그램에 쓰면 되나요?  ", null);
        assertEquals(200, p.status(), String.valueOf(p.body()));
        JsonNode pi = p.body();
        assertEquals("seller", pi.get("target").asString());
        assertEquals("product", pi.get("kind").asString());
        assertEquals(product, pi.get("productId").asLong());
        assertEquals(NURUK, pi.get("productName").asString());
        assertEquals(SHOP, pi.get("shopName").asString());
        assertEquals("얼마나 쓰나요?", pi.get("title").asString());
        assertEquals("쌀 몇 킬로그램에 쓰면 되나요?", pi.get("body").asString());
        assertFalse(pi.get("secret").asBoolean());
        assertEquals("open", pi.get("status").asString());
        assertTrue(pi.get("mine").asBoolean());
        assertEquals("김구매", pi.get("authorName").asString());
        assertTrue(pi.get("subOrderId").isNull());
        assertTrue(pi.get("answer").isNull());
        assertEquals(1, notes("supplies@test.local", "새 문의"));

        long sub = orderSub(buyer);
        Api.Res o = ask(buyer, "seller", null, sub, "언제 보내 주시나요?", "이번 주 안에 받을 수 있을까요?", false);
        assertEquals(200, o.status(), String.valueOf(o.body()));
        assertEquals("order", o.body().get("kind").asString());
        assertTrue(o.body().get("secret").asBoolean());          // false 로 보내도 비밀
        assertEquals(sub, o.body().get("subOrderId").asLong());
        assertFalse(o.body().get("orderNo").isNull());
        assertFalse(o.body().get("orderId").isNull());
        assertEquals(SHOP, o.body().get("shopName").asString());
        assertEquals(2, notes("supplies@test.local", "새 문의"));

        Api.Res a = ask(buyer, "admin", product, null, "환불 문의", "판매자가 답이 없습니다.", false);
        assertEquals(200, a.status());
        assertEquals("admin", a.body().get("kind").asString());
        assertTrue(a.body().get("secret").asBoolean());
        assertTrue(a.body().get("productId").isNull());           // 1:1 문의는 상품과 묶지 않는다
        assertTrue(a.body().get("shopName").isNull());

        JsonNode mine = buyer.get("/api/my/inquiries").body();
        assertEquals(3, mine.get("total").asLong());
        assertEquals(10, mine.get("size").asInt());
        assertEquals("admin", mine.get("items").get(0).get("kind").asString());
    }

    @Test
    @DisplayName("입력 검증: 받는 곳, 제목 2~100자, 본문 5~2000자, 상품·주문 중 하나, 로그인 필요")
    void validation() {
        long product = sample.productId(NURUK);
        assertEquals(400, ask(buyer, null, product, null, "제목입니다", "본문입니다요", false).status());
        assertEquals(400, ask(buyer, "nobody", product, null, "제목입니다", "본문입니다요", false).status());
        assertEquals(400, ask(buyer, "seller", product, null, "제", "본문입니다요", false).status());
        assertEquals(400, ask(buyer, "seller", product, null, "가".repeat(101), "본문입니다요", false).status());
        assertEquals(400, ask(buyer, "seller", product, null, "제목입니다", "넷글자임", false).status());
        assertEquals(400, ask(buyer, "seller", product, null, "제목입니다", "가".repeat(2001), false).status());
        assertEquals(400, ask(buyer, "seller", null, null, "제목입니다", "본문입니다요", false).status());
        long sub = orderSub(buyer);
        assertEquals(400, ask(buyer, "seller", product, sub, "제목입니다", "본문입니다요", false).status());
        assertEquals(404, ask(buyer, "seller", 999_999L, null, "제목입니다", "본문입니다요", false).status());
        db.sql("UPDATE product SET status = 'paused' WHERE id = :p").param("p", product).update();
        assertEquals(404, ask(buyer, "seller", product, null, "제목입니다", "본문입니다요", false).status());
        assertEquals(401, ask(new Api(mvc).open(), "admin", null, null, "제목입니다", "본문입니다요", false).status());
        assertEquals(200, ask(buyer, "seller", null, sub, "가".repeat(100), "가".repeat(2000), false).status());
        assertEquals(1, db.sql("SELECT COUNT(*) FROM inquiry").query(Integer.class).single());
    }

    @Test
    @DisplayName("주문 문의는 내 주문에만 할 수 있다")
    void orderInquiryOwnOnly() {
        long sub = orderSub(buyer);
        assertEquals(404, ask(as("buyer3@test.local"), "seller", null, sub, "언제 오나요?", "배송이 궁금합니다.", false).status());
        assertEquals(404, ask(seller, "seller", null, sub, "언제 오나요?", "배송이 궁금합니다.", false).status());
        assertEquals(404, ask(buyer, "seller", null, 999_999L, "언제 오나요?", "배송이 궁금합니다.", false).status());
        assertEquals(0, db.sql("SELECT COUNT(*) FROM inquiry").query(Integer.class).single());
    }

    // ---- 비밀글 ----

    @Test
    @DisplayName("상품 화면의 비밀글은 쓴 사람·그 가게 판매자·운영자에게만 내용이 보인다")
    void secretVisibility() {
        long open = productInquiry(as("buyer3@test.local"), false);
        long secret = productInquiry(buyer, true);
        assertEquals(200, seller.post("/api/seller/inquiries/" + secret + "/answer", Map.of("answer", "쌀 4kg에 쓰세요.")).status());

        // 로그인하지 않은 사람, 다른 회원, 다른 가게 판매자: 비밀글은 가린다
        for (Api who : new Api[] {new Api(mvc).open(), as("buyer2@test.local"), as("cups@test.local")}) {
            JsonNode list = productList(who);
            assertEquals(2, list.get("total").asLong());
            JsonNode s = list.get("items").get(0);
            assertEquals(secret, s.get("id").asLong());
            assertEquals("비밀글입니다", s.get("title").asString());
            assertTrue(s.get("body").isNull());
            assertTrue(s.get("answer").isNull());
            assertTrue(s.get("secret").asBoolean());
            assertEquals("answered", s.get("status").asString());
            assertEquals("김*매", s.get("authorName").asString());
            assertFalse(s.get("mine").asBoolean());
            JsonNode o = list.get("items").get(1);
            assertEquals(open, o.get("id").asLong());
            assertEquals("얼마나 쓰나요?", o.get("title").asString());
            assertFalse(o.get("body").isNull());
            assertEquals("이*골", o.get("authorName").asString());
        }
        // 쓴 사람, 그 가게 판매자, 운영자: 내용이 보인다(이름은 여러 사람이 보는 목록이라 가린다)
        for (Api who : new Api[] {buyer, seller, admin}) {
            JsonNode s = productList(who).get("items").get(0);
            assertEquals("얼마나 쓰나요?", s.get("title").asString());
            assertEquals("쌀 몇 킬로그램에 쓰면 되나요?", s.get("body").asString());
            assertEquals("쌀 4kg에 쓰세요.", s.get("answer").asString());
            assertEquals("김*매", s.get("authorName").asString());
        }
        assertTrue(productList(buyer).get("items").get(0).get("mine").asBoolean());
        assertFalse(productList(seller).get("items").get(0).get("mine").asBoolean());
        assertEquals(404, new Api(mvc).open().get("/api/products/999999/inquiries").status());

        // 주문 문의와 1:1 문의는 상품 화면에 나오지 않는다
        ask(buyer, "seller", null, orderSub(buyer), "언제 오나요?", "배송이 궁금합니다.", false);
        adminInquiry(buyer);
        assertEquals(2, productList(new Api(mvc).open()).get("total").asLong());
    }

    // ---- 답변 ----

    @Test
    @DisplayName("답변: 상품·주문 문의는 그 가게 판매자만, 1:1 문의는 운영자만. 고칠 수 있고 처음 답할 때만 알린다(메일 포함)")
    void answerPermissions() {
        long product = productInquiry(buyer, false);
        long sub = orderSub(buyer);
        long order = ask(buyer, "seller", null, sub, "언제 오나요?", "배송이 궁금합니다.", false).body().get("id").asLong();
        long direct = adminInquiry(buyer);
        Map<String, String> answer = Map.of("answer", "답변 드립니다.");

        Api cups = as("cups@test.local");
        assertEquals(404, cups.post("/api/seller/inquiries/" + product + "/answer", answer).status());
        assertEquals(404, cups.post("/api/seller/inquiries/" + order + "/answer", answer).status());
        assertEquals(404, seller.post("/api/seller/inquiries/" + direct + "/answer", answer).status());   // 1:1 은 판매자가 못 답한다
        assertEquals(404, admin.post("/api/admin/inquiries/" + product + "/answer", answer).status());    // 판매자 문의는 운영자가 안 답한다
        assertEquals(403, admin.post("/api/seller/inquiries/" + product + "/answer", answer).status());
        assertEquals(403, buyer.post("/api/admin/inquiries/" + direct + "/answer", answer).status());
        assertEquals(403, buyer.post("/api/seller/inquiries/" + product + "/answer", answer).status());
        assertEquals(400, seller.post("/api/seller/inquiries/" + product + "/answer", Map.of("answer", " ")).status());
        assertEquals(400, seller.post("/api/seller/inquiries/" + product + "/answer", Map.of("answer", "가".repeat(2001))).status());

        Api.Res r = seller.post("/api/seller/inquiries/" + order + "/answer", Map.of("answer", " 내일 보냅니다. "));
        assertEquals(200, r.status(), String.valueOf(r.body()));
        assertEquals("answered", r.body().get("status").asString());
        assertEquals("내일 보냅니다.", r.body().get("answer").asString());
        assertFalse(r.body().get("answeredAt").isNull());
        assertEquals("김구매", r.body().get("authorName").asString());
        assertEquals(1, notes("buyer@test.local", "답변이 달렸습니다"));
        assertEquals(1, mails("buyer@test.local", "답변이 달렸습니다"));
        Api.Res edit = seller.post("/api/seller/inquiries/" + order + "/answer", Map.of("answer", "오늘 보냈습니다."));
        assertEquals("오늘 보냈습니다.", edit.body().get("answer").asString());
        assertEquals(1, mails("buyer@test.local", "답변이 달렸습니다"));

        Api.Res ar = admin.post("/api/admin/inquiries/" + direct + "/answer", Map.of("answer", "운영자가 확인했습니다."));
        assertEquals(200, ar.status(), String.valueOf(ar.body()));
        assertEquals("운영자가 확인했습니다.", ar.body().get("answer").asString());
        assertEquals(2, mails("buyer@test.local", "답변이 달렸습니다"));
        assertEquals(sample.memberId("admin@test.local"),
                db.sql("SELECT answered_by FROM inquiry WHERE id = :id").param("id", direct).query(Long.class).single());

        // 쓴 사람의 내 문의에 답변이 보인다
        JsonNode mine = buyer.get("/api/my/inquiries").body().get("items");
        assertEquals("운영자가 확인했습니다.", mine.get(0).get("answer").asString());
        assertEquals("오늘 보냈습니다.", mine.get(1).get("answer").asString());
        assertTrue(mine.get(2).get("answer").isNull());
    }

    // ---- 지우기 ----

    @Test
    @DisplayName("쓴 사람만, 답변이 달리기 전에만 지울 수 있고, 지운 문의는 어디에도 나오지 않는다")
    void deleteRules() {
        long first = productInquiry(buyer, false);
        long second = productInquiry(buyer, true);
        assertEquals(404, as("buyer3@test.local").post("/api/inquiries/" + first + "/delete").status());
        assertEquals(404, seller.post("/api/inquiries/" + first + "/delete").status());
        Api.Res d = buyer.post("/api/inquiries/" + first + "/delete");
        assertEquals(200, d.status());
        assertTrue(d.body().get("ok").asBoolean());
        assertEquals(404, buyer.post("/api/inquiries/" + first + "/delete").status());
        assertEquals(404, seller.post("/api/seller/inquiries/" + first + "/answer", Map.of("answer", "답변")).status());

        seller.post("/api/seller/inquiries/" + second + "/answer", Map.of("answer", "답변 드립니다."));
        Api.Res late = buyer.post("/api/inquiries/" + second + "/delete");
        assertEquals(409, late.status());
        assertTrue(late.body().get("message").asString().contains("답변"));

        assertEquals(1, buyer.get("/api/my/inquiries").body().get("total").asLong());
        assertEquals(1, productList(new Api(mvc).open()).get("total").asLong());
        assertEquals(1, seller.get("/api/seller/inquiries").body().get("total").asLong());
    }

    // ---- 목록과 숫자 ----

    @Test
    @DisplayName("답 안 한 것 거르기, 운영자 목록은 1:1 문의만, 현황 숫자와 운영자 전원 알림")
    void listsAndCounters() {
        // 운영자를 한 명 더 둔다: 1:1 문의는 운영자 전원에게 알린다
        db.sql("UPDATE member SET role = 'admin' WHERE email = 'buyer2@test.local'").update();
        long p1 = productInquiry(buyer, false);
        productInquiry(as("buyer3@test.local"), true);
        long direct1 = adminInquiry(buyer);
        adminInquiry(seller);                                   // 판매자도 운영자에게 물을 수 있다
        assertEquals(2, notes("admin@test.local", "1:1 문의"));
        assertEquals(2, notes("buyer2@test.local", "1:1 문의"));
        assertEquals(0, notes("supplies@test.local", "1:1 문의"));

        assertEquals(2, seller.get("/api/seller/inquiries?unanswered=true").body().get("total").asLong());
        assertEquals(2, seller.get("/api/seller").body().get("todo").get("inquiriesUnanswered").asLong());
        assertEquals(0, as("cups@test.local").get("/api/seller/inquiries").body().get("total").asLong());
        assertEquals(2, admin.get("/api/admin/summary").body().get("inquiriesUnanswered").asLong());
        JsonNode adminList = admin.get("/api/admin/inquiries").body();
        assertEquals(2, adminList.get("total").asLong());
        for (JsonNode i : adminList.get("items")) {
            assertEquals("admin", i.get("kind").asString());
        }
        assertEquals(20, adminList.get("size").asInt());
        assertEquals(403, buyer.get("/api/admin/inquiries").status());
        assertEquals(403, buyer.get("/api/seller/inquiries").status());

        seller.post("/api/seller/inquiries/" + p1 + "/answer", Map.of("answer", "답변 드립니다."));
        admin.post("/api/admin/inquiries/" + direct1 + "/answer", Map.of("answer", "답변 드립니다."));
        assertEquals(1, seller.get("/api/seller/inquiries?unanswered=true").body().get("total").asLong());
        assertEquals(2, seller.get("/api/seller/inquiries?unanswered=false").body().get("total").asLong());
        assertEquals(1, seller.get("/api/seller").body().get("todo").get("inquiriesUnanswered").asLong());
        assertEquals(1, admin.get("/api/admin/inquiries?unanswered=true").body().get("total").asLong());
        assertEquals(2, admin.get("/api/admin/inquiries").body().get("total").asLong());
        assertEquals(1, admin.get("/api/admin/summary").body().get("inquiriesUnanswered").asLong());
        // 판매자 목록에서는 비밀글도 내용이 보인다
        JsonNode secretOne = seller.get("/api/seller/inquiries?unanswered=true").body().get("items").get(0);
        assertTrue(secretOne.get("secret").asBoolean());
        assertEquals("얼마나 쓰나요?", secretOne.get("title").asString());
    }

    @Test
    @DisplayName("시범 데이터에 상품 문의 3개(공개 답변됨·공개 미답변·비밀 미답변)와 1:1 문의 1개가 들어간다")
    void sampleInquiries() {
        sample.loadActivity();
        Api anon = new Api(mvc).open();
        JsonNode makgeolli = anon.get("/api/products/" + sample.productId("한울 생막걸리 750ml") + "/inquiries").body();
        assertEquals(1, makgeolli.get("total").asLong());
        assertEquals("answered", makgeolli.get("items").get(0).get("status").asString());
        assertFalse(makgeolli.get("items").get(0).get("answer").isNull());
        JsonNode nuruk = anon.get("/api/products/" + sample.productId(NURUK) + "/inquiries").body().get("items").get(0);
        assertEquals("open", nuruk.get("status").asString());
        assertFalse(nuruk.get("secret").asBoolean());
        JsonNode vessel = anon.get("/api/products/" + sample.productId("발효조 20L") + "/inquiries").body().get("items").get(0);
        assertEquals("비밀글입니다", vessel.get("title").asString());
        assertEquals(1, admin.get("/api/admin/summary").body().get("inquiriesUnanswered").asLong());
        assertEquals(2, seller.get("/api/seller").body().get("todo").get("inquiriesUnanswered").asLong());
    }
}
