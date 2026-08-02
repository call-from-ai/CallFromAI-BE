package com.example.umcCall.domain.ai.dto;

import java.util.List;

/**
 * 통화 한 건의 <b>주제 라벨</b> 생성 요청.
 *
 * <p>⚠ {@link AiSummaryRequest}(대화 요약)와 <b>다른 물건이다 — 섞지 말 것.</b>
 * 그쪽은 채팅의 <b>관계 누적 요약</b>이라 {@code previousSummary}로 이어 붙이고, 프롬프트가
 * "관심사·취향·성격 중심 + 관계나 분위기 포함 + 참여자를 이름으로 지칭"을 요구한다.
 * 여기서 필요한 건 <b>무엇을 이야기했는가</b> 한 줄이고 <b>이름도 인물도 등장하지 않는다</b>.
 * 그래서 {@code userName}/{@code characterName}/{@code previousSummary}가 없다.
 *
 * <p>기대 출력 예시(화자 중립, 20자 이내):
 * <ul>
 *   <li>{@code 오늘하루와 퇴근 후 일상 이야기}</li>
 *   <li>{@code 출근준비와 아침 일정 이야기}</li>
 * </ul>
 *
 * @param callId        로그 대조용 통화 ID(AI 서버가 값 판단에 쓰지는 않는다)
 * @param messages      통화 전사. <b>시간순</b>이며 비어 있지 않다(비면 호출 자체를 하지 않는다)
 * @param maxCharacters 라벨 길이 상한(공백 포함). ⚠ 서버가 이걸 넘기면 <b>자르지 말고</b>
 *                      애초에 그 안에 들어오게 생성해야 한다 — 잘리면 문장이 끊긴 라벨이 나온다
 */
public record AiCallTopicRequest(
        Long callId,
        List<AiSummaryMessage> messages,
        int maxCharacters
) {
    public AiCallTopicRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
