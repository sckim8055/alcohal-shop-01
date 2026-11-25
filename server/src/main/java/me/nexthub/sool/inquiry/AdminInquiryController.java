package me.nexthub.sool.inquiry;

import jakarta.servlet.http.HttpServletRequest;

import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.auth.Member;
import me.nexthub.sool.common.Paged;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 운영자: 1:1 문의 목록과 답변. 판매자에게 간 문의는 여기 나오지 않는다. */
@RestController
public class AdminInquiryController {

    public record AnswerRequest(String answer) {
    }

    private final Auth auth;
    private final InquiryService inquiries;

    public AdminInquiryController(Auth auth, InquiryService inquiries) {
        this.auth = auth;
        this.inquiries = inquiries;
    }

    @GetMapping("/api/admin/inquiries")
    public Paged<Inquiry> list(@RequestParam(name = "unanswered", required = false) Boolean unanswered,
                               @RequestParam(name = "page", required = false) Integer page,
                               HttpServletRequest request) {
        auth.requireAdmin(request);
        return inquiries.forAdmin(Boolean.TRUE.equals(unanswered), Paged.page(page));
    }

    @PostMapping("/api/admin/inquiries/{id}/answer")
    public Inquiry answer(@PathVariable("id") long id, @RequestBody(required = false) AnswerRequest body,
                          HttpServletRequest request) {
        Member admin = auth.requireAdmin(request);
        return inquiries.answerAsAdmin(admin.id(), id, body == null ? null : body.answer());
    }
}
