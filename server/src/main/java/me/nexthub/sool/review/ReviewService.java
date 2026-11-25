package me.nexthub.sool.review;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.AppClock;
import me.nexthub.sool.common.Db;
import me.nexthub.sool.common.Names;
import me.nexthub.sool.common.Paged;
import me.nexthub.sool.common.Tenant;
import me.nexthub.sool.common.Text;
import me.nexthub.sool.file.FileController;
import me.nexthub.sool.notify.NotificationService;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * 리뷰 규칙(docs/adr/0003 A). 구매 확정된 상품 줄마다 한 번만 쓸 수 있고, 지워도 줄을 남겨 다시 쓰지 못하게 한다
 * (별점을 지웠다 다시 쓰며 바꾸는 일을 막기 위해서다). 상태 변경은 모두 조건부 UPDATE 다.
 */
@Service
public class ReviewService {

    public static final List<String> STATUSES = List.of("visible", "hidden", "deleted");
    /** 구매 확정 후 이 날 수 안에만 리뷰를 쓸 수 있다("쓸 수 있는 리뷰" 목록과 같은 기준) */
    public static final int WRITABLE_DAYS = 90;
    public static final int MAX_IMAGES = 3;
    private static final int PRODUCT_PAGE_SIZE = 10;
    private static final int MY_PAGE_SIZE = 10;
    private static final int PAGE_SIZE = 20;

    public record WriteRequest(Long orderItemId, Integer rating, String body, List<String> images) {
    }

    public record Writable(long orderItemId, long orderId, long productId, String productName, String shopName,
                           int qty, LocalDateTime completedAt) {
    }

    /** counts: 별 1개부터 5개까지 순서대로 리뷰 수 */
    public record ProductReviews(Double ratingAvg, long reviewCount, List<Long> counts, Paged<Review> page) {
    }

    /** 가게 화면에 보여 줄 가게 전체 평점 */
    public record Stats(long reviewCount, Double ratingAvg) {
    }

    /** 권한 확인에 필요한 값(쓴 회원, 가게)까지 담은 내부용 줄. 응답으로는 {@link Review} 만 내보낸다. */
    record Row(long id, long productId, String productName, long sellerId, long memberId, String memberName,
               int rating, String body, String images, String status, String sellerReply,
               LocalDateTime repliedAt, LocalDateTime createdAt, long sellerMemberId) {
    }

    private record ItemState(long id, long productId, long sellerId, long buyerId, String status,
                             LocalDateTime completedAt, String name, long sellerMemberId) {
    }

    private record RatingCount(int rating, long cnt) {
    }

    private record SumCount(long total, long cnt) {
    }

    private static final String SELECT = """
            SELECT r.id, r.product_id, p.name AS product_name, r.seller_id, r.member_id, m.name AS member_name,
                   r.rating, r.body, r.images, r.status, r.seller_reply, r.replied_at, r.created_at,
                   s.member_id AS seller_member_id
              FROM review r
              JOIN product p ON p.id = r.product_id
              JOIN member m ON m.id = r.member_id
              JOIN seller s ON s.id = r.seller_id
            """;

    private final JdbcClient db;
    private final Tenant tenant;
    private final AppClock clock;
    private final FileController files;
    private final NotificationService notifications;

    public ReviewService(JdbcClient db, Tenant tenant, AppClock clock, FileController files,
                         NotificationService notifications) {
        this.db = db;
        this.tenant = tenant;
        this.clock = clock;
        this.files = files;
        this.notifications = notifications;
    }

    // ---- 읽기 ----

    Row row(long id) {
        return db.sql(SELECT + " WHERE r.id = :id AND r.tenant_id = :t").param("id", id).param("t", tenant.id())
                .query(Row.class).optional().orElse(null);
    }

    /** masked: 여러 사람이 보는 목록이면 이름 가운데를 가린다. viewerId 는 로그인하지 않았으면 null */
    static Review view(Row r, Long viewerId, boolean masked) {
        List<String> images = r.images() == null || r.images().isEmpty()
                ? List.of() : Arrays.asList(r.images().split(","));
        return new Review(r.id(), r.productId(), r.productName(), r.rating(), r.body(), images,
                masked ? Names.mask(r.memberName()) : r.memberName(),
                viewerId != null && viewerId == r.memberId(), r.status(), r.sellerReply(), r.repliedAt(), r.createdAt());
    }

    private Paged<Review> list(String where, Map<String, ?> params, int page, int size,
                               Long viewerId, boolean masked) {
        var count = db.sql("SELECT COUNT(*) FROM review r" + where).params(params);
        var query = db.sql(SELECT + where + " ORDER BY r.id DESC LIMIT :limit OFFSET :offset").params(params)
                .param("limit", size).param("offset", Paged.offset(page, size));
        List<Review> items = new ArrayList<>();
        for (Row r : query.query(Row.class).list()) {
            items.add(view(r, viewerId, masked));
        }
        return new Paged<>(items, page, size, count.query(Long.class).single());
    }

    /** 상품 화면: 보이는 리뷰만, 이름은 가린다. */
    public ProductReviews forProduct(long productId, Long viewerId, int page) {
        long[] counts = new long[5];
        long total = 0;
        long sum = 0;
        for (RatingCount c : db.sql("""
                SELECT rating, COUNT(*) AS cnt FROM review
                 WHERE product_id = :p AND tenant_id = :t AND status = 'visible' GROUP BY rating
                """).param("p", productId).param("t", tenant.id()).query(RatingCount.class).list()) {
            counts[c.rating() - 1] = c.cnt();
            total += c.cnt();
            sum += (long) c.rating() * c.cnt();
        }
        List<Long> countList = new ArrayList<>();
        for (long c : counts) {
            countList.add(c);
        }
        Paged<Review> items = list(" WHERE r.product_id = :p AND r.tenant_id = :t AND r.status = 'visible'",
                Map.of("p", productId, "t", tenant.id()), page, PRODUCT_PAGE_SIZE, viewerId, true);
        return new ProductReviews(average(sum, total), total, countList, items);
    }

    /** 소수 한 자리 반올림. 상품 목록의 평점(SQL ROUND)과 같은 값이 나오게 HALF_UP 으로 한다. */
    static Double average(long sum, long count) {
        if (count == 0) {
            return null;
        }
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP).doubleValue();
    }

    public Stats statsForSeller(long sellerId) {
        SumCount sumRow = db.sql("""
                SELECT COALESCE(SUM(rating), 0) AS total, COUNT(*) AS cnt FROM review
                 WHERE seller_id = :s AND tenant_id = :t AND status = 'visible'
                """).param("s", sellerId).param("t", tenant.id()).query(SumCount.class).single();
        return new Stats(sumRow.cnt(), average(sumRow.total(), sumRow.cnt()));
    }

    /** 내가 쓴 리뷰(숨김 포함, 지운 것 제외) */
    public Paged<Review> mine(long memberId, int page) {
        return list(" WHERE r.member_id = :m AND r.status IN ('visible', 'hidden')", Map.of("m", memberId),
                page, MY_PAGE_SIZE, memberId, false);
    }

    /** 아직 리뷰를 쓰지 않은, 최근 90일 안에 구매 확정한 상품 줄 */
    public List<Writable> writable(long memberId) {
        return db.sql("""
                SELECT oi.id AS order_item_id, so.order_id, oi.product_id, oi.name AS product_name, s.shop_name,
                       oi.qty, so.completed_at
                  FROM order_item oi
                  JOIN sub_order so ON so.id = oi.sub_order_id
                  JOIN shop_order o ON o.id = so.order_id
                  JOIN seller s ON s.id = so.seller_id
                 WHERE o.buyer_id = :m AND so.tenant_id = :t AND so.status = 'completed' AND so.completed_at >= :since
                   AND NOT EXISTS (SELECT 1 FROM review r WHERE r.order_item_id = oi.id)
                 ORDER BY so.completed_at DESC, oi.id
                """).param("m", memberId).param("t", tenant.id())
                .param("since", clock.now().minusDays(WRITABLE_DAYS)).query(Writable.class).list();
    }

    /**
     * 판매자: 내 가게 상품의 리뷰(지운 것 제외, 운영자가 숨긴 것은 포함). unanswered 면 답글이 없는 보이는 리뷰만
     * (가게 현황의 "답글 없는 리뷰" 숫자와 같은 기준. 숨긴 리뷰는 답할 일로 세지 않는다).
     */
    public Paged<Review> forSeller(long sellerId, boolean unanswered, int page) {
        return list(" WHERE r.seller_id = :s AND "
                        + (unanswered ? "r.status = 'visible' AND r.seller_reply IS NULL" : "r.status IN ('visible', 'hidden')"),
                Map.of("s", sellerId), page, PAGE_SIZE, null, false);
    }

    /** 운영자: 모든 리뷰. status 를 비우면 전체 */
    public Paged<Review> forAdmin(String status, int page) {
        String st = status == null || status.isBlank() ? null : status.trim();
        if (st != null && !Text.in(st, STATUSES)) {
            throw ApiException.bad("리뷰 상태 값이 올바르지 않습니다.");
        }
        Map<String, Object> params = new HashMap<>();
        params.put("t", tenant.id());
        if (st != null) {
            params.put("st", st);
        }
        return list(" WHERE r.tenant_id = :t" + (st == null ? "" : " AND r.status = :st"), params, page, PAGE_SIZE,
                null, false);
    }

    // ---- 쓰기 ----

    public Review write(long memberId, WriteRequest body) {
        if (body == null || body.orderItemId() == null) {
            throw ApiException.bad("리뷰를 쓸 상품을 골라 주세요.");
        }
        Integer rating = body.rating();
        if (rating == null || rating < 1 || rating > 5) {
            throw ApiException.bad("별점을 1개에서 5개 사이로 골라 주세요.");
        }
        String text = body.body() == null ? "" : body.body().trim();
        if (text.length() < 10 || text.length() > 1000) {
            throw ApiException.bad("리뷰는 10자 이상 1,000자 이하로 써 주세요.");
        }
        List<String> images = body.images() == null ? List.of() : body.images();
        if (images.size() > MAX_IMAGES) {
            throw ApiException.bad("사진은 " + MAX_IMAGES + "장까지 올릴 수 있습니다.");
        }
        List<String> urls = new ArrayList<>();
        for (String url : images) {
            String u = url == null ? "" : url.trim();
            // 남이 올린 파일 주소나 동영상은 받지 않는다. 종류는 올릴 때 서버가 판단한 값을 따른다.
            if (!"image".equals(files.kindOfOwned(u, memberId))) {
                throw ApiException.bad("올린 사진을 찾을 수 없습니다. 사진을 다시 올려 주세요.");
            }
            urls.add(u);
        }

        ItemState item = db.sql("""
                SELECT oi.id, oi.product_id, so.seller_id, o.buyer_id, so.status, so.completed_at, oi.name,
                       s.member_id AS seller_member_id
                  FROM order_item oi
                  JOIN sub_order so ON so.id = oi.sub_order_id
                  JOIN shop_order o ON o.id = so.order_id
                  JOIN seller s ON s.id = so.seller_id
                 WHERE oi.id = :id AND so.tenant_id = :t
                """).param("id", body.orderItemId()).param("t", tenant.id()).query(ItemState.class)
                .optional().orElse(null);
        if (item == null || item.buyerId() != memberId) {
            throw ApiException.notFound();
        }
        if ("refunded".equals(item.status()) || "cancelled".equals(item.status())) {
            throw ApiException.conflict("환불되었거나 취소된 주문에는 리뷰를 쓸 수 없습니다.");
        }
        if (!"completed".equals(item.status())) {
            throw ApiException.conflict("구매를 확정한 뒤에 리뷰를 쓸 수 있습니다.");
        }
        if (item.completedAt() == null || item.completedAt().isBefore(clock.now().minusDays(WRITABLE_DAYS))) {
            throw ApiException.conflict("구매 확정 후 " + WRITABLE_DAYS + "일이 지나 리뷰를 쓸 수 없습니다.");
        }
        String existing = db.sql("SELECT status FROM review WHERE order_item_id = :i").param("i", item.id())
                .query(String.class).optional().orElse(null);
        if (existing != null) {
            throw ApiException.conflict("deleted".equals(existing)
                    ? "지운 리뷰는 다시 쓸 수 없습니다." : "이 상품에는 이미 리뷰를 썼습니다.");
        }
        long id;
        try {
            id = Db.insert(db.sql("""
                    INSERT INTO review (tenant_id, product_id, order_item_id, seller_id, member_id, rating, body, images,
                                        status, created_at)
                    VALUES (:t, :p, :i, :s, :m, :rating, :body, :images, 'visible', :now)
                    """)
                    .param("t", tenant.id()).param("p", item.productId()).param("i", item.id())
                    .param("s", item.sellerId()).param("m", memberId).param("rating", rating).param("body", text)
                    .param("images", String.join(",", urls)).param("now", clock.now()));
        } catch (DuplicateKeyException e) {
            // 같은 상품 줄에 두 요청이 동시에 온 경우
            throw ApiException.conflict("이 상품에는 이미 리뷰를 썼습니다.");
        }
        notifications.notify(item.sellerMemberId(), "새 리뷰가 달렸습니다",
                item.name() + " 상품에 별 " + rating + "개 리뷰가 달렸습니다. 내 가게의 리뷰에서 답글을 달 수 있습니다.",
                "/seller/reviews");
        return view(row(id), memberId, false);
    }

    /** 쓴 사람이 지운다. 줄은 남겨 같은 상품 줄에 다시 쓰지 못하게 한다. */
    public void delete(long memberId, long reviewId) {
        Row r = row(reviewId);
        if (r == null || r.memberId() != memberId || "deleted".equals(r.status())) {
            throw ApiException.notFound();
        }
        int changed = db.sql("""
                UPDATE review SET status = 'deleted' WHERE id = :id AND member_id = :m AND status IN ('visible', 'hidden')
                """).param("id", reviewId).param("m", memberId).update();
        if (changed != 1) {
            throw ApiException.conflict("이미 지운 리뷰입니다.");
        }
    }

    /** 판매자 답글. 한 번 달고 그 뒤로는 고칠 수 있다. 알림은 처음 달 때만 보낸다. */
    public Review reply(long sellerId, long reviewId, String replyIn) {
        Row r = row(reviewId);
        if (r == null || r.sellerId() != sellerId || "deleted".equals(r.status())) {
            throw ApiException.notFound();
        }
        String reply = replyIn == null ? "" : replyIn.trim();
        if (reply.isEmpty()) {
            throw ApiException.bad("답글을 입력해 주세요.");
        }
        if (reply.length() > 500) {
            throw ApiException.bad("답글은 500자까지 쓸 수 있습니다.");
        }
        int changed = db.sql("""
                UPDATE review SET seller_reply = :reply, replied_at = :now
                 WHERE id = :id AND seller_id = :s AND status IN ('visible', 'hidden')
                """).param("reply", reply).param("now", clock.now()).param("id", reviewId).param("s", sellerId).update();
        if (changed != 1) {
            throw ApiException.conflict("구매자가 지운 리뷰에는 답글을 달 수 없습니다.");
        }
        if (r.sellerReply() == null) {
            notifications.notify(r.memberId(), "리뷰에 판매자 답글이 달렸습니다",
                    r.productName() + " 리뷰에 판매자가 답글을 달았습니다.", "/products/" + r.productId());
        }
        return view(row(reviewId), null, false);
    }

    /** 운영자: 숨기기(hidden = true) 또는 다시 보이기. 이미 그 상태면 그대로 둔다. */
    public Review hide(long reviewId, Boolean hidden) {
        if (hidden == null) {
            throw ApiException.bad("숨길지 보일지 골라 주세요.");
        }
        Row r = row(reviewId);
        if (r == null) {
            throw ApiException.notFound();
        }
        int changed = db.sql("""
                UPDATE review SET status = :next WHERE id = :id AND tenant_id = :t AND status IN ('visible', 'hidden')
                """).param("next", hidden ? "hidden" : "visible").param("id", reviewId).param("t", tenant.id())
                .update();
        if (changed != 1) {
            throw ApiException.conflict("쓴 사람이 지운 리뷰입니다.");
        }
        return view(row(reviewId), null, false);
    }
}
