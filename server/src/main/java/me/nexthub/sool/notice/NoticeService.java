package me.nexthub.sool.notice;

import java.util.List;

import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.AppClock;
import me.nexthub.sool.common.Db;
import me.nexthub.sool.common.Paged;
import me.nexthub.sool.common.Tenant;
import me.nexthub.sool.common.Text;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** 공지 읽기·쓰기. 목록은 고정 공지를 먼저, 그 안에서는 최근 것을 먼저 보여 준다. */
@Service
public class NoticeService {

    public static final List<String> AUDIENCES = List.of("all", "seller");
    private static final int PAGE_SIZE = 10;
    private static final int ADMIN_PAGE_SIZE = 20;

    public record NoticeRequest(String audience, String title, String body, Boolean pinned) {
    }

    private static final String SELECT = "SELECT id, audience, title, body, pinned, created_at FROM notice";

    private final JdbcClient db;
    private final Tenant tenant;
    private final AppClock clock;

    public NoticeService(JdbcClient db, Tenant tenant, AppClock clock) {
        this.db = db;
        this.tenant = tenant;
        this.clock = clock;
    }

    /** 공지 화면(누구나): all 공지만 */
    public Paged<Notice> listPublic(int page) {
        return list(" AND audience = 'all'", page, PAGE_SIZE);
    }

    /** 판매자: all 과 seller 공지 */
    public Paged<Notice> listForSeller(int page) {
        return list("", page, PAGE_SIZE);
    }

    /** 운영자: 지운 것을 뺀 전체 */
    public Paged<Notice> listForAdmin(int page) {
        return list("", page, ADMIN_PAGE_SIZE);
    }

    private Paged<Notice> list(String extra, int page, int size) {
        String where = " WHERE tenant_id = :t AND status = 'published'" + extra;
        long total = db.sql("SELECT COUNT(*) FROM notice" + where).param("t", tenant.id()).query(Long.class).single();
        List<Notice> items = db.sql(SELECT + where + " ORDER BY pinned DESC, id DESC LIMIT :limit OFFSET :offset")
                .param("t", tenant.id()).param("limit", size).param("offset", Paged.offset(page, size))
                .query(Notice.class).list();
        return new Paged<>(items, page, size, total);
    }

    /** 공지 하나. 판매자 대상 공지는 sellerOrAdmin 일 때만 보여 주고, 아니면 있는지도 알리지 않는다(404). */
    public Notice find(long id, boolean sellerOrAdmin) {
        Notice n = db.sql(SELECT + " WHERE id = :id AND tenant_id = :t AND status = 'published'")
                .param("id", id).param("t", tenant.id()).query(Notice.class).optional().orElse(null);
        if (n == null || ("seller".equals(n.audience()) && !sellerOrAdmin)) {
            throw ApiException.notFound();
        }
        return n;
    }

    private record Checked(String audience, String title, String body, boolean pinned) {
    }

    private static Checked check(NoticeRequest body) {
        if (body == null || !Text.in(body.audience(), AUDIENCES)) {
            throw ApiException.bad("공지 대상(전체 또는 판매자)을 골라 주세요.");
        }
        String title = body.title() == null ? "" : body.title().trim();
        String text = body.body() == null ? "" : body.body().trim();
        if (title.isEmpty() || title.length() > 100) {
            throw ApiException.bad("제목은 1자 이상 100자 이하로 써 주세요.");
        }
        if (text.isEmpty() || text.length() > 5000) {
            throw ApiException.bad("내용은 1자 이상 5,000자 이하로 써 주세요.");
        }
        return new Checked(body.audience(), title, text, Boolean.TRUE.equals(body.pinned()));
    }

    public Notice create(long adminId, NoticeRequest body) {
        Checked c = check(body);
        long id = Db.insert(db.sql("""
                INSERT INTO notice (tenant_id, audience, title, body, pinned, status, created_by, created_at)
                VALUES (:t, :a, :title, :body, :pinned, 'published', :by, :now)
                """)
                .param("t", tenant.id()).param("a", c.audience()).param("title", c.title()).param("body", c.body())
                .param("pinned", c.pinned()).param("by", adminId).param("now", clock.now()));
        return find(id, true);
    }

    public Notice update(long id, NoticeRequest body) {
        Checked c = check(body);
        int changed = db.sql("""
                UPDATE notice SET audience = :a, title = :title, body = :body, pinned = :pinned
                 WHERE id = :id AND tenant_id = :t AND status = 'published'
                """)
                .param("a", c.audience()).param("title", c.title()).param("body", c.body()).param("pinned", c.pinned())
                .param("id", id).param("t", tenant.id()).update();
        if (changed != 1) {
            throw ApiException.notFound();
        }
        return find(id, true);
    }

    public void delete(long id) {
        int changed = db.sql("UPDATE notice SET status = 'deleted' WHERE id = :id AND tenant_id = :t AND status = 'published'")
                .param("id", id).param("t", tenant.id()).update();
        if (changed != 1) {
            throw ApiException.notFound();
        }
    }
}
