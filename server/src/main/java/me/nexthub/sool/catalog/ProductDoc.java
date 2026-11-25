package me.nexthub.sool.catalog;

import me.nexthub.sool.common.ApiException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 편집기로 꾸민 상품 설명. HTML 이 아니라 편집기 문서(JSON)로 저장한다.
 * 화면은 이 문서에서 허용된 요소(문단, 제목, 목록, 굵게, 링크, 사진 등)만 골라 그리므로
 * 문서에 스크립트 같은 것이 섞여 들어와도 실행되지 않는다.
 */
public final class ProductDoc {

    public static final int MAX_CHARS = 60_000;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ProductDoc() {
    }

    /** 문서가 올바른지 확인하고 다시 직렬화한 글자를 돌려준다. 비어 있으면 null */
    public static String normalize(JsonNode doc) {
        if (doc == null || doc.isNull() || doc.isMissingNode()) {
            return null;
        }
        if (!doc.isObject() || !"doc".equals(doc.path("type").asString(""))) {
            throw ApiException.bad("상품 설명 형식이 올바르지 않습니다.");
        }
        String text = JSON.writeValueAsString(doc);
        if (text.length() > MAX_CHARS) {
            throw ApiException.bad("상품 설명이 너무 깁니다. 내용을 줄여 주세요.");
        }
        return text;
    }

    /** 문서에서 글자만 뽑는다(목록의 요약, 검색엔진 설명에 쓴다). */
    public static String plainText(JsonNode doc, int max) {
        StringBuilder sb = new StringBuilder();
        collect(doc, sb, max);
        String s = sb.toString().replaceAll("\\s+", " ").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static void collect(JsonNode node, StringBuilder sb, int max) {
        if (node == null || sb.length() > max) {
            return;
        }
        if (node.isObject()) {
            if (node.has("text")) {
                sb.append(node.path("text").asString(""));
            }
            JsonNode content = node.path("content");
            if (content.isArray()) {
                for (JsonNode child : content) {
                    collect(child, sb, max);
                }
                sb.append(' ');
            }
        }
    }
}
