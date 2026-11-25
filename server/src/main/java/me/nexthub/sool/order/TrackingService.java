package me.nexthub.sool.order;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.nexthub.sool.common.AppClock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 배송 조회. 지금은 시범용 가짜 데이터를 돌려준다(발송 후 일정 시간이 지나면 배송 완료).
 * 실제 배송 조회 서비스와 계약하면 이 클래스의 track()만 바꾼다.
 */
@Service
public class TrackingService {

    public record Carrier(String code, String name, String urlPrefix) {
    }

    public record Step(String label, LocalDateTime at) {
    }

    public record Tracking(String provider, String carrierName, String link, List<Step> steps, boolean delivered) {
    }

    private static final String[] MOCK_STEPS = {"택배사 접수", "간선 이동 중", "배송 지역 도착", "배송 출발", "배송 완료"};

    /** 조회 링크는 각 택배사 사이트 주소이며 바뀔 수 있으므로 운영 전에 확인이 필요하다. */
    private static final Map<String, Carrier> CARRIERS = new LinkedHashMap<>();

    static {
        add("cj", "CJ대한통운", "https://trace.cjlogistics.com/next/tracking.html?wblNo=");
        add("hanjin", "한진택배", "https://www.hanjin.com/kor/CMS/DeliveryMgr/WaybillResult.do?mCode=MN038&schLang=KR&wblnumText2=");
        add("lotte", "롯데택배", "https://www.lotteglogis.com/home/reservation/tracking/linkView?InvNo=");
        add("epost", "우체국택배", "https://service.epost.go.kr/trace.RetrieveDomRigiTraceList.comm?sid1=");
        add("etc", "기타", null);
    }

    private static void add(String code, String name, String urlPrefix) {
        CARRIERS.put(code, new Carrier(code, name, urlPrefix));
    }

    private final AppClock clock;
    private final long stepSeconds;

    public TrackingService(AppClock clock, @Value("${sool.tracking.mock-step-seconds:30}") long stepSeconds) {
        this.clock = clock;
        this.stepSeconds = Math.max(1, stepSeconds);
    }

    public List<Carrier> carriers() {
        return new ArrayList<>(CARRIERS.values());
    }

    public boolean knownCarrier(String code) {
        return code != null && CARRIERS.containsKey(code);
    }

    /** 발송 전이면 null */
    public Tracking track(String carrierCode, String trackingNo, LocalDateTime shippedAt) {
        if (shippedAt == null || trackingNo == null) {
            return null;
        }
        long elapsed = Math.max(0, Duration.between(shippedAt, clock.now()).getSeconds());
        int index = (int) Math.min(MOCK_STEPS.length - 1, elapsed / stepSeconds);
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i <= index; i++) {
            steps.add(new Step(MOCK_STEPS[i], shippedAt.plusSeconds(i * stepSeconds)));
        }
        Carrier c = CARRIERS.getOrDefault(carrierCode, CARRIERS.get("etc"));
        String link = c.urlPrefix() == null ? null : c.urlPrefix() + URLEncoder.encode(trackingNo, StandardCharsets.UTF_8);
        return new Tracking("mock", c.name(), link, steps, index == MOCK_STEPS.length - 1);
    }
}
