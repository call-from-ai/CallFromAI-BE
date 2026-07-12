package com.example.umcCall.domain.character.service;

import com.example.umcCall.domain.character.dto.request.CharacterCreateRequest;
import com.example.umcCall.domain.character.dto.response.CharacterResponse;
import com.example.umcCall.domain.character.dto.response.CharacterSummaryResponse;
import com.example.umcCall.domain.character.dto.response.PresetImageResponse;
import com.example.umcCall.domain.character.dto.response.TraitOptionResponse;
import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.character.entity.CharacterImage;
import com.example.umcCall.domain.character.entity.CharacterTrait;
import com.example.umcCall.domain.character.enums.Gender;
import com.example.umcCall.domain.character.entity.PresetImages;
import com.example.umcCall.domain.character.enums.Trait;
import com.example.umcCall.domain.character.exception.CharacterErrorCode;
import com.example.umcCall.domain.character.repository.CharacterImageRepository;
import com.example.umcCall.domain.character.repository.CharacterRepository;
import com.example.umcCall.domain.character.repository.CharacterTraitRepository;
import com.example.umcCall.domain.chat.entity.ChatRoom;
import com.example.umcCall.domain.chat.enums.RoomType;
import com.example.umcCall.domain.chat.repository.ChatRoomRepository;
import com.example.umcCall.domain.chat.service.ChatRoomService;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.entity.RelationshipStatus;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import com.example.umcCall.domain.relationship.repository.RelationshipStatusRepository;
import com.example.umcCall.global.exception.BaseException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캐릭터 생성/조회/활성화/삭제를 담당하는 서비스.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CharacterService {

    private static final int MAX_CHARACTER_COUNT = 5;
    private static final int MIN_ACTIVATE_INTERVAL_DAYS = 3;
    private static final int MIN_DELETE_INTERVAL_HOURS = 24;

    private final CharacterRepository characterRepository;
    private final CharacterTraitRepository characterTraitRepository;
    private final CharacterImageRepository characterImageRepository;
    private final RelationshipRepository relationshipRepository;
    private final RelationshipStatusRepository relationshipStatusRepository;
    private final ChatRoomService chatRoomService;
    private final ChatRoomRepository chatRoomRepository;

    // 매력 키워드 목록 조회
    public List<TraitOptionResponse> getTraitOptions() {
        return Arrays.stream(Trait.values())
                .map(TraitOptionResponse::from)
                .toList();
    }

    // 프리셋 이미지 목록 조회
    public List<PresetImageResponse> getPresetImages(Gender gender) {
        if (gender == null) {
            throw new BaseException(CharacterErrorCode.GENDER_REQUIRED);
        }
        return PresetImages.of(gender).stream()
                .map(url -> PresetImageResponse.builder().imageUrl(url).build())
                .toList();
    }

    // 캐릭터 생성 (관계, 관계 통계, 채팅방 함께 생성) — 응답 바디 없음
    @Transactional
    public void createCharacter(Long memberId, CharacterCreateRequest request) {
        // TODO: Member 엔티티에 characterCreatedAt 필드 생기면 24시간 재생성 제한 검증 추가
        // member.getCharacterCreatedAt() 기준으로 24시간 안 지났으면 CharacterErrorCode.CHARACTER_RECREATE_TOO_SOON 던지기

        if (relationshipRepository.countByMemberId(memberId) >= MAX_CHARACTER_COUNT) {
            throw new BaseException(CharacterErrorCode.CHARACTER_LIMIT_EXCEEDED);
        }

        // imageUrl이 있으면 해당 성별의 프리셋 목록에 있는 값인지 검증
        if (request.getImageUrl() != null
                && !PresetImages.contains(request.getGender(), request.getImageUrl())) {
            throw new BaseException(CharacterErrorCode.INVALID_PRESET_IMAGE);
        }

        Character character = characterRepository.save(
                Character.builder()
                        .name(request.getName())
                        .gender(request.getGender())
                        .age(request.getAge())
                        .job(request.getJob())
                        .preferTime(request.getPreferTime())
                        .mbti(request.getMbti())
                        .build()
        );

        if (request.getImageUrl() != null) {
            characterImageRepository.save(
                    CharacterImage.builder()
                            .character(character)
                            .imageUrl(request.getImageUrl())
                            .build()
            );
        }

        request.getTraits().forEach(traitRequest ->
                characterTraitRepository.save(
                        CharacterTrait.builder()
                                .character(character)
                                .trait(traitRequest.getTrait())
                                .priority(traitRequest.getPriority())
                                .build()
                )
        );

        relationshipRepository.findByMemberIdAndMainTrue(memberId)
                .ifPresent(Relationship::deactivate);

        Relationship relationship = relationshipRepository.save(
                Relationship.builder()
                        .memberId(memberId)
                        .character(character)
                        .relationshipStage(request.getRelationshipStage())
                        .spiceLevel(request.getSpiceLevel())
                        .speechStyle(request.getSpeechStyle())
                        .build()
        );

        relationshipStatusRepository.save(
                RelationshipStatus.builder()
                        .relationship(relationship)
                        .build()
        );

        // 캐릭터 생성 시 채팅방도 함께 생성 (ChatRoomService 로직 재사용)
        chatRoomService.createRoom(memberId, relationship.getId(), RoomType.CHARACTER);
    }

    // 현재 메인 캐릭터 조회
    public CharacterResponse getActiveCharacter(Long memberId) {
        Relationship relationship = relationshipRepository.findByMemberIdAndMainTrue(memberId)
                .orElseThrow(() -> new BaseException(CharacterErrorCode.NO_ACTIVE_CHARACTER));
        Character character = relationship.getCharacter();
        List<CharacterTrait> characterTraits = characterTraitRepository.findByCharacterId(character.getId());
        String imageUrl = getImageUrl(character.getId());
        return CharacterResponse.of(character, relationship, characterTraits, imageUrl);
    }

    // 내 캐릭터 목록 조회 (최대 5개라 페이지네이션 없음)
    public List<CharacterSummaryResponse> getMyCharacters(Long memberId) {
        return relationshipRepository.findByMemberId(memberId).stream()
                .map(relationship -> {
                    Character character = relationship.getCharacter();
                    String imageUrl = getImageUrl(character.getId());
                    LocalDateTime lastMessageAt = chatRoomRepository.findByRelationshipId(relationship.getId())
                            .map(ChatRoom::getLastMessageAt)
                            .orElse(null);
                    return CharacterSummaryResponse.builder()
                            .characterId(character.getId())
                            .name(character.getName())
                            .imageUrl(imageUrl)
                            .main(relationship.isMain())
                            .createdAt(character.getCreatedAt())
                            .startedAt(relationship.getStartedAt())
                            .lastMessageAt(lastMessageAt)
                            .build();
                })
                .toList();
    }

    // 활성 캐릭터 변경
    @Transactional
    public void activateCharacter(Long memberId, Long characterId) {
        Relationship target = getOwnedRelationship(memberId, characterId);

        relationshipRepository.findByMemberIdAndMainTrue(memberId)
                .ifPresent(current -> {
                    LocalDateTime becameMainAt = current.getBecameMainAt();
                    if (becameMainAt != null
                            && becameMainAt.plusDays(MIN_ACTIVATE_INTERVAL_DAYS).isAfter(LocalDateTime.now())) {
                        throw new BaseException(CharacterErrorCode.ACTIVE_CHARACTER_CHANGE_TOO_SOON);
                    }
                    current.deactivate();
                });

        target.activate();
    }

    // 캐릭터 삭제 (하드 딜리트)
    @Transactional
    public void deleteCharacter(Long memberId, Long characterId) {
        Relationship relationship = getOwnedRelationship(memberId, characterId);

        if (relationship.isMain()) {
            throw new BaseException(CharacterErrorCode.CANNOT_DELETE_ACTIVE_CHARACTER);
        }

        Character character = relationship.getCharacter();
        if (character.getCreatedAt().plusHours(MIN_DELETE_INTERVAL_HOURS).isAfter(LocalDateTime.now())) {
            throw new BaseException(CharacterErrorCode.CHARACTER_DELETE_TOO_SOON);
        }

        characterImageRepository.deleteByCharacterId(characterId);
        characterTraitRepository.deleteByCharacterId(characterId);
        relationshipStatusRepository.deleteByRelationshipId(relationship.getId());
        relationshipRepository.delete(relationship);
        characterRepository.deleteById(characterId);
    }

    // 캐릭터의 프리셋 이미지 URL 조회 (없으면 null)
    private String getImageUrl(Long characterId) {
        return characterImageRepository.findByCharacterId(characterId)
                .map(CharacterImage::getImageUrl)
                .orElse(null);
    }

    // 본인 소유 캐릭터의 관계인지 확인 후 반환
    private Relationship getOwnedRelationship(Long memberId, Long characterId) {
        Relationship relationship = relationshipRepository.findByCharacterId(characterId)
                .orElseThrow(() -> new BaseException(CharacterErrorCode.CHARACTER_NOT_FOUND));

        if (!relationship.getMemberId().equals(memberId)) {
            throw new BaseException(CharacterErrorCode.CHARACTER_ACCESS_DENIED);
        }
        return relationship;
    }
}
