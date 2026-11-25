package me.nexthub.sool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
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

/** 공지(C), 가게 화면(D), 판매 통계(E), 번호로 상품 불러오기(F) — docs/adr/0003. */
@SpringBootTest
class NoticeShopStatsTest {

    static final String NURUK = "전통 누룩 1kg";        // 누룩과 쌀 12,000원
    static final String RICE = "술빚기용 찹쌀 4kg";      // 누룩과 쌀 21,000원
    static final String VESSEL = "발효조 20L";          // 누룩과 쌀 35,000원
    static final String SHOP = "누룩과 쌀";
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
    Api admin;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        sample.reset();
        sample.loadBase();
        admin = as("admin@test.local");
    }

    Api as(String email) {
        return new Api(mvc).login(email);
    }

    Api anon() {
        return new Api(mvc).open();
    }

    Map<String, Object> notice(String audience, String title, String body, Boolean pinned) {
        Map<String, Object> m = new HashMap<>();
        m.put("audience", audience);
        m.put("title", title);
        m.put("body", body);
        m.put("pinned", pinned);
        return m;
    }

    long okNotice(String audience, String title, boolean pinned) {
        Api.Res r = admin.post("/api/admin/notices", notice(audience, title, "공지 본문입니다.", pinned));
        assertEquals(200, r.status(), String.valueOf(r.body()));
        return r.body().get("id").asLong();
    }

    List<String> titles(JsonNode page) {
        List<String> result = new ArrayList<>();
        for (JsonNode n : page.get("items")) {
            result.add(n.get("title").asString());
        }
        return result;
    }

    // ---- 공지 ----

    @Test
    @DisplayName("공지: 누구나 보는 목록은 전체 대상만, 고정 먼저·최근 먼저. 판매자 공지는 승인된 판매자와 운영자만 본다")
    void noticeAudience() {
        long first = okNotice("all", "첫 공지", false);
        long pinned = okNotice("all", "고정 공지", true);
        okNotice("all", "새 공지", false);
        long forSeller = okNotice("seller", "판매자 공지", false);

        JsonNode list = anon().get("/api/notices").body();
        assertEquals(List.of("고정 공지", "새 공지", "첫 공지"), titles(list));
        assertEquals(3, list.get("total").asLong());
        assertEquals(10, list.get("size").asInt());
        assertTrue(list.get("items").get(0).get("pinned").asBoolean());

        assertEquals(200, anon().get("/api/notices/" + pinned).status());
        assertEquals("첫 공지", anon().get("/api/notices/" + first).body().get("title").asString());
        // 판매자 공지: 로그인 안 함·구매자·심사 중인 판매자는 404, 승인된 판매자·운영자는 볼 수 있다
        assertEquals(404, anon().get("/api/notices/" + forSeller).status());
        assertEquals(404, as("buyer@test.local").get("/api/notices/" + forSeller).status());
        assertEquals(404, as("newseller@test.local").get("/api/notices/" + forSeller).status());
        Api seller = as("supplies@test.local");
        JsonNode n = seller.get("/api/notices/" + forSeller).body();
        assertEquals("seller", n.get("audience").asString());
        assertEquals("판매자 공지", n.get("title").asString());
        assertEquals(200, admin.get("/api/notices/" + forSeller).status());
        assertEquals(404, anon().get("/api/notices/999999").status());

        assertEquals(List.of("고정 공지", "판매자 공지", "새 공지", "첫 공지"), titles(seller.get("/api/seller/notices").body()));
        assertEquals(403, as("newseller@test.local").get("/api/seller/notices").status());
        assertEquals(403, as("buyer@test.local").get("/api/seller/notices").status());
    }

    @Test
    @DisplayName("공지 관리: 운영자만 쓰고 고치고 지우며, 본문은 글자 그대로 저장한다")
    void noticeAdmin() {
        Api buyer = as("buyer@test.local");
        assertEquals(403, buyer.post("/api/admin/notices", notice("all", "제목", "본문", false)).status());
        assertEquals(403, buyer.get("/api/admin/notices").status());
        assertEquals(401, anon().get("/api/admin/notices").status());
        assertEquals(403, as("supplies@test.local").post("/api/admin/notices", notice("all", "제목", "본문", false)).status());

        assertEquals(400, admin.post("/api/admin/notices", notice("everyone", "제목", "본문", false)).status());
        assertEquals(400, admin.post("/api/admin/notices", notice(null, "제목", "본문", false)).status());
        assertEquals(400, admin.post("/api/admin/notices", notice("all", " ", "본문", false)).status());
        assertEquals(400, admin.post("/api/admin/notices", notice("all", "가".repeat(101), "본문", false)).status());
        assertEquals(400, admin.post("/api/admin/notices", notice("all", "제목", "", false)).status());
        assertEquals(400, admin.post("/api/admin/notices", notice("all", "제목", "가".repeat(5001), false)).status());

        String raw = "<b>굵게</b>\n둘째 줄";
        Api.Res c = admin.post("/api/admin/notices", notice("all", "배송 안내", raw, null));
        assertEquals(200, c.status(), String.valueOf(c.body()));
        long id = c.body().get("id").asLong();
        assertEquals(raw, c.body().get("body").asString());
        assertFalse(c.body().get("pinned").asBoolean());
        assertFalse(c.body().get("createdAt").isNull());

        assertEquals(403, buyer.put("/api/admin/notices/" + id, notice("all", "바꿈", "본문", true)).status());
        Api.Res u = admin.put("/api/admin/notices/" + id, notice("seller", "판매자 배송 안내", "바뀐 본문", true));
        assertEquals(200, u.status());
        assertEquals("seller", u.body().get("audience").asString());
        assertTrue(u.body().get("pinned").asBoolean());
        assertEquals(404, anon().get("/api/notices/" + id).status());         // 이제 판매자 공지
        assertEquals(0, anon().get("/api/notices").body().get("total").asLong());
        assertEquals(1, admin.get("/api/admin/notices").body().get("total").asLong());
        assertEquals(404, admin.put("/api/admin/notices/999999", notice("all", "제목", "본문", false)).status());

        assertEquals(403, buyer.post("/api/admin/notices/" + id + "/delete").status());
        Api.Res d = admin.post("/api/admin/notices/" + id + "/delete");
        assertEquals(200, d.status());
        assertTrue(d.body().get("ok").asBoolean());
        assertEquals(404, admin.post("/api/admin/notices/" + id + "/delete").status());
        assertEquals(404, admin.get("/api/notices/" + id).status());
        assertEquals(404, admin.put("/api/admin/notices/" + id, notice("all", "제목", "본문", false)).status());
        assertEquals(0, admin.get("/api/admin/notices").body().get("total").asLong());
    }

    // ---- 가게 화면 ----

    @Test
    @DisplayName("가게 화면: 승인된 가게만, 판매 중인 상품과 평점, 계좌 정보는 넣지 않는다")
    void shopPage() {
        long supplies = sample.sellerId(SHOP);
        Api.Res r = anon().get("/api/shops/" + supplies);
        assertEquals(200, r.status(), String.valueOf(r.body()));
        JsonNode shop = r.body().get("shop");
        long onSale = db.sql("SELECT COUNT(*) FROM product WHERE seller_id = :s AND status = 'on_sale'")
                .param("s", supplies).query(Long.class).single();
        assertEquals(supplies, shop.get("id").asLong());
        assertEquals(SHOP, shop.get("shopName").asString());
        assertEquals("최누룩", shop.get("ceoName").asString());
        assertEquals("000-00-00003", shop.get("bizNo").asString());
        assertEquals("제2026-시범-0004호", shop.get("mailOrderNo").asString());
        assertEquals("000-0000-0004", shop.get("csPhone").asString());
        assertFalse(shop.get("intro").asString().isEmpty());
        assertEquals(onSale, shop.get("productCount").asLong());
        assertEquals(0, shop.get("reviewCount").asLong());
        assertTrue(shop.get("ratingAvg").isNull());
        String text = r.body().toString();
        assertFalse(text.contains("bankAccount") || text.contains("bankName") || text.contains("bankHolder")
                || text.contains("payLink") || text.contains("000-0000-0003\""));
        JsonNode products = r.body().get("products");
        assertEquals(onSale, products.get("total").asLong());
        assertEquals(24, products.get("size").asInt());
        for (JsonNode p : products.get("items")) {
            assertEquals(supplies, p.get("sellerId").asLong());
            assertEquals("on_sale", p.get("status").asString());
        }

        assertEquals(404, anon().get("/api/shops/" + sample.sellerId("새내기 가게")).status());   // 심사 중
        assertEquals(404, anon().get("/api/shops/999999").status());

        // 가게 평점은 가게 상품 전체의 보이는 리뷰로 센다
        sample.loadActivity();
        JsonNode orchard = anon().get("/api/shops/" + sample.sellerId("달빛 과수원 양조")).body().get("shop");
        assertEquals(2, orchard.get("reviewCount").asLong());
        assertEquals(4.5, orchard.get("ratingAvg").asDouble(), 0.0001);
    }

    // ---- 판매 통계 ----

    long order(Api buyer, Object... nameAndQty) {
        for (int i = 0; i < nameAndQty.length; i += 2) {
            assertEquals(200, buyer.post("/api/cart/items",
                    Map.of("productId", sample.productId((String) nameAndQty[i]), "qty", nameAndQty[i + 1])).status());
        }
        Api.Res r = buyer.post("/api/orders", ADDRESS);
        assertEquals(200, r.status(), String.valueOf(r.body()));
        return r.body().get("subs").get(0).get("sub").get("id").asLong();
    }

    JsonNode dayOf(JsonNode stats, LocalDate date) {
        for (JsonNode d : stats.get("daily")) {
            if (d.get("date").asString().equals(date.toString())) {
                return d;
            }
        }
        throw new AssertionError("날짜가 없습니다: " + date);
    }

    @Test
    @DisplayName("판매 통계: 입금 확인한 날(한국 시간)로 묶고 빈 날은 0, 취소·환불·입금 전 주문과 배송비는 뺀다")
    void sellerStats() {
        Api buyer = as("buyer@test.local");
        Api seller = as("supplies@test.local");

        // 첫날: 누룩 2개 입금 확인 (24,000원, 배송비 3,000원은 빼고)
        long a = order(buyer, NURUK, 2);
        seller.post("/api/seller/suborders/" + a + "/confirm");
        LocalDate day1 = clock.today();
        // 환불된 주문은 뺀다
        long refunded = order(buyer, VESSEL, 1);
        seller.post("/api/seller/suborders/" + refunded + "/confirm");
        seller.post("/api/seller/suborders/" + refunded + "/refund");

        clock.advance(Duration.ofDays(2));
        // 사흘째: 찹쌀 1 + 누룩 1 (33,000원)
        long b = order(buyer, RICE, 1, NURUK, 1);
        seller.post("/api/seller/suborders/" + b + "/confirm");
        LocalDate day3 = clock.today();
        // 입금 확인 전 주문과 구매자가 취소한 주문은 뺀다
        order(buyer, VESSEL, 1);
        long cancelled = order(buyer, RICE, 2);
        buyer.post("/api/suborders/" + cancelled + "/cancel");

        Api.Res r = seller.get("/api/seller/stats?days=7");
        assertEquals(200, r.status(), String.valueOf(r.body()));
        JsonNode s = r.body();
        assertEquals(7, s.get("days").asInt());
        assertEquals(day3.toString(), s.get("to").asString());
        assertEquals(day3.minusDays(6).toString(), s.get("from").asString());
        assertEquals(2, s.get("totals").get("orders").asLong());
        assertEquals(24000 + 33000, s.get("totals").get("sales").asLong());
        assertEquals(4, s.get("totals").get("items").asLong());

        JsonNode daily = s.get("daily");
        assertEquals(7, daily.size());
        assertEquals(s.get("from").asString(), daily.get(0).get("date").asString());
        assertEquals(day3.toString(), daily.get(6).get("date").asString());
        assertEquals(1, dayOf(s, day1).get("orders").asLong());
        assertEquals(24000, dayOf(s, day1).get("sales").asLong());
        assertEquals(0, dayOf(s, day1.plusDays(1)).get("orders").asLong());
        assertEquals(0, dayOf(s, day1.plusDays(1)).get("sales").asLong());
        assertEquals(33000, dayOf(s, day3).get("sales").asLong());
        assertEquals(0, daily.get(0).get("sales").asLong());

        JsonNode top = s.get("topProducts");
        assertEquals(2, top.size());
        assertEquals(NURUK, top.get(0).get("name").asString());       // 36,000원
        assertEquals(3, top.get(0).get("qty").asLong());
        assertEquals(36000, top.get(0).get("sales").asLong());
        assertEquals(sample.productId(NURUK), top.get(0).get("productId").asLong());
        assertEquals(RICE, top.get(1).get("name").asString());
        assertEquals(21000, top.get(1).get("sales").asLong());

        // 기본은 30일, 다른 기간 값은 400, 판매자만
        JsonNode def = seller.get("/api/seller/stats").body();
        assertEquals(30, def.get("days").asInt());
        assertEquals(30, def.get("daily").size());
        assertEquals(90, seller.get("/api/seller/stats?days=90").body().get("daily").size());
        assertEquals(400, seller.get("/api/seller/stats?days=5").status());
        assertEquals(403, buyer.get("/api/seller/stats").status());
        assertEquals(0, as("cups@test.local").get("/api/seller/stats").body().get("totals").get("orders").asLong());

        // 엿새 더 지나면 첫날 주문은 7일 범위 밖, 30일 범위 안
        clock.advance(Duration.ofDays(6));
        assertEquals(1, seller.get("/api/seller/stats?days=7").body().get("totals").get("orders").asLong());
        assertEquals(2, seller.get("/api/seller/stats?days=30").body().get("totals").get("orders").asLong());
    }

    @Test
    @DisplayName("판매 통계의 날짜는 한국 시간 자정으로 나눈다")
    void statsKstBoundary() {
        Api buyer = as("buyer@test.local");
        Api seller = as("supplies@test.local");
        long a = order(buyer, NURUK, 1);
        seller.post("/api/seller/suborders/" + a + "/confirm");
        // 입금 확인 시각을 한국 시간 어제 23:30(UTC 14:30)과 오늘 00:30(UTC 전날 15:30)으로 옮겨 본다
        LocalDate today = clock.today();
        db.sql("UPDATE sub_order SET deposit_confirmed_at = :at WHERE id = :id")
                .param("at", today.minusDays(1).atTime(14, 30)).param("id", a).update();
        JsonNode s = seller.get("/api/seller/stats?days=7").body();
        assertEquals(1, dayOf(s, today.minusDays(1)).get("orders").asLong());
        db.sql("UPDATE sub_order SET deposit_confirmed_at = :at WHERE id = :id")
                .param("at", today.minusDays(1).atTime(15, 30)).param("id", a).update();
        s = seller.get("/api/seller/stats?days=7").body();
        assertEquals(1, dayOf(s, today).get("orders").asLong());
        assertEquals(0, dayOf(s, today.minusDays(1)).get("orders").asLong());
    }

    // ---- 번호로 상품 불러오기 ----

    @Test
    @DisplayName("최근 본 상품: 준 순서대로, 판매 중인 것만, 같은 번호는 한 번, 24개까지")
    void productsByIds() {
        long nuruk = sample.productId(NURUK);
        long rice = sample.productId(RICE);
        long paused = sample.productId("준비 중인 상품");
        long draft = sample.productId("당도계");
        long vessel = sample.productId(VESSEL);
        JsonNode list = anon().get("/api/products/by-ids?ids=" + rice + "," + paused + "," + nuruk + ",abc," + draft
                + "," + rice + "," + vessel + ",999999").body();
        assertEquals(3, list.size());
        assertEquals(rice, list.get(0).get("id").asLong());
        assertEquals(nuruk, list.get(1).get("id").asLong());
        assertEquals(vessel, list.get(2).get("id").asLong());
        assertTrue(list.get(0).has("reviewCount"));

        assertEquals(0, anon().get("/api/products/by-ids?ids=").body().size());
        assertEquals(0, anon().get("/api/products/by-ids").body().size());

        List<Long> onSale = db.sql("SELECT id FROM product WHERE status = 'on_sale' ORDER BY id").query(Long.class).list();
        assertTrue(onSale.size() > 24);
        StringBuilder ids = new StringBuilder();
        for (Long id : onSale) {
            ids.append(id).append(',');
        }
        assertEquals(24, anon().get("/api/products/by-ids?ids=" + ids).body().size());
        // 판매자가 승인되지 않은 가게의 상품도 뺀다
        db.sql("UPDATE seller SET status = 'pending' WHERE shop_name = :n").param("n", SHOP).update();
        assertEquals(0, anon().get("/api/products/by-ids?ids=" + nuruk + "," + rice).body().size());
    }
}
