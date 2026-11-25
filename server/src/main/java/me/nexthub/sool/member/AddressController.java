package me.nexthub.sool.member;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.auth.Member;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 배송지 관리. 지우기는 POST 로 받는다(다른 변경 요청과 같은 방식으로 위조 요청을 막기 위해). */
@RestController
public class AddressController {

    private final Auth auth;
    private final AddressService addresses;

    public AddressController(Auth auth, AddressService addresses) {
        this.auth = auth;
        this.addresses = addresses;
    }

    @GetMapping("/api/my/addresses")
    public List<Address> list(HttpServletRequest request) {
        Member user = auth.requireUser(request);
        return addresses.list(user.id());
    }

    @PostMapping("/api/my/addresses")
    public Address create(@RequestBody AddressService.AddressRequest body, HttpServletRequest request) {
        Member user = auth.requireUser(request);
        return addresses.create(user.id(), body);
    }

    @PutMapping("/api/my/addresses/{id}")
    public Address update(@PathVariable("id") long id, @RequestBody AddressService.AddressRequest body,
                          HttpServletRequest request) {
        Member user = auth.requireUser(request);
        return addresses.update(user.id(), id, body);
    }

    @PostMapping("/api/my/addresses/{id}/default")
    public List<Address> setDefault(@PathVariable("id") long id, HttpServletRequest request) {
        Member user = auth.requireUser(request);
        return addresses.setDefault(user.id(), id);
    }

    @PostMapping("/api/my/addresses/{id}/delete")
    public List<Address> delete(@PathVariable("id") long id, HttpServletRequest request) {
        Member user = auth.requireUser(request);
        return addresses.delete(user.id(), id);
    }
}
