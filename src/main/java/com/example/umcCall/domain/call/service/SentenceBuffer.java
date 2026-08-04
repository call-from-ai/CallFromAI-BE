package com.example.umcCall.domain.call.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AI 대사 조각(SSE chunk)을 모아 <b>합성 가능한 문장 단위</b>로 잘라낸다. 통화 TTFA 단축의 핵심 부품이다.
 *
 * <p>스트리밍으로 받은 토막(“응, 나 방금”, “ 퇴근했어. 너는”, …)은 그대로는 TTS에 넘길 수 없다 —
 * 운율이 뭉개지고 호출 수가 폭증한다. 여기서 문장이 완성될 때마다 하나씩 내보내, 첫 문장이 나오는 즉시
 * 합성·송신할 수 있게 한다(= LLM이 나머지를 만드는 시간이 사용자 체감 지연에서 빠진다).
 *
 * <p><b>스레드 안전하지 않다.</b> 통화 워커(통화당 단일 스레드)에서만 쓴다 — SSE 조각 콜백도 그 워커에서
 * 동기로 불리므로 confine이 유지된다.
 *
 * <p>규칙 셋:
 * <ul>
 *   <li><b>경계</b> — 종결 부호({@code . ! ? …})와 개행. 부호 뒤에 닫는 따옴표·괄호가 붙어 있으면 함께 넘긴다.</li>
 *   <li><b>최소 길이</b>({@value #MIN_CHARS}자) — “응.” 같은 토막은 다음 문장과 <b>합쳐서</b> 내보낸다.
 *       잘게 쪼개면 TTS 호출·비용이 늘고 말이 뚝뚝 끊긴다.</li>
 *   <li><b>최대 길이</b>({@value #MAX_CHARS}자) — 종결 부호가 없는 응답에 갇히지 않도록 공백에서 강제로 자른다.</li>
 * </ul>
 */
public class SentenceBuffer {

    /** 이보다 짧은 조각은 문장으로 안 친다(다음 문장과 합쳐진다). */
    private static final int MIN_CHARS = 10;

    /** 종결 부호가 없어도 이만큼 쌓이면 강제로 자른다. */
    private static final int MAX_CHARS = 120;

    /** 문장 끝을 알리는 부호. 개행은 따로 본다. */
    private static final String TERMINALS = ".!?…。！？";

    /** 종결 부호 뒤에 붙어 있으면 앞 문장에 포함시킬 닫는 문자들. */
    private static final String CLOSERS = "\"'’”」』)〕]}";

    private final StringBuilder buffer = new StringBuilder();

    /**
     * 조각 하나를 넣고, 그 결과 <b>완성된 문장들</b>을 순서대로 돌려준다(없으면 빈 리스트).
     * 조각 하나에 문장이 여러 개 들어 있을 수 있어 반환형이 리스트다.
     */
    public List<String> feed(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return List.of();
        }
        buffer.append(chunk);

        List<String> sentences = new ArrayList<>();
        String sentence;
        while ((sentence = takeNext()) != null) {
            sentences.add(sentence);
        }
        return sentences;
    }

    /**
     * 스트림이 끝난 뒤 남은 꼬리를 꺼낸다(마지막 문장은 보통 여기로 나온다).
     * 버퍼는 비워지므로 두 번 불러도 중복 발화가 생기지 않는다.
     */
    public Optional<String> flush() {
        String rest = buffer.toString().strip();
        buffer.setLength(0);
        return rest.isEmpty() ? Optional.empty() : Optional.of(rest);
    }

    /** 지금 버퍼에서 문장 하나를 떼어낸다. 아직 문장이 안 됐으면 {@code null}. */
    private String takeNext() {
        int end = boundaryEnd();
        if (end < 0) {
            end = forcedCut();
        }
        if (end < 0) {
            return null;
        }
        String sentence = buffer.substring(0, end).strip();
        buffer.delete(0, end);
        // 공백·부호만 남은 구간이면 합성할 게 없다. 버리고 다음 문장을 찾는다.
        return sentence.isEmpty() ? takeNext() : sentence;
    }

    /**
     * 첫 문장 경계의 끝 인덱스(exclusive). 없으면 {@code -1}.
     * <p>최소 길이에 못 미치는 경계는 <b>건너뛰고 계속 찾는다</b> — 그게 짧은 토막이 다음 문장과 합쳐지는 방식이다.
     */
    private int boundaryEnd() {
        for (int i = 0; i < buffer.length(); i++) {
            char current = buffer.charAt(i);
            boolean terminal = TERMINALS.indexOf(current) >= 0;
            if (!terminal && current != '\n') {
                continue;
            }
            // ⚠ 소수점 방어. 숫자 사이의 마침표는 문장 끝이 아니다("영하 3.5도" → "영하 3." 로 잘리면 안 된다).
            // 버퍼 끝이면 뒤에 뭐가 붙을지 아직 모르므로 조각 하나를 더 기다린다.
            if (current == '.' && i > 0 && Character.isDigit(buffer.charAt(i - 1))) {
                if (i == buffer.length() - 1) {
                    return -1;
                }
                if (Character.isDigit(buffer.charAt(i + 1))) {
                    continue;
                }
            }
            int end = i + 1;
            while (end < buffer.length() && CLOSERS.indexOf(buffer.charAt(end)) >= 0) {
                end++;
            }
            if (buffer.substring(0, end).strip().length() >= MIN_CHARS) {
                return end;
            }
        }
        return -1;
    }

    /**
     * 종결 부호 없이 {@value #MAX_CHARS}자를 넘겼을 때의 강제 절단 지점. 아직 여유가 있으면 {@code -1}.
     * 말이 어색하게 끊기지 않도록 <b>공백에서</b> 자르고, 공백이 없으면 길이로 자른다.
     */
    private int forcedCut() {
        if (buffer.length() < MAX_CHARS) {
            return -1;
        }
        for (int i = MAX_CHARS - 1; i > MIN_CHARS; i--) {
            if (Character.isWhitespace(buffer.charAt(i))) {
                return i + 1;
            }
        }
        return MAX_CHARS;
    }
}
