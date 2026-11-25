package me.nexthub.sool.catalog;

/** 분류. parentId 가 없으면 대분류, 있으면 그 아래 중분류다. */
public record Category(long id, Long parentId, String name, boolean alcohol, Integer commissionRateBp,
                       int sortOrder, boolean active) {
}
