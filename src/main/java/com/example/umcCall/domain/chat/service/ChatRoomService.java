package com.example.umcCall.domain.chat.service;

import com.example.umcCall.domain.chat.entity.ChatRoom;
import com.example.umcCall.domain.chat.enums.RoomType;
import com.example.umcCall.domain.chat.exception.ChatErrorCode;
import com.example.umcCall.domain.chat.repository.ChatRoomRepository;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import com.example.umcCall.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅방 생성/조회를 담당하는 서비스. 캐릭터 생성 등 다른 도메인에서도 이 서비스를 호출해서 채팅방을 만든다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final RelationshipRepository relationshipRepository;

    // 채팅방 생성
    @Transactional
    public ChatRoom createChatRoom(Long memberId, RoomType roomType, Long relationshipId) {
        if (roomType == RoomType.CHARACTER) {
            validateOwnRelationship(memberId, relationshipId);
        }

        return chatRoomRepository.save(
                ChatRoom.builder()
                        .roomType(roomType)
                        .memberId(memberId)
                        .relationshipId(relationshipId)
                        .build()
        );
    }

    // CHARACTER 타입 채팅방은 본인 소유의 관계인지 확인
    private void validateOwnRelationship(Long memberId, Long relationshipId) {
        if (relationshipId == null) {
            throw new BaseException(ChatErrorCode.CHATROOM_RELATIONSHIP_ID_REQUIRED);
        }

        Relationship relationship = relationshipRepository.findById(relationshipId)
                .orElseThrow(() -> new BaseException(ChatErrorCode.CHATROOM_RELATIONSHIP_ACCESS_DENIED));

        if (!relationship.getMemberId().equals(memberId)) {
            throw new BaseException(ChatErrorCode.CHATROOM_RELATIONSHIP_ACCESS_DENIED);
        }
    }
}
