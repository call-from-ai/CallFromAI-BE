package com.example.umcCall.domain.call.recording;

/**
 * 모노 16-bit PCM 한 덩어리. 샘플은 이미 디코드된 상태({@code short})라 리샘플·믹스가 바로 된다.
 *
 * <p>⚠ 배열 필드를 가진 record라 {@code equals}/{@code hashCode}는 참조 비교다 — 값 비교에 쓰지 말 것.
 */
public record PcmAudio(short[] samples, int sampleRate) {

    /**
     * 다른 샘플레이트로 변환한다(선형 보간). 같은 레이트면 그대로 돌려준다.
     *
     * <p>통화 녹음에서 쓰는 유일한 경로는 <b>Typecast 44.1kHz → 녹음 기준 16kHz</b>다(2026-08-06 이전엔
     * CLOVA 24kHz였다 — 대상 레이트를 헤더에서 읽어 벤더 교체에 영향받지 않았다).
     * 다운샘플 전에 로우패스를 걸지 않아 8kHz 위 성분이 접히는데(에일리어싱), 선형 보간 자체가 약한
     * 로우패스라 음성에서는 귀로 구별되지 않는 수준이다 — 이 판단의 근거는 스펙이 아니라 <b>청취 확인</b>이다.
     * ⚠ 다만 그 청취 확인은 <b>24kHz 원본</b>으로 한 것이다. 44.1kHz는 버리는 대역이 더 넓어
     * 에일리어싱이 커질 수 있으니, 녹음 음질 제보가 오면 여기부터 다시 들어볼 것.
     */
    public PcmAudio resampleTo(int targetRate) {
        if (targetRate == sampleRate || samples.length == 0) {
            return new PcmAudio(samples, targetRate);
        }
        double step = (double) sampleRate / targetRate;
        int outCount = (int) (samples.length / step);
        short[] out = new short[outCount];
        for (int i = 0; i < outCount; i++) {
            double at = i * step;
            int left = (int) at;
            double frac = at - left;
            short a = samples[left];
            short b = (left + 1 < samples.length) ? samples[left + 1] : a;
            out[i] = (short) Math.round(a + (b - a) * frac);
        }
        return new PcmAudio(out, targetRate);
    }
}
