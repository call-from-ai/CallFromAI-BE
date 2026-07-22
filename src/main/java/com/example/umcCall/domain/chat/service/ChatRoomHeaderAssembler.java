package com.example.umcCall.domain.chat.service;

import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.chat.dto.response.CharacterRoomHeader;
import com.example.umcCall.domain.chat.exception.ChatErrorCode;
import com.example.umcCall.domain.chat.exception.ChatException;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅방 헤더용 캐릭터 정보 조립 컴포넌트
 * relationship/character 도메인 레포지토리를 주입해 조립하며, 목록용 provider와는 분리한다.
 */
@Component
@RequiredArgsConstructor
public class ChatRoomHeaderAssembler {

    private final RelationshipRepository relationshipRepository;

    @Transactional(readOnly = true)
    public CharacterRoomHeader assemble(Long chatRoomId, Long relationshipId) {
        // 매니저방 등 관계가 없는 방은 헤더 조립 대상이 아님
        if (relationshipId == null) {
            throw new ChatException(ChatErrorCode.CHATROOM_NOT_FOUND);
        }

        Relationship relationship = relationshipRepository.findById(relationshipId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.CHATROOM_NOT_FOUND));
        Character character = relationship.getCharacter();

        // 관계 시작일 당일을 D+1로 계산
        int dDay = (int) ChronoUnit.DAYS.between(relationship.getStartedAt(), LocalDate.now()) + 1;

        return new CharacterRoomHeader(
                chatRoomId,
                character.getId(),
                character.getFirstName(),
                character.getImageUrl(),
                relationship.isMain(),
                dDay
        );
    }
}
