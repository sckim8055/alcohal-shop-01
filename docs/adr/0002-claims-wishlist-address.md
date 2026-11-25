# 0002. 취소·반품·교환, 찜, 배송지, 다시 담기, 탈퇴, 판매자 정보

2026-11-25. 구현 규격. 서버와 화면은 이 문서의 이름과 모양을 그대로 따른다. DB 는 `V6__claims_wishlist_address.sql`.

## A. 취소·반품·교환 신청 (claim)

대금은 판매자가 직접 받았으므로 환불도 판매자가 구매자에게 직접 돌려준다. 시스템은 신청과 처리 과정을 기록한다.

### 규칙

- 종류 `claimType`: `cancel`(취소), `return`(반품), `exchange`(교환)
- 신청할 수 있는 때 (판매자별 주문의 상태 기준)
  - `cancel`: `awaiting_deposit` 이면서 입금 신고를 한 뒤, 또는 `paid`. (입금 신고 전의 `awaiting_deposit` 은 기존 "주문 취소"로 바로 취소되므로 신청 대상이 아니다 → 409)
  - `return`, `exchange`: `shipped`, `delivered`, 또는 `completed` 이고 `completed_at` 으로부터 7일 이내. 실물 상품이 없는 주문(`has_physical = false`)은 `return`·`exchange` 불가(409), `cancel` 만 가능.
- 판매자별 주문 하나에 진행 중(`requested`, `approved`)인 신청은 하나만. 있으면 409.
- 사유 `reasonCode`: `changed_mind`, `defect`, `wrong_item`, `late`, `other`. `reasonText` 500자(other 이면 필수).
- 상태 `status`: `requested` → (`approved` | `rejected` | `withdrawn`) ; `approved` → `done`
  - 구매자는 `requested` 일 때만 철회(`withdrawn`)할 수 있다.
  - 판매자 승인(`approve`, `sellerNote` 선택):
    - `cancel`: 승인 즉시 기존 `OrderService.refund` 를 실행(상태 `refunded`, 재고·쿠폰 반환)하고 신청은 바로 `done`.
    - `return`: `approved` (구매자가 물건을 돌려보내는 단계). 판매자가 물건을 받고 돈을 돌려준 뒤 `complete` → `OrderService.refund` 실행, 신청 `done`.
    - `exchange`: `approved`. 판매자가 새 물건을 보낸 뒤 `complete` `{carrier, trackingNo}`(필수, 기존 택배사 코드 검증) → 신청 `done`, 판매자별 주문 상태는 그대로.
  - 판매자 거절(`reject`): `sellerNote` 필수.
- 진행 중인 신청이 있는 판매자별 주문은 구매확정(`confirmReceipt`)을 409 로 막고, 7일 자동 구매확정(`sweep`)에서도 건너뛴다.
- 알림: 신청 → 판매자(메일 포함), 승인·거절·완료 → 구매자(메일 포함), 철회 → 판매자.
- 모든 상태 변경은 조건부 UPDATE.

### DB `order_claim`

id, tenant_id, sub_order_id, member_id, claim_type, reason_code, reason_text VARCHAR(500), status, seller_note VARCHAR(500), carrier VARCHAR(20) NULL, tracking_no VARCHAR(40) NULL, requested_at, answered_at NULL, done_at NULL. 색인 (sub_order_id), (tenant_id, status).

### API

```
Claim {
  id, subOrderId, orderId, orderNo, shopName, buyerName,
  claimType: 'cancel'|'return'|'exchange', reasonCode, reasonText,
  status: 'requested'|'approved'|'rejected'|'done'|'withdrawn', sellerNote: string|null,
  carrier: string|null, trackingNo: string|null,
  items: string            // "상품A × 2, 상품B × 1" 한 줄 요약
  total: number,           // 판매자별 주문 합계
  requestedAt, answeredAt, doneAt
}
```

- 구매자
  - `POST /suborders/{id}/claims` `{ claimType, reasonCode, reasonText }` → `Claim`
  - `POST /claims/{id}/withdraw` → `Claim`
  - `GET /my/claims?page=` → `Paged<Claim>` (쪽당 10)
  - `OrderDetail.Sub` 에 `claim: Claim | null` 추가(그 판매자별 주문의 가장 최근 신청). `OrderService.SellerOrder` 의 sub 도 같다.
- 판매자: `GET /seller/claims?status=&page=` → `Paged<Claim>`; `POST /seller/claims/{id}/approve` `{ sellerNote }`; `POST /seller/claims/{id}/reject` `{ sellerNote }`; `POST /seller/claims/{id}/complete` `{ carrier, trackingNo }` (교환만 본문 사용). 모두 `Claim` 을 돌려준다. 남의 가게 신청은 404.
- 운영자: `GET /admin/claims?status=&page=` → `Paged<Claim>` (보기만)

## B. 찜

- DB `wishlist`: tenant_id, member_id, product_id, created_at. 기본키 (member_id, product_id).
- `POST /wishlist/{productId}` 찜하기(이미 있으면 그대로 200), `POST /wishlist/{productId}/remove` 찜 풀기. 둘 다 `{ wished: boolean }`. 판매 중이 아닌 상품은 찜할 수 없다(404).
- `GET /wishlist/ids` → `number[]` 내가 찜한 상품 번호 전체(화면 표시용, 로그인 필요)
- `GET /my/wishlist?page=` → `Paged<ProductView>` (쪽당 24, 찜한 순서 최근 먼저. 판매가 끝난 상품도 보여 주되 `status` 로 구분)

## C. 배송지

- DB `member_address`: id, tenant_id, member_id, label VARCHAR(30), receiver VARCHAR(40), phone VARCHAR(30), address VARCHAR(300), is_default BOOLEAN, created_at.
- `Address { id, label, receiver, phone, address, isDefault }` 응답 필드 이름은 `isDefault` (SQL 에서 `is_default AS is_default` 로 두고 record 구성요소를 `isDefault` 로).
- `GET /my/addresses` → `Address[]` (기본 배송지 먼저)
- `POST /my/addresses` `{ label, receiver, phone, address, isDefault }` → `Address`. 회원당 10개까지(넘으면 409). 첫 배송지는 자동으로 기본.
- `PUT /my/addresses/{id}` 같은 본문 → `Address`
- `POST /my/addresses/{id}/default` → `Address[]` ; `POST /my/addresses/{id}/delete` → `Address[]` (기본 배송지를 지우면 남은 것 중 가장 최근 것이 기본)
- receiver, phone, address 필수. 남의 배송지는 404.

## D. 다시 담기

- `POST /orders/{id}/reorder` (구매자 본인 주문만) → `{ added: number, skipped: string[] }`. 주문했던 상품 가운데 지금 판매 중이고 재고가 있는 것을 같은 수량(재고·99 한도)으로 장바구니에 더한다. `skipped` 는 담지 못한 상품 이름.

## E. 회원 탈퇴

- `POST /me/withdraw` `{ password }` → `{ ok: true }`
- 비밀번호가 틀리면 400. 다음이면 409 와 이유: 구매자로서 끝나지 않은 주문(`awaiting_deposit`, `paid`, `shipped`, `delivered`)이나 진행 중인 신청이 있음; 판매자(가게가 있는 회원)임 — 판매자는 운영자에게 문의하도록 안내; 운영자임.
- 처리: `status = 'withdrawn'`, 이메일을 `withdrawn-<id>@deleted.local`, 이름을 `탈퇴한 회원`, 비밀번호 해시를 쓸 수 없는 값으로 바꾸고, 장바구니·찜·배송지·쓰지 않은 쿠폰(`member_coupon` 중 `issued`)을 지운다. 주문 기록은 남긴다(거래 기록 보관). `auth.expireSessions` 후 세션을 끊는다.

## F. 판매자 정보와 가게 현황

- `seller` 에 추가: ceo_name VARCHAR(40), biz_address VARCHAR(300), mail_order_no VARCHAR(40)(통신판매업 신고번호), return_address VARCHAR(300), cs_phone VARCHAR(30). 모두 NOT NULL DEFAULT ''.
- `Seller` record 와 `PUT /seller/settings` 본문에 `ceoName`, `bizAddress`, `mailOrderNo`, `returnAddress`, `csPhone` 추가.
- 상품 상세 `ProductDetail` 에 `sellerInfo: { shopName, ceoName, bizNo, bizAddress, mailOrderNo, csPhone, returnAddress }` 추가(전자상거래법상 판매자 정보 표시용. 계좌 정보는 넣지 않는다).
- `GET /seller` 의 `Dashboard` 에 `todo: { claimsRequested, returnsApproved, soldOut, shipLate }` 추가.
  - claimsRequested: 내 가게의 `requested` 신청 수. returnsApproved: `approved` 인 반품·교환 수.
  - soldOut: `on_sale` 인데 재고 0 인 상품 수. shipLate: `paid` 이고 실물 상품이 있으며 입금 확인 후 3일이 지난 주문 수.
- `GET /admin/summary` 에 `claimsOpen`(requested + approved 전체) 추가.

## G. 쿠폰 보완

- 금액 쿠폰(`amount`)은 `minOrder >= discountValue` 여야 한다(아니면 400 "최소 주문 금액은 할인 금액 이상이어야 합니다."). 0원 주문을 막기 위한 것이다. 기존 CouponTest 가 이 규칙에 걸리면 그 테스트의 쿠폰 값만 고친다.

## 시범 데이터

- `SampleData.loadBase()`: 판매자 넷(승인된 가게)에 지어낸 판매자 정보를 채운다(대표자 이름, 주소 "시범시 …", 통신판매업 "제2026-시범-000N호", 반품 주소, 고객 전화 "000-0000-000N"). buyer@test.local 에 배송지 2개(집 기본, 회사).
- `SampleData.loadActivity()`: buyer 가 찜 3개. 기존 시범 주문 가운데 하나(입금 확인만 된 buyer3 의 "발효조" 주문)에 취소 신청 1건(`requested`), 배송 중인 buyer 의 "한울 약주" 주문에 교환 신청 1건(`requested`).
- `SampleData.reset()` 삭제 목록에 새 테이블을 FK 순서에 맞게 넣는다.

## 화면

- 주문 상세(`/orders/[id]`): 판매자별 주문마다 상태에 맞는 단추 — "취소 신청"(입금 신고 후·입금 확인 후), "반품 신청"·"교환 신청"(배송 중·배송 완료·구매 확정 7일 이내). 누르면 그 자리에 사유 고르기(선택 상자)와 자세한 사유 입력, "신청하기". 진행 중인 신청이 있으면 단추 대신 신청 상태(도장 모양), 사유, 판매자 답, 교환 송장, `requested` 일 때 "신청 철회" 단추. 맨 위에 "다시 담기" 단추(결과 안내 후 장바구니 링크).
- `/my/claims` "취소·반품·교환 내역": 목록과 쪽 나누기, 주문 상세 링크. 마이 메뉴 "쇼핑" 묶음에 추가.
- `/my/wishlist` "찜한 상품": 상품 카드 격자(기존 `ProductCard`), 카드마다 "찜 풀기". 마이 메뉴 "활동" 묶음에 추가.
- 상품 상세: "찜하기 / 찜 풀기" 단추(로그인 안 했으면 로그인 링크). 아래에 "판매자 정보" 표(상호, 대표자, 사업자등록번호, 통신판매업 신고번호, 주소, 고객 전화, 반품 주소. 빈 값은 줄을 빼기).
- `/my/addresses` "배송지 관리": 목록(기본 표시), 추가·고치기 폼, 기본으로·지우기. 마이 메뉴 "내 정보" 묶음에 추가.
- `/checkout`: 저장한 배송지가 있으면 받는 분 입력 위에 "배송지 고르기" 선택 상자(고르면 이름·연락처·주소 칸이 채워짐, 기본 배송지는 처음부터 채워 둠). "이 주소를 배송지로 저장" 체크 상자(체크하면 주문 성공 뒤 `POST /my/addresses`).
- `/my/profile`: 맨 아래 "회원 탈퇴" 구역(무엇이 지워지고 무엇이 남는지 안내, 비밀번호 입력, 확인 뒤 탈퇴 → 첫 화면으로).
- `/seller/claims` "취소·반품·교환": 상태 탭(신청됨/승인함/끝남/거절·철회), 신청마다 주문·상품·사유, 승인(메모 선택)·거절(사유 필수)·완료 단추. 교환 완료는 택배사·송장 입력. 반품 승인 시 "가게 설정의 반품 주소가 구매자에게 안내됩니다" 안내. 내 가게 메뉴 "주문과 정산" 묶음에 추가.
- `/seller` 가게 현황: 숫자 칸에 "처리할 신청", "품절 상품", "발송이 늦은 주문" 추가(0 이어도 표시, 링크 연결).
- `/seller/settings`: 판매자 정보 칸 추가(대표자, 사업장 주소, 통신판매업 신고번호, 고객 전화, 반품 주소).
- `/admin/claims` "취소·반품·교환": 전체 목록(보기만), 상태 탭. 운영 메뉴 "주문과 정산" 묶음에 추가. 오늘의 장터에 "진행 중인 취소·반품·교환" 숫자.
- 판매자 주문 처리(`/seller/orders`): 진행 중인 신청이 있는 주문에 "취소 신청이 들어왔습니다" 같은 안내와 `/seller/claims` 링크.
