package com.example.umcCall.domain.proactive.enums;

public enum AttachmentLevel {
    LOW,
    NORMAL,
    HIGH;

    public static AttachmentLevel from(Double attachment) {
        double score = attachment == null ? 5.0 : attachment;
        if (score < 4.0) return LOW;
        if (score < 7.0) return NORMAL;
        return HIGH;
    }
}
