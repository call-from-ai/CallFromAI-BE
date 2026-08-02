package com.example.umcCall.domain.call.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.umcCall.domain.ai.dto.AiChatHistoryItem;
import com.example.umcCall.domain.ai.dto.AiChatRequest;
import com.example.umcCall.domain.ai.dto.AiChatResponse;
import com.example.umcCall.domain.ai.mapper.AiCharacterSnapshotMapper;
import com.example.umcCall.domain.ai.mapper.AiRelationshipSnapshotMapper;
import com.example.umcCall.domain.ai.service.AiConversationService;
import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.character.entity.CharacterAiProfile;
import com.example.umcCall.domain.character.repository.CharacterAiProfileRepository;
import com.example.umcCall.domain.character.repository.CharacterRepository;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.entity.RelationshipStatus;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import com.example.umcCall.domain.relationship.repository.RelationshipStatusRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 대화 로그 → AI 요청 조립 검증. 핵심: 로그의 <b>마지막 항목이 message</b>, 그 앞이 <b>history</b>로 파생되는지.
 * 엔티티/스냅샷은 관심사가 아니라 mock으로 둔다.
 */
@ExtendWith(MockitoExtension.class)
class CallConversationServiceTest {

    private static final Long CHARACTER_ID = 10L;
    private static final Long RELATIONSHIP_ID = 20L;
    private static final LocalDateTime T = LocalDateTime.now();

    @Mock private CharacterRepository characterRepository;
    @Mock private CharacterAiProfileRepository characterAiProfileRepository;
    @Mock private RelationshipRepository relationshipRepository;
    @Mock private RelationshipStatusRepository relationshipStatusRepository;
    @Mock private AiCharacterSnapshotMapper characterSnapshotMapper;
    @Mock private AiRelationshipSnapshotMapper relationshipSnapshotMapper;
    @Mock private AiConversationService aiConversationService;

    @InjectMocks private CallConversationService service;

    @BeforeEach
    void loadEntities() {
        when(characterRepository.findById(CHARACTER_ID))
                .thenReturn(Optional.of(mock(Character.class)));
        when(characterAiProfileRepository.findById(CHARACTER_ID))
                .thenReturn(Optional.of(mock(CharacterAiProfile.class)));
        when(relationshipRepository.findById(RELATIONSHIP_ID))
                .thenReturn(Optional.of(mock(Relationship.class)));
        when(relationshipStatusRepository.findByRelationshipId(RELATIONSHIP_ID))
                .thenReturn(Optional.of(mock(RelationshipStatus.class)));
        // 스트리밍 경로 테스트는 chat()을 타지 않는다 — 스텁이 남아도 실패하지 않게 lenient.
        lenient().when(aiConversationService.chat(any()))
                .thenReturn(new AiChatResponse("응답", null, null, null, null));
    }

    private AiChatRequest captureRequest() {
        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiConversationService).chat(captor.capture());
        return captor.getValue();
    }

    @Test
    void 로그의_마지막을_message로_그_앞을_history로_보낸다() {
        List<AiChatHistoryItem> log = new ArrayList<>(List.of(
                new AiChatHistoryItem("user", "안녕", T),
                new AiChatHistoryItem("assistant", "안녕하세요", T),
                new AiChatHistoryItem("user", "뭐해?", T)));

        service.respond(CHARACTER_ID, RELATIONSHIP_ID, log);

        AiChatRequest sent = captureRequest();
        assertThat(sent.message()).isEqualTo("뭐해?");
        assertThat(sent.history())
                .extracting(AiChatHistoryItem::content)
                .containsExactly("안녕", "안녕하세요");
        assertThat(sent.characterId()).isEqualTo(CHARACTER_ID);
        assertThat(sent.requestId()).isNotBlank(); // 멱등성 키
    }

    @Test
    void history는_최근_20개로_윈도잉되고_message는_항상_마지막이다() {
        // user 22개(0~21)를 쌓으면: 마지막(21)이 message, 그 앞 21개 중 최근 20개(1~20)만 history로.
        List<AiChatHistoryItem> log = new ArrayList<>();
        for (int i = 0; i < 22; i++) {
            log.add(new AiChatHistoryItem("user", "m" + i, T));
        }

        service.respond(CHARACTER_ID, RELATIONSHIP_ID, log);

        AiChatRequest sent = captureRequest();
        assertThat(sent.message()).isEqualTo("m21");
        assertThat(sent.history()).hasSize(20);
        assertThat(sent.history()).extracting(AiChatHistoryItem::content)
                .first().isEqualTo("m1");   // m0은 윈도우 밖으로 밀림
        assertThat(sent.history()).extracting(AiChatHistoryItem::content)
                .last().isEqualTo("m20");
    }

    @Test
    void 로그가_한_개면_message는_그것이고_history는_비어있다() {
        List<AiChatHistoryItem> log = new ArrayList<>(List.of(
                new AiChatHistoryItem("user", "혼잣말", T)));

        service.respond(CHARACTER_ID, RELATIONSHIP_ID, log);

        AiChatRequest sent = captureRequest();
        assertThat(sent.message()).isEqualTo("혼잣말");
        assertThat(sent.history()).isEmpty();
    }

    @Test
    void 스트리밍도_같은_요청을_조립해_보낸다() {
        // 통화가 실제로 쓰는 경로다. 조립이 단발과 갈리면 스트리밍 턴만 다른 맥락 위에서 답하게 된다.
        List<AiChatHistoryItem> log = new ArrayList<>(List.of(
                new AiChatHistoryItem("user", "안녕", T),
                new AiChatHistoryItem("assistant", "안녕하세요", T),
                new AiChatHistoryItem("user", "뭐 해?", T)));

        service.respondStream(CHARACTER_ID, RELATIONSHIP_ID, log, chunk -> { });

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiConversationService).chatStream(captor.capture(), any());
        assertThat(captor.getValue().message()).isEqualTo("뭐 해?");
        assertThat(captor.getValue().history()).hasSize(2);
    }
}
