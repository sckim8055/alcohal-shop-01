package me.nexthub.sool.catalog;

import java.util.ArrayList;
import java.util.List;

import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.Paged;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 누구나 볼 수 있는 상품·분류 조회. 주류도 보는 것은 제한하지 않는다(성인인증은 주문 시점). */
@RestController
public class CatalogController {

    private final Catalog catalog;

    public CatalogController(Catalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/api/categories")
    public List<Category> categories() {
        return catalog.categories(false);
    }

    /** 한 번에 불러올 수 있는 상품 수(화면은 최근 본 상품을 12개까지 적어 둔다) */
    private static final int MAX_BY_IDS = 24;

    /** sort: new(기본), price_asc, price_desc, rating(평점 높은 순, 리뷰 없는 상품은 뒤) */
    @GetMapping("/api/products")
    public Paged<ProductView> products(@RequestParam(name = "cat", required = false) Long cat,
                                       @RequestParam(name = "q", required = false) String q,
                                       @RequestParam(name = "sort", required = false) String sort,
                                       @RequestParam(name = "page", required = false) Integer page,
                                       @RequestParam(name = "size", required = false) Integer size) {
        return catalog.list(cat, q, sort, Paged.page(page), Paged.size(size, 24));
    }

    /**
     * 최근 본 상품. ids=1,2,3 순서를 지키고 판매 중인 것만 돌려준다.
     * 화면의 localStorage 값이 깨져 있어도 목록이 안 나오는 일은 없게, 숫자가 아닌 값은 건너뛴다.
     */
    @GetMapping("/api/products/by-ids")
    public List<ProductView> byIds(@RequestParam(name = "ids", required = false) String ids) {
        List<Long> parsed = new ArrayList<>();
        if (ids != null) {
            for (String part : ids.split(",")) {
                if (parsed.size() >= MAX_BY_IDS) {
                    break;
                }
                try {
                    parsed.add(Long.parseLong(part.trim()));
                } catch (NumberFormatException e) {
                    // 건너뛴다
                }
            }
        }
        return catalog.byIds(parsed);
    }

    @GetMapping("/api/products/{id}")
    public ProductDetail product(@PathVariable("id") long id) {
        ProductView p = catalog.find(id);
        if (p == null || !p.onSale()) {
            throw ApiException.notFound();
        }
        return catalog.detail(p);
    }
}
