package com.example.umcCall.domain.character.entity;

import com.example.umcCall.domain.character.service.CharacterAiProfileScores;
import com.example.umcCall.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "character_ai_profile")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterAiProfile extends BaseTimeEntity {

    @Id
    @Column(name = "character_id")
    private Long characterId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id")
    private Character character;

    private String lifeType;
    private Double humor;
    private Double playfulness;
    private Double affection;
    private Double empathy;
    private Double attachment;
    private Double jealousy;
    private Double dominance;
    private Double confidence;
    private Double expressiveness;
    private Double emotionalStability;
    private Integer calculationVersion;

    private CharacterAiProfile(Character character) {
        this.character = character;
    }

    public static CharacterAiProfile create(Character character) {
        return new CharacterAiProfile(character);
    }

    public void update(CharacterAiProfileScores scores, int calculationVersion) {
        this.lifeType = scores.lifeType();
        this.humor = scores.humor();
        this.playfulness = scores.playfulness();
        this.affection = scores.affection();
        this.empathy = scores.empathy();
        this.attachment = scores.attachment();
        this.jealousy = scores.jealousy();
        this.dominance = scores.dominance();
        this.confidence = scores.confidence();
        this.expressiveness = scores.expressiveness();
        this.emotionalStability = scores.emotionalStability();
        this.calculationVersion = calculationVersion;
    }
}
