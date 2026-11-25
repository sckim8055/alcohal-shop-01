# 0003. 리뷰, 문의, 공지, 가게 화면, 판매 통계, 최근 본 상품

2026-11-25. 구현 규격. 서버와 화면은 이 문서의 이름과 모양을 그대로 따른다. DB 는 `V7__reviews_inquiries_notices.sql`.

## A. 리뷰

### 규칙

- 구매 확정(`completed`)된 판매자별 주문의 상품 줄(`order_item`)마다 구매자가 한 번 쓸 수 있다. 환불·취소된 주문은 쓸 수 없다(409).
- 별점 `rating` 1~5(정수), 글 `body` 10~1000자, 사진 `images` 0~3장(이 회원이 올린 사진 파일 주소만. `FileController.kindOfOwned` 로 확인, 동영상 불가).
- 쓴 사람은 자기 리뷰를 지울 수 있다(지우면 다시 쓸 수 없다: 줄은 남기고 `status = 'deleted'`).
- 판매자는 자기 가게 상품의 리뷰에 답글을 한 번 달거나 고칠 수 있다(`sellerReply` 500자).
- 운영자는 리뷰를 숨기거나 다시 보이게 할 수 있다(`hidden`). 숨긴 리뷰는 상품 화면과 평점 계산에서 빠진다.
- 상품 화면에는 `visible` 리뷰만 보이고, 쓴 사람 이름은 가운데를 가린다(`김구매` → `김*매`, 두 글자면 `김*`, 영문·기타도 첫 글자와 끝 글자만).
- 리뷰가 달리면 판매자에게, 답글이 달리면 구매자에게 알림.
- `ProductView` 에 `reviewCount`(visible 수), `ratingAvg`(소수 한 자리, 리뷰가 없으면 null) 추가. `GET /products` 의 `sort` 에 `rating`(평점 높은 순, 리뷰 없는 상품은 뒤) 추가.

### DB `review`

id, tenant_id, product_id, order_item_id(유일), seller_id, member_id, rating INT, body VARCHAR(1000), images VARCHAR(400) NOT NULL DEFAULT ''(주소를 쉼표로 이음), status(`visible`|`hidden`|`deleted`), seller_reply VARCHAR(500) NULL, replied_at NULL, created_at. 색인 (product_id, status), (seller_id), (member_id).

### API

```
Review { id, productId, productName, rating, body, images: string[], authorName, mine: boolean,
         status: 'visible'|'hidden'|'deleted', sellerReply: string|null, repliedAt, createdAt }
Writable { orderItemId, orderId, productId, productName, shopName, qty, completedAt }
```

- `GET /products/{id}/reviews?page=` → `{ ratingAvg: number|null, reviewCount, counts: number[5] (별 1개~5개 순서), page: Paged<Review> }` 누구나. 쪽당 10, 최근 먼저.
- `GET /my/reviews/writable` → `Writable[]` 아직 리뷰를 쓰지 않은 구매 확정 상품(최근 90일)
- `GET /my/reviews?page=` → `Paged<Review>` 내가 쓴 리뷰(숨김 포함, 지운 것 제외)
- `POST /reviews` `{ orderItemId, rating, body, images }` → `Review`
- `POST /reviews/{id}/delete` → `{ ok: true }`
- 판매자: `GET /seller/reviews?unanswered=true|false&page=` → `Paged<Review>` (쪽당 20); `POST /seller/reviews/{id}/reply` `{ reply }` → `Review`
- 운영자: `GET /admin/reviews?status=&page=` → `Paged<Review>`; `POST /admin/reviews/{id}/hide` `{ hidden: boolean }` → `Review`

## B. 문의

### 규칙

- 종류는 받는 쪽과 대상으로 정해진다.
  - 상품 문의: `target = 'seller'`, `productId` 있음. 상품 화면에 목록이 보인다. `secret` 이면 쓴 사람·그 가게 판매자·운영자에게만 내용이 보이고, 다른 사람에게는 제목 "비밀글입니다", 본문·답변 null.
  - 주문 문의: `target = 'seller'`, `subOrderId` 있음(구매자 본인의 주문만). 항상 비밀.
  - 1:1 문의: `target = 'admin'`. 항상 비밀. 구매자·판매자 누구나 운영자에게 물을 수 있다.
- 제목 2~100자, 본문 5~2000자. 답변 2000자. 답변은 받는 쪽(그 가게 판매자 또는 운영자)이 한 번 달거나 고칠 수 있다.
- 문의가 오면 받는 쪽에 알림(판매자 문의는 그 판매자, 1:1 문의는 운영자 전원), 답변이 달리면 쓴 사람에게 알림(메일 포함).
- 쓴 사람은 답변이 달리기 전에만 지울 수 있다.

### DB `inquiry`

id, tenant_id, member_id, target, seller_id NULL, product_id NULL, sub_order_id NULL, title VARCHAR(100), body VARCHAR(2000), secret BOOLEAN, status(`open`|`answered`|`deleted`), answer VARCHAR(2000) NULL, answered_by NULL, answered_at NULL, created_at. 색인 (product_id, status), (seller_id, status), (member_id), (tenant_id, target, status).

### API

```
Inquiry { id, target: 'seller'|'admin', kind: 'product'|'order'|'admin', productId, productName, subOrderId, orderId, orderNo,
          shopName, title, body: string|null, secret, authorName, mine: boolean,
          status: 'open'|'answered', answer: string|null, answeredAt, createdAt }
```

- `POST /inquiries` `{ target, productId, subOrderId, title, body, secret }` → `Inquiry` (로그인 필요)
- `POST /inquiries/{id}/delete` → `{ ok: true }`
- `GET /my/inquiries?page=` → `Paged<Inquiry>` (쪽당 10)
- `GET /products/{id}/inquiries?page=` → `Paged<Inquiry>` 누구나(비밀글은 위 규칙대로 가림, authorName 은 리뷰와 같은 방식으로 가림). 쪽당 10.
- 판매자: `GET /seller/inquiries?unanswered=true|false&page=` → `Paged<Inquiry>` (쪽당 20); `POST /seller/inquiries/{id}/answer` `{ answer }` → `Inquiry`
- 운영자: `GET /admin/inquiries?unanswered=true|false&page=` → `Paged<Inquiry>` (1:1 문의만); `POST /admin/inquiries/{id}/answer` `{ answer }` → `Inquiry`

## C. 공지

- DB `notice`: id, tenant_id, audience(`all`|`seller`), title VARCHAR(100), body VARCHAR(5000), pinned BOOLEAN, status(`published`|`deleted`), created_by, created_at.
- `Notice { id, audience, title, body, pinned, createdAt }`
- `GET /notices?page=` → `Paged<Notice>` 누구나, `all` 공지만(고정 먼저, 최근 먼저, 쪽당 10). `GET /notices/{id}` → `Notice` (판매자 대상 공지는 승인된 판매자와 운영자만, 아니면 404)
- 판매자: `GET /seller/notices?page=` → `Paged<Notice>` (`all` + `seller`)
- 운영자: `GET /admin/notices?page=` (전체), `POST /admin/notices` `{ audience, title, body, pinned }`, `PUT /admin/notices/{id}`, `POST /admin/notices/{id}/delete`
- 본문은 글자 그대로 저장하고 화면은 줄바꿈만 살려 보여 준다(HTML 아님).

## D. 가게 화면

- `GET /shops/{sellerId}` → `{ shop: { id, shopName, intro, ceoName, bizNo, mailOrderNo, csPhone, productCount, reviewCount, ratingAvg }, products: Paged<ProductView> }` (`?page=`, 쪽당 24). 승인된 가게만(아니면 404). 계좌 정보는 넣지 않는다.
- `ProductView` 의 `sellerId` 로 상품 카드·상세에서 가게 화면으로 연결한다.

## E. 판매 통계 (판매자)

- `GET /seller/stats?days=7|30|90` (기본 30) → `{ days, from, to, totals: { orders, sales, items }, daily: { date, orders, sales }[] (빈 날도 0 으로 채움, 날짜 오름차순, 한국 시간 기준), topProducts: { productId, name, qty, sales }[] (매출 순 10개) }`
- 기준: 입금 확인된 날(`deposit_confirmed_at`, 한국 시간 날짜)로 묶고, 취소·환불된 주문은 뺀다. sales 는 상품 금액 합(배송비·쿠폰 할인 제외).

## F. 최근 본 상품

- 서버에 저장하지 않는다. 화면이 브라우저 `localStorage` 에 상품 번호를 최근 12개까지 적어 두고, `GET /products/by-ids?ids=1,2,3` → `ProductView[]`(판매 중인 것만, 요청 순서 유지, 최대 24개)로 불러온다.

## G. 현황 숫자

- `GET /seller` 의 `todo` 에 `inquiriesUnanswered`, `reviewsUnanswered` 추가.
- `GET /admin/summary` 에 `inquiriesUnanswered`(1:1 문의 중 답 안 한 것) 추가.

## 시범 데이터

- `loadActivity()`: 구매 확정된 시범 주문(buyer3 의 "달빛 사과 와인"·"달빛 오미자주", "막걸리 빚기 하루 수업")에 리뷰 3개(별 5·4·5, 그중 하나에 판매자 답글). 상품 문의 3개("한울 생막걸리 750ml" 공개 1개 답변됨, "전통 누룩 1kg" 공개 1개 미답변, "발효조 20L" 비밀 1개 미답변), 1:1 문의 1개(미답변). 공지 2개(`all` 고정 "술장터 시범 운영 안내", `seller` "수수료 청구 안내").
- `reset()` 삭제 목록에 새 테이블 추가.

## 화면

- 상품 상세: 이름 아래 평점(별과 숫자, 리뷰 수 — 리뷰 구역으로 이동 링크), 가게 이름을 가게 화면 링크로. 아래에 "리뷰" 구역(평균, 별점별 막대 5줄, 목록, 사진은 누르면 크게, 판매자 답글, 쪽 나누기)과 "상품 문의" 구역(목록, 비밀글 표시, 답변, "문의하기" 폼: 제목·내용·비밀글 체크). 상품 카드(`ProductCard`)에는 리뷰가 있을 때만 별점과 리뷰 수 한 줄.
- 별 모양은 글자(★☆)로 그리고 색은 `--marker`. 새 색을 들이지 않는다.
- `/my/reviews` "리뷰 관리": "쓸 수 있는 리뷰"(상품마다 "리뷰 쓰기" → 별점 고르기, 글, 사진 3장까지 올리기)와 "내가 쓴 리뷰"(지우기) 탭. 마이 메뉴 "활동" 묶음.
- `/my/inquiries` "문의 내역": 목록(종류, 답변 여부, 답변 펼쳐 보기), "운영자에게 1:1 문의" 폼. 주문 상세의 판매자별 주문마다 "판매자에게 문의" 단추(주문 문의 폼). 마이 메뉴 "활동" 묶음.
- `/shops/[id]` 가게 화면: 간판처럼 가게 이름과 소개, 평점·상품 수, 판매자 정보, 상품 격자와 쪽 나누기. 서버에서 그리는 화면(검색에 잡히게).
- `/notices`, `/notices/[id]` 공지: 목록(고정 표시)과 본문. 화면 맨 아래 띠(footer)에 "공지" 링크. 첫 화면에 고정 공지가 있으면 제목 한 줄을 띠로 보여 준다.
- 첫 화면 정렬에 "평점 높은 순" 추가. 첫 화면 아래에 "최근 본 상품" 줄(있을 때만). 상품 상세를 열면 기록한다.
- `/seller/reviews`(답 안 한 것 / 전체 탭, 답글 쓰기·고치기), `/seller/inquiries`(답 안 한 것 / 전체 탭, 답변 쓰기·고치기, 상품·주문 링크), `/seller/notices`, `/seller/stats`(기간 7·30·90일 단추, 합계 숫자 칸, 날짜별 매출 막대 그림은 SVG 로 직접 그림 — 막대 색 `--bottle`, 축 글자는 본문 글꼴, 막대에 마우스를 올리면 날짜와 금액, 아래에 같은 자료의 표를 접어서 제공; 상품 순위 표). 내 가게 메뉴에 "고객" 묶음(리뷰, 문의)과 "가게" 묶음의 "판매 통계", "공지" 추가. 가게 현황 숫자에 "답할 문의", "답글 없는 리뷰" 추가.
- `/admin/reviews`(숨기기·다시 보이기), `/admin/inquiries`(1:1 문의 답변), `/admin/notices`(쓰기·고치기·지우기, 대상·고정). 운영 메뉴에 "고객" 묶음. 오늘의 장터에 "답할 1:1 문의" 숫자.
