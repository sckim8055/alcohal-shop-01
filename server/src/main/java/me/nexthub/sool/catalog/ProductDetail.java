package me.nexthub.sool.catalog;

import java.util.List;

/** 상품 상세: 기본 정보, 꾸민 설명(편집기 문서, JSON 글자), 사진·동영상, 판매자 정보. */
public record ProductDetail(ProductView product, String descriptionDoc, List<Media> media, SellerInfo sellerInfo) {

    /**
     * 전자상거래법상 상품 화면에 보여 줄 판매자 정보. 계좌는 넣지 않는다:
     * 입금 안내는 주문한 사람에게 주문 화면에서만 보여 주기 위해서다.
     */
    public record SellerInfo(String shopName, String ceoName, String bizNo, String bizAddress, String mailOrderNo,
                             String csPhone, String returnAddress) {
    }

    /** kind: image, video, youtube. youtube 는 url 자리에 영상 번호(11자)가 들어 있다. */
    public record Media(String kind, String url) {
    }
}
