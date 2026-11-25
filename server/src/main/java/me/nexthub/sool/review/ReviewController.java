package me.nexthub.sool.review;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.auth.Member;
import me.nexthub.sool.catalog.Catalog;
import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.Paged;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 상품 화면의 리뷰(누구나)와 구매자의 리뷰 쓰기·지우기·내 리뷰. */
@RestController
public class ReviewController {

    private static final Map<String, Boolean> OK = Map.of("ok", true);

    private final Auth auth;
    private final Catalog catalog;
    private final ReviewService reviews;

    public ReviewController(Auth auth, Catalog catalog, ReviewService reviews) {
        this.auth = auth;
        this.catalog = catalog;
        this.reviews = reviews;
    }

    @GetMapping("/api/products/{id}/reviews")
    public ReviewService.ProductReviews forProduct(@PathVariable("id") long id,
                                                   @RequestParam(name = "page", required = false) Integer page,
                                                   HttpServletRequest request) {
        if (catalog.find(id) == null) {
            throw ApiException.notFound();
        }
        Member user = auth.user(request);
        return reviews.forProduct(id, user == null ? null : user.id(), Paged.page(page));
    }

    @GetMapping("/api/my/reviews/writable")
    public List<ReviewService.Writable> writable(HttpServletRequest request) {
        Member user = auth.requireUser(request);
        return reviews.writable(user.id());
    }

    @GetMapping("/api/my/reviews")
    public Paged<Review> mine(@RequestParam(name = "page", required = false) Integer page, HttpServletRequest request) {
        Member user = auth.requireUser(request);
        return reviews.mine(user.id(), Paged.page(page));
    }

    @PostMapping("/api/reviews")
    public Review write(@RequestBody ReviewService.WriteRequest body, HttpServletRequest request) {
        Member user = auth.requireUser(request);
        return reviews.write(user.id(), body);
    }

    @PostMapping("/api/reviews/{id}/delete")
    public Map<String, Boolean> delete(@PathVariable("id") long id, HttpServletRequest request) {
        Member user = auth.requireUser(request);
        reviews.delete(user.id(), id);
        return OK;
    }
}
