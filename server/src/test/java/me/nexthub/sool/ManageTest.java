package me.nexthub.sool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
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

/** 회원 관리, 세션, 알림, 분류, 상품 상태, 사진·동영상, 페이지 나누기. */
@SpringBootTest
class ManageTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 0, 0, 0, 0};
    static final Map<String, String> ADDRESS = Map.of("receiver", "김구매", "phone", "010-0000-0000", "address", "시범시 시범로 1");

    @Autowired
    WebApplicationContext context;
    @Autowired
    SampleData sample;
    @Autowired
    AppClock clock;
    @Autowired
    JdbcClient db;
    @Autowired
    me.nexthub.sool.file.VideoCompressor compressor;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        sample.reset();
        sample.loadBase();
    }

    Api as(String email) {
        return new Api(mvc).login(email);
    }

    long categoryId(String name) {
        return db.sql("SELECT id FROM category WHERE name = :n").param("n", name).query(Long.class).single();
    }

    Map<String, Object> product(String name, long categoryId) {
        Map<String, Object> p = new HashMap<>();
        p.put("categoryId", categoryId);
        p.put("name", name);
        p.put("price", 10000);
        p.put("stock", 5);
        return p;
    }

    // ---- 회원 관리 ----

    @Test
    @DisplayName("운영자는 회원을 찾고, 운영자로 지정하고, 정지할 수 있다")
    void memberAdmin() {
        Api admin = as("admin@test.local");
        assertEquals(403, as("buyer@test.local").get("/api/admin/members").status());

        JsonNode all = admin.get("/api/admin/members").body();
        assertEquals(9, all.get("total").asLong());
        assertFalse(all.toString().contains("pbkdf2"));
        JsonNode found = admin.get("/api/admin/members?q=buyer2").body();
        assertEquals(1, found.get("total").asLong());
        assertEquals(3, admin.get("/api/admin/members?role=buyer").body().get("total").asLong());

        long buyer = sample.memberId("buyer@test.local");
        long seller = sample.memberId("cups@test.local");
        long self = sample.memberId("admin@test.local");

        assertEquals(200, admin.post("/api/admin/members/" + buyer + "/admin", Map.of("admin", true)).status());
        assertEquals(200, as("buyer@test.local").get("/api/admin/members").status());
        assertEquals(200, admin.post("/api/admin/members/" + buyer + "/admin", Map.of("admin", false)).status());
        assertEquals("buyer", as("buyer@test.local").get("/api/me").body().get("user").get("role").asString());

        // 운영자를 해제하면 가게가 있는 회원은 판매자로 돌아간다
        admin.post("/api/admin/members/" + seller + "/admin", Map.of("admin", true));
        admin.post("/api/admin/members/" + seller + "/admin", Map.of("admin", false));
        assertEquals("seller", as("cups@test.local").get("/api/me").body().get("user").get("role").asString());

        assertEquals(400, admin.post("/api/admin/members/" + self + "/admin", Map.of("admin", false)).status());
        assertEquals(400, admin.post("/api/admin/members/" + self + "/status", Map.of("status", "suspended")).status());
    }

    @Test
    @DisplayName("정지하면 이미 로그인한 세션도 끊기고 다시 로그인할 수 없다")
    void suspendCutsSession() {
        Api admin = as("admin@test.local");
        Api buyer = as("buyer@test.local");
        long id = sample.memberId("buyer@test.local");
        assertEquals(200, buyer.get("/api/cart").status());

        assertEquals(200, admin.post("/api/admin/members/" + id + "/status", Map.of("status", "suspended")).status());
        assertEquals(401, buyer.get("/api/cart").status());
        Api again = new Api(mvc).open();
        assertEquals(403, again.post("/api/auth/login", Map.of("email", "buyer@test.local", "password", SampleData.PASSWORD)).status());

        admin.post("/api/admin/members/" + id + "/status", Map.of("status", "active"));
        assertEquals(200, as("buyer@test.local").get("/api/cart").status());
    }

    // ---- 세션 ----

    @Test
    @DisplayName("비밀번호를 다섯 번 틀리면 잠기고, 시간이 지나면 풀린다")
    void loginLock() {
        Api api = new Api(mvc).open();
        Map<String, String> wrong = Map.of("email", "buyer@test.local", "password", "wrong-password");
        for (int i = 0; i < 5; i++) {
            assertEquals(401, api.post("/api/auth/login", wrong).status());
        }
        Map<String, String> right = Map.of("email", "buyer@test.local", "password", SampleData.PASSWORD);
        assertEquals(429, api.post("/api/auth/login", right).status());
        clock.advance(Duration.ofMinutes(11));
        assertEquals(200, api.post("/api/auth/login", right).status());
    }

    @Test
    @DisplayName("비밀번호를 바꾸면 다른 기기의 로그인은 끊긴다")
    void passwordChange() {
        Api phone = as("buyer@test.local");
        Api pc = as("buyer@test.local");
        assertEquals(400, pc.post("/api/me/password", Map.of("current", "wrong", "next", "new-password-1")).status());
        assertEquals(400, pc.post("/api/me/password", Map.of("current", SampleData.PASSWORD, "next", "short")).status());
        assertEquals(200, pc.post("/api/me/password", Map.of("current", SampleData.PASSWORD, "next", "new-password-1")).status());

        assertEquals(200, pc.get("/api/cart").status());      // 바꾼 기기는 그대로
        assertEquals(401, phone.get("/api/cart").status());   // 다른 기기는 끊김
        Api again = new Api(mvc).open();
        assertEquals(401, again.post("/api/auth/login", Map.of("email", "buyer@test.local", "password", SampleData.PASSWORD)).status());
        assertEquals(200, again.post("/api/auth/login", Map.of("email", "buyer@test.local", "password", "new-password-1")).status());

        assertEquals(200, pc.put("/api/me", Map.of("name", "김새이름")).status());
        assertEquals("김새이름", pc.get("/api/me").body().get("user").get("name").asString());
    }

    // ---- 알림 ----

    @Test
    @DisplayName("주문·입금 신고·입금 확인·발송마다 상대방에게 알림이 가고 메일이 대기열에 쌓인다")
    void notifications() {
        Api buyer = as("buyer@test.local");
        Api seller = as("brewery@test.local");
        assertEquals(0, seller.get("/api/me").body().get("unreadCount").asInt());

        buyer.post("/api/cart/items", Map.of("productId", sample.productId("한울 생막걸리 750ml"), "qty", 1));
        JsonNode order = buyer.post("/api/orders", ADDRESS).body();
        long sub = order.get("subs").get(0).get("sub").get("id").asLong();
        assertEquals(1, seller.get("/api/me").body().get("unreadCount").asInt());           // 새 주문

        buyer.post("/api/suborders/" + sub + "/report", Map.of("depositor", "김구매"));
        JsonNode sellerList = seller.get("/api/notifications").body();
        assertEquals(2, sellerList.get("total").asLong());
        assertTrue(sellerList.get("items").get(0).get("title").asString().contains("입금"));

        seller.post("/api/seller/suborders/" + sub + "/confirm");
        seller.post("/api/seller/suborders/" + sub + "/ship", Map.of("carrier", "cj", "trackingNo", "1234"));
        JsonNode buyerList = buyer.get("/api/notifications").body();
        assertEquals(2, buyerList.get("total").asLong());                                    // 입금 확인, 발송
        assertEquals(2, buyer.get("/api/me").body().get("unreadCount").asInt());

        long first = buyerList.get("items").get(0).get("id").asLong();
        buyer.post("/api/notifications/" + first + "/read");
        assertEquals(1, buyer.get("/api/me").body().get("unreadCount").asInt());
        buyer.post("/api/notifications/read-all");
        assertEquals(0, buyer.get("/api/me").body().get("unreadCount").asInt());

        // 남의 알림은 읽음 처리되지 않는다
        long sellerFirst = sellerList.get("items").get(0).get("id").asLong();
        buyer.post("/api/notifications/" + sellerFirst + "/read");
        assertEquals(2, seller.get("/api/me").body().get("unreadCount").asInt());

        int mails = db.sql("SELECT COUNT(*) FROM message_outbox WHERE channel = 'email'").query(Integer.class).single();
        assertEquals(4, mails);
    }

    // ---- 분류 ----

    @Test
    @DisplayName("운영자가 분류를 추가·수정하고, 대분류로 찾으면 중분류 상품까지 나온다")
    void categories() {
        Api admin = as("admin@test.local");
        long alcohol = categoryId("전통주");
        long takju = categoryId("탁주·막걸리");

        assertEquals(4, admin.get("/api/products?cat=" + takju).body().get("total").asLong() + 1);   // 막걸리 3종
        assertEquals(8, admin.get("/api/products?cat=" + alcohol).body().get("total").asLong());     // 전통주 전체

        Api.Res top = admin.post("/api/admin/categories", Map.of("name", "테스트 안주", "alcohol", false));
        assertEquals(200, top.status());
        long topId = top.body().get("id").asLong();
        assertEquals(409, admin.post("/api/admin/categories", Map.of("name", "테스트 안주")).status());
        Api.Res child = admin.post("/api/admin/categories", Map.of("name", "테스트 마른안주", "parentId", topId));
        assertEquals(200, child.status());
        long childId = child.body().get("id").asLong();
        assertEquals(400, admin.post("/api/admin/categories", Map.of("name", "테스트 3단계", "parentId", childId)).status());

        // 중분류의 주류 여부는 대분류를 따른다
        Api.Res sub = admin.post("/api/admin/categories", Map.of("name", "테스트 주종", "parentId", alcohol, "alcohol", false));
        assertTrue(sub.body().get("alcohol").asBoolean());

        // 새 분류에 상품을 올릴 수 있고, 쓰지 않음으로 바꾸면 고를 수 없다
        Api seller = as("supplies@test.local");
        assertEquals(200, seller.post("/api/seller/products", product("육포", childId)).status());
        assertEquals(200, admin.put("/api/admin/categories/" + childId, Map.of("active", false)).status());
        assertEquals(400, seller.post("/api/seller/products", product("오징어", childId)).status());
        assertFalse(seller.get("/api/categories").body().toString().contains("테스트 마른안주"));
        assertTrue(admin.get("/api/admin/categories").body().toString().contains("테스트 마른안주"));
        assertEquals(403, seller.post("/api/admin/categories", Map.of("name", "테스트 몰래")).status());
        assertEquals(400, admin.post("/api/admin/sellers/1/status", Map.of()).status());   // 값이 빠진 요청
    }

    @Test
    @DisplayName("수수료율은 중분류, 대분류, 기본 순서로 찾는다")
    void categoryRateFallback() {
        Api admin = as("admin@test.local");
        Api buyer = as("buyer@test.local");
        long takju = categoryId("탁주·막걸리");
        long product = sample.productId("한울 생막걸리 750ml");

        buyer.post("/api/cart/items", Map.of("productId", product, "qty", 1));
        JsonNode first = buyer.post("/api/orders", ADDRESS).body();
        assertEquals(100, first.get("subs").get(0).get("items").get(0).get("commissionRateBp").asInt());   // 대분류 1%

        admin.put("/api/admin/categories/" + takju + "/commission", Map.of("ratePercent", 2));
        buyer.post("/api/cart/items", Map.of("productId", product, "qty", 1));
        JsonNode second = buyer.post("/api/orders", ADDRESS).body();
        assertEquals(200, second.get("subs").get(0).get("items").get(0).get("commissionRateBp").asInt());  // 중분류 2%
    }

    // ---- 상품 상태 ----

    @Test
    @DisplayName("임시저장·판매 중지·판매 종료 상품은 보이지 않고, 운영자가 막은 상품은 판매자가 풀 수 없다")
    void productStatuses() {
        Api seller = as("supplies@test.local");
        Api admin = as("admin@test.local");
        Api buyer = as("buyer@test.local");
        Map<String, Object> draft = product("새 발효조", categoryId("발효 용기"));
        draft.put("status", "draft");
        long id = seller.post("/api/seller/products", draft).body().get("product").get("id").asLong();
        assertEquals(404, buyer.get("/api/products/" + id).status());

        assertEquals(200, seller.post("/api/seller/products/" + id + "/status", Map.of("status", "on_sale")).status());
        assertEquals(200, buyer.get("/api/products/" + id).status());
        assertEquals(200, buyer.post("/api/cart/items", Map.of("productId", id, "qty", 1)).status());

        // 장바구니에 담긴 뒤 판매가 중지되면 주문할 수 없다
        seller.post("/api/seller/products/" + id + "/status", Map.of("status", "paused"));
        assertEquals(404, buyer.get("/api/products/" + id).status());
        assertEquals(409, buyer.post("/api/orders", ADDRESS).status());

        assertEquals(400, seller.post("/api/seller/products/" + id + "/status", Map.of("status", "blocked")).status());
        assertEquals(200, admin.post("/api/admin/products/" + id + "/block", Map.of("blocked", true)).status());
        assertEquals(403, seller.post("/api/seller/products/" + id + "/status", Map.of("status", "on_sale")).status());
        Map<String, Object> edit = product("새 발효조", categoryId("발효 용기"));
        edit.put("status", "on_sale");
        assertEquals("blocked", seller.put("/api/seller/products/" + id, edit).body().get("product").get("status").asString());
        assertTrue(seller.get("/api/notifications").body().toString().contains("판매가 중단"));

        admin.post("/api/admin/products/" + id + "/block", Map.of("blocked", false));
        assertEquals(200, seller.post("/api/seller/products/" + id + "/status", Map.of("status", "ended")).status());
        assertEquals(1, admin.get("/api/admin/products?status=ended").body().get("total").asLong());
        assertEquals(1, seller.get("/api/seller/products?status=draft").body().get("total").asLong());   // 당도계
    }

    // ---- 사진·동영상·꾸민 설명 ----

    @Test
    @DisplayName("사진을 올려 상품에 붙이고, 유튜브 주소는 영상 번호로 저장된다")
    void media() {
        Api seller = as("supplies@test.local");
        assertEquals(400, seller.upload("evil.png", "<script>alert(1)</script>".getBytes()).status());   // 내용이 사진이 아님
        assertEquals(401, new Api(mvc).open().upload("a.png", PNG).status());

        String first = seller.upload("a.png", PNG).body().get("url").asString();
        String second = seller.upload("b.png", PNG).body().get("url").asString();
        assertTrue(first.matches("/api/files/[a-f0-9]{32}\\.png"));

        Map<String, Object> p = product("사진 있는 상품", categoryId("발효 용기"));
        p.put("media", List.of(
                Map.of("kind", "image", "url", second),
                Map.of("kind", "youtube", "url", "https://www.youtube.com/watch?v=abcdefghijk&t=10s"),
                Map.of("kind", "image", "url", first)));
        JsonNode saved = seller.post("/api/seller/products", p).body();
        assertEquals(second, saved.get("product").get("mainImage").asString());   // 첫 사진이 대표 사진
        assertEquals(3, saved.get("media").size());
        assertEquals("abcdefghijk", saved.get("media").get(1).get("url").asString());
        long id = saved.get("product").get("id").asLong();
        assertEquals(second, seller.get("/api/products?q=사진 있는").body().get("items").get(0).get("mainImage").asString());

        // 남이 올린 파일, 바깥 주소, 잘못된 유튜브 주소는 붙일 수 없다
        Map<String, Object> stolen = product("남의 사진", categoryId("술잔"));
        stolen.put("media", List.of(Map.of("kind", "image", "url", first)));
        assertEquals(400, as("cups@test.local").post("/api/seller/products", stolen).status());
        p.put("media", List.of(Map.of("kind", "image", "url", "https://example.com/x.png")));
        assertEquals(400, seller.put("/api/seller/products/" + id, p).status());
        p.put("media", List.of(Map.of("kind", "youtube", "url", "javascript:alert(1)")));
        assertEquals(400, seller.put("/api/seller/products/" + id, p).status());

        p.put("media", List.of());
        assertEquals(0, seller.put("/api/seller/products/" + id, p).body().get("media").size());
    }

    @Test
    @DisplayName("올린 동영상은 뒤에서 줄여 원본보다 작아진다 (ffmpeg 이 있을 때만 확인)")
    void videoCompression() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(compressor.available(), "ffmpeg 이 없어 건너뜀");
        java.nio.file.Path src = java.nio.file.Files.createTempFile("sool-test", ".mp4");
        // 일부러 용량이 큰 3초짜리 시험 영상을 만든다.
        Process make = new ProcessBuilder("ffmpeg", "-y", "-f", "lavfi", "-i", "testsrc=duration=3:size=1920x1080:rate=30",
                "-c:v", "libx264", "-crf", "1", "-pix_fmt", "yuv420p", src.toString())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertEquals(0, make.waitFor());
        byte[] bytes = java.nio.file.Files.readAllBytes(src);

        Api seller = as("supplies@test.local");
        JsonNode up = seller.upload("clip.mp4", bytes).body();
        assertEquals("video", up.get("kind").asString());
        assertTrue(up.get("compressing").asBoolean());
        String name = up.get("url").asString().substring("/api/files/".length());

        String status = "pending";
        for (int i = 0; i < 120 && "pending".equals(status); i++) {
            Thread.sleep(500);
            status = db.sql("SELECT compress_status FROM upload_file WHERE stored_name = :n").param("n", name).query(String.class).single();
        }
        assertEquals("done", status);
        long size = db.sql("SELECT size_bytes FROM upload_file WHERE stored_name = :n").param("n", name).query(Long.class).single();
        assertTrue(size < bytes.length, "줄인 뒤 " + size + " < 원본 " + bytes.length);
        assertFalse(seller.upload("photo.png", PNG).body().get("compressing").asBoolean());   // 사진은 대상이 아니다
    }

    @Test
    @DisplayName("꾸민 설명은 문서로 저장되고 요약 글이 자동으로 만들어진다")
    void richDescription() {
        Api seller = as("supplies@test.local");
        Map<String, Object> p = product("설명이 긴 상품", categoryId("발효 용기"));
        p.put("abv", 6.5);
        p.put("volumeMl", 750);
        p.put("origin", "국내산");
        p.put("descriptionDoc", Map.of("type", "doc", "content", List.of(
                Map.of("type", "heading", "attrs", Map.of("level", 2), "content", List.of(Map.of("type", "text", "text", "큰 제목"))),
                Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", "본문 글입니다."))))));
        JsonNode saved = seller.post("/api/seller/products", p).body();
        assertEquals("큰 제목 본문 글입니다.", saved.get("product").get("description").asString());
        assertTrue(saved.get("descriptionDoc").asString().contains("\"heading\""));
        assertEquals(750, saved.get("product").get("volumeMl").asInt());

        p.put("descriptionDoc", Map.of("type", "html", "content", "<script>"));
        assertEquals(400, seller.post("/api/seller/products", p).status());
    }

    @Test
    @DisplayName("꾸민 설명: 표, 직접 올린 동영상, 유튜브, 사진 묶음, 글자 색·크기·맞춤, 외부 사진·링크는 받는다")
    void richDescriptionBlocks() {
        Api seller = as("supplies@test.local");
        Map<String, Object> p = product("꾸민 설명 상품", categoryId("발효 용기"));
        Map<String, Object> colored = Map.of("type", "text", "text", "빨간 큰 글",
                "marks", List.of(Map.of("type", "textStyle", "attrs", Map.of("color", "#d62b1f", "fontSize", "20px")),
                        Map.of("type", "link", "attrs", Map.of("href", "https://example.com/brewery"))));
        Map<String, Object> cell = Map.of("type", "tableCell", "content", List.of(
                Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", "도수")))));
        p.put("descriptionDoc", Map.of("type", "doc", "content", List.of(
                Map.of("type", "paragraph", "attrs", Map.of("textAlign", "center"), "content", List.of(colored)),
                Map.of("type", "table", "content", List.of(Map.of("type", "tableRow", "content", List.of(cell, cell)))),
                Map.of("type", "video", "attrs", Map.of("src", "/api/files/" + "a".repeat(32) + ".mp4")),
                Map.of("type", "youtube", "attrs", Map.of("src", "https://www.youtube.com/watch?v=dQw4w9WgXcQ")),
                Map.of("type", "imageRow", "attrs", Map.of("layout", "slide"), "content", List.of(
                        Map.of("type", "image", "attrs", Map.of("src", "/api/files/" + "b".repeat(32) + ".jpg")),
                        Map.of("type", "image", "attrs", Map.of("src", "https://thumbnail.example.com/detail/1.jpg")))),
                Map.of("type", "callout", "content", List.of(
                        Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", "냉장 보관"))))))));
        JsonNode saved = seller.post("/api/seller/products", p).body();
        String doc = saved.get("descriptionDoc").asString();
        assertTrue(doc.contains("\"youtube\"") && doc.contains("\"imageRow\"") && doc.contains("\"tableCell\""));
        assertTrue(saved.get("product").get("description").asString().contains("냉장 보관"));
    }

    @Test
    @DisplayName("꾸민 설명: 위험한 주소, 모르는 블록, 다른 사이트 동영상, 자리가 틀린 블록은 거절한다")
    void richDescriptionRejects() {
        Api seller = as("supplies@test.local");
        Map<String, Object> p = product("거절될 설명", categoryId("발효 용기"));
        List<Map<String, Object>> bad = List.of(
                // 스크립트 주소 링크
                Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", "눌러",
                        "marks", List.of(Map.of("type", "link", "attrs", Map.of("href", "javascript:alert(1)")))))),
                // 파일 안에 박힌 사진, http 사진
                Map.of("type", "image", "attrs", Map.of("src", "data:image/png;base64,AAAA")),
                Map.of("type", "image", "attrs", Map.of("src", "http://example.com/a.jpg")),
                // 다른 사이트 동영상, 유튜브가 아닌 주소
                Map.of("type", "video", "attrs", Map.of("src", "https://example.com/a.mp4")),
                Map.of("type", "youtube", "attrs", Map.of("src", "https://evil.example.com/watch?v=dQw4w9WgXcQ")),
                // 모르는 블록, 스타일에 끼운 다른 값
                Map.of("type", "iframe", "attrs", Map.of("src", "https://example.com")),
                Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", "색",
                        "marks", List.of(Map.of("type", "textStyle", "attrs", Map.of("color", "red;background:url(x)")))))),
                Map.of("type", "paragraph", "attrs", Map.of("textAlign", "expression(x)")),
                // 자리가 틀린 블록: 문단 안의 사진, 맨 바깥의 표 줄
                Map.of("type", "paragraph", "content", List.of(Map.of("type", "image", "attrs", Map.of("src", "https://example.com/a.jpg")))),
                Map.of("type", "tableRow", "content", List.of()));
        for (Map<String, Object> node : bad) {
            p.put("descriptionDoc", Map.of("type", "doc", "content", List.of(node)));
            var res = seller.post("/api/seller/products", p);
            assertEquals(400, res.status(), "거절해야 함: " + node);
            assertTrue(res.body().get("message").asString().startsWith("상품 설명"), node.toString());
        }
    }

    // ---- 페이지 나누기 ----

    @Test
    @DisplayName("상품 목록은 쪽으로 나뉘고 가격순으로 볼 수 있다")
    void paging() {
        Api api = new Api(mvc).open();
        JsonNode first = api.get("/api/products").body();
        assertEquals(25, first.get("total").asLong());
        assertEquals(24, first.get("items").size());
        assertEquals(1, api.get("/api/products?page=2").body().get("items").size());
        assertEquals(5, api.get("/api/products?size=5&page=5").body().get("items").size());

        JsonNode cheap = api.get("/api/products?sort=price_asc&size=1").body().get("items").get(0);
        assertEquals(6000, cheap.get("currentPrice").asLong());   // 쌀누룩
        JsonNode dear = api.get("/api/products?sort=price_desc&size=1").body().get("items").get(0);
        assertEquals(68000, dear.get("currentPrice").asLong());   // 옹기 술독
    }

    @Test
    @DisplayName("주문 목록은 기간과 상태로 걸러 볼 수 있다")
    void orderFilters() {
        sample.loadActivity();
        Api buyer = as("buyer@test.local");
        int year = clock.today().getYear();
        assertEquals(3, buyer.get("/api/orders").body().get("total").asLong());
        assertEquals(3, buyer.get("/api/orders?period=" + year).body().get("total").asLong());
        assertEquals(0, buyer.get("/api/orders?period=" + (year - 2)).body().get("total").asLong());
        assertEquals(3, buyer.get("/api/orders?period=all").body().get("total").asLong());

        Api seller = as("supplies@test.local");
        long all = seller.get("/api/seller/orders").body().get("total").asLong();
        long paid = seller.get("/api/seller/orders?status=paid").body().get("total").asLong();
        assertEquals(4, all);
        assertEquals(1, paid);
        assertTrue(as("admin@test.local").get("/api/admin/orders?status=awaiting_deposit").body().get("total").asLong() >= 3);
        assertEquals(1, as("admin@test.local").get("/api/admin/summary").body().get("pendingSellers").asLong());
    }
}
