package me.nexthub.sool.board;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.auth.Member;
import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.AppClock;
import me.nexthub.sool.common.Db;
import me.nexthub.sool.common.Tenant;
import me.nexthub.sool.common.Text;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 이야기 게시판: 빚은 기록과 레시피를 나눈다. 술을 사고파는 글은 올릴 수 없다(약관·운영 기준). */
@RestController
public class BoardController {

    public record PostSummary(long id, String title, String authorName, LocalDateTime createdAt) {
    }

    public record Post(long id, String title, String body, String authorName, LocalDateTime createdAt) {
    }

    public record PostRequest(String title, String body) {
    }

    private final JdbcClient db;
    private final Auth auth;
    private final Tenant tenant;
    private final AppClock clock;

    public BoardController(JdbcClient db, Auth auth, Tenant tenant, AppClock clock) {
        this.db = db;
        this.auth = auth;
        this.tenant = tenant;
        this.clock = clock;
    }

    @GetMapping("/api/posts")
    public List<PostSummary> list() {
        return db.sql("""
                SELECT p.id, p.title, m.name AS author_name, p.created_at
                  FROM post p JOIN member m ON m.id = p.member_id
                 WHERE p.tenant_id = :t ORDER BY p.id DESC LIMIT 100
                """).param("t", tenant.id()).query(PostSummary.class).list();
    }

    @GetMapping("/api/posts/{id}")
    public Post get(@PathVariable("id") long id) {
        return db.sql("""
                SELECT p.id, p.title, p.body, m.name AS author_name, p.created_at
                  FROM post p JOIN member m ON m.id = p.member_id
                 WHERE p.tenant_id = :t AND p.id = :id
                """).param("t", tenant.id()).param("id", id).query(Post.class).optional()
                .orElseThrow(ApiException::notFound);
    }

    @PostMapping("/api/posts")
    public Post create(@RequestBody PostRequest body, HttpServletRequest request) {
        Member user = auth.requireUser(request);
        String title = Text.clean(body.title(), 100);
        String text = Text.clean(body.body(), 5000);
        if (title.isEmpty() || text.isEmpty()) {
            throw ApiException.bad("제목과 내용을 입력해 주세요.");
        }
        long id = Db.insert(db.sql("""
                INSERT INTO post (tenant_id, member_id, title, body, created_at) VALUES (:t, :m, :title, :body, :now)
                """).param("t", tenant.id()).param("m", user.id()).param("title", title).param("body", text)
                .param("now", clock.now()));
        return get(id);
    }
}
