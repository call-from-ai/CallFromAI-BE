-- 프리셋 이미지 시드 (로컬 전용) — female_1~7 / male_1~7
--
-- ⚠ prod에는 돌리지 말 것. prod preset_image에는 이미 행이 있고, 컬럼 추가는
--    scripts/migrate_preset_image_voice.sql이 담당한다. 이 파일은 <b>비어 있는 로컬 DB</b>를
--    prod와 같은 상태로 만들어 캐릭터별 목소리를 실제로 확인하기 위한 것이다.
--
-- ⚠ 프리셋이 없으면 CallVoiceResolver가 매번 폴백으로 떨어져(로그: "프리셋 매칭 실패")
--    모든 캐릭터가 기본 목소리로만 말한다 — 매핑을 로컬에서 검증할 수 없다.
--
-- voice 값은 마이그레이션 2단계와 동일하게 맞췄다. 한쪽만 바꾸면 로컬과 prod의 목소리가 갈린다.

-- 컬럼이 아직 없으면 먼저 만든다(비어 있는 테이블이라 NOT NULL을 바로 붙여도 안전하다).
-- 이미 ddl-auto=update가 만들었거나 마이그레이션을 돌렸다면 이 줄은 건너뛸 것.
-- ALTER TABLE preset_image ADD COLUMN voice VARCHAR(20) NOT NULL AFTER gender;

INSERT INTO preset_image (image_url, gender, voice) VALUES
  ('https://callfromai-images.s3.ap-northeast-2.amazonaws.com/female_1.png', 'FEMALE', 'HEERA'),
  ('https://callfromai-images.s3.ap-northeast-2.amazonaws.com/female_2.png', 'FEMALE', 'ARA_PRO'),
  ('https://callfromai-images.s3.ap-northeast-2.amazonaws.com/female_3.png', 'FEMALE', 'MINYOUNG'),
  ('https://callfromai-images.s3.ap-northeast-2.amazonaws.com/female_4.png', 'FEMALE', 'YUNA_PRO'),
  ('https://callfromai-images.s3.ap-northeast-2.amazonaws.com/female_5.png', 'FEMALE', 'GOEUN_PRO'),
  ('https://callfromai-images.s3.ap-northeast-2.amazonaws.com/female_6.png', 'FEMALE', 'SHASHA'),
  ('https://callfromai-images.s3.ap-northeast-2.amazonaws.com/female_7.png', 'FEMALE', 'YOUNGMI'),
  ('https://callfromai-images.s3.ap-northeast-2.amazonaws.com/male_1.png',   'MALE',   'MINSANG'),
  ('https://callfromai-images.s3.ap-northeast-2.amazonaws.com/male_2.png',   'MALE',   'DONGHYUN_PRO'),
  ('https://callfromai-images.s3.ap-northeast-2.amazonaws.com/male_3.png',   'MALE',   'SANGDO'),
  ('https://callfromai-images.s3.ap-northeast-2.amazonaws.com/male_4.png',   'MALE',   'DAESEONG_PRO'),
  ('https://callfromai-images.s3.ap-northeast-2.amazonaws.com/male_5.png',   'MALE',   'RAEWON'),
  ('https://callfromai-images.s3.ap-northeast-2.amazonaws.com/male_6.png',   'MALE',   'KITAE'),
  ('https://callfromai-images.s3.ap-northeast-2.amazonaws.com/male_7.png',   'MALE',   'KYUWON');

-- 확인 (14행):
--   SELECT preset_image_id, gender, voice, image_url FROM preset_image ORDER BY gender, preset_image_id;
