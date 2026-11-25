package me.nexthub.sool.claim;

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

/** 판매자: 내 가게로 들어온 취소·반품·교환 신청 처리. 남의 가게 신청은 404. */
@RestController
public class SellerClaimController {

    public record NoteRequest(String sellerNote) {
    }

    public record CompleteRequest(String carrier, String trackingNo) {
    }

    private final Auth auth;
    private final ClaimService claims;
    private final ClaimQuery query;

    public SellerClaimController(Auth auth, ClaimService claims, ClaimQuery query) {
        this.auth = auth;
        this.claims = claims;
        this.query = query;
    }

    /** status: requested, approved, done, rejected, withdrawn 또는 쉼표로 여럿(rejected,withdrawn). 비우면 전체 */
    @GetMapping("/api/seller/claims")
    public Paged<Claim> list(@RequestParam(name = "status", required = false) String status,
                             @RequestParam(name = "page", required = false) Integer page,
                             HttpServletRequest request) {
        Seller seller = auth.requireSeller(request);
        return query.listForSeller(seller.id(), status, Paged.page(page));
    }

    @PostMapping("/api/seller/claims/{id}/approve")
    public Claim approve(@PathVariable("id") long id, @RequestBody(required = false) NoteRequest body,
                         HttpServletRequest request) {
        Seller seller = auth.requireSeller(request);
        return claims.approve(seller.id(), id, body == null ? null : body.sellerNote());
    }

    @PostMapping("/api/seller/claims/{id}/reject")
    public Claim reject(@PathVariable("id") long id, @RequestBody(required = false) NoteRequest body,
                        HttpServletRequest request) {
        Seller seller = auth.requireSeller(request);
        return claims.reject(seller.id(), id, body == null ? null : body.sellerNote());
    }

    /** 본문(택배사, 송장번호)은 교환에서만 쓴다. */
    @PostMapping("/api/seller/claims/{id}/complete")
    public Claim complete(@PathVariable("id") long id, @RequestBody(required = false) CompleteRequest body,
                          HttpServletRequest request) {
        Seller seller = auth.requireSeller(request);
        return claims.complete(seller.id(), id, body == null ? null : body.carrier(),
                body == null ? null : body.trackingNo());
    }
}
