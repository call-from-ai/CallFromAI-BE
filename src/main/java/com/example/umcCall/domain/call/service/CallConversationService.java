package com.example.umcCall.domain.call.service;

import com.example.umcCall.domain.ai.dto.AiChatHistoryItem;
import com.example.umcCall.domain.ai.dto.AiChatRequest;
import com.example.umcCall.domain.ai.service.AiConversationService;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 통화 한 턴의 AI 대화 오케스트레이션. STT final 텍스트 → AI 요청 조립 → chatStream() 호출.
 *
 * <p>AI 경계 호출은 {@link AiConversationService}(client + stale 가드)에, 스냅샷 조립은
 * {@link CallAiRequestAssembler}에 맡긴다. 여긴 <b>순서만</b> 정한다.
 *
 * <p>⚠ <b>이 메서드에 트랜잭션을 걸면 안 된다.</b> 아래 스트림은 수 초간 열려 있어, 감싸는 순간 그동안
 * DB 커넥션을 붙잡아 풀이 고갈된다. 조립에 필요한 트랜잭션은 {@link CallAiRequestAssembler} 안에서
 * 시작하고 <b>끝난다</b> — 스트림이 열릴 땐 이미 커밋·반납된 뒤다.
 */
@Service
@RequiredArgsConstructor
public class CallConversationService {

    private final CallAiRequestAssembler requestAssembler;
    private final AiConversationService aiConversationService;

    /**
     * 통화 대화 로그를 AI로 넘겨 대사 조각을 <b>스트리밍</b>으로 받는다. 조각이 도착할 때마다 {@code onChunk}가 불린다.
     * <p>로그는 <b>이번 사용자 발화까지 포함</b>한 append-only 이벤트 로그다(호출부가 STT final 시 append).
     *
     * <p>첫 문장이 나오는 즉시 합성·송신할 수 있어야 체감 지연(TTFA)이 줄어든다. 조각을 문장으로 묶는 건
     * 호출부({@code SentenceBuffer})가 하고, 여긴 순서만 맡는다.
     *
     * <p>⚠ {@code onChunk}는 <b>이 메서드를 호출한 스레드에서 동기로</b> 불린다(통화 워커). 그래서 안에서
     * 합성·송신을 해도 턴 순서가 그대로 지켜진다.
     *
     * @param conversation 이번 발화를 마지막에 포함한 대화 로그(비어 있으면 안 된다)
     */
    public void respondStream(Long characterId, Long relationshipId,
                              List<AiChatHistoryItem> conversation, Consumer<String> onChunk) {
        AiChatRequest request = requestAssembler.assemble(characterId, relationshipId, conversation);
        aiConversationService.chatStream(request, onChunk);
    }
}
