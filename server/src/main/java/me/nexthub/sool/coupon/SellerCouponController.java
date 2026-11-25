package me.nexthub.sool.coupon;

import jakarta.servlet.http.HttpServletRequest;

import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.auth.Seller;
import me.nexthub.sool.common.Paged;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 판매자: 내 가게 쿠폰. 비용은 판매자가 부담하고 그 가게 주문에만 쓸 수 있다. */
@RestController
public class SellerCouponController {

    public record StatusRequest(String status) {
    }

    private final Auth auth;
    private final CouponService coupons;

    public SellerCouponController(Auth auth, CouponService coupons) {
        this.auth = auth;
        this.coupons = coupons;
    }

    @GetMapping("/api/seller/coupons")
    public Paged<Coupon> list(@RequestParam(name = "page", required = false) Integer page, HttpServletRequest request) {
        Seller seller = auth.requireSeller(request);
        return coupons.listForSeller(seller.id(), Paged.page(page));
    }

    @PostMapping("/api/seller/coupons")
    public Coupon create(@RequestBody CouponService.CouponRequest body, HttpServletRequest request) {
        Seller seller = auth.requireSeller(request);
        return coupons.create(body, seller.id());
    }

    @PostMapping("/api/seller/coupons/{id}/status")
    public Coupon status(@PathVariable("id") long id, @RequestBody StatusRequest body, HttpServletRequest request) {
        Seller seller = auth.requireSeller(request);
        return coupons.setStatus(id, seller.id(), body.status());
    }
}
