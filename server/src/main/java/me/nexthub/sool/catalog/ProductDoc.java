package me.nexthub.sool.catalog;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import me.nexthub.sool.common.ApiException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 편집기로 꾸민 상품 설명. HTML 이 아니라 편집기 문서(JSON)로 저장한다.
 * 화면은 이 문서에서 허용된 요소(문단, 제목, 목록, 굵게, 링크, 사진 등)만 골라 그리므로
 * 문서에 스크립트 같은 것이 섞여 들어와도 실행되지 않는다.
 * 그래도 저장할 때 허용 목록으로 한 번 더 검사한다. 화면 코드가 바뀌거나 다른 곳(앱 등)이 이 문서를 그릴 때도
 * 위험한 주소가 남아 있지 않게 하려는 것이다. 허용 규칙은 web/lib/richdoc.ts 와 같아야 한다.
 */
public final class ProductDoc {

    public static final int MAX_CHARS = 60_000;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final int MAX_DEPTH = 40;
    private static final int MAX_IMAGES = 200;
    private static final int MAX_VIDEOS = 20;

    /** 노드 종류 → 그 안에 들어갈 수 있는 자식. 빈 집합은 자식이 없어야 하는 노드 */
    private static final Set<String> BLOCK = Set.of("paragraph", "heading", "bulletList", "orderedList", "blockquote",
            "callout", "horizontalRule", "image", "imageRow", "video", "youtube", "table");
    private static final Set<String> INLINE = Set.of("text", "hardBreak");
    private static final Map<String, Set<String>> CHILDREN = Map.ofEntries(
            Map.entry("doc", BLOCK),
            Map.entry("paragraph", INLINE),
            Map.entry("heading", INLINE),
            Map.entry("bulletList", Set.of("listItem")),
            Map.entry("orderedList", Set.of("listItem")),
            Map.entry("listItem", BLOCK),
            Map.entry("blockquote", BLOCK),
            Map.entry("callout", Set.of("paragraph")),
            Map.entry("imageRow", Set.of("image")),
            Map.entry("table", Set.of("tableRow")),
            Map.entry("tableRow", Set.of("tableCell", "tableHeader")),
            Map.entry("tableCell", BLOCK),
            Map.entry("tableHeader", BLOCK),
            Map.entry("horizontalRule", Set.of()),
            Map.entry("image", Set.of()),
            Map.entry("video", Set.of()),
            Map.entry("youtube", Set.of()),
            Map.entry("hardBreak", Set.of()),
            Map.entry("text", Set.of()));
    private static final Set<String> MARKS = Set.of("bold", "italic", "underline", "strike", "link", "textStyle");
    private static final Set<String> ALIGNS = Set.of("left", "center", "right", "justify");

    private static final Pattern OWN_IMAGE = Pattern.compile("^/api/files/[a-f0-9]{32}\\.(jpg|png|gif|webp)$");
    private static final Pattern OWN_VIDEO = Pattern.compile("^/api/files/[a-f0-9]{32}\\.(mp4|webm)$");
    private static final Pattern OUTSIDE_IMAGE = Pattern.compile("^https://[^\\s\"'<>]{1,2000}$");
    private static final Pattern LINK = Pattern.compile("^(?i)https?://[^\\s\"'<>]{1,2000}$");
    private static final Pattern YOUTUBE = Pattern.compile("^https?://(?:www\\.|m\\.)?(?:youtube\\.com/(?:watch\\?(?:[^#]*&)?v=|embed/|shorts/|live/)"
            + "|youtube-nocookie\\.com/embed/|youtu\\.be/)([A-Za-z0-9_-]{11})(?:[?&#/].*)?$");
    private static final Pattern COLOR = Pattern.compile("^(#[0-9a-fA-F]{3,8}|rgba?\\(\\s*[0-9.]+%?\\s*,\\s*[0-9.]+%?\\s*,\\s*[0-9.]+%?\\s*(,\\s*[0-9.]+\\s*)?\\))$");
    private static final Pattern SIZE = Pattern.compile("^[0-9]{1,2}(\\.[0-9]+)?(px|em|rem)$");

    /** 검사하면서 센 사진·동영상 수 */
    private static final class Count {
        int images;
        int videos;
    }

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
        check(doc, "doc", 0, new Count());
        String text = JSON.writeValueAsString(doc);
        if (text.length() > MAX_CHARS) {
            throw ApiException.bad("상품 설명이 너무 깁니다. 내용을 줄여 주세요.");
        }
        return text;
    }

    private static void check(JsonNode node, String parent, int depth, Count count) {
        if (depth > MAX_DEPTH) {
            throw bad("너무 깊게 겹친 내용");
        }
        String type = node.path("type").asString("");
        Set<String> allowedHere = CHILDREN.get(parent);
        if (!CHILDREN.containsKey(type) || allowedHere == null || (depth > 0 && !allowedHere.contains(type))) {
            throw bad(type.isEmpty() ? "종류가 없는 블록" : "'" + clip(type) + "' 블록");
        }
        JsonNode attrs = node.path("attrs");
        switch (type) {
            case "text" -> {
                if (!node.path("text").isString()) {
                    throw bad("글자가 없는 글 조각");
                }
                checkMarks(node.path("marks"));
            }
            case "heading" -> {
                int level = attrs.path("level").asInt(2);
                if (level != 2 && level != 3) {
                    throw bad("제목 크기");
                }
                checkAlign(attrs);
            }
            case "paragraph" -> checkAlign(attrs);
            case "image" -> {
                String src = attrs.path("src").asString("");
                if (!OWN_IMAGE.matcher(src).matches() && !OUTSIDE_IMAGE.matcher(src).matches()) {
                    throw bad("사진 주소(https:// 로 시작하거나 이 사이트에 올린 사진만)");
                }
                if (++count.images > MAX_IMAGES) {
                    throw ApiException.bad("상품 설명에는 사진을 " + MAX_IMAGES + "장까지 넣을 수 있습니다.");
                }
            }
            case "video" -> {
                if (!OWN_VIDEO.matcher(attrs.path("src").asString("")).matches()) {
                    throw bad("동영상 주소(이 사이트에 올린 동영상만)");
                }
                countVideo(count);
            }
            case "youtube" -> {
                if (!YOUTUBE.matcher(attrs.path("src").asString("")).matches()) {
                    throw bad("유튜브 주소");
                }
                countVideo(count);
            }
            case "imageRow" -> {
                String layout = attrs.path("layout").asString("grid");
                if (!"grid".equals(layout) && !"slide".equals(layout)) {
                    throw bad("사진 묶음 모양");
                }
            }
            default -> {
            }
        }
        JsonNode content = node.path("content");
        if (!content.isMissingNode() && !content.isNull()) {
            if (!content.isArray()) {
                throw bad("'" + type + "' 의 내용 형식");
            }
            if (CHILDREN.get(type).isEmpty() && !content.isEmpty()) {
                throw bad("'" + type + "' 안의 내용");
            }
            for (JsonNode child : content) {
                check(child, type, depth + 1, count);
            }
        }
    }

    private static void checkMarks(JsonNode marks) {
        if (marks.isMissingNode() || marks.isNull()) {
            return;
        }
        if (!marks.isArray()) {
            throw bad("글자 꾸밈 형식");
        }
        for (JsonNode m : marks) {
            String type = m.path("type").asString("");
            if (!MARKS.contains(type)) {
                throw bad("'" + clip(type) + "' 글자 꾸밈");
            }
            JsonNode attrs = m.path("attrs");
            if ("link".equals(type) && !LINK.matcher(attrs.path("href").asString("")).matches()) {
                throw bad("링크 주소(http:// 또는 https:// 로 시작해야 함)");
            }
            if ("textStyle".equals(type)) {
                String color = textOrNull(attrs.path("color"));
                String size = textOrNull(attrs.path("fontSize"));
                if (color != null && !COLOR.matcher(color).matches()) {
                    throw bad("글자 색");
                }
                if (size != null && !SIZE.matcher(size).matches()) {
                    throw bad("글자 크기");
                }
            }
        }
    }

    private static void checkAlign(JsonNode attrs) {
        String align = textOrNull(attrs.path("textAlign"));
        if (align != null && !ALIGNS.contains(align)) {
            throw bad("글 맞춤");
        }
    }

    private static void countVideo(Count count) {
        if (++count.videos > MAX_VIDEOS) {
            throw ApiException.bad("상품 설명에는 동영상을 " + MAX_VIDEOS + "개까지 넣을 수 있습니다.");
        }
    }

    private static String textOrNull(JsonNode v) {
        return v.isString() ? v.asString() : null;
    }

    private static String clip(String s) {
        return s.length() > 30 ? s.substring(0, 30) : s;
    }

    private static ApiException bad(String what) {
        return ApiException.bad("상품 설명에 넣을 수 없는 내용이 있습니다: " + what);
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
