package me.nexthub.sool.inquiry;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.auth.Member;
import me.nexthub.sool.auth.Seller;
import me.nexthub.sool.catalog.Catalog;
import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.Paged;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 문의 쓰기·지우기, 내 문의, 상품 화면의 문의 목록(누구나). */
@RestController
public class InquiryController {

    private static final Map<String, Boolean> OK = Map.of("ok", true);

    private final Auth auth;
    private final Catalog catalog;
    private final InquiryService inquiries;

    public InquiryController(Auth auth, Catalog catalog, InquiryService inquiries) {
        this.auth = auth;
        this.catalog = catalog;
        this.inquiries = inquiries;
    }

    @PostMapping("/api/inquiries")
    public Inquiry write(@RequestBody InquiryService.WriteRequest body, HttpServletRequest request) {
        Member user = auth.requireUser(request);
        return inquiries.write(user.id(), body);
    }

    @PostMapping("/api/inquiries/{id}/delete")
    public Map<String, Boolean> delete(@PathVariable("id") long id, HttpServletRequest request) {
        Member user = auth.requireUser(request);
        inquiries.delete(user.id(), id);
        return OK;
    }

    @GetMapping("/api/my/inquiries")
    public Paged<Inquiry> mine(@RequestParam(name = "page", required = false) Integer page,
                               HttpServletRequest request) {
        Member user = auth.requireUser(request);
        return inquiries.mine(user.id(), Paged.page(page));
    }

    @GetMapping("/api/products/{id}/inquiries")
    public Paged<Inquiry> forProduct(@PathVariable("id") long id,
                                     @RequestParam(name = "page", required = false) Integer page,
                                     HttpServletRequest request) {
        if (catalog.find(id) == null) {
            throw ApiException.notFound();
        }
        Member user = auth.user(request);
        InquiryService.Viewer viewer = InquiryService.Viewer.ANONYMOUS;
        if (user != null) {
            Seller seller = auth.sellerOf(user.id());
            viewer = new InquiryService.Viewer(user.id(), seller != null && seller.approved() ? seller.id() : null,
                    user.admin());
        }
        return inquiries.forProduct(id, viewer, Paged.page(page));
    }
}
