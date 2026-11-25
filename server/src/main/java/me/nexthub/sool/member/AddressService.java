package me.nexthub.sool.member;

import java.util.List;

import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.AppClock;
import me.nexthub.sool.common.Db;
import me.nexthub.sool.common.Tenant;
import me.nexthub.sool.common.Text;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배송지. 회원당 10개까지, 배송지가 하나라도 있으면 기본 배송지는 늘 하나다.
 * 남의 배송지는 없는 것처럼 404 로 답한다.
 */
@Service
public class AddressService {

    public static final int MAX_ADDRESSES = 10;

    public record AddressRequest(String label, String receiver, String phone, String address, Boolean isDefault) {
    }

    private static final String SELECT = """
            SELECT id, label, receiver, phone, address, is_default AS is_default FROM member_address
            """;

    private final JdbcClient db;
    private final Tenant tenant;
    private final AppClock clock;

    public AddressService(JdbcClient db, Tenant tenant, AppClock clock) {
        this.db = db;
        this.tenant = tenant;
        this.clock = clock;
    }

    /** 기본 배송지 먼저, 그다음 최근에 넣은 것 */
    public List<Address> list(long memberId) {
        return db.sql(SELECT + " WHERE member_id = :m ORDER BY is_default DESC, id DESC")
                .param("m", memberId).query(Address.class).list();
    }

    private Address own(long memberId, long id) {
        Address a = db.sql(SELECT + " WHERE id = :id AND member_id = :m AND tenant_id = :t")
                .param("id", id).param("m", memberId).param("t", tenant.id()).query(Address.class).optional().orElse(null);
        if (a == null) {
            throw ApiException.notFound();
        }
        return a;
    }

    /** 검증을 통과한 입력값 */
    private record Checked(String label, String receiver, String phone, String address, boolean makeDefault) {
    }

    private static Checked check(AddressRequest r) {
        if (r == null) {
            throw ApiException.bad("받는 분, 연락처, 주소를 입력해 주세요.");
        }
        String receiver = Text.clean(r.receiver(), 40);
        String phone = Text.clean(r.phone(), 30);
        String address = Text.clean(r.address(), 300);
        if (receiver.isEmpty() || phone.isEmpty() || address.isEmpty()) {
            throw ApiException.bad("받는 분, 연락처, 주소를 입력해 주세요.");
        }
        return new Checked(Text.clean(r.label(), 30), receiver, phone, address, Boolean.TRUE.equals(r.isDefault()));
    }

    @Transactional
    public Address create(long memberId, AddressRequest body) {
        Checked c = check(body);
        // 회원 줄을 잠가 동시에 여러 개를 넣어도 10개를 넘지 않게 한다
        db.sql("SELECT id FROM member WHERE id = :m FOR UPDATE").param("m", memberId).query(Long.class).list();
        int count = db.sql("SELECT COUNT(*) FROM member_address WHERE member_id = :m").param("m", memberId)
                .query(Integer.class).single();
        if (count >= MAX_ADDRESSES) {
            throw ApiException.conflict("배송지는 " + MAX_ADDRESSES + "개까지 저장할 수 있습니다. 쓰지 않는 배송지를 지운 뒤 넣어 주세요.");
        }
        boolean makeDefault = c.makeDefault() || count == 0;
        if (makeDefault) {
            clearDefault(memberId);
        }
        long id = Db.insert(db.sql("""
                INSERT INTO member_address (tenant_id, member_id, label, receiver, phone, address, is_default, created_at)
                VALUES (:t, :m, :label, :r, :p, :a, :d, :now)
                """)
                .param("t", tenant.id()).param("m", memberId).param("label", c.label()).param("r", c.receiver())
                .param("p", c.phone()).param("a", c.address()).param("d", makeDefault).param("now", clock.now()));
        return own(memberId, id);
    }

    /** isDefault 가 true 면 기본으로 바꾼다. false 를 줘도 기본 배송지를 없애지는 않는다(기본은 늘 하나). */
    @Transactional
    public Address update(long memberId, long id, AddressRequest body) {
        own(memberId, id);
        Checked c = check(body);
        db.sql("""
                UPDATE member_address SET label = :label, receiver = :r, phone = :p, address = :a
                 WHERE id = :id AND member_id = :m
                """).param("label", c.label()).param("r", c.receiver()).param("p", c.phone()).param("a", c.address())
                .param("id", id).param("m", memberId).update();
        if (c.makeDefault()) {
            makeDefault(memberId, id);
        }
        return own(memberId, id);
    }

    @Transactional
    public List<Address> setDefault(long memberId, long id) {
        own(memberId, id);
        makeDefault(memberId, id);
        return list(memberId);
    }

    /** 기본 배송지를 지우면 남은 것 가운데 가장 최근 것이 기본이 된다. */
    @Transactional
    public List<Address> delete(long memberId, long id) {
        Address a = own(memberId, id);
        db.sql("DELETE FROM member_address WHERE id = :id AND member_id = :m").param("id", id).param("m", memberId).update();
        if (a.isDefault()) {
            Long next = db.sql("SELECT id FROM member_address WHERE member_id = :m ORDER BY created_at DESC, id DESC LIMIT 1")
                    .param("m", memberId).query(Long.class).optional().orElse(null);
            if (next != null) {
                makeDefault(memberId, next);
            }
        }
        return list(memberId);
    }

    private void clearDefault(long memberId) {
        db.sql("UPDATE member_address SET is_default = FALSE WHERE member_id = :m AND is_default = TRUE")
                .param("m", memberId).update();
    }

    private void makeDefault(long memberId, long id) {
        db.sql("""
                UPDATE member_address SET is_default = CASE WHEN id = :id THEN TRUE ELSE FALSE END WHERE member_id = :m
                """).param("id", id).param("m", memberId).update();
    }
}
