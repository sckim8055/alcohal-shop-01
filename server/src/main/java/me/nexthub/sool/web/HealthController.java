package me.nexthub.sool.web;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 개발 환경 확인용: JDK 버전, DB 연결, 가상 스레드 사용 여부를 돌려준다. */
@RestController
public class HealthController {

    public record Health(String status, String javaVersion, boolean db, boolean virtualThread) {
    }

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/health")
    public Health health() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM tenant", Integer.class);
        return new Health(
                "ok",
                Runtime.version().toString(),
                count != null && count > 0,
                Thread.currentThread().isVirtual());
    }
}
