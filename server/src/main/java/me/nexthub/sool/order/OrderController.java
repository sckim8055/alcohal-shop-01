package me.nexthub.sool.order;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.auth.Member;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.AppClock;
import me.nexthub.sool.common.Paged;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 구매자의 주문, 입금 신고, 취소, 구매확정, 영수증. */
@RestController
public class OrderController {

    public record CreateRequest(String receiver, String phone, String address, String cashReceiptNo,
                                List<OrderService.CouponChoice> coupons) {
    }

    public record DepositRequest(String depositor) {
    }

    private static final Map<String, Boolean> OK = Map.of("ok", true);

    private final Auth auth;
    private final OrderService orders;
    private final TrackingService tracking;
    private final AppClock clock;

    public OrderController(Auth auth, OrderService orders, TrackingService tracking, AppClock clock) {
        this.clock = clock;
        this.auth = auth;
        this.orders = orders;
        this.tracking = tracking;
    }

    @GetMapping("/api/carriers")
    public List<TrackingService.Carrier> carriers() {
        return tracking.carriers();
    }

    @PostMapping("/api/orders")
    public OrderDetail create(@RequestBody CreateRequest body, HttpServletRequest request) {
        Member user = auth.requireUser(request);
        long id = orders.createOrder(user, body.receiver(), body.phone(), body.address(), body.cashReceiptNo(), body.coupons());
        return orders.find(id);
    }

    /** period: 6m(최근 6개월, 기본), all(전체), 또는 연도(예: 2026) */
    @GetMapping("/api/orders")
    public Paged<OrderDetail> list(@RequestParam(name = "period", required = false) String period,
                                   @RequestParam(name = "page", required = false) Integer page,
                                   HttpServletRequest request) {
        LocalDateTime from = null;
        LocalDateTime to = null;
        LocalDate today = clock.today();
        if (period != null && period.matches("\\d{4}")) {
            int year = Integer.parseInt(period);
            from = kstStart(LocalDate.of(year, 1, 1));
            to = kstStart(LocalDate.of(year + 1, 1, 1));
        } else if (!"all".equals(period)) {
            from = kstStart(today.minusMonths(6));
        }
        return orders.listForBuyer(auth.requireUser(request).id(), from, to, Paged.page(page), 10);
    }

    private static LocalDateTime kstStart(LocalDate date) {
        return date.atStartOfDay(AppClock.KST).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** 주문 상세 겸 통합 영수증 자료: 구매자 본인과 운영자만 볼 수 있다. */
    @GetMapping("/api/orders/{id}")
    public OrderDetail detail(@PathVariable("id") long id, HttpServletRequest request) {
        Member user = auth.requireUser(request);
        OrderDetail o = orders.find(id);
        if (o == null || (o.order().buyerId() != user.id() && !user.admin())) {
            throw ApiException.notFound();
        }
        return o;
    }

    /** 판매자별 영수증 자료: 구매자, 운영자, 그리고 해당 판매자가 볼 수 있다. */
    @GetMapping("/api/orders/{id}/receipt/{subId}")
    public OrderDetail splitReceipt(@PathVariable("id") long id, @PathVariable("subId") long subId,
                                    HttpServletRequest request) {
        Member user = auth.requireUser(request);
        OrderDetail o = orders.find(id);
        if (o == null) {
            throw ApiException.notFound();
        }
        for (OrderDetail.Sub s : o.subs()) {
            if (s.sub().id() == subId) {
                boolean allowed = o.order().buyerId() == user.id() || user.admin()
                        || s.sub().sellerMemberId() == user.id();
                if (allowed) {
                    return new OrderDetail(o.order(), List.of(s));
                }
            }
        }
        throw ApiException.notFound();
    }

    @PostMapping("/api/suborders/{id}/report")
    public Map<String, Boolean> report(@PathVariable("id") long id, @RequestBody DepositRequest body,
                                       HttpServletRequest request) {
        orders.reportDeposit(id, auth.requireUser(request).id(), body.depositor());
        return OK;
    }

    @PostMapping("/api/suborders/{id}/cancel")
    public Map<String, Boolean> cancel(@PathVariable("id") long id, HttpServletRequest request) {
        orders.cancelByBuyer(id, auth.requireUser(request).id());
        return OK;
    }

    @PostMapping("/api/suborders/{id}/receive")
    public Map<String, Boolean> receive(@PathVariable("id") long id, HttpServletRequest request) {
        orders.confirmReceipt(id, auth.requireUser(request).id());
        return OK;
    }
}
