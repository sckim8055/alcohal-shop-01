package me.nexthub.sool.common;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** 입력값 정리. */
public final class Text {

    private Text() {
    }

    /** 앞뒤 공백을 없애고 최대 길이로 자른다. null은 빈 문자열로 본다. */
    public static String clean(String v, int max) {
        if (v == null) {
            return "";
        }
        String s = v.trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** yyyy-MM-dd 형식이 아니면 null */
    public static LocalDate date(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(v.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
