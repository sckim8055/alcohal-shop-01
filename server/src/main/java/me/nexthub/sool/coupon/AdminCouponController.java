package me.nexthub.sool.coupon;

import jakarta.servlet.http.HttpServletRequest;

import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.common.Paged;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 운영자: 플랫폼 쿠폰 만들기, 모든 쿠폰의 상태 바꾸기, 회원에게 직접 지급. */
@RestController
public class AdminCouponController {

    public record StatusRequest(String status) {
    }

    public record GrantRequest(String email) {
    }

    private final Auth auth;
    private final CouponService coupons;

    public AdminCouponController(Auth auth, CouponService coupons) {
        this.auth = auth;
        this.coupons = coupons;
    }

    @GetMapping("/api/admin/coupons")
    public Paged<Coupon> list(@RequestParam(name = "issuer", required = false) String issuer,
                              @RequestParam(name = "page", required = false) Integer page,
                              HttpServletRequest request) {
        auth.requireAdmin(request);
        return coupons.listAll(issuer, Paged.page(page));
    }

    @PostMapping("/api/admin/coupons")
    public Coupon create(@RequestBody CouponService.CouponRequest body, HttpServletRequest request) {
        auth.requireAdmin(request);
        return coupons.create(body, null);
    }

    @PostMapping("/api/admin/coupons/{id}/status")
    public Coupon status(@PathVariable("id") long id, @RequestBody StatusRequest body, HttpServletRequest request) {
        auth.requireAdmin(request);
        return coupons.setStatus(id, null, body.status());
    }

    @PostMapping("/api/admin/coupons/{id}/grant")
    public MemberCoupon grant(@PathVariable("id") long id, @RequestBody GrantRequest body, HttpServletRequest request) {
        auth.requireAdmin(request);
        return coupons.grant(id, body.email());
    }
}
