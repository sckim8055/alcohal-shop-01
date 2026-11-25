package me.nexthub.sool.catalog;

import java.util.List;

/** 상품 상세: 기본 정보, 꾸민 설명(편집기 문서, JSON 글자), 사진·동영상. */
public record ProductDetail(ProductView product, String descriptionDoc, List<Media> media) {

    /** kind: image, video, youtube. youtube 는 url 자리에 영상 번호(11자)가 들어 있다. */
    public record Media(String kind, String url) {
    }
}
