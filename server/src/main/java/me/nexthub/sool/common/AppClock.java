package me.nexthub.sool.common;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

/** 현재 시각. DB에는 UTC로 저장하고, 날짜(할인 기간 등)는 한국 시간 기준으로 본다. */
@Component
public class AppClock {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private volatile Duration offset = Duration.ZERO;

    public Instant instant() {
        return Instant.now().plus(offset);
    }

    /** UTC 기준 현재 시각 */
    public LocalDateTime now() {
        return LocalDateTime.ofInstant(instant(), ZoneOffset.UTC);
    }

    /** 한국 시간 기준 오늘 날짜 */
    public LocalDate today() {
        return LocalDate.ofInstant(instant(), KST);
    }

    /** 테스트 전용: 시간을 앞으로 돌린다. */
    public void advance(Duration d) {
        offset = offset.plus(d);
    }

    /** 테스트 전용: 시간을 원래대로 되돌린다. */
    public void reset() {
        offset = Duration.ZERO;
    }
}
