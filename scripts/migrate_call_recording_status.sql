-- 통화 녹음(다시듣기) 준비 상태 컬럼 추가 (#125)
-- DEFAULT 'NONE'이 기존 행 백필까지 겸한다 — 이 값 없이 붙이면 기존 행이 ''가 되어 enum 파싱이 터진다.
-- ⚠ 앱을 켜기 전에 돌릴 것(prod는 ddl-auto=validate라 컬럼이 없으면 기동 자체가 실패한다).
ALTER TABLE calls
    ADD COLUMN recording_status VARCHAR(20) NOT NULL DEFAULT 'NONE'
    AFTER audio_url;
