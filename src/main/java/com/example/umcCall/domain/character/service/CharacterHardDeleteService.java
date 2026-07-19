package com.example.umcCall.domain.character.service;

import com.example.umcCall.domain.character.repository.CharacterAiProfileRepository;
import com.example.umcCall.domain.character.repository.CharacterImageRepository;
import com.example.umcCall.domain.character.repository.CharacterRepository;
import com.example.umcCall.domain.character.repository.CharacterTraitRepository;
import com.example.umcCall.domain.chat.repository.ChatMessageRepository;
import com.example.umcCall.domain.chat.repository.ChatRoomRepository;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import com.example.umcCall.domain.relationship.repository.RelationshipStatusRepository;
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
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public void purge(Long characterId) {
        relationshipRepository.findByCharacterId(characterId).ifPresent(relationship -> {
            chatRoomRepository.findByRelationshipId(relationship.getId()).ifPresent(chatRoom -> {
                chatMessageRepository.deleteByChatRoomId(chatRoom.getId());
                chatRoomRepository.delete(chatRoom);
            });
            relationshipStatusRepository.deleteByRelationshipId(relationship.getId());
            relationshipRepository.delete(relationship);
        });

        characterAiProfileRepository.deleteById(characterId);
        characterTraitRepository.deleteByCharacterId(characterId);
        characterRepository.deleteById(characterId);
    }
}
