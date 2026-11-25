package me.nexthub.sool.catalog;

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

    /** sort: new(기본), price_asc, price_desc */
    @GetMapping("/api/products")
    public Paged<ProductView> products(@RequestParam(name = "cat", required = false) Long cat,
                                       @RequestParam(name = "q", required = false) String q,
                                       @RequestParam(name = "sort", required = false) String sort,
                                       @RequestParam(name = "page", required = false) Integer page,
                                       @RequestParam(name = "size", required = false) Integer size) {
        return catalog.list(cat, q, sort, Paged.page(page), Paged.size(size, 24));
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
