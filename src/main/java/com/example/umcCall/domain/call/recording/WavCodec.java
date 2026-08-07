package com.example.umcCall.domain.call.recording;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * wav 컨테이너 최소 코덱. 통화 녹음이 다루는 두 방향만 있다 —
 * <b>TTS가 준 wav를 열어 PCM을 꺼내고</b>, <b>모아둔 PCM 앞에 붙일 헤더를 만든다</b>.
 *
 * <p>범용 코덱이 아니다: <b>PCM 16-bit 모노</b>만 받고 그 외는 예외다. 다운믹스·비트 변환을 넣지 않은 건
 * 실제 입력이 TTS 하나뿐이고 그 포맷이 벤더 쪽에 고정돼 있기 때문이다(Typecast 표준 TTS = 16-bit 모노
 * 44.1kHz) — 포맷이 바뀌면 조용히 뭉개지는 대신 <b>예외로 드러나는 게 낫다</b>.
 */
public final class WavCodec {

    /** 표준 44바이트 헤더(RIFF + fmt 16 + data). */
    public static final int HEADER_BYTES = 44;

    private static final int PCM_FORMAT = 1;
    private static final int BITS_PER_SAMPLE = 16;

    private WavCodec() {
    }

    /**
     * wav 바이트에서 PCM을 꺼낸다. 청크를 순회하므로 {@code fmt }·{@code data} 사이에
     * {@code LIST} 같은 게 끼어 있어도 된다(고정 오프셋 파싱은 그때 깨진다).
     *
     * @throws IllegalArgumentException RIFF/WAVE가 아니거나 PCM 16-bit 모노가 아닐 때
     */
    public static PcmAudio decode(byte[] wav) {
        ByteBuffer buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        if (wav.length < 12 || !"RIFF".equals(readTag(buffer, 0)) || !"WAVE".equals(readTag(buffer, 8))) {
            throw new IllegalArgumentException("wav가 아닙니다(RIFF/WAVE 헤더 없음). bytes=" + wav.length);
        }

        int sampleRate = 0;
        int position = 12;
        while (position + 8 <= wav.length) {
            String id = readTag(buffer, position);
            int size = buffer.getInt(position + 4);
            int payload = position + 8;
            if (size < 0 || payload + size > wav.length) {
                size = wav.length - payload; // 길이가 깨진 꼬리는 남은 만큼만 읽는다
            }

            if ("fmt ".equals(id)) {
                int format = buffer.getShort(payload) & 0xFFFF;
                int channels = buffer.getShort(payload + 2) & 0xFFFF;
                sampleRate = buffer.getInt(payload + 4);
                int bits = buffer.getShort(payload + 14) & 0xFFFF;
                if (format != PCM_FORMAT || channels != 1 || bits != BITS_PER_SAMPLE) {
                    throw new IllegalArgumentException(
                            "PCM 16-bit 모노만 지원합니다. format=%d, channels=%d, bits=%d"
                                    .formatted(format, channels, bits));
                }
            } else if ("data".equals(id)) {
                if (sampleRate == 0) {
                    throw new IllegalArgumentException("fmt 청크보다 data 청크가 먼저 나왔습니다.");
                }
                return new PcmAudio(toSamples(buffer, payload, size), sampleRate);
            }
            position = payload + size + (size % 2); // 청크는 짝수 바이트로 패딩된다
        }
        throw new IllegalArgumentException("data 청크가 없습니다. bytes=" + wav.length);
    }

    /** PCM 앞에 붙일 44바이트 헤더. 모노 16-bit 고정. */
    public static byte[] header(int dataBytes, int sampleRate) {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(36 + dataBytes); // RIFF 청크 크기 = 전체 - 8
        buffer.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buffer.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(16);                       // fmt 청크 크기(PCM)
        buffer.putShort((short) PCM_FORMAT);
        buffer.putShort((short) 1);              // 채널 = 모노
        buffer.putInt(sampleRate);
        buffer.putInt(sampleRate * 2);           // byteRate = 레이트 × 블록 정렬
        buffer.putShort((short) 2);              // blockAlign = 채널 × 바이트/샘플
        buffer.putShort((short) BITS_PER_SAMPLE);
        buffer.put("data".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(dataBytes);
        return buffer.array();
    }

    /** 리틀엔디언 16-bit 바이트열을 샘플 배열로 편다. */
    static short[] toSamples(ByteBuffer buffer, int offset, int bytes) {
        short[] samples = new short[bytes / 2];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = buffer.getShort(offset + i * 2);
        }
        return samples;
    }

    private static String readTag(ByteBuffer buffer, int offset) {
        byte[] tag = new byte[4];
        buffer.get(offset, tag);
        return new String(tag, StandardCharsets.US_ASCII);
    }
}
