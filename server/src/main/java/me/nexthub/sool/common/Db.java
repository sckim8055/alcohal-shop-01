package me.nexthub.sool.common;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;

/** JdbcClient 보조 기능. */
public final class Db {

    private Db() {
    }

    /** INSERT를 실행하고 새로 생긴 id를 돌려준다. */
    public static long insert(JdbcClient.StatementSpec spec) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        spec.update(keys, "id");
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("생성된 id를 받지 못했습니다.");
        }
        return key.longValue();
    }
}
