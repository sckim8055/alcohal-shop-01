package me.nexthub.sool.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 현재 몰(테넌트). 지금은 몰이 하나라 설정값을 그대로 돌려준다.
 * 몰이 여러 개가 되면 접속 주소로 몰을 찾도록 이 클래스만 바꾼다.
 */
@Component
public class Tenant {

    private final long id;

    public Tenant(@Value("${sool.tenant-id:1}") long id) {
        this.id = id;
    }

    public long id() {
        return id;
    }
}
