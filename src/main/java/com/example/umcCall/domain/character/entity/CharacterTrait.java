package com.example.umcCall.domain.character.entity;

import com.example.umcCall.domain.character.enums.Trait;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 캐릭터와 매력 키워드의 매핑 (우선순위 포함).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterTrait {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "character_trait_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id")
    private Character character;

    @Enumerated(EnumType.STRING)
    private Trait trait;

    private Integer priority;

    @Builder
    public CharacterTrait(Character character, Trait trait, Integer priority) {
        this.character = character;
        this.trait = trait;
        this.priority = priority;
    }
}
