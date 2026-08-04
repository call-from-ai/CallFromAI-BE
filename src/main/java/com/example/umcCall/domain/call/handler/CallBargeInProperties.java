package com.example.umcCall.domain.call.handler;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 끼어들기(barge-in) 감지 설정. {@code call.barge-in.*}에서 주입한다.
 *
 * <p>⚠ <b>CLOVA 설정({@code clova.speech.*}) 아래에 두지 않는다</b> — 트리거를 STT에서 떼어낸 게
 * #139의 핵심이라, 같은 자리에 두면 다음 사람이 다시 엮는다.
 *
 * <p>두 값이 <b>코드 상수가 아니라 yml</b>인 이유는 마이크·환경마다 적정값이 다르기 때문이다.
 * 에너지 VAD엔 CLOVA의 "단어로 인식됨" 필터가 없어 에코·잡음도 통과할 수 있는데
 * ({@link CallVad} 참고), 그 완화를 <b>재배포 없이</b> 하려면 설정이어야 한다.
 *
 * @param rmsThreshold 이 RMS 진폭(0~32767)을 넘겨야 "소리 있음". 올리면 잡음에 둔감해지지만
 *                     작게 말하는 사용자의 끼어들기를 놓친다.
 * @param minSpeechMs  그 소리가 <b>연속으로</b> 이만큼 이어져야 "말 시작". 올리면 클릭·기침 같은
 *                     짧은 잡음을 걸러내지만 그만큼 AI가 늦게 멈춘다.
 *                     ⚠ 끼어들기는 200~300ms 안에 멈춰야 자연스러워, 여기 큰 값을 넣으면
 *                     이 기능을 만든 이유가 사라진다.
 */
@Validated
@ConfigurationProperties(prefix = "call.barge-in")
public record CallBargeInProperties(
        @Min(value = 1, message = "call.barge-in.rms-threshold는 1 이상이어야 합니다")
        int rmsThreshold,
        @Min(value = 20, message = "call.barge-in.min-speech-ms는 최소 20ms 이상이어야 합니다")
        int minSpeechMs
) {
    /** 이 통화에서 쓸 감지기 하나. 상태를 갖는 객체라 통화마다 새로 만든다. */
    public CallVad newVad() {
        return new CallVad(rmsThreshold, minSpeechMs);
    }
}
