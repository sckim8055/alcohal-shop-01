package me.nexthub.sool.member;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.auth.Member;
import me.nexthub.sool.catalog.ProductView;
import me.nexthub.sool.common.Paged;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 찜하기, 찜 풀기, 찜한 상품. */
@RestController
public class WishlistController {

    private final Auth auth;
    private final WishlistService wishlist;

    public WishlistController(Auth auth, WishlistService wishlist) {
        this.auth = auth;
        this.wishlist = wishlist;
    }

    @PostMapping("/api/wishlist/{productId}")
    public Map<String, Boolean> add(@PathVariable("productId") long productId, HttpServletRequest request) {
        Member user = auth.requireUser(request);
        wishlist.add(user.id(), productId);
        return Map.of("wished", true);
    }

    @PostMapping("/api/wishlist/{productId}/remove")
    public Map<String, Boolean> remove(@PathVariable("productId") long productId, HttpServletRequest request) {
        Member user = auth.requireUser(request);
        wishlist.remove(user.id(), productId);
        return Map.of("wished", false);
    }

    /** 화면에서 하트 표시를 하기 위한 내가 찜한 상품 번호 전체 */
    @GetMapping("/api/wishlist/ids")
    public List<Long> ids(HttpServletRequest request) {
        Member user = auth.requireUser(request);
        return wishlist.ids(user.id());
    }

    @GetMapping("/api/my/wishlist")
    public Paged<ProductView> mine(@RequestParam(name = "page", required = false) Integer page,
                                   HttpServletRequest request) {
        Member user = auth.requireUser(request);
        return wishlist.list(user.id(), Paged.page(page));
    }
}
