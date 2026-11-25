package me.nexthub.sool.catalog;

import java.util.List;

import me.nexthub.sool.common.ApiException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 누구나 볼 수 있는 상품·카테고리 조회. 주류도 보는 것은 제한하지 않는다(성인인증은 주문 시점). */
@RestController
public class CatalogController {

    private final Catalog catalog;

    public CatalogController(Catalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/api/categories")
    public List<Category> categories() {
        return catalog.categories();
    }

    @GetMapping("/api/products")
    public List<ProductView> products(@RequestParam(name = "cat", required = false) Long cat,
                                      @RequestParam(name = "q", required = false) String q) {
        return catalog.list(cat, q);
    }

    @GetMapping("/api/products/{id}")
    public ProductView product(@PathVariable("id") long id) {
        ProductView p = catalog.find(id);
        if (p == null || !"active".equals(p.status()) || !"approved".equals(p.sellerStatus())) {
            throw ApiException.notFound();
        }
        return p;
    }
}
