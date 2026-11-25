package me.nexthub.sool.coupon;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.AppClock;
import me.nexthub.sool.common.Db;
import me.nexthub.sool.common.Paged;
import me.nexthub.sool.common.Tenant;
import me.nexthub.sool.common.Text;
import me.nexthub.sool.notify.NotificationService;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.SimplePropertyRowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 규칙(docs/adr/0001-coupons.md): 만들기, 받기, 쓸 수 있는지 판단, 할인액 계산, 쓰기와 돌려주기.
 * 할인액 계산은 {@link #quote} 한 곳에만 둔다. 주문서의 쿠폰 목록과 실제 주문이 같은 값을 내야 하기 때문이다.
 */
@Service
public class CouponService {

    public static final List<String> DISCOUNT_TYPES = List.of("amount", "percent", "free_shipping");
    public static final List<String> ISSUE_TYPES = List.of("signup", "download", "code", "grant");
    public static final List<String> SELLER_ISSUE_TYPES = List.of("download", "code");
    public static final List<String> STATUSES = List.of("active", "paused", "ended");
    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9-]{4,40}$");
    private static final int PAGE_SIZE = 20;
    private static final int MY_PAGE_SIZE = 10;

    /** 만들기·고치기 요청. 판매자 요청에서 excludeAlcohol, issuer 는 무시하거나 거절한다. */
    public record CouponRequest(String name, String discountType, Integer discountValue, Long maxDiscount,
                                Long minOrder, Long categoryId, Boolean excludeAlcohol, String issueType, String code,
                                Integer totalLimit, String validFrom, String validTo, Integer validDays,
                                String issuer) {
    }

    /** 쿠폰 받기 화면의 한 줄 */
    public record Listing(Coupon coupon, boolean owned, boolean soldOut) {
    }

    /** 쿠폰 계산에 쓰는 상품 줄. parentCategoryId 는 상품 분류의 대분류(없으면 null) */
    public record Line(long categoryId, Long parentCategoryId, boolean alcohol, long amount) {
    }

    /** 판매자 한 곳의 묶음 */
    public record Basket(long sellerId, String shopName, List<Line> lines, long shipFee) {
    }

    /** 할인 계산 결과. perLine 은 basket.lines 와 같은 순서로 각 줄에 나눈 상품 할인 */
    public record Quote(long eligible, long itemDiscount, long shipDiscount, List<Long> perLine) {
    }

    public record Option(long memberCouponId, String name, String issuer, String discountType, long discount,
                         long shipDiscount) {
    }

    public record CheckoutGroup(long sellerId, String shopName, List<Option> options) {
    }

    private record CategoryParent(long id, Long parentId) {
    }

    private static final String COUPON_COLUMNS = """
            cp.id, cp.issuer, cp.seller_id, s.shop_name, cp.name, cp.discount_type, cp.discount_value,
            cp.max_discount, cp.min_order, cp.category_id, c.name AS category_name, cp.exclude_alcohol,
            cp.issue_type, cp.code, cp.total_limit, cp.issued_count, cp.valid_from, cp.valid_to, cp.valid_days,
            cp.status, cp.created_at
            """;
    private static final String COUPON_JOINS = """
             LEFT JOIN seller s ON s.id = cp.seller_id
             LEFT JOIN category c ON c.id = cp.category_id
            """;
    private static final String COUPON_SELECT = "SELECT " + COUPON_COLUMNS + " FROM coupon cp" + COUPON_JOINS;
    private static final String HELD_SELECT = """
            SELECT mc.id AS mc_id, mc.status AS mc_status, mc.issued_at AS mc_issued_at,
                   mc.expires_at AS mc_expires_at, mc.used_at AS mc_used_at,
            """ + COUPON_COLUMNS + " FROM member_coupon mc JOIN coupon cp ON cp.id = mc.coupon_id" + COUPON_JOINS;

    private final SimplePropertyRowMapper<Coupon> couponMapper = new SimplePropertyRowMapper<>(Coupon.class);

    private final JdbcClient db;
    private final Tenant tenant;
    private final AppClock clock;
    private final NotificationService notifications;

    public CouponService(JdbcClient db, Tenant tenant, AppClock clock, NotificationService notifications) {
        this.db = db;
        this.tenant = tenant;
        this.clock = clock;
        this.notifications = notifications;
    }

    // ---- 계산 (한 곳) ----

    /** 쿠폰 범위에 드는 줄인지: 분류(대분류면 아래 중분류 포함), 술 제외 */
    static boolean inScope(Coupon c, Line l) {
        if (c.excludeAlcohol() && l.alcohol()) {
            return false;
        }
        if (c.categoryId() == null) {
            return true;
        }
        long cat = c.categoryId();
        return l.categoryId() == cat || (l.parentCategoryId() != null && l.parentCategoryId() == cat);
    }

    /** 할인액. 쓸 수 있는지는 {@link #unusableReason} 로 먼저 확인한다. */
    public Quote quote(Coupon c, Basket b) {
        long eligible = 0;
        for (Line l : b.lines()) {
            if (inScope(c, l)) {
                eligible += l.amount();
            }
        }
        List<Long> perLine = new ArrayList<>();
        for (int i = 0; i < b.lines().size(); i++) {
            perLine.add(0L);
        }
        if ("free_shipping".equals(c.discountType())) {
            return new Quote(eligible, 0, b.shipFee(), perLine);
        }
        long discount;
        if ("percent".equals(c.discountType())) {
            discount = eligible * c.discountValue() / 100;
            if (c.maxDiscount() != null) {
                discount = Math.min(discount, c.maxDiscount());
            }
        } else {
            discount = Math.min(c.discountValue(), eligible);
        }
        // 대상 줄에 줄 금액 비율로 나눈다. 원 미만은 버리고, 남는 원은 마지막 대상 줄에 얹어 합이 정확히 맞게 한다.
        if (discount > 0 && eligible > 0) {
            long given = 0;
            int last = -1;
            for (int i = 0; i < b.lines().size(); i++) {
                Line l = b.lines().get(i);
                if (inScope(c, l)) {
                    long share = discount * l.amount() / eligible;
                    perLine.set(i, share);
                    given += share;
                    last = i;
                }
            }
            perLine.set(last, perLine.get(last) + (discount - given));
        }
        return new Quote(eligible, discount, 0, perLine);
    }

    /** 이 묶음에 이 쿠폰을 쓸 수 없는 이유. 쓸 수 있으면 null */
    public String unusableReason(MemberCoupon mc, Basket b) {
        Coupon c = mc.coupon();
        if (!"active".equals(c.status())) {
            return "지금은 쓸 수 없는 쿠폰입니다.";
        }
        if (!c.inPeriod(clock.today())) {
            return "쿠폰 사용 기간이 아닙니다.";
        }
        if (!"issued".equals(mc.status())) {
            return "이미 사용한 쿠폰입니다.";
        }
        if (!mc.expiresAt().isAfter(clock.now())) {
            return "기한이 지난 쿠폰입니다.";
        }
        if ("seller".equals(c.issuer()) && (c.sellerId() == null || c.sellerId() != b.sellerId())) {
            return "이 가게에서는 쓸 수 없는 쿠폰입니다.";
        }
        Quote q = quote(c, b);
        if ("free_shipping".equals(c.discountType())) {
            if (b.shipFee() <= 0) {
                return "배송비가 없는 주문에는 무료배송 쿠폰을 쓸 수 없습니다.";
            }
        } else if (q.eligible() <= 0) {
            return "이 가게 묶음에는 쿠폰을 쓸 수 있는 상품이 없습니다.";
        }
        if (q.eligible() < c.minOrder()) {
            return "쿠폰 대상 상품이 " + String.format("%,d원", c.minOrder()) + " 이상이어야 합니다.";
        }
        return null;
    }

    /** 분류 id → 대분류 id(없으면 null). 묶음을 만들 때 한 번 읽어 쓴다. */
    public Map<Long, Long> categoryParents() {
        Map<Long, Long> parents = new HashMap<>();
        for (CategoryParent c : db.sql("SELECT id, parent_id FROM category WHERE tenant_id = :t")
                .param("t", tenant.id()).query(CategoryParent.class).list()) {
            parents.put(c.id(), c.parentId());
        }
        return parents;
    }

    /** 주문서: 가게 묶음마다 지금 쓸 수 있는 내 쿠폰과 할인액 */
    public List<CheckoutGroup> options(long memberId, List<Basket> baskets) {
        List<MemberCoupon> usable = held(memberId, "usable", Integer.MAX_VALUE, 0);
        List<CheckoutGroup> result = new ArrayList<>();
        for (Basket b : baskets) {
            List<Option> options = new ArrayList<>();
            for (MemberCoupon mc : usable) {
                if (unusableReason(mc, b) == null) {
                    Quote q = quote(mc.coupon(), b);
                    Coupon c = mc.coupon();
                    options.add(new Option(mc.id(), c.name(), c.issuer(), c.discountType(), q.itemDiscount(),
                            q.shipDiscount()));
                }
            }
            result.add(new CheckoutGroup(b.sellerId(), b.shopName(), options));
        }
        return result;
    }

    // ---- 쓰기와 돌려주기 (주문 트랜잭션 안에서 부른다) ----

    /** 쓴 것으로 바꾼다. 같은 쿠폰으로 동시에 두 주문이 들어와도 한쪽만 성공한다. */
    public void markUsed(long memberCouponId, long memberId, long subOrderId) {
        LocalDateTime now = clock.now();
        int changed = db.sql("""
                UPDATE member_coupon SET status = 'used', used_at = :now, sub_order_id = :sub
                 WHERE id = :id AND member_id = :m AND status = 'issued' AND expires_at > :now
                """).param("now", now).param("sub", subOrderId).param("id", memberCouponId).param("m", memberId)
                .update();
        if (changed != 1) {
            throw ApiException.conflict("이미 사용했거나 기한이 지난 쿠폰입니다.");
        }
    }

    /** 판매자별 주문이 취소·환불되면 쓴 쿠폰을 돌려준다. 기한이 지났으면 돌려받아도 쓸 수 없다. */
    public void restore(long subOrderId) {
        db.sql("""
                UPDATE member_coupon SET status = 'issued', used_at = NULL, sub_order_id = NULL
                 WHERE sub_order_id = :s AND status = 'used'
                """).param("s", subOrderId).update();
    }

    // ---- 조회 ----

    public Coupon find(long id) {
        return db.sql(COUPON_SELECT + " WHERE cp.tenant_id = :t AND cp.id = :id")
                .param("t", tenant.id()).param("id", id).query(Coupon.class).optional().orElse(null);
    }

    /** 쿠폰 받기 화면: 지금 받을 수 있는 download 쿠폰. memberId 가 null 이면 owned 는 모두 false */
    public List<Listing> downloadable(Long memberId) {
        LocalDate today = clock.today();
        List<Coupon> list = db.sql(COUPON_SELECT + """
                 WHERE cp.tenant_id = :t AND cp.issue_type = 'download' AND cp.status = 'active'
                   AND cp.valid_from <= :today AND :today <= cp.valid_to
                   AND (cp.seller_id IS NULL OR s.status = 'approved')
                 ORDER BY cp.id DESC
                """).param("t", tenant.id()).param("today", today).query(Coupon.class).list();
        List<Long> owned = memberId == null ? List.of() : db.sql("SELECT coupon_id FROM member_coupon WHERE member_id = :m")
                .param("m", memberId).query(Long.class).list();
        List<Listing> result = new ArrayList<>();
        for (Coupon c : list) {
            result.add(new Listing(c.forBuyer(), owned.contains(c.id()), c.soldOut()));
        }
        return result;
    }

    /** status: usable(기본), used, expired */
    public Paged<MemberCoupon> mine(long memberId, String status, int page) {
        String st = status == null || status.isBlank() ? "usable" : status;
        if (!Text.in(st, List.of("usable", "used", "expired"))) {
            throw ApiException.bad("쿠폰 상태 값이 올바르지 않습니다.");
        }
        long total = db.sql("SELECT COUNT(*) FROM member_coupon mc WHERE mc.member_id = :m" + heldWhere(st))
                .param("m", memberId).param("now", clock.now()).query(Long.class).single();
        return new Paged<>(held(memberId, st, MY_PAGE_SIZE, Paged.offset(page, MY_PAGE_SIZE)), page, MY_PAGE_SIZE, total);
    }

    private static String heldWhere(String status) {
        return switch (status) {
            case "used" -> " AND mc.status = 'used'";
            case "expired" -> " AND mc.status = 'issued' AND mc.expires_at <= :now";
            default -> " AND mc.status = 'issued' AND mc.expires_at > :now";
        };
    }

    private List<MemberCoupon> held(long memberId, String status, int limit, int offset) {
        return db.sql(HELD_SELECT + " WHERE mc.member_id = :m" + heldWhere(status)
                        + " ORDER BY mc.id DESC LIMIT :limit OFFSET :offset")
                .param("m", memberId).param("now", clock.now()).param("limit", limit).param("offset", offset)
                .query(this::mapHeld).list();
    }

    /** 이 회원이 가진 쿠폰 한 장. 없거나 남의 것이면 null */
    public MemberCoupon held(long memberId, long memberCouponId) {
        return db.sql(HELD_SELECT + " WHERE mc.id = :id AND mc.member_id = :m AND mc.tenant_id = :t")
                .param("id", memberCouponId).param("m", memberId).param("t", tenant.id())
                .query(this::mapHeld).optional().orElse(null);
    }

    private MemberCoupon heldById(long memberCouponId) {
        return db.sql(HELD_SELECT + " WHERE mc.id = :id").param("id", memberCouponId).query(this::mapHeld).single();
    }

    private MemberCoupon mapHeld(ResultSet rs, int n) throws SQLException {
        Coupon c = couponMapper.mapRow(rs, n);
        String status = rs.getString("mc_status");
        LocalDateTime expiresAt = rs.getObject("mc_expires_at", LocalDateTime.class);
        boolean expired = "issued".equals(status) && !expiresAt.isAfter(clock.now());
        return new MemberCoupon(rs.getLong("mc_id"), c.forBuyer(), status,
                rs.getObject("mc_issued_at", LocalDateTime.class), expiresAt,
                rs.getObject("mc_used_at", LocalDateTime.class), expired);
    }

    public Paged<Coupon> listForSeller(long sellerId, int page) {
        long total = db.sql("SELECT COUNT(*) FROM coupon WHERE seller_id = :s").param("s", sellerId)
                .query(Long.class).single();
        List<Coupon> items = db.sql(COUPON_SELECT + " WHERE cp.seller_id = :s ORDER BY cp.id DESC LIMIT :limit OFFSET :offset")
                .param("s", sellerId).param("limit", PAGE_SIZE).param("offset", Paged.offset(page, PAGE_SIZE))
                .query(Coupon.class).list();
        return new Paged<>(items, page, PAGE_SIZE, total);
    }

    /** issuer 가 null 이면 전체 */
    public Paged<Coupon> listAll(String issuer, int page) {
        String is = issuer == null || issuer.isBlank() ? null : issuer;
        if (is != null && !Text.in(is, List.of("platform", "seller"))) {
            throw ApiException.bad("발행자 값이 올바르지 않습니다.");
        }
        String where = " WHERE cp.tenant_id = :t AND (:is IS NULL OR cp.issuer = :is)";
        long total = db.sql("SELECT COUNT(*) FROM coupon cp" + where).param("t", tenant.id()).param("is", is)
                .query(Long.class).single();
        List<Coupon> items = db.sql(COUPON_SELECT + where + " ORDER BY cp.id DESC LIMIT :limit OFFSET :offset")
                .param("t", tenant.id()).param("is", is).param("limit", PAGE_SIZE)
                .param("offset", Paged.offset(page, PAGE_SIZE)).query(Coupon.class).list();
        return new Paged<>(items, page, PAGE_SIZE, total);
    }

    // ---- 만들기, 상태 바꾸기 ----

    /** sellerId 가 있으면 판매자 쿠폰(받기·코드만, 술 제외 고정), 없으면 플랫폼 쿠폰 */
    @Transactional
    public Coupon create(CouponRequest r, Long sellerId) {
        boolean bySeller = sellerId != null;
        if (bySeller && r.issuer() != null && !"seller".equals(r.issuer())) {
            throw ApiException.forbidden("판매자는 플랫폼 쿠폰을 만들 수 없습니다.");
        }
        String name = Text.clean(r.name(), 60);
        if (name.isEmpty()) {
            throw ApiException.bad("쿠폰 이름을 입력해 주세요.");
        }
        if (r.name().trim().length() > 60) {
            throw ApiException.bad("쿠폰 이름은 60자까지 쓸 수 있습니다.");
        }
        String type = r.discountType();
        if (!Text.in(type, DISCOUNT_TYPES)) {
            throw ApiException.bad("할인 방식을 골라 주세요.");
        }
        int value = r.discountValue() == null ? 0 : r.discountValue();
        if ("amount".equals(type) && (value < 100 || value > 1_000_000)) {
            throw ApiException.bad("할인 금액은 100원~1,000,000원 사이로 입력해 주세요.");
        }
        if ("percent".equals(type) && (value < 1 || value > 90)) {
            throw ApiException.bad("할인 비율은 1~90% 사이로 입력해 주세요.");
        }
        if ("free_shipping".equals(type) && value != 0) {
            throw ApiException.bad("무료배송 쿠폰은 할인 값을 0으로 둡니다.");
        }
        // 상한은 비율 할인에만 뜻이 있다
        Long maxDiscount = "percent".equals(type) ? r.maxDiscount() : null;
        if (maxDiscount != null && maxDiscount < 1) {
            throw ApiException.bad("최대 할인 금액은 1원 이상으로 입력해 주세요.");
        }
        long minOrder = r.minOrder() == null ? 0 : r.minOrder();
        if (minOrder < 0) {
            throw ApiException.bad("최소 주문 금액은 0원 이상으로 입력해 주세요.");
        }
        // 금액 할인이 주문 금액보다 크면 0원 주문이 생긴다. 최소 주문 금액으로 막는다.
        if ("amount".equals(type) && minOrder < value) {
            throw ApiException.bad("최소 주문 금액은 할인 금액 이상이어야 합니다.");
        }
        if (r.categoryId() != null) {
            int found = db.sql("SELECT COUNT(*) FROM category WHERE id = :id AND tenant_id = :t")
                    .param("id", r.categoryId()).param("t", tenant.id()).query(Integer.class).single();
            if (found == 0) {
                throw ApiException.bad("분류를 찾을 수 없습니다.");
            }
        }
        String issueType = r.issueType();
        if (!Text.in(issueType, bySeller ? SELLER_ISSUE_TYPES : ISSUE_TYPES)) {
            throw ApiException.bad(bySeller ? "판매자는 쿠폰 받기·코드 입력 쿠폰만 만들 수 있습니다." : "받는 방법을 골라 주세요.");
        }
        String code = null;
        if ("code".equals(issueType)) {
            String raw = r.code() == null ? "" : r.code().trim();
            if (!CODE.matcher(raw).matches()) {
                throw ApiException.bad("쿠폰 코드는 영문·숫자·- 로 4~40자를 입력해 주세요.");
            }
            code = raw.toUpperCase();
            if (codeTaken(code)) {
                throw ApiException.conflict("이미 쓰고 있는 쿠폰 코드입니다.");
            }
        }
        if (r.totalLimit() != null && r.totalLimit() < 1) {
            throw ApiException.bad("발급 수 제한은 1장 이상이거나 비워 두세요.");
        }
        LocalDate from = Text.date(r.validFrom());
        LocalDate to = Text.date(r.validTo());
        if (from == null || to == null || from.isAfter(to)) {
            throw ApiException.bad("사용 기간을 바르게 입력해 주세요(시작일이 끝일보다 늦을 수 없습니다).");
        }
        if (r.validDays() != null && (r.validDays() < 1 || r.validDays() > 365)) {
            throw ApiException.bad("받은 뒤 쓸 수 있는 날 수는 1~365일 사이로 입력해 주세요.");
        }
        // 판매자 쿠폰은 술 할인 규제가 확인되기 전까지 항상 술을 뺀다
        boolean excludeAlcohol = bySeller || r.excludeAlcohol() == null || r.excludeAlcohol();
        long id;
        try {
            id = Db.insert(db.sql("""
                    INSERT INTO coupon (tenant_id, issuer, seller_id, name, discount_type, discount_value, max_discount,
                                        min_order, category_id, exclude_alcohol, issue_type, code, total_limit,
                                        valid_from, valid_to, valid_days, status, created_at)
                    VALUES (:t, :issuer, :seller, :name, :type, :value, :max, :min, :cat, :ex, :issueType, :code,
                            :limit, :from, :to, :days, 'active', :now)
                    """)
                    .param("t", tenant.id()).param("issuer", bySeller ? "seller" : "platform").param("seller", sellerId)
                    .param("name", name).param("type", type).param("value", value).param("max", maxDiscount)
                    .param("min", minOrder).param("cat", r.categoryId()).param("ex", excludeAlcohol)
                    .param("issueType", issueType).param("code", code).param("limit", r.totalLimit())
                    .param("from", from).param("to", to).param("days", r.validDays()).param("now", clock.now()));
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("이미 쓰고 있는 쿠폰 코드입니다.");
        }
        return find(id);
    }

    private boolean codeTaken(String code) {
        return db.sql("SELECT COUNT(*) FROM coupon WHERE tenant_id = :t AND code = :c")
                .param("t", tenant.id()).param("c", code).query(Integer.class).single() > 0;
    }

    /** sellerId 가 있으면 그 판매자 쿠폰만 바꿀 수 있다. 끝낸 쿠폰은 되돌릴 수 없다. */
    public Coupon setStatus(long couponId, Long sellerId, String status) {
        if (!Text.in(status, STATUSES)) {
            throw ApiException.bad("쿠폰 상태 값이 올바르지 않습니다.");
        }
        Coupon c = find(couponId);
        if (c == null || (sellerId != null && !sellerId.equals(c.sellerId()))) {
            throw ApiException.notFound();
        }
        int changed = db.sql("UPDATE coupon SET status = :st WHERE id = :id AND status <> 'ended'")
                .param("st", status).param("id", couponId).update();
        if (changed != 1) {
            throw ApiException.conflict("끝낸 쿠폰은 되돌릴 수 없습니다.");
        }
        return find(couponId);
    }

    // ---- 받기 ----

    /** 쿠폰 받기 화면에서 받기 */
    @Transactional
    public MemberCoupon claim(long memberId, long couponId) {
        Coupon c = find(couponId);
        if (c == null || !"download".equals(c.issueType())) {
            throw ApiException.notFound();
        }
        requireIssuable(c);
        return heldById(issue(c, memberId));
    }

    /** 코드 입력으로 받기. 대소문자는 가리지 않는다. */
    @Transactional
    public MemberCoupon redeem(long memberId, String codeIn) {
        String code = codeIn == null ? "" : codeIn.trim().toUpperCase();
        Coupon c = code.isEmpty() ? null : db.sql(COUPON_SELECT + " WHERE cp.tenant_id = :t AND cp.code = :c AND cp.issue_type = 'code'")
                .param("t", tenant.id()).param("c", code).query(Coupon.class).optional().orElse(null);
        if (c == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "없는 쿠폰 코드입니다.");
        }
        requireIssuable(c);
        return heldById(issue(c, memberId));
    }

    /** 운영자가 회원에게 직접 지급. 코드 쿠폰은 코드로만 받게 한다. */
    @Transactional
    public MemberCoupon grant(long couponId, String emailIn) {
        Coupon c = find(couponId);
        if (c == null) {
            throw ApiException.notFound();
        }
        if (!Text.in(c.issueType(), List.of("grant", "signup", "download"))) {
            throw ApiException.bad("코드 입력 쿠폰은 직접 지급할 수 없습니다.");
        }
        String email = Text.clean(emailIn, 190).toLowerCase();
        Long memberId = db.sql("SELECT id FROM member WHERE tenant_id = :t AND email = :e AND status = 'active'")
                .param("t", tenant.id()).param("e", email).query(Long.class).optional().orElse(null);
        if (memberId == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "그 이메일로 가입한 회원을 찾을 수 없습니다.");
        }
        if (!"active".equals(c.status()) || clock.today().isAfter(c.validTo())) {
            throw ApiException.conflict("멈췄거나 기간이 끝난 쿠폰은 지급할 수 없습니다.");
        }
        long id = issue(c, memberId);
        notifications.notify(memberId, "쿠폰이 도착했습니다", "'" + c.name() + "' 쿠폰을 받았습니다.", "/my/coupons");
        return heldById(id);
    }

    /** 가입하면 지금 기간 안인 가입 쿠폰을 모두 준다. 다 떨어진 쿠폰은 건너뛴다. */
    @Transactional
    public int issueSignup(long memberId) {
        LocalDate today = clock.today();
        List<Coupon> list = db.sql(COUPON_SELECT + """
                 WHERE cp.tenant_id = :t AND cp.issue_type = 'signup' AND cp.status = 'active'
                   AND cp.valid_from <= :today AND :today <= cp.valid_to
                 ORDER BY cp.id
                """).param("t", tenant.id()).param("today", today).query(Coupon.class).list();
        int count = 0;
        for (Coupon c : list) {
            // 이미 가졌거나 다 떨어진 경우는 issue 가 쓰기 전에 거절하므로 건너뛰어도 남는 것이 없다
            try {
                issue(c, memberId);
                count++;
            } catch (ApiException e) {
                // 건너뛴다
            }
        }
        if (count > 0) {
            notifications.notify(memberId, "가입 축하 쿠폰이 도착했습니다", "내 쿠폰에서 확인하고 주문할 때 써 보세요.", "/my/coupons");
        }
        return count;
    }

    private void requireIssuable(Coupon c) {
        if (!"active".equals(c.status()) || !c.inPeriod(clock.today())) {
            throw ApiException.conflict("지금은 받을 수 없는 쿠폰입니다.");
        }
        if (c.sellerId() != null) {
            String sellerStatus = db.sql("SELECT status FROM seller WHERE id = :id").param("id", c.sellerId())
                    .query(String.class).single();
            if (!"approved".equals(sellerStatus)) {
                throw ApiException.conflict("지금은 받을 수 없는 쿠폰입니다.");
            }
        }
    }

    /** 한 장 지급하고 새 회원 쿠폰 id 를 돌려준다. 호출하는 쪽이 트랜잭션을 연다. */
    private long issue(Coupon c, long memberId) {
        int owned = db.sql("SELECT COUNT(*) FROM member_coupon WHERE coupon_id = :c AND member_id = :m")
                .param("c", c.id()).param("m", memberId).query(Integer.class).single();
        if (owned > 0) {
            throw ApiException.conflict("이미 받은 쿠폰입니다.");
        }
        // 발급 수 제한: 남은 수량이 있을 때만 하나 올린다. 동시에 받아도 제한을 넘지 않는다.
        int counted = db.sql("""
                UPDATE coupon SET issued_count = issued_count + 1
                 WHERE id = :id AND (total_limit IS NULL OR issued_count < total_limit)
                """).param("id", c.id()).update();
        if (counted != 1) {
            throw ApiException.conflict("쿠폰이 모두 소진되었습니다.");
        }
        try {
            return Db.insert(db.sql("""
                    INSERT INTO member_coupon (tenant_id, coupon_id, member_id, status, issued_at, expires_at)
                    VALUES (:t, :c, :m, 'issued', :now, :exp)
                    """)
                    .param("t", tenant.id()).param("c", c.id()).param("m", memberId).param("now", clock.now())
                    .param("exp", expiresAt(c)));
        } catch (DuplicateKeyException e) {
            // 같은 사람이 동시에 두 번 눌렀을 때. 예외로 트랜잭션이 되돌아가 올린 발급 수도 되돌아간다.
            throw ApiException.conflict("이미 받은 쿠폰입니다.");
        }
    }

    /**
     * 만료 시각(UTC). 기본은 쿠폰 기간 끝날(한국 시간) 자정.
     * validDays 가 있으면 받은 날을 1일째로 세어 그 마지막 날 자정까지이며, 쿠폰 기간 끝을 넘지 않는다.
     */
    LocalDateTime expiresAt(Coupon c) {
        LocalDate last = c.validTo();
        if (c.validDays() != null) {
            LocalDate byDays = clock.today().plusDays(c.validDays() - 1L);
            if (byDays.isBefore(last)) {
                last = byDays;
            }
        }
        return last.plusDays(1).atStartOfDay(AppClock.KST).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
