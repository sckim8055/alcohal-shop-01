package me.nexthub.sool.review;

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

/** 판매자: 내 가게 상품의 리뷰와 답글. 남의 가게 리뷰는 404. */
@RestController
public class SellerReviewController {

    public record ReplyRequest(String reply) {
    }

    private final Auth auth;
    private final ReviewService reviews;

    public SellerReviewController(Auth auth, ReviewService reviews) {
        this.auth = auth;
        this.reviews = reviews;
    }

    @GetMapping("/api/seller/reviews")
    public Paged<Review> list(@RequestParam(name = "unanswered", required = false) Boolean unanswered,
                              @RequestParam(name = "page", required = false) Integer page,
                              HttpServletRequest request) {
        Seller seller = auth.requireSeller(request);
        return reviews.forSeller(seller.id(), Boolean.TRUE.equals(unanswered), Paged.page(page));
    }

    @PostMapping("/api/seller/reviews/{id}/reply")
    public Review reply(@PathVariable("id") long id, @RequestBody(required = false) ReplyRequest body,
                        HttpServletRequest request) {
        Seller seller = auth.requireSeller(request);
        return reviews.reply(seller.id(), id, body == null ? null : body.reply());
    }
}
