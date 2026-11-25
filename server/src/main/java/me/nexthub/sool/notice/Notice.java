package me.nexthub.sool.notice;

import java.time.LocalDateTime;

/** 운영자 공지(docs/adr/0003 C). audience: all(누구나) 또는 seller(승인된 판매자). body 는 쓴 글자 그대로(HTML 아님). */
public record Notice(long id, String audience, String title, String body, boolean pinned, LocalDateTime createdAt) {
}
