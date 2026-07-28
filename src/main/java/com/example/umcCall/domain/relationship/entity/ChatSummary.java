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

    private ChatSummary(Relationship relationship, String summary, Long lastMessageId) {
        this.relationship = relationship;
        this.summary = summary;
        this.lastMessageId = lastMessageId;
    }

    public static ChatSummary create(
            Relationship relationship, String summary, Long lastMessageId) {
        return new ChatSummary(relationship, summary, lastMessageId);
    }

    public void update(String summary, Long lastMessageId) {
        this.summary = summary;
        this.lastMessageId = lastMessageId;
    }
}
