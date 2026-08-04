-- 프리셋 이미지별 통화 음성 매핑 (#127)
-- 캐릭터가 고른 프리셋 이미지가 곧 목소리를 정한다. 값은 TTSVoice enum 이름(예: 'MINYOUNG').
--
-- ⚠ 앱을 켜기 전에 끝까지 돌릴 것(prod는 ddl-auto=validate라 컬럼이 없으면 기동 자체가 실패한다).
-- ⚠ 3단계로 나눈 이유: preset_image에 이미 행이 있어서, NOT NULL을 한 번에 붙이면 MySQL이 암묵 기본값
--    ''로 채우고 나중에 TTSVoice 파싱이 터진다. 반드시 NULL로 추가 → 채우기 → NOT NULL 순서.
-- ⚠ DB DEFAULT를 걸지 않는다 — 기본값이 있으면 앞으로 프리셋을 추가할 때 목소리를 안 넣어도 통과해,
--    nullable=false로 막으려던 누락이 그대로 돌아온다. 게다가 기본값은 성별이 하나라
--    남성 프리셋을 깜빡하면 남캐가 여성 목소리로 말하게 된다.
--
-- 대상: female_1~11 / male_1~11 (성별당 11개, 총 22행). 화자도 성별당 11종이라 <b>1:1로 정확히
--       맞아떨어진다</b> — 남는 화자가 없다. 그래서 배정에 중복이 하나라도 있으면 반드시 안 쓰이는
--       화자가 생긴다(에러는 아니지만 의도한 게 아니라면 실수다 — 3단계 전 검증 쿼리 ⓑ가 잡는다).

-- ── 1단계: 컬럼 추가 (배정 전까지 NULL 허용) ────────────────────────────────────
ALTER TABLE preset_image
    ADD COLUMN voice VARCHAR(20) NULL
    AFTER gender;

-- ── 2단계: 이미지별 목소리 배정 ────────────────────────────────────────────────
-- ⚠ 매칭 키를 image_url로 잡았다 — id 순서(ROW_NUMBER)는 삽입 순서에 기대므로, 나중에 프리셋을
--    지웠다 다시 넣으면 배정이 통째로 밀린다. URL은 그 가정이 필요 없고 눈으로 검증된다.
-- ⚠ 배정 자체는 임의다(듣고 정한 게 아니다). 4단계에서 한 줄씩 바꾸면 된다.
-- ⚠ ELSE voice가 없으면 매칭 안 된 행이 NULL로 덮인다 — 지우지 말 것.
UPDATE preset_image
SET voice = CASE image_url
    -- 여성 11
    WHEN 'https://callfromai-images.s3.ap-northeast-2.amazonaws.com/female_1.png'  THEN 'HEERA'        -- 희라
    WHEN 'https://callfromai-images.s3.ap-northeast-2.amazonaws.com/female_2.png'  THEN 'ARA_PRO'      -- 아라(Pro)
    WHEN 'https://callfromai-images.s3.ap-northeast-2.amazonaws.com/female_3.png'  THEN 'MINYOUNG'     -- 민영
    WHEN 'https://callfromai-images.s3.ap-northeast-2.amazonaws.com/female_4.png'  THEN 'YUNA_PRO'     -- 유나(Pro)
    WHEN 'https://callfromai-images.s3.ap-northeast-2.amazonaws.com/female_5.png'  THEN 'GOEUN_PRO'    -- 고은(Pro)
    WHEN 'https://callfromai-images.s3.ap-northeast-2.amazonaws.com/female_6.png'  THEN 'SHASHA'       -- 샤샤
    WHEN 'https://callfromai-images.s3.ap-northeast-2.amazonaws.com/female_7.png'  THEN 'YOUNGMI'      -- 영미
    WHEN 'https://callfromai-images.s3.ap-northeast-2.amazonaws.com/female_8.png'  THEN 'SOHYUN'       -- 소현
    WHEN 'https://callfromai-images.s3.ap-northeast-2.amazonaws.com/female_9.png'  THEN 'SUJIN'        -- 수진
    WHEN 'https://callfromai-images.s3.ap-northeast-2.amazonaws.com/female_10.png' THEN 'YEJI'         -- 예지
    WHEN 'https://callfromai-images.s3.ap-northeast-2.amazonaws.com/female_11.png' THEN 'EUNSEO'       -- 은서
    -- 남성 11
    WHEN 'https://callfromai-images.s3.ap-northeast-2.amazonaws.com/male_1.png'    THEN 'MINSANG'      -- 민상
    WHEN 'https://callfromai-images.s3.ap-northeast-2.amazonaws.com/male_2.png'    THEN 'DONGHYUN_PRO' -- 동현(Pro)
    WHEN 'https://callfromai-images.s3.ap-northeast-2.amazonaws.com/male_3.png'    THEN 'SANGDO'       -- 상도
    WHEN 'https://callfromai-images.s3.ap-northeast-2.amazonaws.com/male_4.png'    THEN 'DAESEONG_PRO' -- 대성(Pro)
    WHEN 'https://callfromai-images.s3.ap-northeast-2.amazonaws.com/male_5.png'    THEN 'RAEWON'       -- 래원
    WHEN 'https://callfromai-images.s3.ap-northeast-2.amazonaws.com/male_6.png'    THEN 'KITAE'        -- 기태
    WHEN 'https://callfromai-images.s3.ap-northeast-2.amazonaws.com/male_7.png'    THEN 'KYUWON'       -- 규원
    WHEN 'https://callfromai-images.s3.ap-northeast-2.amazonaws.com/male_8.png'    THEN 'SEONGHOON'    -- 성훈
    WHEN 'https://callfromai-images.s3.ap-northeast-2.amazonaws.com/male_9.png'    THEN 'SIYOON'       -- 시윤
    WHEN 'https://callfromai-images.s3.ap-northeast-2.amazonaws.com/male_10.png'   THEN 'SINU'         -- 신우 (= MALE 폴백 기본값)
    WHEN 'https://callfromai-images.s3.ap-northeast-2.amazonaws.com/male_11.png'   THEN 'JIHUN'        -- 지훈
    ELSE voice
END;

-- ⚠ 3단계 전 반드시 확인 — ⓐ는 0건, ⓑ는 22/22여야 한다.
--    ⓐ 1건이라도 남으면 URL이 위와 다른 프리셋이 있다는 뜻이다(경로 오타·확장자 차이·신규 추가).
--       그대로 3단계를 돌리면 ALTER가 실패하거나 ''로 채워진다.
--    ⓑ 1:1 매핑이 실제로 지켜졌는지 본다. voices_used가 rows_total보다 작으면 어딘가 중복 배정이라
--       안 쓰이는 화자가 있다(위 CASE를 손댔다면 여기서 걸린다).
--   ⓐ SELECT preset_image_id, gender, image_url FROM preset_image WHERE voice IS NULL;
--   ⓑ SELECT COUNT(*) AS rows_total, COUNT(DISTINCT voice) AS voices_used FROM preset_image;

-- ── 3단계: NOT NULL로 조인다 (2단계 확인 후에만) ───────────────────────────────
ALTER TABLE preset_image
    MODIFY COLUMN voice VARCHAR(20) NOT NULL;

-- ── 4단계(선택): 듣고 다시 배정 ────────────────────────────────────────────────
-- 2단계 배정은 임의라, 이미지와 목소리를 맞추려면 여기서 바꾼다. 앱 재시작 없이 반영된다
-- (통화 시작 시 조회하므로 이미 진행 중인 통화만 이전 목소리를 유지한다).
--
--   SELECT preset_image_id, gender, voice, image_url FROM preset_image ORDER BY gender, preset_image_id;
--   UPDATE preset_image SET voice = 'SUJIN' WHERE image_url LIKE '%female_3.png';
--
-- ⚠ 화자와 프리셋이 1:1이라 <b>둘을 맞바꾸는 식(swap)으로 고치는 게 안전하다</b> — 한쪽만 덮어쓰면
--    그 화자가 두 프리셋에 붙고 원래 쓰이던 화자 하나가 미아가 된다(위 ⓑ로 확인할 것).
--
-- 쓸 수 있는 화자 (TTSVoice enum 이름). ⚠ 성별을 지킬 것 — 코드가 막아주지 않는다.
-- 여성: HEERA(희라) ARA_PRO(아라Pro) MINYOUNG(민영) YUNA_PRO(유나Pro) GOEUN_PRO(고은Pro)
--       SHASHA(샤샤) YOUNGMI(영미) SOHYUN(소현) SUJIN(수진) YEJI(예지) EUNSEO(은서)
-- 남성: MINSANG(민상) DONGHYUN_PRO(동현Pro) SANGDO(상도) DAESEONG_PRO(대성Pro) RAEWON(래원)
--       KITAE(기태) KYUWON(규원) SEONGHOON(성훈) SIYOON(시윤) SINU(신우) JIHUN(지훈)
