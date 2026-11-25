-- 개발용 DB(sool)를 비운다. 서버를 끈 뒤 실행하고, 다시 켜면 표와 시범 데이터가 새로 들어간다.
--   psql -h localhost -p 5433 -U sool -d sool -f db\reset-db.sql
-- 올린 사진·동영상과 로그인 세션은 server\data 폴더에 있으므로 함께 지우려면 그 폴더도 지운다.
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
