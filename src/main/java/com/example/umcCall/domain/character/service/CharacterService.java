package com.example.umcCall.domain.character.service;

import com.example.umcCall.domain.character.dto.request.CharacterCreateRequest;
import com.example.umcCall.domain.character.dto.request.CharacterUpdateRequest;
import com.example.umcCall.domain.character.dto.request.TraitRequest;
import com.example.umcCall.domain.character.dto.response.CharacterResponse;
import com.example.umcCall.domain.character.dto.response.CharacterSummaryResponse;
import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.character.entity.CharacterTrait;
import com.example.umcCall.domain.character.enums.Trait;
import com.example.umcCall.domain.character.exception.CharacterErrorCode;
import com.example.umcCall.domain.character.repository.CharacterRepository;
import com.example.umcCall.domain.character.repository.CharacterTraitRepository;
import com.example.umcCall.domain.chat.entity.ChatRoom;
import com.example.umcCall.domain.chat.enums.RoomType;
import com.example.umcCall.domain.chat.repository.ChatRoomRepository;
import com.example.umcCall.domain.chat.service.ChatRoomService;
import com.example.umcCall.domain.image.repository.PresetImageRepository;
import com.example.umcCall.domain.member.exception.MemberErrorCode;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.entity.RelationshipStatus;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import com.example.umcCall.domain.relationship.repository.RelationshipStatusRepository;
import com.example.umcCall.global.exception.BaseException;
import com.example.umcCall.domain.ai.enums.CharacterSyncOperation;
import com.example.umcCall.domain.ai.service.CharacterSyncTaskService;
import com.example.umcCall.domain.member.entity.Member;
import com.example.umcCall.domain.member.repository.MemberRepository;
import com.example.umcCall.domain.proactive.service.ProactiveScheduleCoordinator;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final CharacterAiProfileService characterAiProfileService;
    private final RelationshipRepository relationshipRepository;
    private final RelationshipStatusRepository relationshipStatusRepository;
    private final ChatRoomService chatRoomService;
    private final ChatRoomRepository chatRoomRepository;
    private final MemberRepository memberRepository;
    private final CharacterSyncTaskService syncTaskService;
    private final PresetImageRepository presetImageRepository;
    private final ProactiveScheduleCoordinator proactiveScheduleCoordinator;


    // 캐릭터 생성 (관계, 관계 통계, 채팅방 함께 생성) — 응답 바디 없음
    @Transactional
    public void createCharacter(Long memberId, CharacterCreateRequest request) {
        // 동시 요청으로 인한 개수 초과/메인 중복 생성을 막기 위해 회원 행에 락을 건다
        Member member = lockMember(memberId);

        if (member.getCharacterCreatedAt() != null
                && member.getCharacterCreatedAt().plusHours(24).isAfter(LocalDateTime.now())) {
            throw new BaseException(CharacterErrorCode.CHARACTER_RECREATE_TOO_SOON);
        }

        if (relationshipRepository.countByMemberIdAndCharacterDeletedAtIsNull(memberId) >= MAX_CHARACTER_COUNT) {
            throw new BaseException(CharacterErrorCode.CHARACTER_LIMIT_EXCEEDED);
        }

        // imageUrl이 있으면 해당 성별의 프리셋 목록에 있는 값인지 검증
        if (request.getImageUrl() != null
                && !presetImageRepository.existsByGenderAndImageUrl(request.getGender(), request.getImageUrl())) {
            throw new BaseException(CharacterErrorCode.INVALID_PRESET_IMAGE);
        }
        validateTraits(request.getTraits());

        Character character = characterRepository.save(
                Character.builder()
                        .lastName(request.getLastName())
                        .firstName(request.getFirstName())
                        .gender(request.getGender())
                        .age(request.getAge())
                        .job(request.getJob())
                        .preferTime(request.getPreferTime())
                        .mbti(request.getMbti())
                        .imageUrl(request.getImageUrl())
                        .build()
        );

        List<CharacterTrait> savedTraits = request.getTraits().stream()
                .map(traitRequest -> characterTraitRepository.save(
                        CharacterTrait.builder()
                                .character(character)
                                .trait(traitRequest.getTrait())
                                .priority(traitRequest.getPriority())
                                .build()))
                .toList();
        characterAiProfileService.calculateAndSave(character, savedTraits);

        // 최초 캐릭터만 메인으로 지정한다. 이후 생성되는 캐릭터는 사용자가
        // 활성화 API를 명시적으로 호출하기 전까지 기존 메인을 변경하지 않는다.
        boolean firstCharacter = relationshipRepository.findByMemberIdAndMainTrue(memberId).isEmpty();

        Relationship relationship = relationshipRepository.save(
                Relationship.builder()
                        .memberId(memberId)
                        .character(character)
                        .relationshipStage(request.getRelationshipStage())
                        .spiceLevel(request.getSpiceLevel())
                        .speechStyle(request.getSpeechStyle())
                        .main(firstCharacter)
                        .build()
        );

        relationshipStatusRepository.save(
                RelationshipStatus.builder()
                        .relationship(relationship)
                        .build()
        );

        // 캐릭터 생성 시 채팅방도 함께 생성 (ChatRoomService 로직 재사용)
        chatRoomService.createRoom(memberId, relationship.getId(), RoomType.CHARACTER);
        proactiveScheduleCoordinator.create(relationship);
        member.markCharacterCreated();
    }

    // 현재 메인 캐릭터 조회
    public CharacterResponse getActiveCharacter(Long memberId) {
        validateMemberExists(memberId);
        Relationship relationship = relationshipRepository.findByMemberIdAndMainTrue(memberId)
                .orElseThrow(() -> new BaseException(CharacterErrorCode.NO_ACTIVE_CHARACTER));
        Character character = relationship.getCharacter();
        List<CharacterTrait> characterTraits = characterTraitRepository.findByCharacterIdOrderByPriorityAsc(character.getId());
        return CharacterResponse.of(character, relationship, characterTraits, character.getImageUrl());
    }

    // 내 캐릭터 목록 조회 (최대 5개라 페이지네이션 없음)
    public List<CharacterSummaryResponse> getMyCharacters(Long memberId) {
        validateMemberExists(memberId);
        return relationshipRepository.findByMemberIdAndCharacterDeletedAtIsNull(memberId).stream()
                .map(relationship -> {
                    Character character = relationship.getCharacter();
                    LocalDateTime lastMessageAt = chatRoomRepository.findByRelationshipId(relationship.getId())
                            .map(ChatRoom::getLastMessageAt)
                            .orElse(null);
                    int daysTogether = (int) ChronoUnit.DAYS.between(relationship.getStartedAt(), LocalDate.now()) + 1;
                    return CharacterSummaryResponse.builder()
                            .characterId(character.getId())
                            .name(character.getName())
                            .imageUrl(character.getImageUrl())
                            .main(relationship.isMain())
                            .createdAt(character.getCreatedAt())
                            .startedAt(relationship.getStartedAt())
                            .daysTogether(daysTogether)
                            .lastMessageAt(lastMessageAt)
                            .build();
                })
                .toList();
    }

    // 캐릭터 수정
    @Transactional
    public void updateCharacter(Long memberId, Long characterId, CharacterUpdateRequest request) {
        Relationship relationship = getOwnedRelationship(memberId, characterId);
        Character character = relationship.getCharacter();

        if (character.isEdited()) {
            throw new BaseException(CharacterErrorCode.CHARACTER_ALREADY_EDITED);
        }

        if (request.getImageUrl() != null
                && !presetImageRepository.existsByGenderAndImageUrl(request.getGender(), request.getImageUrl())) {
            throw new BaseException(CharacterErrorCode.INVALID_PRESET_IMAGE);
        }

        validateTraits(request.getTraits());

        character.updateProfile(
                request.getLastName(), request.getFirstName(), request.getGender(),
                request.getAge(), request.getJob(), request.getPreferTime(),
                request.getMbti(), request.getImageUrl()
        );

        relationship.updateInfo(request.getRelationshipStage(), request.getSpiceLevel(), request.getSpeechStyle());

        characterTraitRepository.deleteByCharacterId(characterId);
        List<CharacterTrait> savedTraits = request.getTraits().stream()
                .map(traitRequest -> characterTraitRepository.save(
                        CharacterTrait.builder()
                                .character(character)
                                .trait(traitRequest.getTrait())
                                .priority(traitRequest.getPriority())
                                .build()))
                .toList();
        characterAiProfileService.calculateAndSave(character, savedTraits);

        proactiveScheduleCoordinator.reschedule(relationship);

    }

    // 활성 캐릭터 변경
    @Transactional
    public void activateCharacter(Long memberId, Long characterId) {
        // 동시 요청으로 인한 메인 캐릭터 중복 지정을 막기 위해 회원 행에 락을 건다
        lockMember(memberId);

        Relationship target = getOwnedRelationship(memberId, characterId);

        if (target.isMain()) {
            return;
        }

        relationshipRepository.findByMemberIdAndMainTrue(memberId)
                .ifPresent(current -> {
                    LocalDateTime becameMainAt = current.getBecameMainAt();
                    if (becameMainAt != null
                            && becameMainAt.plusDays(MIN_ACTIVATE_INTERVAL_DAYS).isAfter(LocalDateTime.now())) {
                        throw new BaseException(CharacterErrorCode.ACTIVE_CHARACTER_CHANGE_TOO_SOON);
                    }
                    current.deactivate();
                    // 메인에서 내려와도 선제 채팅 스케줄은 계속 유지한다.
                    proactiveScheduleCoordinator.reschedule(current);
                });

        target.activate();
        proactiveScheduleCoordinator.activate(target);
    }

    // 캐릭터 삭제 (하드 딜리트)
    @Transactional
    public void deleteCharacter(Long memberId, Long characterId) {
        Relationship relationship = getOwnedRelationship(memberId, characterId);

        if (relationship.isMain()) {
            throw new BaseException(CharacterErrorCode.CANNOT_DELETE_ACTIVE_CHARACTER);
        }

        Character character = relationship.getCharacter();

        character.markDeleted();
        relationship.deactivate();
        proactiveScheduleCoordinator.delete(relationship);
        chatRoomService.archiveRoom(relationship.getId());
        syncTaskService.enqueue(characterId, CharacterSyncOperation.DELETE);
    }


    // 회원 행에 비관적 락을 걸어 캐릭터 개수/메인 지정 동시성 문제를 막는다.
    private Member lockMember(Long memberId) {
        return memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BaseException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    private void validateMemberExists(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new BaseException(MemberErrorCode.MEMBER_NOT_FOUND);
        }
    }

    private void validateTraits(List<TraitRequest> traitRequests) {
        Set<Trait> traits = traitRequests.stream()
                .map(TraitRequest::getTrait)
                .collect(Collectors.toSet());
        Set<Integer> priorities = traitRequests.stream()
                .map(TraitRequest::getPriority)
                .collect(Collectors.toSet());
        boolean continuous = priorities.size() == traitRequests.size()
                && priorities.stream().allMatch(priority -> priority >= 1 && priority <= traitRequests.size());
        if (traits.size() != traitRequests.size() || !continuous) {
            throw new BaseException(CharacterErrorCode.INVALID_TRAIT_SELECTION);
        }
    }

    // 본인 소유 캐릭터의 관계인지 확인 후 반환
    private Relationship getOwnedRelationship(Long memberId, Long characterId) {
        Relationship relationship = relationshipRepository.findByCharacterIdAndCharacterDeletedAtIsNull(characterId)
                .orElseThrow(() -> new BaseException(CharacterErrorCode.CHARACTER_NOT_FOUND));

        if (!relationship.getMemberId().equals(memberId)) {
            throw new BaseException(CharacterErrorCode.CHARACTER_ACCESS_DENIED);
        }
        return relationship;
    }
}
