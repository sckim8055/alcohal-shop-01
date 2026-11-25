package me.nexthub.sool.claim;

import jakarta.servlet.http.HttpServletRequest;

import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.auth.Member;
import me.nexthub.sool.common.Paged;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 구매자: 취소·반품·교환 신청, 철회, 내 신청 내역. */
@RestController
public class ClaimController {

    private final Auth auth;
    private final ClaimService claims;
    private final ClaimQuery query;

    public ClaimController(Auth auth, ClaimService claims, ClaimQuery query) {
        this.auth = auth;
        this.claims = claims;
        this.query = query;
    }

    @PostMapping("/api/suborders/{id}/claims")
    public Claim request(@PathVariable("id") long id, @RequestBody ClaimService.ClaimRequest body,
                         HttpServletRequest request) {
        Member user = auth.requireUser(request);
        return claims.request(user.id(), id, body);
    }

    @PostMapping("/api/claims/{id}/withdraw")
    public Claim withdraw(@PathVariable("id") long id, HttpServletRequest request) {
        Member user = auth.requireUser(request);
        return claims.withdraw(user.id(), id);
    }

    @GetMapping("/api/my/claims")
    public Paged<Claim> mine(@RequestParam(name = "page", required = false) Integer page, HttpServletRequest request) {
        Member user = auth.requireUser(request);
        return query.listForBuyer(user.id(), Paged.page(page));
    }
}
