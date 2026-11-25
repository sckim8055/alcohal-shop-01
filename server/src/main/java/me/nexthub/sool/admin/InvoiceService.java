package me.nexthub.sool.admin;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;

import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.AppClock;
import me.nexthub.sool.common.Tenant;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 월 수수료 청구서. 그 달(한국 시간)에 입금 확인된 주문 중 취소·환불되지 않은 것만 계산한다.
 * 수수료는 상품 금액에만 붙고 배송비에는 붙지 않는다.
 */
@Service
public class InvoiceService {

    public record Invoice(long id, long sellerId, String shopName, String periodYm, long salesTotal, long amount,
                          String status, LocalDateTime issuedAt, LocalDateTime paidAt) {
    }

    public record Amounts(long sales, long amount) {
    }

    private record Line(long unitPrice, int qty, int commissionRateBp) {
    }

    private record Existing(long id, String status) {
    }

    private static final String INVOICE_SELECT = """
            SELECT ci.id, ci.seller_id, s.shop_name, ci.period_ym, ci.sales_total, ci.amount, ci.status,
                   ci.issued_at, ci.paid_at
              FROM commission_invoice ci JOIN seller s ON s.id = ci.seller_id
            """;

    private final JdbcClient db;
    private final Tenant tenant;
    private final AppClock clock;

    public InvoiceService(JdbcClient db, Tenant tenant, AppClock clock) {
        this.db = db;
        this.tenant = tenant;
        this.clock = clock;
    }

    public static YearMonth parsePeriod(String period) {
        try {
            if (period != null && period.matches("\\d{4}-\\d{2}")) {
                return YearMonth.parse(period);
            }
        } catch (DateTimeParseException e) {
            // 아래에서 안내한다
        }
        throw ApiException.bad("기간은 YYYY-MM 형식으로 입력해 주세요.");
    }

    public Amounts compute(long sellerId, YearMonth period) {
        LocalDateTime from = period.atDay(1).atStartOfDay(AppClock.KST)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime to = period.plusMonths(1).atDay(1).atStartOfDay(AppClock.KST)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        List<Line> lines = db.sql("""
                SELECT oi.unit_price, oi.qty, oi.commission_rate_bp
                  FROM order_item oi JOIN sub_order so ON so.id = oi.sub_order_id
                 WHERE so.seller_id = :s
                   AND so.status IN ('paid', 'shipped', 'delivered', 'completed')
                   AND so.deposit_confirmed_at >= :from AND so.deposit_confirmed_at < :to
                """).param("s", sellerId).param("from", from).param("to", to).query(Line.class).list();
        long sales = 0;
        long amount = 0;
        for (Line l : lines) {
            long line = l.unitPrice() * l.qty();
            sales += line;
            amount += (line * l.commissionRateBp() + 5_000) / 10_000;   // 줄 단위로 원 미만 반올림
        }
        return new Amounts(sales, amount);
    }

    /** 승인된 판매자 전체의 청구서를 만들거나 다시 계산한다. 납부 완료된 청구서는 건드리지 않는다. */
    @Transactional
    public int generate(String periodText) {
        YearMonth period = parsePeriod(periodText);
        String ym = period.toString();
        int count = 0;
        List<Long> sellers = db.sql("SELECT id FROM seller WHERE tenant_id = :t AND status = 'approved'")
                .param("t", tenant.id()).query(Long.class).list();
        for (Long sellerId : sellers) {
            Amounts a = compute(sellerId, period);
            Existing existing = db.sql("SELECT id, status FROM commission_invoice WHERE seller_id = :s AND period_ym = :p")
                    .param("s", sellerId).param("p", ym).query(Existing.class).optional().orElse(null);
            if (existing != null && "paid".equals(existing.status())) {
                continue;
            }
            if (existing != null) {
                db.sql("UPDATE commission_invoice SET sales_total = :sales, amount = :amount, issued_at = :now WHERE id = :id")
                        .param("sales", a.sales()).param("amount", a.amount()).param("now", clock.now())
                        .param("id", existing.id()).update();
                count++;
            } else if (a.sales() > 0) {
                db.sql("""
                        INSERT INTO commission_invoice (tenant_id, seller_id, period_ym, sales_total, amount, issued_at)
                        VALUES (:t, :s, :p, :sales, :amount, :now)
                        """)
                        .param("t", tenant.id()).param("s", sellerId).param("p", ym)
                        .param("sales", a.sales()).param("amount", a.amount()).param("now", clock.now()).update();
                count++;
            }
        }
        return count;
    }

    public void markPaid(long invoiceId) {
        db.sql("UPDATE commission_invoice SET status = 'paid', paid_at = :now WHERE id = :id AND tenant_id = :t AND status = 'issued'")
                .param("now", clock.now()).param("id", invoiceId).param("t", tenant.id()).update();
    }

    public List<Invoice> listAll() {
        return db.sql(INVOICE_SELECT + " WHERE ci.tenant_id = :t ORDER BY ci.period_ym DESC, ci.seller_id")
                .param("t", tenant.id()).query(Invoice.class).list();
    }

    public List<Invoice> listForSeller(long sellerId) {
        return db.sql(INVOICE_SELECT + " WHERE ci.seller_id = :s ORDER BY ci.period_ym DESC")
                .param("s", sellerId).query(Invoice.class).list();
    }
}
