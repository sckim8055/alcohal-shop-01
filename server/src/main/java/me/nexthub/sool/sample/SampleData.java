package me.nexthub.sool.sample;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import me.nexthub.sool.admin.InvoiceService;
import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.common.AppClock;
import me.nexthub.sool.common.Db;
import me.nexthub.sool.common.Passwords;
import me.nexthub.sool.common.Tenant;
import me.nexthub.sool.order.OrderDetail;
import me.nexthub.sool.order.OrderService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 시범 데이터. 가게·상품·사람 이름, 사업자번호, 계좌는 모두 지어낸 것이다.
 * 모든 시범 계정의 비밀번호는 test1234 이다.
 */
@Component
public class SampleData implements ApplicationRunner {

    public static final String PASSWORD = "test1234";

    private final JdbcClient db;
    private final Tenant tenant;
    private final AppClock clock;
    private final Auth auth;
    private final OrderService orders;
    private final InvoiceService invoices;
    private final boolean enabled;

    public SampleData(JdbcClient db, Tenant tenant, AppClock clock, Auth auth, OrderService orders,
                      InvoiceService invoices, @Value("${sool.sample-data:false}") boolean enabled) {
        this.db = db;
        this.tenant = tenant;
        this.clock = clock;
        this.auth = auth;
        this.orders = orders;
        this.invoices = invoices;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (enabled && db.sql("SELECT COUNT(*) FROM member").query(Integer.class).single() == 0) {
            loadBase();
            loadActivity();
        }
    }

    /** 테스트 전용: 모든 업무 데이터를 지우고 설정을 처음 값으로 돌린다. */
    public void reset() {
        for (String table : List.of("member_coupon", "coupon", "order_item", "sub_order", "shop_order", "cart_item", "commission_invoice",
                "commission_rate_log", "post", "product_media", "product", "seller", "upload_file",
                "notification", "message_outbox", "member")) {
            db.sql("DELETE FROM " + table).update();
        }
        db.sql("DELETE FROM category WHERE name LIKE '테스트%' AND parent_id IS NOT NULL").update();
        db.sql("DELETE FROM category WHERE name LIKE '테스트%'").update();
        db.sql("UPDATE category SET active = TRUE, commission_rate_bp = CASE WHEN is_alcohol = TRUE AND parent_id IS NULL THEN 100 ELSE NULL END").update();
        db.sql("UPDATE app_setting SET setting_value = '300' WHERE setting_key = 'default_commission_rate_bp'").update();
        clock.reset();
    }

    // ---- 회원, 판매자, 상품 ----

    public void loadBase() {
        String hash = Passwords.hash(PASSWORD);
        member("admin@test.local", "운영자", "admin", true, hash);
        long mBrew = member("brewery@test.local", "한울 양조장", "seller", true, hash);
        long mOrchard = member("orchard@test.local", "달빛 과수원", "seller", true, hash);
        long mCups = member("cups@test.local", "도담 공방", "seller", true, hash);
        long mSupplies = member("supplies@test.local", "누룩과 쌀", "seller", true, hash);
        long mNew = member("newseller@test.local", "새내기 판매자", "seller", true, hash);
        member("buyer@test.local", "김구매", "buyer", true, hash);
        member("buyer2@test.local", "박새봄", "buyer", false, hash);
        member("buyer3@test.local", "이단골", "buyer", true, hash);

        long brew = seller(mBrew, "한울 양조장", "000-00-00001", "지역 쌀로 빚는 작은 양조장입니다.", "approved",
                "000-0000-0001", "한울양조장", "https://example.com/pay/hanul", "지역특산주", "시범-001", true, 4000, 50000);
        long orchard = seller(mOrchard, "달빛 과수원 양조", "000-00-00005", "직접 기른 과일로 과실주를 빚습니다.", "approved",
                "000-0000-0005", "달빛과수원", "https://example.com/pay/dalbit", "지역특산주", "시범-002", true, 3500, 60000);
        long cups = seller(mCups, "도담 공방", "000-00-00002", "손으로 빚은 술잔과 주병을 만듭니다.", "approved",
                "000-0000-0002", "도담공방", "", "", "", false, 3500, 40000);
        long supplies = seller(mSupplies, "누룩과 쌀", "000-00-00003", "술 빚는 재료와 기구, 빚기 수업을 준비합니다.", "approved",
                "000-0000-0003", "누룩과쌀", "https://example.com/pay/nuruk", "", "", false, 3000, null);
        seller(mNew, "새내기 가게", "000-00-00004", "입점 심사를 기다리는 가게입니다.", "pending",
                "", "", "", "", "", false, 3500, null);

        long takju = category("탁주·막걸리");
        long yakju = category("약주·청주");
        long fruit = category("과실주·와인");
        long gift = category("선물 세트");
        long starter = category("누룩·효모");
        long grain = category("쌀·곡물");
        long vessel = category("발효 용기");
        long gauge = category("계측 도구");
        long filter = category("거름·병입 도구");
        long cup = category("술잔");
        long jug = category("주병·주전자");
        long pot = category("술독·항아리");
        long prop = category("소품");
        long lesson = category("빚기 수업");
        LocalDate today = clock.today();

        product(brew, takju, "한울 생막걸리 750ml", "지역 쌀과 전통 누룩으로 빚은 생막걸리. 냉장 보관.", 8000, 50);
        product(brew, yakju, "한울 약주 500ml", "맑게 거른 약주. 선물용 상자 포함.", 22000, 20, 19000L, null, null, "physical", "on_sale");
        product(brew, takju, "한울 탁주 원주 375ml", "물을 타지 않은 진한 원주.", 15000, 30);
        product(brew, takju, "한울 생막걸리 6병 묶음", "생막걸리 750ml 여섯 병.", 45000, 15);

        product(orchard, fruit, "달빛 사과 와인 375ml", "가을 사과로 빚은 달지 않은 와인.", 18000, 25);
        product(orchard, fruit, "달빛 오미자주 500ml", "오미자의 다섯 가지 맛을 담은 술.", 24000, 18);
        product(orchard, fruit, "달빛 복분자주 375ml", "진한 색과 향의 복분자주. 기간 할인 중.", 16000, 40, 14000L,
                today.minusDays(3), today.plusDays(10), "physical", "on_sale");
        product(orchard, gift, "달빛 과실주 선물 세트", "사과 와인과 오미자주 두 병 세트.", 39000, 10);

        product(cups, cup, "백자 술잔 2개 세트", "물레로 빚은 백자 술잔 두 개.", 18000, 30);
        product(cups, jug, "분청 주병", "막걸리와 약주 모두 어울리는 주병.", 35000, 10);
        product(cups, cup, "유리 막걸리잔 4개", "입이 넓은 유리 막걸리잔 네 개.", 16000, 40);
        product(cups, pot, "옹기 술독 5L", "숨 쉬는 옹기 술독. 집에서 빚기 좋은 크기.", 68000, 6);
        product(cups, prop, "나무 술받침", "호두나무로 깎은 술받침.", 12000, 25);
        product(cups, jug, "주병·술잔 세트", "분청 주병 하나와 술잔 두 개.", 52000, 8, 46000L, null, null, "physical", "on_sale");

        product(supplies, starter, "전통 누룩 1kg", "밀로 띄운 전통 누룩.", 12000, 100);
        product(supplies, grain, "술빚기용 찹쌀 4kg", "고두밥 짓기 좋은 햇찹쌀.", 21000, 60);
        product(supplies, starter, "입국 500g", "쌀에 띄운 입국. 깔끔한 맛을 낼 때 씁니다.", 9000, 80);
        product(supplies, starter, "건조 효모 100g", "발효가 안정적인 건조 효모.", 7000, 120);
        product(supplies, starter, "쌀누룩 300g", "다음 입고를 기다리는 품절 상품입니다.", 6000, 0);
        product(supplies, vessel, "발효조 20L", "에어락이 달린 발효조.", 35000, 15);
        product(supplies, vessel, "유리 발효병 5L", "소량으로 빚을 때 쓰는 유리병.", 14000, 30);
        product(supplies, gauge, "비중계·온도계 세트", "발효 상태를 확인하는 기본 도구.", 19000, 20);
        product(supplies, filter, "면보·거름망 세트", "술을 거를 때 쓰는 면보와 거름망.", 8000, 50);
        product(supplies, lesson, "막걸리 빚기 하루 수업", "재료 제공, 빚은 술은 가져갑니다. 날짜는 주문 후 안내.", 40000, 12,
                null, null, null, "ticket", "on_sale");
        product(supplies, lesson, "약주 거르기 반나절 수업", "빚어 둔 술을 거르고 병에 담는 수업.", 30000, 10,
                null, null, null, "ticket", "on_sale");
        product(supplies, vessel, "준비 중인 상품", "아직 공개하지 않은 상품 예시입니다.", 10000, 5,
                null, null, null, "physical", "paused");
        product(supplies, gauge, "당도계", "아직 쓰는 중인 임시저장 상품 예시입니다.", 23000, 0,
                null, null, null, "physical", "draft");
        attributes();
        coupons(supplies, cups, today);
    }

    /** 시범 쿠폰. 기존 시범 회원에게는 미리 주지 않는다(기존 테스트의 금액이 바뀌지 않게). */
    private void coupons(long supplies, long cups, LocalDate today) {
        coupon("platform", null, "가입 축하 3,000원", "amount", 3000, null, 20000, true, "signup", null, null,
                today.minusDays(1), today.plusDays(365), 30);
        coupon("platform", null, "첫 장보기 무료배송", "free_shipping", 0, null, 10000, false, "code", "SOOL-FREE", null,
                today.minusDays(1), today.plusDays(90), null);
        coupon("seller", supplies, "누룩과 쌀 10% 할인", "percent", 10, 5000L, 15000, true, "download", null, 100,
                today.minusDays(1), today.plusDays(60), null);
        coupon("seller", cups, "도담 공방 2,000원", "amount", 2000, null, 30000, true, "download", null, null,
                today.minusDays(1), today.plusDays(60), null);
    }

    private void coupon(String issuer, Long sellerId, String name, String type, int value, Long maxDiscount,
                        long minOrder, boolean excludeAlcohol, String issueType, String code, Integer totalLimit,
                        LocalDate from, LocalDate to, Integer validDays) {
        db.sql("""
                INSERT INTO coupon (tenant_id, issuer, seller_id, name, discount_type, discount_value, max_discount,
                                    min_order, exclude_alcohol, issue_type, code, total_limit, valid_from, valid_to,
                                    valid_days, status, created_at)
                VALUES (:t, :issuer, :seller, :name, :type, :value, :max, :min, :ex, :issueType, :code, :limit,
                        :from, :to, :days, 'active', :now)
                """)
                .param("t", tenant.id()).param("issuer", issuer).param("seller", sellerId).param("name", name)
                .param("type", type).param("value", value).param("max", maxDiscount).param("min", minOrder)
                .param("ex", excludeAlcohol).param("issueType", issueType).param("code", code)
                .param("limit", totalLimit).param("from", from).param("to", to).param("days", validDays)
                .param("now", clock.now()).update();
    }

    /** 주류 속성(도수, 용량, 원재료 산지)과 꾸민 설명 예시 */
    private void attributes() {
        attr("한울 생막걸리 750ml", "6.0", 750, "국내산 쌀");
        attr("한울 약주 500ml", "13.0", 500, "국내산 쌀");
        attr("한울 탁주 원주 375ml", "12.0", 375, "국내산 쌀");
        attr("한울 생막걸리 6병 묶음", "6.0", 4500, "국내산 쌀");
        attr("달빛 사과 와인 375ml", "11.0", 375, "국내산 사과");
        attr("달빛 오미자주 500ml", "15.0", 500, "국내산 오미자");
        attr("달빛 복분자주 375ml", "14.0", 375, "국내산 복분자");
        db.sql("UPDATE product SET description_doc = :doc WHERE name = '한울 생막걸리 750ml'").param("doc", """
                {"type":"doc","content":[
                 {"type":"heading","attrs":{"level":2},"content":[{"type":"text","text":"지역 쌀로 빚은 생막걸리"}]},
                 {"type":"paragraph","content":[{"type":"text","text":"전통 누룩으로 "},{"type":"text","marks":[{"type":"bold"}],"text":"열흘 동안"},{"type":"text","text":" 천천히 익혔습니다. 살균하지 않아 효모가 살아 있습니다."}]},
                 {"type":"bulletList","content":[
                  {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"받으면 바로 냉장 보관해 주세요."}]}]},
                  {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"병을 세워 두고, 마시기 전에 가볍게 흔들어 주세요."}]}]},
                  {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"제조일로부터 30일 안에 드세요."}]}]}]}]}
                """).update();
    }

    private void attr(String name, String abv, int volumeMl, String origin) {
        db.sql("UPDATE product SET abv = :abv, volume_ml = :vol, origin = :origin WHERE name = :n")
                .param("abv", new java.math.BigDecimal(abv)).param("vol", volumeMl).param("origin", origin).param("n", name).update();
    }

    // ---- 주문, 청구서, 게시글 ----

    public void loadActivity() {
        long buyer = memberId("buyer@test.local");
        long regular = memberId("buyer3@test.local");
        long brew = sellerId("한울 양조장");
        long orchard = sellerId("달빛 과수원 양조");
        long cups = sellerId("도담 공방");
        long supplies = sellerId("누룩과 쌀");

        // 1) 입금 대기: 세 판매자에게 나뉘는 주문
        order(buyer, "김구매", "한울 생막걸리 750ml", 2, "전통 누룩 1kg", 1, "백자 술잔 2개 세트", 1);

        // 2) 한쪽은 배송 중, 한쪽은 입금 신고만 된 주문
        OrderDetail o2 = order(buyer, "김구매", "한울 약주 500ml", 1, "분청 주병", 1);
        for (OrderDetail.Sub s : o2.subs()) {
            orders.reportDeposit(s.sub().id(), buyer, "김구매");
        }
        long o2brew = subOf(o2, brew);
        orders.confirmDeposit(o2brew, brew, null);
        orders.ship(o2brew, brew, "cj", "123456789012");

        // 3) 수업: 입금 확인 → 전달 완료 → 구매확정
        OrderDetail o3 = order(regular, "이단골", "막걸리 빚기 하루 수업", 1);
        long o3sub = subOf(o3, supplies);
        orders.reportDeposit(o3sub, regular, "이단골");
        orders.confirmDeposit(o3sub, supplies, "시범승인-0001");
        orders.deliverTicket(o3sub, supplies);
        orders.confirmReceipt(o3sub, regular);

        // 4) 과실주: 발송 후 구매확정까지 끝난 주문
        OrderDetail o4 = order(regular, "이단골", "달빛 사과 와인 375ml", 2, "달빛 오미자주 500ml", 1);
        long o4sub = subOf(o4, orchard);
        orders.reportDeposit(o4sub, regular, "이단골");
        orders.confirmDeposit(o4sub, orchard, null);
        orders.ship(o4sub, orchard, "hanjin", "555500001111");
        orders.confirmReceipt(o4sub, regular);

        // 5) 입금 확인 후 환불된 주문
        OrderDetail o5 = order(regular, "이단골", "유리 막걸리잔 4개", 1);
        long o5sub = subOf(o5, cups);
        orders.confirmDeposit(o5sub, cups, null);
        orders.refund(o5sub, cups);

        // 6) 구매자가 취소한 주문
        OrderDetail o6 = order(buyer, "김구매", "건조 효모 100g", 1);
        orders.cancelByBuyer(subOf(o6, supplies), buyer);

        // 7) 입금 확인만 된 주문(발송 대기)
        OrderDetail o7 = order(regular, "이단골", "발효조 20L", 1, "전통 누룩 1kg", 2);
        long o7sub = subOf(o7, supplies);
        orders.reportDeposit(o7sub, regular, "이단골");
        orders.confirmDeposit(o7sub, supplies, null);

        invoices.generate(YearMonth.from(clock.today()).toString());

        post("supplies@test.local", "단양주 처음 빚어 본 기록",
                "고두밥을 식히고 누룩을 섞어 항아리에 담았습니다. 사흘째부터 끓는 소리가 납니다.");
        post("cups@test.local", "술잔 고르는 법",
                "막걸리는 입이 넓은 잔, 약주는 작고 깊은 잔이 향을 잘 모아 줍니다.");
        post("brewery@test.local", "생막걸리는 왜 냉장 보관할까",
                "효모가 살아 있어 따뜻하면 발효가 계속됩니다. 받으면 바로 냉장고에 세워 두세요.");
        post("buyer3@test.local", "수업 다녀온 후기",
                "하루 수업에서 빚은 막걸리를 일주일 뒤에 걸렀습니다. 생각보다 어렵지 않았어요.");
        post("orchard@test.local", "올해 사과 수확 이야기",
                "비가 적어 알은 작지만 당도가 높습니다. 올해 와인은 조금 더 진할 것 같습니다.");
    }

    /** 이름과 수량을 번갈아 받는다: order(buyer, "받는 분", "상품A", 2, "상품B", 1) */
    private OrderDetail order(long memberId, String receiver, Object... nameAndQty) {
        for (int i = 0; i < nameAndQty.length; i += 2) {
            orders.addToCart(memberId, productId((String) nameAndQty[i]), (Integer) nameAndQty[i + 1]);
        }
        long id = orders.createOrder(auth.load(memberId), receiver, "010-0000-0000", "시범시 시범구 시범로 1", "");
        return orders.find(id);
    }

    private static long subOf(OrderDetail o, long sellerId) {
        for (OrderDetail.Sub s : o.subs()) {
            if (s.sub().sellerId() == sellerId) {
                return s.sub().id();
            }
        }
        throw new IllegalStateException("판매자별 주문을 찾을 수 없습니다.");
    }

    private long member(String email, String name, String role, boolean adult, String hash) {
        return Db.insert(db.sql("""
                INSERT INTO member (tenant_id, email, password_hash, name, role, adult_verified_at, created_at)
                VALUES (:t, :e, :h, :n, :r, :adult, :now)
                """)
                .param("t", tenant.id()).param("e", email).param("h", hash).param("n", name).param("r", role)
                .param("adult", adult ? clock.now() : null).param("now", clock.now()));
    }

    private long seller(long memberId, String shop, String biz, String intro, String status, String account,
                        String holder, String link, String licenseType, String licenseNo, boolean verified,
                        int fee, Integer freeMin) {
        return Db.insert(db.sql("""
                INSERT INTO seller (tenant_id, member_id, shop_name, biz_no, intro, status, bank_name, bank_account,
                                    bank_holder, pay_link, license_type, license_no, license_verified,
                                    base_ship_fee, free_ship_min, created_at)
                VALUES (:t, :m, :shop, :biz, :intro, :status, :bank, :account, :holder, :link, :lt, :ln, :verified,
                        :fee, :min, :now)
                """)
                .param("t", tenant.id()).param("m", memberId).param("shop", shop).param("biz", biz)
                .param("intro", intro).param("status", status).param("bank", account.isEmpty() ? "" : "시범은행")
                .param("account", account).param("holder", holder).param("link", link).param("lt", licenseType)
                .param("ln", licenseNo).param("verified", verified).param("fee", fee).param("min", freeMin)
                .param("now", clock.now()));
    }

    private void product(long sellerId, long categoryId, String name, String description, long price, int stock) {
        product(sellerId, categoryId, name, description, price, stock, null, null, null, "physical", "on_sale");
    }

    private void product(long sellerId, long categoryId, String name, String description, long price, int stock,
                         Long salePrice, LocalDate saleStart, LocalDate saleEnd, String type, String status) {
        db.sql("""
                INSERT INTO product (tenant_id, seller_id, category_id, name, description, product_type, price,
                                     sale_price, sale_start, sale_end, stock, status, created_at)
                VALUES (:t, :s, :c, :n, :d, :type, :price, :sale, :ss, :se, :stock, :status, :now)
                """)
                .param("t", tenant.id()).param("s", sellerId).param("c", categoryId).param("n", name)
                .param("d", description).param("type", type).param("price", price).param("sale", salePrice)
                .param("ss", saleStart).param("se", saleEnd).param("stock", stock).param("status", status)
                .param("now", clock.now()).update();
    }

    private void post(String email, String title, String body) {
        db.sql("INSERT INTO post (tenant_id, member_id, title, body, created_at) VALUES (:t, :m, :title, :body, :now)")
                .param("t", tenant.id()).param("m", memberId(email)).param("title", title).param("body", body)
                .param("now", clock.now()).update();
    }

    private long category(String name) {
        return db.sql("SELECT id FROM category WHERE tenant_id = :t AND name = :n")
                .param("t", tenant.id()).param("n", name).query(Long.class).single();
    }

    public long memberId(String email) {
        return db.sql("SELECT id FROM member WHERE email = :e").param("e", email).query(Long.class).single();
    }

    public long sellerId(String shopName) {
        return db.sql("SELECT id FROM seller WHERE shop_name = :n").param("n", shopName).query(Long.class).single();
    }

    public long productId(String name) {
        return db.sql("SELECT id FROM product WHERE name = :n").param("n", name).query(Long.class).single();
    }
}
