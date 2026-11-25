# 술장터 (alcohal-shop-01) 작업 규칙

술과 술에 관한 모든 것을 거래하는 멀티셀러 장터. 대금은 구매자가 판매자에게 직접 보내고(계좌·송금 QR),
플랫폼은 기록하고 월 수수료만 청구한다. 플랫폼이 판매 대금을 받거나 나눠 주는 기능은 만들지 않는다.

## 구조

- `server/` Java(Spring Boot 4.2, JDK 25 대상) API 서버. 꾸러미: `auth`, `catalog`, `order`, `seller`, `admin`, `board`, `notify`, `file`, `sample`, `common`
- `web/` Next.js 16 + React 19 + TypeScript. `/api` 요청은 Next.js 가 서버로 전달한다.
- `docs/backlog.md` 할 일, `docs/adr/` 설계 결정

## 서버 규칙

- Java 1.8 스타일을 기본으로 하고 `record`, 텍스트 블록, `switch` 식, `instanceof` 패턴, 지역 변수 `var` 만 추가로 쓴다. 미리보기 기능, sealed, Lombok 은 쓰지 않는다.
- 새 라이브러리를 추가하지 않는다(이 환경은 인터넷에서 라이브러리를 받을 수 없다). Spring Security, JPA, validation 없음.
- DB 접근은 `JdbcClient` + 이름 붙인 매개변수 + record 매핑. `List.of(...).contains(null)` 은 오류가 나므로 `Text.in()` 을 쓴다.
- DB 변경은 `server/src/main/resources/db/migration/V<n>__이름.sql` 새 파일로만 한다. 기존 파일은 고치지 않는다. H2(MySQL 모드)와 MySQL 8 둘 다에서 돌아가는 SQL 만 쓴다.
- 모든 업무 테이블에 `tenant_id`. 금액은 원 단위 정수(`long`), 수수료율은 1만분율, 시각은 UTC `LocalDateTime`(`AppClock.now()`), 날짜는 한국 시간(`AppClock.today()`).
- 상태 변경은 "현재 상태가 맞을 때만" 바뀌는 조건부 UPDATE 로 한다(중복 요청·동시 요청에 안전).
- 권한은 컨트롤러 첫 줄에서 `auth.requireUser/requireSeller/requireAdmin` 으로 확인한다. 남의 자료는 404 또는 409 로 답한다.
- 오류는 `ApiException`(400 입력 오류, 401, 403, 404, 409 지금은 할 수 없음)으로 던지고 문구는 사용자에게 그대로 보여 줄 한국어로 쓴다.
- 목록은 `Paged<T>` 로 쪽을 나눈다. 응답 record 에 비밀번호 해시 같은 내부 값을 넣지 않는다.
- 주석과 테스트 이름은 한국어. 주석은 "왜"를 쓴다.
- 기능마다 `server/src/test/java/me/nexthub/sool/` 에 API 테스트를 쓴다(`support/Api.java` 사용, `@BeforeEach` 에서 `sample.reset(); sample.loadBase();`). 새 테이블을 만들면 `SampleData.reset()` 의 삭제 목록에 넣는다.

## 화면 규칙

- 디자인은 `web/app/globals.css` 의 변수와 기존 클래스(`sheet`, `btn`, `chip`, `tabs`, `stamp`, `note`, `figures`, `pager`, `with-side`)를 쓴다. 새 색·글꼴·둥근 카드·흐린 그림자를 들이지 않는다.
- API 호출은 `lib/api.ts` 의 `api()`, 타입은 `lib/types.ts`, 금액·시각 표시는 `lib/format.ts`.
- 문구는 쉬운 한국어, 단추 이름은 누르면 일어나는 일 그대로. 빈 목록에는 다음에 할 일을 안내한다.
- 사용자가 쓴 글을 `dangerouslySetInnerHTML` 로 넣지 않는다.
- 메뉴가 필요한 화면은 `app/my`, `app/seller`, `app/admin` 의 `layout.tsx` 메뉴에 항목을 추가한다.

## 빌드와 테스트 (이 작업 환경)

- 서버: `/home/claude/cb/build.sh test` (오프라인 빌드. 임시 복사본 `/tmp/sb` 에서 JDK 21 로 돌린다). 특정 테스트만: `/home/claude/cb/build.sh test --tests '*CouponTest*'`
- 화면: `cd web && npx next build`
- `git commit`, `git push` 는 하지 않는다. 검토자가 확인한 뒤 올린다.
