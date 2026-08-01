package com.example.umcCall.domain.character.service;

import com.example.umcCall.domain.call.repository.CallHistoryRepository;
import com.example.umcCall.domain.call.repository.CallRepository;
import com.example.umcCall.domain.character.repository.CharacterAiProfileRepository;
import com.example.umcCall.domain.character.repository.CharacterRepository;
import com.example.umcCall.domain.character.repository.CharacterTraitRepository;
import com.example.umcCall.domain.chat.repository.ChatMessageRepository;
import com.example.umcCall.domain.chat.repository.ChatRoomRepository;
import com.example.umcCall.domain.chat.service.ChatPhotoCleaner;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import com.example.umcCall.domain.relationship.repository.RelationshipStatusRepository;
import com.example.umcCall.domain.relationship.repository.ChatSummaryRepository;
import com.example.umcCall.domain.proactive.repository.ProactiveContactScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AI 서버 정리가 성공한 캐릭터의 백엔드 데이터를 FK 역순으로 물리 삭제한다. */
@Service
@RequiredArgsConstructor
public class CharacterHardDeleteService {

    private final CharacterRepository characterRepository;
    private final CharacterTraitRepository characterTraitRepository;
    private final CharacterAiProfileRepository characterAiProfileRepository;
    private final RelationshipRepository relationshipRepository;
    private final RelationshipStatusRepository relationshipStatusRepository;
    private final ChatSummaryRepository chatSummaryRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CallRepository callRepository;
    private final CallHistoryRepository callHistoryRepository;
    private final ChatPhotoCleaner chatPhotoCleaner;
    private final ProactiveContactScheduleRepository proactiveContactScheduleRepository;

    @Transactional
    public void purge(Long characterId) {
        relationshipRepository.findByCharacterId(characterId).ifPresent(relationship -> {
            proactiveContactScheduleRepository.deleteByRelationshipId(relationship.getId());
            chatRoomRepository.findByRelationshipId(relationship.getId()).ifPresent(chatRoom -> {
                chatPhotoCleaner.purgeRoomPhotos(chatRoom.getId());
                chatMessageRepository.deleteByChatRoomId(chatRoom.getId());
                chatRoomRepository.delete(chatRoom);
            });
            // 전사(call_history)가 통화를 nullable=false FK로 참조한다. Call엔 cascade가 없어 순서를 지켜야 한다.
            callHistoryRepository.deleteByRelationshipId(relationship.getId());
            callRepository.deleteByRelationshipId(relationship.getId());
            chatSummaryRepository.deleteByRelationshipId(relationship.getId());
            relationshipStatusRepository.deleteByRelationshipId(relationship.getId());
            relationshipRepository.delete(relationship);
        });

        characterAiProfileRepository.deleteById(characterId);
        characterTraitRepository.deleteByCharacterId(characterId);
        characterRepository.deleteById(characterId);
    }
}
