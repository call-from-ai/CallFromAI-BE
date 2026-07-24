package com.example.umcCall.domain.chat.service;

import com.example.umcCall.domain.ai.dto.AiChatHistoryItem;
import com.example.umcCall.domain.ai.dto.AiChatRequest;
import com.example.umcCall.domain.ai.dto.AiChatResponse;
import com.example.umcCall.domain.ai.service.AiConversationService;
import com.example.umcCall.domain.chat.dto.response.ChatMessageResponse;
import com.example.umcCall.domain.chat.entity.ChatMessage;
import com.example.umcCall.domain.chat.entity.ChatRoom;
import com.example.umcCall.domain.chat.enums.SenderType;
import com.example.umcCall.domain.chat.repository.ChatMessageRepository;
import com.example.umcCall.domain.chat.repository.ChatRoomRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * 유저가 보낸 메시지에 대한 AI 답장을 만들어 주는 곳.
 *모아서 한 번에 답장(디바운스): 유저가 "내가"·"오늘"·"화나"처럼 짧게 연속으로 보내면,
 *하나씩 답하지 않고 잠깐 기다렸다가 그동안 온 걸 모아 한 번만 AI에게 물어본다.
 * 한 방은 한 번에 하나씩: 같은 방에서 AI 답장 만들기가 두 개 동시에 돌지 않게 막는다.
 *   <li>{@code pending} : 방마다 "예약된 대기 타이머" 보관</li>
 *   <li>{@code inFlight} : 지금 답장 만드는 중인 방 표시(처리 중 팻말)</li>
 *   <li>{@code answeredWatermark} : 방마다 "여기까지는 이미 답했다"는 표시(유저 메시지 id)</li>
 */
@Slf4j
@Component
public class AiReplyDebouncer {

    /** 유저가 4초동안 조용하면 "말 끝났구나" 하고 그동안 온 메시지를 모아 처리한다. */
    private static final Duration QUIET_WINDOW = Duration.ofSeconds(4);
    /** 한 번에 AI로 보낼 유저 메시지 최대 개수. 유저가 도배해도 이만큼만 묶고 나머지는 다음 번에. */
    private static final int BATCH_LIMIT = 30;
    /** AI에게 참고하라고 같이 보내는 지난 대화 개수. */
    private static final int HISTORY_SIZE = 20;
    /** 메시지들을 이어붙일 때 */
    private static final String MESSAGE_JOINER = "\n";

    private final TaskScheduler timer;
    private final Executor worker;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AiReplyGenerator aiReplyGenerator;
    private final AiConversationService aiConversationService;
    private final ChatMessageService chatMessageService;
    private final ChatSseService chatSseService;

    /** 방마다 "곧 처리할 예약 타이머"를 담아둔다. 새 메시지가 오면 이 타이머를 미룬다. */
    private final Map<Long, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    /** 지금 답장을 만드는 중인 방 목록. 여기 들어있는 방은 새 처리를 시작하지 않는다 */
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();
    /** 방마다 "이 id까지의 유저 메시지는 이미 답했다"는 표시. 다음엔 이 id 다음 것부터 처리한다. */
    private final Map<Long, Long> answeredWatermark = new ConcurrentHashMap<>();

    // 스프링이 어떤 스레드풀을 넣어줄지 이름(@Qualifier)으로 정확히 지정하려고 생성자를 직접 썼다.
    public AiReplyDebouncer(
            @Qualifier("aiReplyTimer") TaskScheduler timer,
            @Qualifier("aiReplyWorker") Executor worker,
            ChatRoomRepository chatRoomRepository,
            ChatMessageRepository chatMessageRepository,
            AiReplyGenerator aiReplyGenerator,
            AiConversationService aiConversationService,
            ChatMessageService chatMessageService,
            ChatSseService chatSseService) {
        this.timer = timer;
        this.worker = worker;
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.aiReplyGenerator = aiReplyGenerator;
        this.aiConversationService = aiConversationService;
        this.chatMessageService = chatMessageService;
        this.chatSseService = chatSseService;
    }

    /**
     * "이 방에 유저가 방금 뭔가 보냈다"고 알려주는 입구. 유저 메시지 저장이 끝나면 호출된다.
     * 이미 걸려있던 대기 타이머가 있으면 취소하고, 다시 처음부터 시간을 잰다.
     */
    public void onActivity(Long roomId) {
        // compute: "타이머 취소 + 새로 예약"을 한 묶음으로 처리해, 그 사이 다른 스레드가 끼어들지 못하게 한다.
        pending.compute(roomId, (id, existing) -> {
            if (existing != null) {
                existing.cancel(false);
            }
            return scheduleFire(roomId);
        });
    }

    /** QUIET_WINDOW 뒤에 fire()가 실행되도록 예약한다. */
    private ScheduledFuture<?> scheduleFire(Long roomId) {
        // 만든 타이머를 그 타이머가 하는 일(fire) 안에서 알아야 해서, 1칸짜리 배열에 담아 건네준다.
        // 실행은 QUIET_WINDOW 뒤라, 그 전에 holder[0] 값이 이미 채워져 있으니 안전하다.
        ScheduledFuture<?>[] holder = new ScheduledFuture<?>[1];
        holder[0] = timer.schedule(() -> fire(roomId, holder[0]), Instant.now().plus(QUIET_WINDOW));
        return holder[0];
    }

    /** 대기 시간이 지나 타이머가 울리면 실행 */
    private void fire(Long roomId, ScheduledFuture<?> self) {
        // 이 방의 대기 타이머 칸이 "아직 나"일 때만 비운다.
        // (그새 새 메시지가 와서 더 최신 타이머로 바뀌었다면, 그건 건드리지 않는다.)
        pending.remove(roomId, self);

        // 이 방을 "처리 중"으로 표시. 이미 처리 중이면 add가 false를 주므로 지금은 물러난다.
        if (!inFlight.add(roomId)) {
            // 지금 도는 처리가 끝난 뒤에 남은 메시지를 처리하도록 다시 예약해두고 물러난다.
            onActivity(roomId);
            return;
        }

        try {
            worker.execute(() -> {
                try {
                    process(roomId);
                } catch (Exception e) {
                    log.error("AI 답장 처리 실패. roomId={}", roomId, e);
                } finally {
                    // 성공이든 실패든 "처리 중" 표시는 반드시 뗀다. 안 떼면 이 방이 다시는 처리 안 됨.
                    inFlight.remove(roomId);
                }
            });
        } catch (RejectedExecutionException e) {
            // worker에게 넘기는 것 자체가 거절되면(서버 종료 등) 위 finally가 안 도니, 여기서 표시를 떼준다.
            inFlight.remove(roomId);
            log.error("AI 답장 작업 제출 실패. roomId={}", roomId, e);
        }
    }

    /**
     * 진짜 AI 답장을 만드는 부분. worker 스레드에서 돌며, 한 방은 한 번에 하나만 여기 들어온다.
     */
    private void process(Long roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId).orElse(null);
        if (room == null || room.getRelationshipId() == null) {
            return;   // 방이 없거나, AI가 없는 매니저 안내방이면 답장 안 함
        }

        // 이 방에서 "여기까지 답했다"는 경계를 가져와, 그 다음 유저 메시지들만 모은다
        long watermark = resolveWatermark(roomId);
        List<ChatMessage> batch = chatMessageRepository.findUserMessagesAfter(
                roomId, SenderType.USER, watermark, PageRequest.of(0, BATCH_LIMIT));
        if (batch.isEmpty()) {
            return;   // 답할 새 메시지가 없으면 끝
        }

        long batchMaxId = batch.get(batch.size() - 1).getId();   // 모은 것 중 가장 최신 id
        long batchFirstId = batch.get(0).getId();                // 모은 것 중 가장 오래된 id

        // 모은 메시지 내용을 줄바꿈으로 이어 하나의 말처럼 만든다. (예: "내가\n오늘\n화나")
        String message = batch.stream()
                .map(ChatMessage::getContent)
                .filter(content -> content != null && !content.isBlank())
                .collect(Collectors.joining(MESSAGE_JOINER));
        if (message.isBlank()) {
            // TODO: 이미지가 붙으면 "내용이 비어도 이미지가 있으면" AI에게 이미지를 보내 답을 받아야 한다.
            //   그때는 이 조건을 "텍스트도 없고 이미지도 없을 때만 스킵"으로 바꾸고, buildRequest를 이미지까지 싣도록 확장할 것.
            answeredWatermark.put(roomId, batchMaxId);
            return;
        }

        // AI에게 참고용으로 줄 지난 대화도 준비한다.
        List<AiChatHistoryItem> history = buildHistory(roomId, batchFirstId);

        // AI 서버에 물어본다.
        AiChatRequest request = aiReplyGenerator.buildRequest(room.getRelationshipId(), message, history);
        AiChatResponse response = aiConversationService.chat(request);
        String reply = response.reply();
        if (reply == null || reply.isBlank()) {
            return;
        }

        // 답장을 저장하고, 유저 화면(SSE)으로 실시간 전달한다.
        ChatMessage aiMessage = chatMessageService.saveAiMessage(roomId, reply);
        chatSseService.sendToMember(room.getMemberId(), "message", ChatMessageResponse.from(aiMessage));

        // 여기까지 잘 됐으니 "이 방은 batchMaxId까지 답했다"고 경계를 옮긴다.
        answeredWatermark.put(roomId, batchMaxId);
    }

    /**
     * 이 방에서 "여기까지 답했다"는 경계 id를 알려준다.
     */
    private long resolveWatermark(Long roomId) {
        Long cached = answeredWatermark.get(roomId);
        if (cached != null) {
            return cached;
        }
        Long lastAiId = chatMessageRepository.findMaxAiMessageId(roomId, SenderType.AI);
        long resolved = (lastAiId == null) ? 0L : lastAiId;   // AI 메시지가 아직 없으면 0(=처음부터)
        answeredWatermark.put(roomId, resolved);
        return resolved;
    }

    /** AI에게 참고로 줄 지난 대화를 만든다. beforeMessageId보다 이전 것들을 오래된 순서로 정리한다. */
    private List<AiChatHistoryItem> buildHistory(Long roomId, long beforeMessageId) {
        List<ChatMessage> recent = new ArrayList<>(chatMessageRepository.findMessagesByCursor(
                roomId, null, beforeMessageId, PageRequest.of(0, HISTORY_SIZE)));
        Collections.reverse(recent);   // 최신→과거로 뽑혔으니, 과거→최신으로 뒤집는다
        return recent.stream()
                .map(m -> new AiChatHistoryItem(toRole(m.getSenderType()), m.getContent(), m.getCreatedAt()))
                .toList();
    }

    /** 우리 쪽 발신자 값을 AI 서버가 쓰는 말로 바꾼다. 유저는 "user", 나머지는 "assistant". */
    private String toRole(SenderType senderType) {
        return senderType == SenderType.USER ? "user" : "assistant";
    }
}
