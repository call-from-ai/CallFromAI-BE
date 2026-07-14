CREATE TABLE character_ai_profile (
    character_id BIGINT NOT NULL,
    mind VARCHAR(100) NULL,
    response_style VARCHAR(100) NULL,
    life_type VARCHAR(100) NULL,
    romance_style_score INT NOT NULL DEFAULT 0,
    humor INT NOT NULL DEFAULT 0,
    playfulness INT NOT NULL DEFAULT 0,
    affection INT NOT NULL DEFAULT 0,
    empathy INT NOT NULL DEFAULT 0,
    attachment INT NOT NULL DEFAULT 0,
    jealousy INT NOT NULL DEFAULT 0,
    dominance INT NOT NULL DEFAULT 0,
    confidence INT NOT NULL DEFAULT 0,
    expressiveness INT NOT NULL DEFAULT 0,
    emotional_stability INT NOT NULL DEFAULT 0,
    calculation_version INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (character_id),
    CONSTRAINT fk_character_ai_profile_character
        FOREIGN KEY (character_id) REFERENCES `character` (character_id)
        ON DELETE CASCADE
);
