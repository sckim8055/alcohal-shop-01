package me.nexthub.sool.common;

import java.util.List;

/** 목록 한 쪽. page 는 1부터 센다. */
public record Paged<T>(List<T> items, int page, int size, long total) {

    public static int page(Integer page) {
        return page == null || page < 1 ? 1 : Math.min(page, 100_000);
    }

    public static int size(Integer size, int defaultSize) {
        return size == null || size < 1 ? defaultSize : Math.min(size, 100);
    }

    public static int offset(int page, int size) {
        return (page - 1) * size;
    }
}
