package me.nexthub.sool.auth;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import me.nexthub.sool.common.ApiException;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 모든 API 요청 앞에서 실행된다.
 * 1) 세션에 로그인 정보가 있으면 회원을 읽어 요청에 붙인다.
 * 2) 값을 바꾸는 요청(POST, PUT, DELETE)은 X-CSRF-Token 머리말이 세션의 토큰과 같아야 한다.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    static final String SESSION_UID = "uid";
    static final String SESSION_CSRF = "csrf";
    static final String SESSION_EPOCH = "epoch";
    public static final String CSRF_HEADER = "X-CSRF-Token";

    private final Auth auth;

    public AuthInterceptor(Auth auth) {
        this.auth = auth;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SESSION_UID) instanceof Long uid
                && session.getAttribute(SESSION_EPOCH) instanceof Integer epoch) {
            request.setAttribute(Auth.USER_ATTR, auth.loadForSession(uid, epoch));
        }
        String method = request.getMethod();
        boolean readOnly = "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
        if (!readOnly) {
            String expected = session == null ? null : (String) session.getAttribute(SESSION_CSRF);
            String actual = request.getHeader(CSRF_HEADER);
            if (expected == null || actual == null || !MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
                throw ApiException.forbidden("보안 확인에 실패했습니다. 페이지를 새로 고친 뒤 다시 시도해 주세요.");
            }
        }
        return true;
    }
}
