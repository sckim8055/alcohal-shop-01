package me.nexthub.sool.member;

import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.auth.Member;
import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.Passwords;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴. 개인 정보(이메일, 이름, 비밀번호)는 알아볼 수 없게 바꾸고 장바구니·찜·배송지·쓰지 않은 쿠폰은 지운다.
 * 주문 기록은 거래 기록 보관 의무가 있어 남긴다.
 */
@Service
public class WithdrawService {

    /** Passwords.matches 가 어떤 입력에도 false 를 내는 값(pbkdf2 형식이 아니다) */
    static final String UNUSABLE_HASH = "withdrawn";
    public static final String WITHDRAWN_NAME = "탈퇴한 회원";

    private final JdbcClient db;
    private final Auth auth;

    public WithdrawService(JdbcClient db, Auth auth) {
        this.db = db;
        this.auth = auth;
    }

    @Transactional
    public void withdraw(Member user, String password) {
        String hash = db.sql("SELECT password_hash FROM member WHERE id = :id").param("id", user.id())
                .query(String.class).single();
        if (!Passwords.matches(password == null ? "" : password, hash)) {
            throw ApiException.bad("비밀번호가 맞지 않습니다.");
        }
        if (user.admin()) {
            throw ApiException.conflict("운영자 계정은 탈퇴할 수 없습니다.");
        }
        if (auth.sellerOf(user.id()) != null) {
            throw ApiException.conflict("가게가 있는 회원은 바로 탈퇴할 수 없습니다. 정산과 주문 정리를 위해 운영자에게 문의해 주세요.");
        }
        int openOrders = db.sql("""
                SELECT COUNT(*) FROM sub_order so JOIN shop_order o ON o.id = so.order_id
                 WHERE o.buyer_id = :m AND so.status IN ('awaiting_deposit', 'paid', 'shipped', 'delivered')
                """).param("m", user.id()).query(Integer.class).single();
        if (openOrders > 0) {
            throw ApiException.conflict("끝나지 않은 주문이 있어 탈퇴할 수 없습니다. 주문이 끝나거나 취소된 뒤 다시 시도해 주세요.");
        }
        int openClaims = db.sql("SELECT COUNT(*) FROM order_claim WHERE member_id = :m AND status IN ('requested', 'approved')")
                .param("m", user.id()).query(Integer.class).single();
        if (openClaims > 0) {
            throw ApiException.conflict("진행 중인 취소·반품·교환 신청이 있어 탈퇴할 수 없습니다. 신청이 끝난 뒤 다시 시도해 주세요.");
        }
        int changed = db.sql("""
                UPDATE member SET status = 'withdrawn', email = :email, name = :name, password_hash = :hash
                 WHERE id = :id AND status = 'active'
                """).param("email", "withdrawn-" + user.id() + "@deleted.local").param("name", WITHDRAWN_NAME)
                .param("hash", UNUSABLE_HASH).param("id", user.id()).update();
        if (changed != 1) {
            throw ApiException.conflict("이미 탈퇴했거나 지금은 탈퇴할 수 없는 계정입니다.");
        }
        for (String sql : new String[] {
                "DELETE FROM cart_item WHERE member_id = :m",
                "DELETE FROM wishlist WHERE member_id = :m",
                "DELETE FROM member_address WHERE member_id = :m",
                "DELETE FROM member_coupon WHERE member_id = :m AND status = 'issued'"}) {
            db.sql(sql).param("m", user.id()).update();
        }
        auth.expireSessions(user.id());
    }
}
