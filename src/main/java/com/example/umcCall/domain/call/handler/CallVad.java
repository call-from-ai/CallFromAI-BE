package com.example.umcCall.domain.call.handler;

/**
 * 통화 한 건의 <b>발화 시작 감지</b>(VAD). 업스트림 PCM 프레임의 에너지만 보고 "사용자가 지금 말을
 * 시작했다"를 판정해 끼어들기를 트리거한다.
 *
 * <p><b>왜 STT partial이 아니라 VAD인가</b>(#139): 끼어들기의 원래 트리거는 STT partial이었는데,
 * CLOVA CONFIG가 partial을 만들어내는 이벤트를 전부 껐거나 20초로 올려둔 상태라
 * ({@code durationThreshold} 20000 / {@code usePeriodEpd} false / {@code syllableThreshold} 미설정)
 * <b>보통 길이의 발화에서는 partial이 영영 오지 않는다</b> — 즉 끼어들기가 구조적으로 죽어 있었다.
 * 그 CONFIG는 턴이 잘게 쪼개지는 걸 막으려고 그렇게 잡은 것이라 <b>되돌릴 수 없다</b>(낮추면 세그먼트가
 * 닫혀 뒤이은 gap final이 발화의 마지막 조각만 담게 되고 전사·AI 맥락이 망가진다). 그래서 트리거를
 * STT 설정에서 <b>떼어냈다</b>.
 *
 * <p>부수 효과로 반응이 훨씬 빨라졌다 — partial 경로는 실측 지연이 2.4초였고, 여기서는
 * {@code minSpeechMs}(기본 60ms) + 네트워크가 전부다. 끼어들기는 200~300ms 안에 멈춰야 자연스럽다.
 *
 * <p>⚠ <b>CLOVA의 "단어로 인식됨" 필터가 사라진다.</b> partial은 CLOVA가 말로 알아들은 것만 통과시켰지만
 * 에너지 VAD엔 그 필터가 없어 <b>에코·기침·키보드 소리도 AI를 끊을 수 있다</b>. 그래서 프론트의
 * 에코 제거(AEC) 의존도가 커진다. 완화 수단은 임계값 둘뿐이고 <b>코드가 아니라 yml</b>에 있다
 * ({@code call.barge-in.*}) — 마이크·환경마다 달라서다.
 *
 * <p>스레드 안전하지 않다. WS 수신 스레드 <b>하나만</b> 호출한다(통화당 인스턴스 하나).
 */
public final class CallVad {

    /** 업스트림 오디오 계약(16kHz 모노 16-bit raw PCM). 이 값이 아니면 지속시간 계산이 어긋난다. */
    private static final int SAMPLE_RATE = 16_000;

    private static final int MS_PER_SECOND = 1_000;

    private final int rmsThreshold;
    /**
     * 발화로 인정할 최소 누적 <b>샘플 수</b>.
     *
     * <p>⚠ <b>프레임마다 ms로 환산해 더하지 않는다</b>(PR #142 리뷰 지적). 정수 나눗셈이라
     * 16샘플(=1ms) 미만인 프레임은 환산값이 <b>0</b>이 되어, 사용자가 계속 말해도 누적이 영영
     * 안 늘어난다 — 그 통화에선 끼어들기가 통째로 죽는다. 그보다 큰 프레임도 프레임마다 소수부를
     * 버려 실제보다 늦게 감지된다. 프레임 크기는 <b>프론트가 정하는 값</b>이라 20ms라고 가정할 수
     * 없으므로, 환산은 여기서 <b>한 번만</b> 하고 누적은 샘플 단위로 한다.
     * (16000은 1000으로 나누어떨어져 이 환산 자체엔 오차가 없다.)
     */
    private final int minSpeechSamples;

    /** 임계를 <b>연속으로</b> 넘긴 샘플 수. 중간에 한 프레임이라도 조용하면 0으로 돌아간다. */
    private int voicedSamples;
    /** 이번 발화를 이미 알렸는가. 말하는 내내 매 프레임 알리지 않으려고 둔다. */
    private boolean notified;

    public CallVad(int rmsThreshold, int minSpeechMs) {
        this.rmsThreshold = rmsThreshold;
        this.minSpeechSamples = minSpeechMs * SAMPLE_RATE / MS_PER_SECOND;
    }

    /**
     * 프레임 하나를 먹이고 <b>"말 시작"이 이번에 확정됐는지</b> 돌려준다.
     *
     * <p>연속으로 {@code minSpeechMs}만큼 임계를 넘겨야 true다 — 한 프레임짜리 잡음(클릭·마우스)으로
     * AI가 끊기는 걸 막는 최소한의 히스테리시스다. 발화 하나에 <b>한 번만</b> true이고, 조용해지면
     * 다시 무장한다.
     *
     * <p>⚠ 여기서 여러 번 true가 나와도 상위(TurnGate)가 턴당 1회로 줄이므로 통지가 중복되지는 않는다.
     * 그래도 발화당 1회로 좁혀두는 편이 호출 비용과 로그 소음 양쪽에 낫다.
     *
     * @param pcm16le 16-bit little-endian 모노 PCM. 길이가 홀수면 남는 1바이트는 무시한다.
     * @return 이 프레임에서 발화 시작이 새로 확정되면 true
     */
    public boolean isSpeechStart(byte[] pcm16le) {
        int samples = pcm16le.length / 2;
        if (samples == 0) {
            return false;
        }
        if (rms(pcm16le, samples) < rmsThreshold) {
            // 조용해졌다 → 누적을 버리고 다음 발화를 다시 알릴 수 있게 무장한다.
            voicedSamples = 0;
            notified = false;
            return false;
        }
        voicedSamples += samples;
        if (notified || voicedSamples < minSpeechSamples) {
            return false;
        }
        notified = true;
        return true;
    }

    /** 프레임의 RMS 진폭(0~32767). 제곱합이 int를 넘기므로 long으로 모은다. */
    private static long rms(byte[] pcm16le, int samples) {
        long sumOfSquares = 0;
        for (int i = 0; i < samples; i++) {
            // little-endian: 낮은 바이트가 앞. 상위 바이트만 부호 확장한다.
            int sample = (pcm16le[i * 2] & 0xff) | (pcm16le[i * 2 + 1] << 8);
            sumOfSquares += (long) sample * sample;
        }
        return (long) Math.sqrt((double) sumOfSquares / samples);
    }
}
