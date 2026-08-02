-- 통화 요약(주제 라벨) 준비 상태 컬럼 추가 (#129)
-- DEFAULT 'NONE'이 기존 행 백필까지 겸한다 — 이 값 없이 붙이면 기존 행이 ''가 되어 enum 파싱이 터진다.
-- ⚠ 앱을 켜기 전에 돌릴 것(prod는 ddl-auto=validate라 컬럼이 없으면 기동 자체가 실패한다).
--
-- ⚠ 여기서 DEFAULT를 쓰는 건 preset_image.voice와 상황이 다르기 때문이다:
--    저기선 "프리셋 추가 시 목소리 누락"을 컬럼 제약으로 막는 게 목적이라 DEFAULT가 그 방어를 무력화했지만,
--    여기선 NONE("요약 없음")이 <b>기존 통화의 실제 상태</b>다 — 지난 통화엔 요약이 정말 없다.
ALTER TABLE calls
    ADD COLUMN summary_status VARCHAR(20) NOT NULL DEFAULT 'NONE'
    AFTER ai_summary;
