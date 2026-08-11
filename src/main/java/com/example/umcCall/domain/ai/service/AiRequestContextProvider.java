package com.example.umcCall.domain.ai.service;

import com.example.umcCall.domain.ai.dto.AiUserSnapshot;
import com.example.umcCall.domain.member.entity.Member;
import com.example.umcCall.domain.member.repository.MemberRepository;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
public class AiRequestContextProvider {

    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final String FALLBACK_USER_NAME = "너";
    private final MemberRepository memberRepository;

    public AiRequestContextProvider(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public Context create(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalStateException("회원을 찾을 수 없습니다: " + memberId));
        return create(member);
    }

    public Context create(Member member) {
        if (member == null) {
            throw new IllegalArgumentException("member is required");
        }

        return new Context(
                resolveUserName(member),
                DEFAULT_ZONE_ID.getId(),
                OffsetDateTime.now(DEFAULT_ZONE_ID),
                new AiUserSnapshot(
                        member.getBirth(),
                        member.getGender() == null ? null : member.getGender().name(),
                        member.getJob() == null ? null : member.getJob().name(),
                        member.getMbti() == null ? null : member.getMbti().name()));
    }

    private String resolveUserName(Member member) {
        if (member.getFirstName() != null && !member.getFirstName().isBlank()) {
            return member.getFirstName().strip();
        }
        if (member.getLastName() != null && !member.getLastName().isBlank()) {
            return member.getLastName().strip();
        }
        return FALLBACK_USER_NAME;
    }

    public record Context(String userName, String userTimeZone, OffsetDateTime localDateTime,
                          AiUserSnapshot user) {
    }
}
