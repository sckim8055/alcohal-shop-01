# 술장터 (alcohal-shop-01)

술과 술에 관한 모든 것(전통주, 원재료, 양조 기구, 술잔)을 거래하는 멀티셀러 몰.

## 구성

| 폴더 | 내용 |
| --- | --- |
| `server/` | API 서버: JDK 25, Spring Boot 4, Gradle 9, H2(로컬) / MySQL(운영), Flyway |
| `web/` | 화면: React 19 + Next.js 16 (TypeScript). `/api` 요청은 Next.js가 서버로 전달한다 |

서버 코드는 `auth`(회원·권한), `catalog`(상품), `order`(장바구니·주문·배송), `seller`, `admin`(운영·청구서), `board`, `sample`(시범 데이터), `common` 꾸러미로 나뉜다.

## 로컬 실행 (Windows, 휴대용)

도구는 설치하지 않고 이 폴더의 상위 폴더(`F:\works`)에 압축을 풀어 둔 것을 쓴다.

| 도구 | 폴더 |
| --- | --- |
| JDK (Eclipse Temurin 25) | `..\jdk-25.0.5.1+2` |
| Gradle | `..\gradle-9.9.2` |
| Node.js | `..\node-v26.13.1-win-x64` |
| Gradle 라이브러리 캐시 | `..\_gradle-cache` (자동 생성) |
| npm 캐시 | `..\_npm-cache` (자동 생성) |
| ffmpeg (선택) | `..\ffmpeg` (`ffmpeg\bin\ffmpeg.exe` 가 있어야 함). 있으면 올린 동영상을 자동으로 줄인다 |

도구 버전을 바꾸면 `env.bat`의 폴더 이름만 고치면 된다.

| 파일 | 하는 일 |
| --- | --- |
| `check.bat` | 도구 버전 확인 |
| `test.bat` | 자동 테스트 실행 (첫 실행 때 라이브러리를 내려받음) |
| `run.bat` | API 서버 실행 → http://localhost:8080/api/health |
| `web.bat` | 화면 실행 → http://localhost:3000 (첫 실행 때 패키지를 내려받음) |

사이트를 보려면 `run.bat`과 `web.bat`을 둘 다 켜 두고 http://localhost:3000 을 연다.

로컬 DB 파일은 `server/data/`에 생기며, 지우면 초기 상태로 돌아간다.

## 시범 계정

DB가 비어 있으면 서버를 처음 켤 때 시범 데이터(판매자 5곳, 상품 27개, 주문 7건, 청구서, 게시글 5개)가 들어간다.
가게·상품·사람 이름, 사업자번호, 계좌는 모두 지어낸 것이다. 비밀번호는 전부 `test1234`.

| 계정 | 역할 |
| --- | --- |
| `admin@test.local` | 운영자 |
| `brewery@test.local` | 판매자: 한울 양조장 (주류 면허 확인됨) |
| `orchard@test.local` | 판매자: 달빛 과수원 양조 (주류 면허 확인됨) |
| `cups@test.local` | 판매자: 도담 공방 |
| `supplies@test.local` | 판매자: 누룩과 쌀 (수업 포함) |
| `newseller@test.local` | 판매자: 새내기 가게 (입점 심사 중) |
| `buyer@test.local` | 구매자 (성인인증 완료, 주문 3건) |
| `buyer2@test.local` | 구매자 (성인인증 전) |
| `buyer3@test.local` | 구매자 (성인인증 완료, 주문 4건) |

처음 상태로 되돌리려면 서버를 끄고 `server/data/` 폴더를 지운다(DB, 올린 사진·동영상, 로그인 세션이 모두 여기 있다).

운영 화면은 `admin@test.local` 로 로그인한 뒤 위쪽 메뉴의 **운영**(`/admin`)이다.

## API

모든 주소는 `/api` 로 시작한다. 화면은 이 API만 쓴다.

- 화면을 열 때 `GET /api/me` 로 로그인 상태와 `csrfToken` 을 받는다.
- 값을 바꾸는 요청(POST, PUT)은 `X-CSRF-Token` 머리말에 그 토큰을 넣어야 한다. 로그인하면 토큰이 바뀐다.
- 오류는 `{"message": "안내 문구"}` 형식이며 상태 코드는 400(입력 오류), 401(로그인 필요), 403(권한 없음), 404(없음), 409(지금은 할 수 없음)이다.
- 시각은 UTC이고 시간대 표시가 없다(예: `2026-11-25T02:59:56`). 금액은 원 단위 정수, 수수료율은 1만분율(`commissionRateBp` 100 = 1%)이다.

| 구분 | 주소 |
| --- | --- |
| 회원 | `GET /me`, `POST /auth/register`, `/auth/login`, `/auth/logout`, `/auth/verify-adult` |
| 상품 | `GET /categories`, `/products?cat=&q=&sort=&page=`, `/products/{id}`, `/carriers` |
| 내 정보 | `PUT /me`, `POST /me/password`, `GET /notifications`, `POST /notifications/read-all`, `/notifications/{id}/read` |
| 파일 | `POST /files`(사진·동영상 올리기), `GET /files/{이름}` |
| 쿠폰 | `GET /coupons`, `POST /coupons/{id}/claim`, `/coupons/redeem`, `GET /my/coupons`, `/checkout/coupons`, `GET·POST /seller/coupons`, `GET·POST /admin/coupons`, `POST /admin/coupons/{id}/grant` (규격: `docs/adr/0001-coupons.md`) |
| 장바구니 | `GET /cart`, `POST /cart/items`, `PUT /cart/items/{productId}` |
| 주문 | `POST /orders`, `GET /orders`, `/orders/{id}`, `/orders/{id}/receipt/{subId}` |
| 구매자 처리 | `POST /suborders/{id}/report`, `/cancel`, `/receive` |
| 판매자 | `POST /seller/apply`, `GET /seller`, `PUT /seller/settings`, `GET·POST /seller/products`, `PUT /seller/products/{id}`, `POST /seller/products/{id}/status`, `GET /seller/orders?status=&page=` |
| 판매자 처리 | `POST /seller/suborders/{id}/confirm`, `/ship`, `/deliver`, `/refund` |
| 운영자 | `GET /admin`, `POST /admin/sellers/{id}/status`, `/license`, `PUT /admin/commission/default`, `/admin/categories/{id}/commission`, `/admin/sellers/{id}/commission`, `POST /admin/invoices/generate`, `/admin/invoices/{id}/paid` |
| 운영자 관리 | `GET /admin/summary`, `/admin/members`, `POST /admin/members/{id}/admin`, `/status`, `GET /admin/products`, `POST /admin/products/{id}/block`, `GET·POST /admin/categories`, `PUT /admin/categories/{id}`, `GET /admin/orders` |
| 게시판 | `GET /posts`, `/posts/{id}`, `POST /posts` |

목록은 `{items, page, size, total}` 모양으로 쪽을 나눠 돌려준다.

업무 규칙은 `server/src/test/java/me/nexthub/sool/` 의 `FlowTest`(주문·입금·배송·수수료)와 `ManageTest`(회원·세션·알림·분류·상품) 테스트 이름을 읽으면 한눈에 볼 수 있다.

앞으로 할 일과 빠진 것은 `docs/backlog.md` 에 적어 둔다.

## 화면 디자인

막걸리 장터에서 가져온 모양이다: 초록·흰 줄무늬 천막, 노란 양은 주전자 색 간판, 골판지 가격표와 빨간 매직 글씨, 도장 모양 상태 표시.
색과 글꼴은 `web/app/globals.css` 맨 위의 변수로 모아 두었다. 글꼴(도현, 고운돋움, 개구)은 패키지에 들어 있어 인터넷 없이도 나온다.
사진을 올리지 않은 상품은 분류별 그림(`web/components/Art.tsx`)을 대신 보여 준다.
상품 설명은 편집기(Tiptap)로 쓰고 HTML 이 아닌 문서(JSON)로 저장한다. 화면은 허용한 요소만 골라 그리므로 스크립트가 섞여 들어와도 실행되지 않는다.

## 아직 시범용인 부분

- 알림: 사이트 안 알림은 동작한다. 메일은 보낼 내용만 쌓이고 실제로 나가지 않는다. 문자·알림톡은 없다.
- 올린 사진·동영상은 서버 디스크(`server/data/uploads`)에 둔다. 사진은 5MB, 동영상은 100MB 까지다.
- 동영상은 ffmpeg 이 있으면 올린 뒤 뒤에서 자동으로 줄인다(MP4 만, 가로 최대 1280, 한 번에 하나씩). ffmpeg 이 없으면 원본 그대로 둔다. 사진은 줄이지 않고 원본을 보여 준다.

- 성인인증: 입력한 생년월일만 본다. 법적 성인인증이 아니므로 실제 판매 전에 본인확인 서비스로 바꿔야 한다.
- 배송 조회: 발송 후 약 2분이 지나면 배송 완료가 되는 가짜 데이터다.
- 로그인: 세션은 서버를 정상 종료하면 디스크에 남아 재시작 뒤에도 유지된다. 서버를 여러 대로 늘리려면 세션 저장소가 필요하다.
- 비밀번호를 5번 틀리면 10분 잠긴다. 비밀번호 찾기와 이메일 확인은 없다.
- MySQL에서는 아직 돌려 보지 않았다(H2의 MySQL 호환 모드로만 확인).

## 코딩 기준

- Java 1.8 스타일을 기본으로 하고 `record`, 텍스트 블록, `switch` 식, `instanceof` 패턴, 지역 변수 `var`만 추가로 쓴다.
- 미리보기(preview) 기능은 쓰지 않는다.
- 모든 업무 테이블에 `tenant_id`를 둔다.
- 금액은 원 단위 정수, 수수료율은 1만분율(100 = 1%)로 저장한다.
- DB 변경은 Flyway 마이그레이션 파일로만 한다.
