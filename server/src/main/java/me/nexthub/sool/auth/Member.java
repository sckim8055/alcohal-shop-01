package me.nexthub.sool.auth;

import java.time.LocalDateTime;

/** 로그인한 회원. 비밀번호 해시는 담지 않는다. */
public record Member(long id, String email, String name, String role, LocalDateTime adultVerifiedAt) {

    public boolean admin() {
        return "admin".equals(role);
    }
}
