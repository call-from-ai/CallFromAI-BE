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

    private String mind;
    private String responseStyle;
    private String lifeType;
    private Integer romanceStyleScore;
    private Integer humor;
    private Integer playfulness;
    private Integer affection;
    private Integer empathy;
    private Integer attachment;
    private Integer jealousy;
    private Integer dominance;
    private Integer confidence;
    private Integer expressiveness;
    private Integer emotionalStability;
    private Integer calculationVersion;

    private CharacterAiProfile(Character character) {
        this.character = character;
    }

    public static CharacterAiProfile create(Character character) {
        return new CharacterAiProfile(character);
    }

    public void update(CharacterAiProfileScores scores, int calculationVersion) {
        this.mind = scores.mind();
        this.responseStyle = scores.responseStyle();
        this.lifeType = scores.lifeType();
        this.romanceStyleScore = scores.romanceStyleScore();
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
