package me.nexthub.sool.order;

import jakarta.servlet.http.HttpServletRequest;

import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.auth.Member;
import me.nexthub.sool.catalog.Catalog;
import me.nexthub.sool.catalog.ProductView;
import me.nexthub.sool.common.ApiException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CartController {

    public record AddRequest(Long productId, Integer qty) {
    }

    public record QtyRequest(Integer qty) {
    }

    private final Auth auth;
    private final Catalog catalog;
    private final OrderService orders;

    public CartController(Auth auth, Catalog catalog, OrderService orders) {
        this.auth = auth;
        this.catalog = catalog;
        this.orders = orders;
    }

    @GetMapping("/api/cart")
    public CartSummary cart(HttpServletRequest request) {
        return orders.cart(auth.requireUser(request).id());
    }

    @PostMapping("/api/cart/items")
    public CartSummary add(@RequestBody AddRequest body, HttpServletRequest request) {
        Member user = auth.requireUser(request);
        int qty = body.qty() == null ? 1 : body.qty();
        ProductView p = body.productId() == null ? null : catalog.find(body.productId());
        if (p == null || !"active".equals(p.status()) || !"approved".equals(p.sellerStatus()) || qty < 1 || qty > 99) {
            throw ApiException.bad("담을 수 없는 상품입니다.");
        }
        orders.addToCart(user.id(), p.id(), qty);
        return orders.cart(user.id());
    }

    /** 수량 변경. 0이면 장바구니에서 뺀다. */
    @PutMapping("/api/cart/items/{productId}")
    public CartSummary setQty(@PathVariable("productId") long productId, @RequestBody QtyRequest body,
                              HttpServletRequest request) {
        Member user = auth.requireUser(request);
        if (body.qty() == null || body.qty() < 0 || body.qty() > 99) {
            throw ApiException.bad("수량은 0~99 사이로 입력해 주세요.");
        }
        orders.setCartQty(user.id(), productId, body.qty());
        return orders.cart(user.id());
    }
}
