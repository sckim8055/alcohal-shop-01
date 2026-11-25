package me.nexthub.sool.notice;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.auth.Member;
import me.nexthub.sool.auth.Seller;
import me.nexthub.sool.common.Paged;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 공지: 누구나 보는 목록과 본문, 판매자 공지 목록, 운영자의 쓰기·고치기·지우기. */
@RestController
public class NoticeController {

    private static final Map<String, Boolean> OK = Map.of("ok", true);

    private final Auth auth;
    private final NoticeService notices;

    public NoticeController(Auth auth, NoticeService notices) {
        this.auth = auth;
        this.notices = notices;
    }

    @GetMapping("/api/notices")
    public Paged<Notice> list(@RequestParam(name = "page", required = false) Integer page) {
        return notices.listPublic(Paged.page(page));
    }

    /** 판매자 대상 공지는 승인된 판매자와 운영자만 볼 수 있다. */
    @GetMapping("/api/notices/{id}")
    public Notice one(@PathVariable("id") long id, HttpServletRequest request) {
        Member user = auth.user(request);
        boolean allowed = false;
        if (user != null) {
            Seller seller = user.admin() ? null : auth.sellerOf(user.id());
            allowed = user.admin() || (seller != null && seller.approved());
        }
        return notices.find(id, allowed);
    }

    @GetMapping("/api/seller/notices")
    public Paged<Notice> forSeller(@RequestParam(name = "page", required = false) Integer page,
                                   HttpServletRequest request) {
        auth.requireSeller(request);
        return notices.listForSeller(Paged.page(page));
    }

    @GetMapping("/api/admin/notices")
    public Paged<Notice> forAdmin(@RequestParam(name = "page", required = false) Integer page,
                                  HttpServletRequest request) {
        auth.requireAdmin(request);
        return notices.listForAdmin(Paged.page(page));
    }

    @PostMapping("/api/admin/notices")
    public Notice create(@RequestBody NoticeService.NoticeRequest body, HttpServletRequest request) {
        Member admin = auth.requireAdmin(request);
        return notices.create(admin.id(), body);
    }

    @PutMapping("/api/admin/notices/{id}")
    public Notice update(@PathVariable("id") long id, @RequestBody NoticeService.NoticeRequest body,
                         HttpServletRequest request) {
        auth.requireAdmin(request);
        return notices.update(id, body);
    }

    @PostMapping("/api/admin/notices/{id}/delete")
    public Map<String, Boolean> delete(@PathVariable("id") long id, HttpServletRequest request) {
        auth.requireAdmin(request);
        notices.delete(id);
        return OK;
    }
}
