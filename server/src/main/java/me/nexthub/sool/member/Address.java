package me.nexthub.sool.member;

/** 저장한 배송지. 응답 필드 이름은 isDefault (SQL 에서는 is_default). */
public record Address(long id, String label, String receiver, String phone, String address, boolean isDefault) {
}
