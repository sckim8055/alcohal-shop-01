package me.nexthub.sool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import me.nexthub.sool.sample.SampleData;
import me.nexthub.sool.support.Api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.JsonNode;

/** 검토에서 보완한 규칙: 취소 신청 중 발송 방지, 승인된 반품의 반품 주소 안내. */
@SpringBootTest
class ClaimExtraTest {

    static final Map<String, String> ADDRESS = Map.of("receiver", "김구매", "phone", "010-0000-0000", "address", "시범시 시범로 1");

    @Autowired
    WebApplicationContext context;
    @Autowired
    SampleData sample;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        sample.reset();
        sample.loadBase();
    }

    Api as(String email) {
        return new Api(mvc).login(email);
    }

    /** 누룩 한 봉지를 주문해 입금 확인까지 마친 판매자별 주문 번호 */
    long paidOrder(Api buyer, Api seller) {
        buyer.post("/api/cart/items", Map.of("productId", sample.productId("전통 누룩 1kg"), "qty", 1));
        JsonNode order = buyer.post("/api/orders", ADDRESS).body();
        long sub = order.get("subs").get(0).get("sub").get("id").asLong();
        assertEquals(200, seller.post("/api/seller/suborders/" + sub + "/confirm").status());
        return sub;
    }

    @Test
    @DisplayName("취소 신청이 들어와 있으면 발송할 수 없고, 거절한 뒤에는 발송할 수 있다")
    void cannotShipWhileCancelRequested() {
        Api buyer = as("buyer@test.local");
        Api seller = as("supplies@test.local");
        long sub = paidOrder(buyer, seller);
        Map<String, String> ship = Map.of("carrier", "cj", "trackingNo", "1234");

        JsonNode claim = buyer.post("/api/suborders/" + sub + "/claims",
                Map.of("claimType", "cancel", "reasonCode", "changed_mind", "reasonText", "")).body();
        assertEquals(409, seller.post("/api/seller/suborders/" + sub + "/ship", ship).status());

        long id = claim.get("id").asLong();
        assertEquals(200, seller.post("/api/seller/claims/" + id + "/reject", Map.of("sellerNote", "이미 포장했습니다")).status());
        assertEquals(200, seller.post("/api/seller/suborders/" + sub + "/ship", ship).status());
    }

    @Test
    @DisplayName("반품이 승인되면 구매자에게 반품 주소가 보이고, 그 전과 취소 신청에는 보이지 않는다")
    void returnAddressShownAfterApproval() {
        Api buyer = as("buyer@test.local");
        Api seller = as("supplies@test.local");
        long sub = paidOrder(buyer, seller);
        seller.post("/api/seller/suborders/" + sub + "/ship", Map.of("carrier", "cj", "trackingNo", "1234"));

        JsonNode claim = buyer.post("/api/suborders/" + sub + "/claims",
                Map.of("claimType", "return", "reasonCode", "defect", "reasonText", "봉지가 터져 있었습니다")).body();
        assertTrue(claim.get("returnAddress").isNull());

        long id = claim.get("id").asLong();
        JsonNode approved = seller.post("/api/seller/claims/" + id + "/approve", Map.of("sellerNote", "")).body();
        assertTrue(approved.get("returnAddress").asString().contains("반품"));
        JsonNode mine = buyer.get("/api/my/claims").body().get("items").get(0);
        assertTrue(mine.get("returnAddress").asString().contains("반품"));
    }
}
