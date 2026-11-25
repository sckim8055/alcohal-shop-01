package me.nexthub.sool.review;

import jakarta.servlet.http.HttpServletRequest;

import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.common.Paged;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 운영자: 모든 리뷰 보기, 숨기기와 다시 보이기. */
@RestController
public class AdminReviewController {

    public record HideRequest(Boolean hidden) {
    }

    private final Auth auth;
    private final ReviewService reviews;

    public AdminReviewController(Auth auth, ReviewService reviews) {
        this.auth = auth;
        this.reviews = reviews;
    }

    /** status: visible, hidden, deleted. 비우면 전체 */
    @GetMapping("/api/admin/reviews")
    public Paged<Review> list(@RequestParam(name = "status", required = false) String status,
                              @RequestParam(name = "page", required = false) Integer page,
                              HttpServletRequest request) {
        auth.requireAdmin(request);
        return reviews.forAdmin(status, Paged.page(page));
    }

    @PostMapping("/api/admin/reviews/{id}/hide")
    public Review hide(@PathVariable("id") long id, @RequestBody(required = false) HideRequest body,
                       HttpServletRequest request) {
        auth.requireAdmin(request);
        return reviews.hide(id, body == null ? null : body.hidden());
    }
}
