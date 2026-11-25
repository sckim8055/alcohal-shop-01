# 술장터 (alcohal-shop-01)

술과 술에 관한 모든 것(전통주, 원재료, 양조 기구, 술잔)을 거래하는 멀티셀러 몰.

## 구성

| 폴더 | 내용 |
| --- | --- |
| `server/` | API 서버: JDK 25, Spring Boot 4, Gradle 9, H2(로컬) / MySQL(운영), Flyway |
| `web/` | 화면: React(Next.js), 아직 없음 |

## 로컬 실행 (Windows, 휴대용)

도구는 설치하지 않고 이 폴더의 상위 폴더(`F:\works`)에 압축을 풀어 둔 것을 쓴다.

| 도구 | 폴더 |
| --- | --- |
| JDK (Eclipse Temurin 25) | `..\jdk-25.0.5.1+2` |
| Gradle | `..\gradle-9.9.2` |
| Node.js | `..\node-v26.13.1-win-x64` |
| Gradle 라이브러리 캐시 | `..\_gradle-cache` (자동 생성) |

도구 버전을 바꾸면 `env.bat`의 폴더 이름만 고치면 된다.

| 파일 | 하는 일 |
| --- | --- |
| `check.bat` | 도구 버전 확인 |
| `test.bat` | 자동 테스트 실행 (첫 실행 때 라이브러리를 내려받음) |
| `run.bat` | 서버 실행 → http://localhost:8080/api/health |

로컬 DB 파일은 `server/data/`에 생기며, 지우면 초기 상태로 돌아간다.

## 코딩 기준

- Java 1.8 스타일을 기본으로 하고 `record`, 텍스트 블록, `switch` 식, `instanceof` 패턴, 지역 변수 `var`만 추가로 쓴다.
- 미리보기(preview) 기능은 쓰지 않는다.
- 모든 업무 테이블에 `tenant_id`를 둔다.
- 금액은 원 단위 정수, 수수료율은 1만분율(100 = 1%)로 저장한다.
- DB 변경은 Flyway 마이그레이션 파일로만 한다.
