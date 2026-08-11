package com.example.umcCall.domain.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.umcCall.domain.member.entity.Member;
import com.example.umcCall.domain.image.enums.Gender;
import com.example.umcCall.domain.member.enums.Job;
import com.example.umcCall.domain.member.enums.Mbti;
import com.example.umcCall.domain.member.repository.MemberRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiRequestContextProviderTest {

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final AiRequestContextProvider provider = new AiRequestContextProvider(memberRepository);

    @Test
    void 이름이_없으면_너를_사용한다() {
        Member member = mock(Member.class);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        AiRequestContextProvider.Context context = provider.create(1L);

        assertThat(context.userName()).isEqualTo("너");
        assertThat(context.userTimeZone()).isEqualTo("Asia/Seoul");
        assertThat(context.localDateTime()).isNotNull();
    }

    @Test
    void 이름이_있으면_firstName을_우선한다() {
        Member member = mock(Member.class);
        when(member.getFirstName()).thenReturn(" 민준 ");
        when(member.getLastName()).thenReturn("김");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThat(provider.create(1L).userName()).isEqualTo("민준");
    }

    @Test
    void 사용자_생년월일_성별_직업_MBTI를_스냅샷으로_만든다() {
        Member member = mock(Member.class);
        when(member.getBirth()).thenReturn(LocalDate.of(2000, 1, 1));
        when(member.getGender()).thenReturn(Gender.MALE);
        when(member.getJob()).thenReturn(Job.EMPLOYEE);
        when(member.getMbti()).thenReturn(Mbti.INTJ);

        AiRequestContextProvider.Context context = provider.create(member);

        assertThat(context.user().birth()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(context.user().gender()).isEqualTo("MALE");
        assertThat(context.user().job()).isEqualTo("EMPLOYEE");
        assertThat(context.user().mbti()).isEqualTo("INTJ");
    }
}
