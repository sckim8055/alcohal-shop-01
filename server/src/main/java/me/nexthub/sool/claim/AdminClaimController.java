package me.nexthub.sool.claim;

import jakarta.servlet.http.HttpServletRequest;

import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.common.Paged;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 운영자: 모든 가게의 취소·반품·교환 신청 보기. 처리는 판매자가 한다(대금을 판매자가 직접 주고받기 때문). */
@RestController
public class AdminClaimController {

    private final Auth auth;
    private final ClaimQuery query;

    public AdminClaimController(Auth auth, ClaimQuery query) {
        this.auth = auth;
        this.query = query;
    }

    @GetMapping("/api/admin/claims")
    public Paged<Claim> list(@RequestParam(name = "status", required = false) String status,
                             @RequestParam(name = "page", required = false) Integer page,
                             HttpServletRequest request) {
        auth.requireAdmin(request);
        return query.listAll(status, Paged.page(page));
    }
}
