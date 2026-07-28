package com.example.umcCall.domain.relationship.entity;

import com.example.umcCall.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "chat_summary")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatSummary extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_summary_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "relationship_id", nullable = false, unique = true)
    private Relationship relationship;

    @Column(nullable = false, length = 200)
    private String summary;

    @Column(name = "last_message_id", nullable = false)
    private Long lastMessageId;

    /**
     * 요약 요청 형식이나 프롬프트가 바뀌었을 때 기존 캐시를 자동으로 무효화하기 위한 버전.
     * 기존 행은 null인 채로 남을 수 있으며 서비스에서 오래된 캐시로 취급한다.
     */
    @Column(name = "cache_version")
    private Integer cacheVersion;

    private ChatSummary(
            Relationship relationship, String summary, Long lastMessageId, int cacheVersion) {
        this.relationship = relationship;
        this.summary = summary;
        this.lastMessageId = lastMessageId;
        this.cacheVersion = cacheVersion;
    }

    public static ChatSummary create(
            Relationship relationship, String summary, Long lastMessageId, int cacheVersion) {
        return new ChatSummary(relationship, summary, lastMessageId, cacheVersion);
    }

    public void update(String summary, Long lastMessageId, int cacheVersion) {
        this.summary = summary;
        this.lastMessageId = lastMessageId;
        this.cacheVersion = cacheVersion;
    }
}
