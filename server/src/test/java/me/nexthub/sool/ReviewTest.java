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
import me.nexthub.sool.common.Names;
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

/** 리뷰(docs/adr/0003 A): 쓸 수 있는 때, 입력 검증, 지우기, 판매자 답글, 운영자 숨김, 평점 계산, 이름 가리기. */
@SpringBootTest
class ReviewTest {

    static final String NURUK = "전통 누룩 1kg";        // 누룩과 쌀
    static final String RICE = "술빚기용 찹쌀 4kg";      // 누룩과 쌀
    static final String YEAST = "건조 효모 100g";       // 누룩과 쌀
    static final String CUP = "백자 술잔 2개 세트";      // 도담 공방
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 0, 0, 0, 0};
    static final byte[] MP4 = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 0, 0};
    static final Map<String, String> ADDRESS = Map.of("receiver", "김구매", "phone", "010-0000-0000", "address", "시범시 시범로 1");
    static final String BODY = "향이 좋고 포장도 꼼꼼했습니다.";

    @Autowired
    WebApplicationContext context;
    @Autowired
    SampleData sample;
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

    /** 상품 하나를 주문하고 판매자별 주문 번호를 돌려준다. */
    long order(Api who, String product, int qty) {
        assertEquals(200, who.post("/api/cart/items", Map.of("productId", sample.productId(product), "qty", qty)).status());
        Api.Res r = who.post("/api/orders", ADDRESS);
        assertEquals(200, r.status(), String.valueOf(r.body()));
        return r.body().get("subs").get(0).get("sub").get("id").asLong();
    }

    /** 입금 확인 → 발송 → 구매 확정까지 마친 주문의 상품 줄 번호 */
    long completedItem(Api who, Api shop, String product) {
        long sub = order(who, product, 1);
        assertEquals(200, shop.post("/api/seller/suborders/" + sub + "/confirm").status());
        assertEquals(200, shop.post("/api/seller/suborders/" + sub + "/ship", Map.of("carrier", "cj", "trackingNo", "1234")).status());
        assertEquals(200, who.post("/api/suborders/" + sub + "/receive").status());
        return itemOf(sub);
    }

    long itemOf(long sub) {
        return db.sql("SELECT id FROM order_item WHERE sub_order_id = :s ORDER BY id LIMIT 1").param("s", sub)
                .query(Long.class).single();
    }

    long subOfItem(long item) {
        return db.sql("SELECT sub_order_id FROM order_item WHERE id = :i").param("i", item).query(Long.class).single();
    }

    Api.Res write(Api who, long item, Integer rating, String body, List<String> images) {
        Map<String, Object> m = new HashMap<>();
        m.put("orderItemId", item);
        m.put("rating", rating);
        m.put("body", body);
        m.put("images", images);
        return who.post("/api/reviews", m);
    }

    long okReview(Api who, long item, int rating) {
        Api.Res r = write(who, item, rating, BODY, List.of());
        assertEquals(200, r.status(), String.valueOf(r.body()));
        return r.body().get("id").asLong();
    }

    String upload(Api who, String name, byte[] content) {
        Api.Res r = who.upload(name, content);
        assertEquals(200, r.status(), String.valueOf(r.body()));
        return r.body().get("url").asString();
    }

    int notes(String email, String titlePart) {
        return db.sql("SELECT COUNT(*) FROM notification WHERE member_id = :m AND title LIKE :t")
                .param("m", sample.memberId(email)).param("t", "%" + titlePart + "%").query(Integer.class).single();
    }

    JsonNode productInList(String name, String sort) {
        JsonNode items = new Api(mvc).open().get("/api/products?size=100" + (sort == null ? "" : "&sort=" + sort)).body().get("items");
        for (JsonNode p : items) {
            if (p.get("name").asString().equals(name)) {
                return p;
            }
        }
        throw new AssertionError("목록에 없습니다: " + name);
    }

    List<String> names(JsonNode items) {
        List<String> result = new ArrayList<>();
        for (JsonNode n : items) {
            result.add(n.get("name").asString());
        }
        return result;
    }

    // ---- 쓸 수 있는 때 ----

    @Test
    @DisplayName("구매 확정한 상품 줄에만 쓸 수 있고, 확정 전·환불·취소·남의 주문은 안 된다")
    void writableOnlyCompleted() {
        long sub = order(buyer, NURUK, 1);
        long item = itemOf(sub);
        assertEquals(409, write(buyer, item, 5, BODY, List.of()).status());          // 입금 대기
        seller.post("/api/seller/suborders/" + sub + "/confirm");
        assertEquals(409, write(buyer, item, 5, BODY, List.of()).status());          // 입금 확인
        seller.post("/api/seller/suborders/" + sub + "/ship", Map.of("carrier", "cj", "trackingNo", "1"));
        assertEquals(409, write(buyer, item, 5, BODY, List.of()).status());          // 배송 중
        assertEquals(0, buyer.get("/api/my/reviews/writable").body().size());
        buyer.post("/api/suborders/" + sub + "/receive");

        // 남의 주문 상품 줄은 404
        assertEquals(404, write(as("buyer3@test.local"), item, 5, BODY, List.of()).status());
        assertEquals(404, write(buyer, 999_999L, 5, BODY, List.of()).status());
        assertEquals(401, write(new Api(mvc).open(), item, 5, BODY, List.of()).status());

        JsonNode writable = buyer.get("/api/my/reviews/writable").body();
        assertEquals(1, writable.size());
        JsonNode w = writable.get(0);
        assertEquals(item, w.get("orderItemId").asLong());
        assertEquals(NURUK, w.get("productName").asString());
        assertEquals("누룩과 쌀", w.get("shopName").asString());
        assertEquals(1, w.get("qty").asInt());
        assertEquals(sample.productId(NURUK), w.get("productId").asLong());
        assertFalse(w.get("completedAt").isNull());
        assertEquals(db.sql("SELECT order_id FROM sub_order WHERE id = :s").param("s", sub).query(Long.class).single(),
                w.get("orderId").asLong());

        Api.Res r = write(buyer, item, 4, "  " + BODY + "  ", null);
        assertEquals(200, r.status(), String.valueOf(r.body()));
        JsonNode rv = r.body();
        assertEquals(4, rv.get("rating").asInt());
        assertEquals(BODY, rv.get("body").asString());
        assertEquals("visible", rv.get("status").asString());
        assertTrue(rv.get("mine").asBoolean());
        assertEquals("김구매", rv.get("authorName").asString());
        assertEquals(NURUK, rv.get("productName").asString());
        assertEquals(0, rv.get("images").size());
        assertTrue(rv.get("sellerReply").isNull());
        assertEquals(1, notes("supplies@test.local", "새 리뷰"));

        // 한 줄에 한 번만, 쓴 뒤에는 쓸 수 있는 목록에서 빠진다
        assertEquals(409, write(buyer, item, 5, BODY, List.of()).status());
        assertEquals(0, buyer.get("/api/my/reviews/writable").body().size());
    }

    @Test
    @DisplayName("환불·취소된 주문에는 쓸 수 없다")
    void refundedOrCancelledRejected() {
        long refunded = order(buyer, NURUK, 1);
        seller.post("/api/seller/suborders/" + refunded + "/confirm");
        assertEquals(200, seller.post("/api/seller/suborders/" + refunded + "/refund").status());
        Api.Res r = write(buyer, itemOf(refunded), 5, BODY, List.of());
        assertEquals(409, r.status());
        assertTrue(r.body().get("message").asString().contains("환불"));

        long cancelled = order(buyer, RICE, 1);
        assertEquals(200, buyer.post("/api/suborders/" + cancelled + "/cancel").status());
        assertEquals(409, write(buyer, itemOf(cancelled), 5, BODY, List.of()).status());

        // 구매 확정 후 반품으로 환불된 주문도 쓸 수 없고, 쓸 수 있는 목록에도 없다
        long item = completedItem(buyer, seller, YEAST);
        db.sql("UPDATE sub_order SET status = 'refunded' WHERE id = :s").param("s", subOfItem(item)).update();
        assertEquals(409, write(buyer, item, 5, BODY, List.of()).status());
        assertEquals(0, buyer.get("/api/my/reviews/writable").body().size());
    }

    @Test
    @DisplayName("쓸 수 있는 목록은 구매 확정 후 90일까지이고, 그 뒤에는 쓸 수 없다")
    void writableWindow() {
        long item = completedItem(buyer, seller, NURUK);
        clock.advance(Duration.ofDays(89));
        assertEquals(1, buyer.get("/api/my/reviews/writable").body().size());
        clock.advance(Duration.ofDays(2));
        assertEquals(0, buyer.get("/api/my/reviews/writable").body().size());
        assertEquals(409, write(buyer, item, 5, BODY, List.of()).status());
    }

    @Test
    @DisplayName("별점은 1~5, 글은 10~1000자, 사진은 내가 올린 사진만 3장까지")
    void validation() {
        long item = completedItem(buyer, seller, NURUK);
        assertEquals(400, write(buyer, item, 0, BODY, List.of()).status());
        assertEquals(400, write(buyer, item, 6, BODY, List.of()).status());
        assertEquals(400, write(buyer, item, null, BODY, List.of()).status());
        assertEquals(400, write(buyer, item, 5, "짧은 글", List.of()).status());
        assertEquals(400, write(buyer, item, 5, "          아홉글자임         ", List.of()).status());   // 앞뒤 공백 빼고 5자
        assertEquals(400, write(buyer, item, 5, "가".repeat(1001), List.of()).status());
        assertEquals(400, write(buyer, item, 5, null, List.of()).status());

        String a = upload(buyer, "a.png", PNG);
        String b = upload(buyer, "b.png", PNG);
        String c = upload(buyer, "c.png", PNG);
        String d = upload(buyer, "d.png", PNG);
        String video = upload(buyer, "clip.mp4", MP4);
        String others = upload(as("buyer3@test.local"), "x.png", PNG);
        assertEquals(400, write(buyer, item, 5, BODY, List.of(a, b, c, d)).status());       // 4장
        assertEquals(400, write(buyer, item, 5, BODY, List.of(a, video)).status());         // 동영상
        assertEquals(400, write(buyer, item, 5, BODY, List.of(others)).status());           // 남이 올린 사진
        assertEquals(400, write(buyer, item, 5, BODY, List.of("https://example.com/a.png")).status());
        // 실패한 요청은 아무것도 남기지 않는다
        assertEquals(0, db.sql("SELECT COUNT(*) FROM review").query(Integer.class).single());

        Api.Res ok = write(buyer, item, 5, "가".repeat(1000), List.of(a, b, c));
        assertEquals(200, ok.status(), String.valueOf(ok.body()));
        JsonNode images = ok.body().get("images");
        assertEquals(3, images.size());
        assertEquals(a, images.get(0).asString());
        assertEquals(c, images.get(2).asString());
    }

    @Test
    @DisplayName("쓴 사람만 지울 수 있고, 지우면 상품 화면과 내 리뷰에서 빠지며 다시 쓸 수 없다")
    void deleteThenCannotRewrite() {
        long item = completedItem(buyer, seller, NURUK);
        long id = okReview(buyer, item, 2);
        long product = sample.productId(NURUK);
        assertEquals(1, buyer.get("/api/my/reviews").body().get("total").asLong());

        assertEquals(404, as("buyer3@test.local").post("/api/reviews/" + id + "/delete").status());
        assertEquals(404, seller.post("/api/reviews/" + id + "/delete").status());
        Api.Res del = buyer.post("/api/reviews/" + id + "/delete");
        assertEquals(200, del.status());
        assertTrue(del.body().get("ok").asBoolean());
        assertEquals(404, buyer.post("/api/reviews/" + id + "/delete").status());
        assertEquals("deleted", db.sql("SELECT status FROM review WHERE id = :id").param("id", id).query(String.class).single());

        assertEquals(0, buyer.get("/api/my/reviews").body().get("total").asLong());
        JsonNode pr = new Api(mvc).open().get("/api/products/" + product + "/reviews").body();
        assertEquals(0, pr.get("reviewCount").asLong());
        assertTrue(pr.get("ratingAvg").isNull());
        assertEquals(0, productInList(NURUK, null).get("reviewCount").asLong());

        Api.Res again = write(buyer, item, 5, BODY, List.of());
        assertEquals(409, again.status());
        assertTrue(again.body().get("message").asString().contains("지운"));
        assertEquals(0, buyer.get("/api/my/reviews/writable").body().size());
        // 지운 리뷰에는 판매자도 답할 수 없고, 운영자 숨김도 안 된다
        assertEquals(404, seller.post("/api/seller/reviews/" + id + "/reply", Map.of("reply", "고맙습니다")).status());
        assertEquals(409, as("admin@test.local").post("/api/admin/reviews/" + id + "/hide", Map.of("hidden", true)).status());
    }

    // ---- 상품 화면, 평점 ----

    @Test
    @DisplayName("상품 화면: 평균·리뷰 수·별점별 수, 최근 먼저, 상품 목록과 상세에도 평점이 붙는다")
    void productReviewsAndAverage() {
        Api third = as("buyer3@test.local");
        long product = sample.productId(NURUK);
        okReview(buyer, completedItem(buyer, seller, NURUK), 5);
        okReview(third, completedItem(third, seller, NURUK), 4);
        long last = okReview(buyer, completedItem(buyer, seller, NURUK), 4);

        Api anon = new Api(mvc).open();
        JsonNode pr = anon.get("/api/products/" + product + "/reviews").body();
        assertEquals(3, pr.get("reviewCount").asLong());
        assertEquals(4.3, pr.get("ratingAvg").asDouble(), 0.0001);      // 13 / 3 = 4.33 → 4.3
        JsonNode counts = pr.get("counts");
        assertEquals(5, counts.size());
        assertEquals(0, counts.get(0).asLong());
        assertEquals(2, counts.get(3).asLong());
        assertEquals(1, counts.get(4).asLong());
        JsonNode page = pr.get("page");
        assertEquals(3, page.get("total").asLong());
        assertEquals(10, page.get("size").asInt());
        assertEquals(last, page.get("items").get(0).get("id").asLong());
        assertFalse(page.get("items").get(0).get("mine").asBoolean());   // 로그인하지 않았다
        assertTrue(buyer.get("/api/products/" + product + "/reviews").body().get("page").get("items").get(0).get("mine").asBoolean());

        JsonNode inList = productInList(NURUK, null);
        assertEquals(3, inList.get("reviewCount").asLong());
        assertEquals(4.3, inList.get("ratingAvg").asDouble(), 0.0001);
        JsonNode detail = anon.get("/api/products/" + product).body().get("product");
        assertEquals(3, detail.get("reviewCount").asLong());
        assertEquals(4.3, detail.get("ratingAvg").asDouble(), 0.0001);
        // 리뷰가 없는 상품은 0 과 null
        JsonNode none = productInList(RICE, null);
        assertEquals(0, none.get("reviewCount").asLong());
        assertTrue(none.get("ratingAvg").isNull());

        assertEquals(404, anon.get("/api/products/999999/reviews").status());
        // 장바구니(상품 조회 식을 그대로 다시 쓴다)도 그대로 동작한다
        assertEquals(200, buyer.post("/api/cart/items", Map.of("productId", product, "qty", 1)).status());
        assertEquals(NURUK, buyer.get("/api/cart").body().get("groups").get(0).get("lines").get(0).get("name").asString());
    }

    @Test
    @DisplayName("평점 높은 순 정렬: 평균이 높은 상품이 먼저, 리뷰 없는 상품은 뒤")
    void sortByRating() {
        okReview(buyer, completedItem(buyer, seller, NURUK), 3);
        okReview(buyer, completedItem(buyer, seller, RICE), 5);
        Api cups = as("cups@test.local");
        okReview(buyer, completedItem(buyer, cups, CUP), 4);

        List<String> sorted = names(new Api(mvc).open().get("/api/products?sort=rating&size=100").body().get("items"));
        assertEquals(List.of(RICE, CUP, NURUK), sorted.subList(0, 3));
        JsonNode fourth = new Api(mvc).open().get("/api/products?sort=rating&size=100").body().get("items").get(3);
        assertEquals(0, fourth.get("reviewCount").asLong());
        assertEquals(1, new Api(mvc).open().get("/api/products?sort=rating&size=1&page=2").body().get("items").size());
    }

    @Test
    @DisplayName("이름은 가운데를 가린다: 세 글자, 두 글자, 더 긴 이름, 영문")
    void maskedNames() {
        assertEquals("김*매", Names.mask("김구매"));
        assertEquals("김*", Names.mask("김구"));
        assertEquals("남**매", Names.mask("남궁구매"));
        assertEquals("J**n", Names.mask("John"));
        assertEquals("*", Names.mask("김"));
        assertEquals("*", Names.mask(null));

        long product = sample.productId(NURUK);
        okReview(buyer, completedItem(buyer, seller, NURUK), 5);
        assertEquals("김*매", new Api(mvc).open().get("/api/products/" + product + "/reviews").body()
                .get("page").get("items").get(0).get("authorName").asString());
        // 내 리뷰 화면에서는 가리지 않는다
        assertEquals("김구매", buyer.get("/api/my/reviews").body().get("items").get(0).get("authorName").asString());

        Api third = as("buyer3@test.local");
        okReview(third, completedItem(third, seller, NURUK), 4);
        db.sql("UPDATE member SET name = '이단' WHERE email = 'buyer3@test.local'").update();
        assertEquals("이*", new Api(mvc).open().get("/api/products/" + product + "/reviews").body()
                .get("page").get("items").get(0).get("authorName").asString());
        db.sql("UPDATE member SET name = 'Alice' WHERE email = 'buyer3@test.local'").update();
        assertEquals("A***e", new Api(mvc).open().get("/api/products/" + product + "/reviews").body()
                .get("page").get("items").get(0).get("authorName").asString());
        db.sql("UPDATE member SET name = '제갈공명이' WHERE email = 'buyer3@test.local'").update();
        assertEquals("제***이", new Api(mvc).open().get("/api/products/" + product + "/reviews").body()
                .get("page").get("items").get(0).get("authorName").asString());
    }

    // ---- 판매자 답글 ----

    @Test
    @DisplayName("판매자 답글: 그 가게 판매자만, 한 번 달고 고칠 수 있으며 처음 달 때 구매자에게 알린다")
    void sellerReply() {
        long id = okReview(buyer, completedItem(buyer, seller, NURUK), 5);
        Api cups = as("cups@test.local");
        assertEquals(1, seller.get("/api/seller/reviews?unanswered=true").body().get("total").asLong());
        assertEquals(0, cups.get("/api/seller/reviews").body().get("total").asLong());
        assertEquals(1, seller.get("/api/seller").body().get("todo").get("reviewsUnanswered").asLong());

        assertEquals(404, cups.post("/api/seller/reviews/" + id + "/reply", Map.of("reply", "고맙습니다")).status());
        assertEquals(403, buyer.post("/api/seller/reviews/" + id + "/reply", Map.of("reply", "고맙습니다")).status());
        assertEquals(400, seller.post("/api/seller/reviews/" + id + "/reply", Map.of("reply", "  ")).status());
        assertEquals(400, seller.post("/api/seller/reviews/" + id + "/reply", Map.of("reply", "가".repeat(501))).status());

        Api.Res r = seller.post("/api/seller/reviews/" + id + "/reply", Map.of("reply", " 고맙습니다 "));
        assertEquals(200, r.status(), String.valueOf(r.body()));
        assertEquals("고맙습니다", r.body().get("sellerReply").asString());
        assertFalse(r.body().get("repliedAt").isNull());
        assertEquals(1, notes("buyer@test.local", "판매자 답글"));

        Api.Res edit = seller.post("/api/seller/reviews/" + id + "/reply", Map.of("reply", "다시 한 번 고맙습니다"));
        assertEquals(200, edit.status());
        assertEquals("다시 한 번 고맙습니다", edit.body().get("sellerReply").asString());
        assertEquals(1, notes("buyer@test.local", "판매자 답글"));      // 고칠 때는 다시 알리지 않는다

        assertEquals(0, seller.get("/api/seller/reviews?unanswered=true").body().get("total").asLong());
        assertEquals(1, seller.get("/api/seller/reviews?unanswered=false").body().get("total").asLong());
        assertEquals(20, seller.get("/api/seller/reviews").body().get("size").asInt());
        assertEquals(0, seller.get("/api/seller").body().get("todo").get("reviewsUnanswered").asLong());
        assertEquals("다시 한 번 고맙습니다", new Api(mvc).open().get("/api/products/" + sample.productId(NURUK) + "/reviews")
                .body().get("page").get("items").get(0).get("sellerReply").asString());
    }

    // ---- 운영자 숨김 ----

    @Test
    @DisplayName("운영자가 숨긴 리뷰는 상품 화면·평점·리뷰 수에서 빠지고, 다시 보이게 할 수 있다")
    void adminHide() {
        long product = sample.productId(NURUK);
        long low = okReview(buyer, completedItem(buyer, seller, NURUK), 1);
        okReview(buyer, completedItem(buyer, seller, NURUK), 5);
        Api admin = as("admin@test.local");

        assertEquals(403, buyer.post("/api/admin/reviews/" + low + "/hide", Map.of("hidden", true)).status());
        assertEquals(403, buyer.get("/api/admin/reviews").status());
        assertEquals(400, admin.post("/api/admin/reviews/" + low + "/hide", Map.of()).status());
        assertEquals(404, admin.post("/api/admin/reviews/999999/hide", Map.of("hidden", true)).status());

        Api.Res h = admin.post("/api/admin/reviews/" + low + "/hide", Map.of("hidden", true));
        assertEquals(200, h.status());
        assertEquals("hidden", h.body().get("status").asString());
        assertEquals(200, admin.post("/api/admin/reviews/" + low + "/hide", Map.of("hidden", true)).status());   // 두 번 눌러도 같다

        JsonNode pr = new Api(mvc).open().get("/api/products/" + product + "/reviews").body();
        assertEquals(1, pr.get("reviewCount").asLong());
        assertEquals(5.0, pr.get("ratingAvg").asDouble(), 0.0001);
        assertEquals(0, pr.get("counts").get(0).asLong());
        assertEquals(1, pr.get("page").get("total").asLong());
        JsonNode inList = productInList(NURUK, null);
        assertEquals(1, inList.get("reviewCount").asLong());
        assertEquals(5.0, inList.get("ratingAvg").asDouble(), 0.0001);

        // 쓴 사람의 "내 리뷰"와 판매자 목록에는 숨김 상태로 남는다
        JsonNode mine = buyer.get("/api/my/reviews").body();
        assertEquals(2, mine.get("total").asLong());
        assertEquals("hidden", mine.get("items").get(1).get("status").asString());
        assertEquals(2, seller.get("/api/seller/reviews").body().get("total").asLong());
        assertEquals(1, seller.get("/api/seller/reviews?unanswered=true").body().get("total").asLong());

        assertEquals(1, admin.get("/api/admin/reviews?status=hidden").body().get("total").asLong());
        assertEquals(2, admin.get("/api/admin/reviews").body().get("total").asLong());
        assertEquals(400, admin.get("/api/admin/reviews?status=wrong").status());

        Api.Res v = admin.post("/api/admin/reviews/" + low + "/hide", Map.of("hidden", false));
        assertEquals("visible", v.body().get("status").asString());
        assertEquals(3.0, new Api(mvc).open().get("/api/products/" + product + "/reviews").body().get("ratingAvg").asDouble(), 0.0001);
        assertEquals(0, admin.get("/api/admin/reviews?status=hidden").body().get("total").asLong());
    }

    @Test
    @DisplayName("시범 데이터에 구매 확정 주문의 리뷰 3개(별 5·4·5)와 판매자 답글 하나가 들어간다")
    void sampleReviews() {
        sample.loadActivity();
        Api admin = as("admin@test.local");
        JsonNode all = admin.get("/api/admin/reviews").body();
        assertEquals(3, all.get("total").asLong());
        int replies = 0;
        int sum = 0;
        for (JsonNode r : all.get("items")) {
            sum += r.get("rating").asInt();
            replies += r.get("sellerReply").isNull() ? 0 : 1;
        }
        assertEquals(14, sum);
        assertEquals(1, replies);
        assertEquals(5.0, productInList("달빛 사과 와인 375ml", null).get("ratingAvg").asDouble(), 0.0001);
        // buyer3 의 구매 확정 상품은 모두 리뷰를 썼다
        assertEquals(0, as("buyer3@test.local").get("/api/my/reviews/writable").body().size());
    }
}
