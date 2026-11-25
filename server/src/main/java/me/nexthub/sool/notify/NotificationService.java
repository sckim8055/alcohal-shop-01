package me.nexthub.sool.notify;

import java.time.LocalDateTime;
import java.util.List;

import me.nexthub.sool.common.AppClock;
import me.nexthub.sool.common.Paged;
import me.nexthub.sool.common.Tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * 알림. 사이트 안 알림은 바로 저장되어 화면의 종 모양에 나타난다.
 * 메일은 보낼 내용을 message_outbox 에 남기고 기록만 한다. 실제 발송은 메일 서비스(SMTP)를 연결한 뒤
 * send() 한 곳만 바꾸면 된다. 문자·알림톡도 같은 자리에 붙인다.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public record Notification(long id, String title, String body, String link, LocalDateTime readAt,
                               LocalDateTime createdAt) {
    }

    private final JdbcClient db;
    private final Tenant tenant;
    private final AppClock clock;

    public NotificationService(JdbcClient db, Tenant tenant, AppClock clock) {
        this.db = db;
        this.tenant = tenant;
        this.clock = clock;
    }

    /** 사이트 안 알림만 남긴다. */
    public void notify(long memberId, String title, String body, String link) {
        db.sql("""
                INSERT INTO notification (tenant_id, member_id, title, body, link, created_at)
                VALUES (:t, :m, :title, :body, :link, :now)
                """)
                .param("t", tenant.id()).param("m", memberId).param("title", cut(title, 100))
                .param("body", cut(body, 500)).param("link", cut(link, 200)).param("now", clock.now()).update();
    }

    /** 사이트 안 알림과 함께 메일도 보낸다(돈이나 배송처럼 놓치면 안 되는 일). */
    public void notifyWithMail(long memberId, String title, String body, String link) {
        notify(memberId, title, body, link);
        String email = db.sql("SELECT email FROM member WHERE id = :m").param("m", memberId)
                .query(String.class).optional().orElse(null);
        if (email != null) {
            send(memberId, email, "[술장터] " + title, body);
        }
    }

    private void send(long memberId, String email, String subject, String body) {
        db.sql("""
                INSERT INTO message_outbox (tenant_id, member_id, channel, recipient, subject, body, status, created_at)
                VALUES (:t, :m, 'email', :to, :subject, :body, 'logged', :now)
                """)
                .param("t", tenant.id()).param("m", memberId).param("to", email).param("subject", cut(subject, 200))
                .param("body", cut(body, 2000)).param("now", clock.now()).update();
        log.info("메일 발송 대기(발송 서비스 미연결): to={} subject={}", email, subject);
    }

    public int unreadCount(long memberId) {
        return db.sql("SELECT COUNT(*) FROM notification WHERE member_id = :m AND read_at IS NULL")
                .param("m", memberId).query(Integer.class).single();
    }

    public Paged<Notification> list(long memberId, int page, int size) {
        long total = db.sql("SELECT COUNT(*) FROM notification WHERE member_id = :m")
                .param("m", memberId).query(Long.class).single();
        List<Notification> items = db.sql("""
                SELECT id, title, body, link, read_at, created_at FROM notification
                 WHERE member_id = :m ORDER BY id DESC LIMIT :limit OFFSET :offset
                """).param("m", memberId).param("limit", size).param("offset", Paged.offset(page, size))
                .query(Notification.class).list();
        return new Paged<>(items, page, size, total);
    }

    public void markRead(long memberId, Long notificationId) {
        String sql = "UPDATE notification SET read_at = :now WHERE member_id = :m AND read_at IS NULL"
                + (notificationId == null ? "" : " AND id = :id");
        var spec = db.sql(sql).param("now", clock.now()).param("m", memberId);
        if (notificationId != null) {
            spec = spec.param("id", notificationId);
        }
        spec.update();
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
