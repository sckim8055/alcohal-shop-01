# 0001. 쿠폰

2026-11-25. 구현 규격. 서버와 화면은 이 문서의 이름과 모양을 그대로 따른다.

## 결정

1. 쿠폰마다 비용을 누가 내는지 정해져 있다. 발행자(`issuer`)가 곧 부담자다.
   - `seller`: 판매자가 만들고 부담한다. 그 가게 주문에만 쓸 수 있다.
   - `platform`: 운영자가 만들고 플랫폼이 부담한다. 어느 가게 주문에나 쓸 수 있다. 구매자가 덜 입금한 금액은 그 가게의 월 수수료 청구서에서 빼 준다(`platformCouponCredit`).
2. 술은 기본적으로 쿠폰 대상에서 뺀다(`excludeAlcohol` 기본 true). 주류 할인 규제가 확인되지 않았기 때문이다. 판매자 쿠폰은 항상 true, 플랫폼 쿠폰만 운영자가 false 로 바꿀 수 있다.
3. 주문 한 건 안에서 가게(판매자별 주문)마다 쿠폰을 한 장까지 쓴다. 한 장의 쿠폰은 한 번만 쓴다.
4. 판매자별 주문이 취소·환불되면 쓴 쿠폰을 돌려준다(유효기간이 남았을 때만 다시 쓸 수 있다).

## 종류

- 할인 방식 `discountType`: `amount`(금액), `percent`(비율, `maxDiscount` 로 상한), `free_shipping`(그 가게 배송비 전액)
- 받는 방법 `issueType`: `signup`(가입하면 자동 지급), `download`(쿠폰 받기 화면에서 받기), `code`(코드 입력), `grant`(운영자가 회원에게 직접 지급)
- 판매자는 `download`, `code` 쿠폰만 만들 수 있다.

## 계산

- 대상 금액(eligible) = 그 가게 묶음의 상품 줄 가운데 쿠폰 범위에 드는 줄의 합. 범위: `categoryId` 가 있으면 그 분류(대분류면 아래 중분류 포함)의 상품만, `excludeAlcohol` 이면 술 제외.
- 쓸 수 있는 조건: 쿠폰 상태 `active`, 오늘이 `validFrom`~`validTo` 안, 회원 쿠폰 상태 `issued` 이고 `expiresAt` 이전, 판매자 쿠폰이면 그 가게, 대상 금액 ≥ `minOrder`, 대상 금액 > 0 (무료배송은 배송비 > 0 이고 대상 금액 ≥ `minOrder`).
- 할인액: `amount` → min(값, 대상 금액). `percent` → floor(대상 금액 × 값 / 100), `maxDiscount` 가 있으면 그 이하. `free_shipping` → 배송비.
- 상품 할인(`amount`, `percent`)은 대상 줄에 줄 금액 비율로 나눠 `order_item.coupon_discount` 에 적는다(원 미만 버림, 남는 원은 마지막 대상 줄에). 무료배송 할인은 `sub_order.ship_discount` 에 적는다.
- 판매자별 주문 합계 `total` = 상품 합계 + 배송비 − 상품 할인 − 배송 할인. 주문 전체 `total` 도 같다.
- 수수료: 줄마다 round((줄 금액 − 판매자 부담 상품 할인) × 수수료율). 플랫폼 쿠폰의 할인은 수수료 기준 금액에서 빼지 않는다(판매자가 결국 그 금액을 받기 때문).
- 청구서: `platformCouponCredit` = 청구 대상 주문(입금 확인됨, 취소·환불 아님)의 플랫폼 쿠폰 할인 합(상품 + 배송). `net` = `amount` − `platformCouponCredit`. 음수면 플랫폼이 판매자에게 그만큼 보낸다.

## DB (V5__coupons.sql)

- `coupon`: id, tenant_id, issuer, seller_id(판매자 쿠폰만), name(60), discount_type, discount_value INT, max_discount BIGINT NULL, min_order BIGINT 기본 0, category_id NULL, exclude_alcohol BOOLEAN 기본 TRUE, issue_type, code VARCHAR(40) NULL(테넌트 안에서 유일, 대문자로 저장), total_limit INT NULL(전체 발급 수 제한), issued_count INT 기본 0, valid_from DATE, valid_to DATE, valid_days INT NULL(받은 날부터 며칠. 있으면 만료 = min(받은 날 + 일수, valid_to 끝)), status(`active`|`paused`|`ended`), created_at
- `member_coupon`: id, tenant_id, coupon_id, member_id, status(`issued`|`used`), issued_at, expires_at, used_at NULL, sub_order_id NULL. (coupon_id, member_id) 유일: 한 회원은 같은 쿠폰을 한 장만 가진다.
- `sub_order` 추가: item_discount BIGINT 기본 0, ship_discount BIGINT 기본 0, member_coupon_id NULL, coupon_issuer VARCHAR(10) NULL, coupon_name VARCHAR(60) NULL
- `order_item` 추가: coupon_discount BIGINT 기본 0
- `commission_invoice` 추가: platform_coupon_credit BIGINT 기본 0
- 발급 수 제한은 `UPDATE coupon SET issued_count = issued_count + 1 WHERE id = ? AND (total_limit IS NULL OR issued_count < total_limit)` 로 지킨다. 쿠폰 사용은 `UPDATE member_coupon SET status = 'used' ... WHERE id = ? AND status = 'issued'` 로 지킨다.

## API (모두 `/api` 아래)

응답 모양. 이름을 그대로 쓴다.

```
Coupon {
  id, issuer: 'platform'|'seller', sellerId: number|null, shopName: string|null, name,
  discountType: 'amount'|'percent'|'free_shipping', discountValue, maxDiscount: number|null, minOrder,
  categoryId: number|null, categoryName: string|null, excludeAlcohol,
  issueType: 'signup'|'download'|'code'|'grant', code: string|null   // code 는 만든 사람(판매자·운영자)에게만 보인다. 구매자 응답에서는 항상 null
  totalLimit: number|null, issuedCount, validFrom, validTo, validDays: number|null,
  status: 'active'|'paused'|'ended', createdAt
}
MemberCoupon { id, coupon: Coupon, status: 'issued'|'used', issuedAt, expiresAt, usedAt, expired: boolean }
```

구매자
- `GET /coupons` → `{ coupon: Coupon, owned: boolean, soldOut: boolean }[]` 지금 받을 수 있는 `download` 쿠폰(상태 active, 기간 안). 로그인하지 않아도 볼 수 있다(owned 는 false).
- `POST /coupons/{id}/claim` → `MemberCoupon`. download 쿠폰 받기. 이미 받았으면 409, 다 떨어졌으면 409.
- `POST /coupons/redeem` `{ code }` → `MemberCoupon`. code 쿠폰 받기. 코드는 대소문자 구분 없이, 앞뒤 공백 제거. 없는 코드 404.
- `GET /my/coupons?status=usable|used|expired&page=` → `Paged<MemberCoupon>`. usable = issued 이고 만료 전.
- `GET /checkout/coupons` → `{ sellerId, shopName, options: { memberCouponId, name, issuer, discountType, discount, shipDiscount }[] }[]` 지금 장바구니 기준으로 가게별로 쓸 수 있는 내 쿠폰과 그 할인액. discount 는 상품 할인, shipDiscount 는 배송 할인(둘 중 하나만 0 이 아니다).
- `POST /orders` 본문에 `coupons: { sellerId, memberCouponId }[]` 추가(없어도 됨). 조건이 안 맞으면 400 과 이유. 같은 쿠폰을 두 가게에 쓰면 400.
- `OrderDetail.SubRow` 에 `itemDiscount`, `shipDiscount`, `couponName`, `couponIssuer` 추가. `Item` 에 `couponDiscount` 추가. `CartSummary` 는 바꾸지 않는다.
- 가입(`POST /auth/register`)하면 active 이고 기간 안인 `signup` 쿠폰을 모두 지급하고 알림을 남긴다("가입 축하 쿠폰이 도착했습니다", 링크 `/my/coupons`).

판매자 (승인된 판매자만)
- `GET /seller/coupons?page=` → `Paged<Coupon>` 내 가게 쿠폰
- `POST /seller/coupons` `{ name, discountType, discountValue, maxDiscount, minOrder, categoryId, issueType, code, totalLimit, validFrom, validTo, validDays }` → `Coupon`. issuer=seller, excludeAlcohol=true 고정. issueType 은 download 또는 code.
- `POST /seller/coupons/{id}/status` `{ status }` active|paused|ended. ended 는 되돌릴 수 없다.

운영자
- `GET /admin/coupons?issuer=&page=` → `Paged<Coupon>` 전체
- `POST /admin/coupons` 판매자와 같은 본문 + `excludeAlcohol`. issuer=platform. 모든 issueType 가능.
- `POST /admin/coupons/{id}/status` `{ status }` (판매자 쿠폰도 멈출 수 있다)
- `POST /admin/coupons/{id}/grant` `{ email }` → `MemberCoupon`. grant·signup·download 쿠폰을 회원에게 직접 지급하고 알림을 남긴다.
- 청구서 `Invoice` 에 `platformCouponCredit`, `net` 추가.

검증: name 필수(60자), discountValue amount 는 100~1,000,000, percent 는 1~90, free_shipping 은 0. minOrder ≥ 0. validFrom ≤ validTo. code 쿠폰은 code 필수(영문·숫자·`-` 4~40자), 중복이면 409. totalLimit ≥ 1 또는 null. validDays 1~365 또는 null.

상태를 바꾸는 주문 흐름
- 주문 생성 트랜잭션 안에서 쿠폰을 `used` 로 바꾼다(실패하면 주문 전체가 실패).
- `cancelByBuyer`, 입금 기한 자동 취소(`sweep`), `refund` 에서 그 판매자별 주문의 쿠폰을 `issued` 로 되돌린다(used_at, sub_order_id 비움).

## 시범 데이터 (SampleData.loadBase)

- 플랫폼, signup, "가입 축하 3,000원", amount 3000, minOrder 20000, 술 제외, validDays 30, 기간 오늘−1 ~ 오늘+365
- 플랫폼, code `SOOL-FREE`, "첫 장보기 무료배송", free_shipping, minOrder 10000, 술 제외 아님(excludeAlcohol=false), 기간 오늘−1 ~ 오늘+90
- 판매자(누룩과 쌀), download, "누룩과 쌀 10% 할인", percent 10, maxDiscount 5000, minOrder 15000, 기간 오늘−1 ~ 오늘+60, totalLimit 100
- 판매자(도담 공방), download, "도담 공방 2,000원", amount 2000, minOrder 30000, 기간 오늘−1 ~ 오늘+60
- 기존 시범 회원에게는 쿠폰을 미리 지급하지 않는다(기존 테스트의 금액이 바뀌지 않게).

## 화면

- `/coupons` 쿠폰 받기: 받을 수 있는 쿠폰 목록(받기 단추, 받음 표시, 다 떨어짐), 코드 입력 칸. 위쪽 메뉴에 "쿠폰" 추가.
- `/my/coupons` 내 쿠폰: 쓸 수 있는 쿠폰 / 쓴 쿠폰 / 기한 지난 쿠폰 탭, 쪽 나누기. 마이 메뉴 "쇼핑" 묶음에 "내 쿠폰" 추가.
- `/checkout`: 가게 묶음마다 쿠폰 고르기(`<select>`, "쓰지 않음" 포함), 고르면 그 가게 합계와 전체 합계가 바로 바뀐다. 같은 쿠폰을 두 가게에서 고를 수 없게 한다.
- 주문 상세·영수증·판매자 주문 처리: 쿠폰 할인 줄 표시("쿠폰 할인 (이름)"), 판매자 화면에는 플랫폼 쿠폰이면 "이 금액은 수수료 청구서에서 빼 드립니다" 안내.
- `/seller/coupons`: 내 가게 쿠폰 목록과 만들기, 멈춤·다시 시작·끝내기. 내 가게 메뉴 "상품" 묶음 아래에 "쿠폰" 추가.
- `/admin/coupons`: 전체 쿠폰 목록(발행자 필터)과 플랫폼 쿠폰 만들기, 상태 바꾸기, 회원에게 지급. 운영 메뉴 "주문과 정산" 묶음에 "쿠폰" 추가.
- 청구서 화면(운영·판매자): 플랫폼 쿠폰 차감과 실제 낼 금액 열 추가. 음수면 "플랫폼이 보낼 금액"으로 표시.
- 쿠폰 한 장의 모양: 골판지 가격표와 같은 계열로, 왼쪽에 할인 내용(큰 글씨), 오른쪽에 조건(최소 금액, 범위, 기한). 점선 절취선 하나. 새 색을 들이지 않는다.
