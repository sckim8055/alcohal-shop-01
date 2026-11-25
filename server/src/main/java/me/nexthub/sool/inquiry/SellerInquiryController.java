package me.nexthub.sool.inquiry;

import jakarta.servlet.http.HttpServletRequest;

import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.auth.Seller;
import me.nexthub.sool.common.Paged;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 판매자: 내 가게로 온 상품·주문 문의와 답변. 남의 가게 문의는 404. */
@RestController
public class SellerInquiryController {

    public record AnswerRequest(String answer) {
    }

    private final Auth auth;
    private final InquiryService inquiries;

    public SellerInquiryController(Auth auth, InquiryService inquiries) {
        this.auth = auth;
        this.inquiries = inquiries;
    }

    @GetMapping("/api/seller/inquiries")
    public Paged<Inquiry> list(@RequestParam(name = "unanswered", required = false) Boolean unanswered,
                               @RequestParam(name = "page", required = false) Integer page,
                               HttpServletRequest request) {
        Seller seller = auth.requireSeller(request);
        return inquiries.forSeller(seller.id(), Boolean.TRUE.equals(unanswered), Paged.page(page));
    }

    @PostMapping("/api/seller/inquiries/{id}/answer")
    public Inquiry answer(@PathVariable("id") long id, @RequestBody(required = false) AnswerRequest body,
                          HttpServletRequest request) {
        Seller seller = auth.requireSeller(request);
        return inquiries.answerAsSeller(seller.id(), seller.memberId(), id, body == null ? null : body.answer());
    }
}
