package me.nexthub.sool.auth;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.AppClock;
import me.nexthub.sool.common.Db;
import me.nexthub.sool.common.Passwords;
import me.nexthub.sool.common.Tenant;
import me.nexthub.sool.common.Text;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 가입, 로그인, 로그아웃, 성인인증, 내 정보. */
@RestController
public class AuthController {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final SecureRandom RANDOM = new SecureRandom();
    /** 없는 이메일로 로그인해도 응답 시간이 같도록 비교에 쓰는 해시 */
    private static final String DUMMY_HASH = Passwords.hash("not-a-real-password");

    public record Me(Member user, Seller seller, String csrfToken, int cartCount) {
    }

    public record RegisterRequest(String email, String name, String password) {
    }

    public record LoginRequest(String email, String password) {
    }

    public record VerifyRequest(String birth) {
    }

    private record Credential(long id, String passwordHash) {
    }

    private final JdbcClient db;
    private final Auth auth;
    private final Tenant tenant;
    private final AppClock clock;

    public AuthController(JdbcClient db, Auth auth, Tenant tenant, AppClock clock) {
        this.db = db;
        this.auth = auth;
        this.tenant = tenant;
        this.clock = clock;
    }

    /** 화면이 처음 열릴 때 호출한다. 위조 요청 방지 토큰도 여기서 받는다. */
    @GetMapping("/api/me")
    public Me me(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        String csrf = (String) session.getAttribute(AuthInterceptor.SESSION_CSRF);
        if (csrf == null) {
            csrf = newToken();
            session.setAttribute(AuthInterceptor.SESSION_CSRF, csrf);
        }
        return view(auth.user(request), csrf);
    }

    @PostMapping("/api/auth/register")
    public Me register(@RequestBody RegisterRequest body, HttpServletRequest request) {
        String email = Text.clean(body.email(), 190).toLowerCase();
        String name = Text.clean(body.name(), 40);
        String password = body.password() == null ? "" : body.password();
        if (!EMAIL.matcher(email).matches() || name.isEmpty() || password.length() < 8) {
            throw ApiException.bad("이메일, 이름, 8자 이상 비밀번호를 입력해 주세요.");
        }
        boolean exists = db.sql("SELECT COUNT(*) FROM member WHERE tenant_id = :t AND email = :e")
                .param("t", tenant.id()).param("e", email).query(Integer.class).single() > 0;
        if (exists) {
            throw ApiException.conflict("이미 가입된 이메일입니다.");
        }
        long id = Db.insert(db.sql("""
                INSERT INTO member (tenant_id, email, password_hash, name, role, created_at)
                VALUES (:t, :e, :h, :n, 'buyer', :now)
                """)
                .param("t", tenant.id()).param("e", email).param("h", Passwords.hash(password))
                .param("n", name).param("now", clock.now()));
        return signIn(request, id);
    }

    @PostMapping("/api/auth/login")
    public Me login(@RequestBody LoginRequest body, HttpServletRequest request) {
        String email = Text.clean(body.email(), 190).toLowerCase();
        String password = body.password() == null ? "" : body.password();
        Credential c = db.sql("SELECT id, password_hash FROM member WHERE tenant_id = :t AND email = :e")
                .param("t", tenant.id()).param("e", email)
                .query(Credential.class).optional().orElse(null);
        boolean ok = Passwords.matches(password, c == null ? DUMMY_HASH : c.passwordHash());
        if (c == null || !ok) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 맞지 않습니다.");
        }
        return signIn(request, c.id());
    }

    @PostMapping("/api/auth/logout")
    public Map<String, Boolean> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return Map.of("ok", true);
    }

    /**
     * 성인인증(시범용). 입력한 생년월일만으로 판단하므로 법적 성인인증이 아니다.
     * 실제 서비스에서는 본인확인 서비스 연동으로 바꿔야 한다.
     */
    @PostMapping("/api/auth/verify-adult")
    public Me verifyAdult(@RequestBody VerifyRequest body, HttpServletRequest request) {
        Member user = auth.requireUser(request);
        var birth = Text.date(body.birth());
        int thisYear = clock.today().getYear();
        // 청소년보호법 기준: 만 19세가 되는 해의 1월 1일을 지난 사람
        if (birth == null || birth.getYear() < 1900 || thisYear - birth.getYear() < 19) {
            throw ApiException.forbidden("성인만 주류를 구매할 수 있습니다.");
        }
        db.sql("UPDATE member SET adult_verified_at = :now WHERE id = :id")
                .param("now", clock.now()).param("id", user.id()).update();
        HttpSession session = request.getSession(true);
        return view(auth.load(user.id()), (String) session.getAttribute(AuthInterceptor.SESSION_CSRF));
    }

    /** 로그인할 때 세션 번호와 토큰을 새로 발급한다(세션 고정 공격 방지). */
    private Me signIn(HttpServletRequest request, long memberId) {
        request.getSession(true);
        request.changeSessionId();
        HttpSession session = request.getSession();
        String csrf = newToken();
        session.setAttribute(AuthInterceptor.SESSION_UID, memberId);
        session.setAttribute(AuthInterceptor.SESSION_CSRF, csrf);
        return view(auth.load(memberId), csrf);
    }

    private Me view(Member user, String csrf) {
        if (user == null) {
            return new Me(null, null, csrf, 0);
        }
        int cart = db.sql("SELECT COALESCE(SUM(qty), 0) FROM cart_item WHERE member_id = :m")
                .param("m", user.id()).query(Integer.class).single();
        return new Me(user, auth.sellerOf(user.id()), csrf, cart);
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
