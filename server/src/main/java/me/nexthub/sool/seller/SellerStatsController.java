package me.nexthub.sool.seller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.auth.Seller;
import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.AppClock;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 판매 통계(docs/adr/0003 E). 판매자가 돈을 확인한 날(deposit_confirmed_at, 한국 시간 날짜)로 묶는다.
 * 취소·환불된 주문은 빼고, sales 는 상품 금액(단가 × 수량) 합이다. 배송비와 쿠폰 할인은 넣지 않는다.
 */
@RestController
public class SellerStatsController {

    public static final List<Integer> DAYS = List.of(7, 30, 90);
    private static final int TOP = 10;

    public record Totals(long orders, long sales, long items) {
    }

    public record Day(LocalDate date, long orders, long sales) {
    }

    public record TopProduct(long productId, String name, long qty, long sales) {
    }

    public record Stats(int days, LocalDate from, LocalDate to, Totals totals, List<Day> daily,
                        List<TopProduct> topProducts) {
    }

    private record SubSales(long id, LocalDateTime depositConfirmedAt, long sales, long items) {
    }

    private final JdbcClient db;
    private final Auth auth;
    private final AppClock clock;

    public SellerStatsController(JdbcClient db, Auth auth, AppClock clock) {
        this.db = db;
        this.auth = auth;
        this.clock = clock;
    }

    @GetMapping("/api/seller/stats")
    public Stats stats(@RequestParam(name = "days", required = false) Integer daysIn, HttpServletRequest request) {
        Seller seller = auth.requireSeller(request);
        int days = daysIn == null ? 30 : daysIn;
        if (!DAYS.contains(days)) {
            throw ApiException.bad("기간은 7일, 30일, 90일 가운데 골라 주세요.");
        }
        LocalDate to = clock.today();
        LocalDate from = to.minusDays(days - 1);
        // 한국 시간 날짜의 시작을 DB 에 저장한 UTC 시각으로 바꿔 범위를 정한다
        LocalDateTime start = from.atStartOfDay(AppClock.KST).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime end = to.plusDays(1).atStartOfDay(AppClock.KST).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        String range = """
                 WHERE so.seller_id = :s AND so.deposit_confirmed_at IS NOT NULL
                   AND so.status NOT IN ('cancelled', 'refunded')
                   AND so.deposit_confirmed_at >= :start AND so.deposit_confirmed_at < :end
                """;

        List<SubSales> subs = db.sql("""
                SELECT so.id, so.deposit_confirmed_at,
                       COALESCE(SUM(oi.unit_price * oi.qty), 0) AS sales, COALESCE(SUM(oi.qty), 0) AS items
                  FROM sub_order so JOIN order_item oi ON oi.sub_order_id = so.id
                """ + range + " GROUP BY so.id, so.deposit_confirmed_at")
                .param("s", seller.id()).param("start", start).param("end", end).query(SubSales.class).list();

        Map<LocalDate, long[]> byDay = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            byDay.put(d, new long[2]);
        }
        long orders = 0;
        long sales = 0;
        long items = 0;
        for (SubSales s : subs) {
            LocalDate day = s.depositConfirmedAt().atOffset(ZoneOffset.UTC).atZoneSameInstant(AppClock.KST).toLocalDate();
            long[] bucket = byDay.get(day);
            if (bucket != null) {
                bucket[0]++;
                bucket[1] += s.sales();
            }
            orders++;
            sales += s.sales();
            items += s.items();
        }
        List<Day> daily = new ArrayList<>();
        for (Map.Entry<LocalDate, long[]> e : byDay.entrySet()) {
            daily.add(new Day(e.getKey(), e.getValue()[0], e.getValue()[1]));
        }

        List<TopProduct> top = db.sql("""
                SELECT oi.product_id, p.name, SUM(oi.qty) AS qty, SUM(oi.unit_price * oi.qty) AS sales
                  FROM sub_order so
                  JOIN order_item oi ON oi.sub_order_id = so.id
                  JOIN product p ON p.id = oi.product_id
                """ + range + " GROUP BY oi.product_id, p.name ORDER BY sales DESC, qty DESC, oi.product_id LIMIT :top")
                .param("s", seller.id()).param("start", start).param("end", end).param("top", TOP)
                .query(TopProduct.class).list();

        return new Stats(days, from, to, new Totals(orders, sales, items), daily, top);
    }
}
