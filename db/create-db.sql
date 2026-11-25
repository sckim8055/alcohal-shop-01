-- 술장터용 DB 만들기 (PostgreSQL 16). 한 번만 실행한다.
-- 다른 프로젝트와 같은 PostgreSQL 서버를 쓰더라도 DB 와 계정을 따로 두어 서로의 표를 건드리지 못하게 한다.
--
-- 실행(관리자 계정으로):
--   psql -h localhost -p 5433 -U postgres -f db\create-db.sql
-- 마지막 줄에서 sool 계정의 암호를 두 번 묻는다. 여기서 정한 암호를 db.local.bat 에 적는다.
--
-- pgAdmin, DBeaver 같은 도구에서 실행한다면 마지막 줄(\password) 대신
--   ALTER ROLE sool PASSWORD '직접 정한 암호';
-- 를 실행한다.

CREATE ROLE sool LOGIN;

-- sool: 개발하면서 쓰는 DB.  sool_test: 자동 테스트가 자료를 지우고 다시 넣는 DB.
CREATE DATABASE sool      OWNER sool TEMPLATE template0 ENCODING 'UTF8';
CREATE DATABASE sool_test OWNER sool TEMPLATE template0 ENCODING 'UTF8';

\password sool
