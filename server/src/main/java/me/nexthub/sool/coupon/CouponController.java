package me.nexthub.sool.coupon;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.auth.Member;
import me.nexthub.sool.common.Paged;
import me.nexthub.sool.order.OrderService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 구매자: 쿠폰 받기, 코드 입력, 내 쿠폰, 주문서에서 쓸 수 있는 쿠폰. */
@RestController
public class CouponController {

    public record RedeemRequest(String code) {
    }

    private final Auth auth;
    private final CouponService coupons;
    private final OrderService orders;

    public CouponController(Auth auth, CouponService coupons, OrderService orders) {
        this.auth = auth;
        this.coupons = coupons;
        this.orders = orders;
    }

    /** 로그인하지 않아도 볼 수 있다. */
    @GetMapping("/api/coupons")
    public List<CouponService.Listing> list(HttpServletRequest request) {
        Member user = auth.user(request);
        return coupons.downloadable(user == null ? null : user.id());
    }

    @PostMapping("/api/coupons/{id}/claim")
    public MemberCoupon claim(@PathVariable("id") long id, HttpServletRequest request) {
        Member user = auth.requireUser(request);
        return coupons.claim(user.id(), id);
    }

    @PostMapping("/api/coupons/redeem")
    public MemberCoupon redeem(@RequestBody RedeemRequest body, HttpServletRequest request) {
        Member user = auth.requireUser(request);
        return coupons.redeem(user.id(), body.code());
    }

    @GetMapping("/api/my/coupons")
    public Paged<MemberCoupon> mine(@RequestParam(name = "status", required = false) String status,
                                    @RequestParam(name = "page", required = false) Integer page,
                                    HttpServletRequest request) {
        Member user = auth.requireUser(request);
        return coupons.mine(user.id(), status, Paged.page(page));
    }

    @GetMapping("/api/checkout/coupons")
    public List<CouponService.CheckoutGroup> checkout(HttpServletRequest request) {
        Member user = auth.requireUser(request);
        return orders.checkoutCoupons(user.id());
    }
}
