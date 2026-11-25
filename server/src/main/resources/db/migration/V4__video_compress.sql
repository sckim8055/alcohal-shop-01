-- 올린 동영상을 서버에서 줄인 결과를 남긴다.
-- compress_status: none 대상 아님(사진 등), pending 줄이는 중, done 줄임, skipped 줄일 필요 없음, failed 실패(원본 유지)
ALTER TABLE upload_file ADD COLUMN compress_status VARCHAR(10) NOT NULL DEFAULT 'none';
ALTER TABLE upload_file ADD COLUMN original_size BIGINT;
