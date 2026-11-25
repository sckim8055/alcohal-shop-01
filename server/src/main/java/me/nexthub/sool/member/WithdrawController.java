package me.nexthub.sool.member;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.auth.Member;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 회원 탈퇴 */
@RestController
public class WithdrawController {

    public record WithdrawRequest(String password) {
    }

    private final Auth auth;
    private final WithdrawService withdraws;

    public WithdrawController(Auth auth, WithdrawService withdraws) {
        this.auth = auth;
        this.withdraws = withdraws;
    }

    @PostMapping("/api/me/withdraw")
    public Map<String, Boolean> withdraw(@RequestBody(required = false) WithdrawRequest body, HttpServletRequest request) {
        Member user = auth.requireUser(request);
        withdraws.withdraw(user, body == null ? null : body.password());
        // 다른 기기는 expireSessions 로, 지금 이 브라우저는 세션을 끊어 바로 로그아웃시킨다
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return Map.of("ok", true);
    }
}
