package me.nexthub.sool.auth;

import java.time.LocalDate;

/** 판매자(가게) 정보. 본인과 운영자에게만 전체를 보여 준다. */
public record Seller(
        long id, long memberId, String shopName, String bizNo, String intro, String status,
        String bankName, String bankAccount, String bankHolder, String payLink,
        String licenseType, String licenseNo, boolean licenseVerified,
        int baseShipFee, Integer freeShipMin, LocalDate freeShipStart, LocalDate freeShipEnd,
        Integer commissionRateBp,
        String ceoName, String bizAddress, String mailOrderNo, String returnAddress, String csPhone) {

    public boolean approved() {
        return "approved".equals(status);
    }
}
