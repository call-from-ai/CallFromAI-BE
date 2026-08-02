package com.example.umcCall.domain.call.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.umcCall.domain.call.entity.Call;
import com.example.umcCall.domain.call.enums.CallSender;
import com.example.umcCall.domain.call.enums.CallStatus;
import com.example.umcCall.domain.call.event.CallRingingEvent;
import com.example.umcCall.domain.call.repository.CallRepository;
import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ImmediateAiCallServiceTest {

    private static final long RELATIONSHIP_ID = 10L;
    private static final long MEMBER_ID = 20L;
    private static final long CHARACTER_ID = 30L;
    private static final String CHARACTER_NAME = "지호";
    private static final String CHARACTER_IMAGE_URL = "https://cdn.example.com/character/30.png";

    @Mock
    private RelationshipRepository relationshipRepository;

    @Mock
    private CallRepository callRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ImmediateAiCallService immediateAiCallService;

    @Test
    void ring은_AI_RINGING_통화를_즉시_생성한다() {
        Relationship relationship = callableRelationship();
        when(relationshipRepository.findByIdForUpdate(RELATIONSHIP_ID))
                .thenReturn(Optional.of(relationship));
        when(callRepository.existsByRelationshipIdAndStatusIn(
                eq(RELATIONSHIP_ID), eq(CallStatus.ACTIVE))).thenReturn(false);
        when(callRepository.save(any(Call.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean created = immediateAiCallService.ring(RELATIONSHIP_ID);

        assertThat(created).isTrue();
        ArgumentCaptor<Call> captor = ArgumentCaptor.forClass(Call.class);
        verify(callRepository).save(captor.capture());
        assertThat(captor.getValue().getSender()).isEqualTo(CallSender.AI);
        assertThat(captor.getValue().getStatus()).isEqualTo(CallStatus.RINGING);
    }

    @Test
    void ring은_이미_활성_통화가_있으면_새_통화를_만들지_않는다() {
        Relationship relationship = callableRelationship();
        when(relationshipRepository.findByIdForUpdate(RELATIONSHIP_ID))
                .thenReturn(Optional.of(relationship));
        when(callRepository.existsByRelationshipIdAndStatusIn(
                eq(RELATIONSHIP_ID), eq(CallStatus.ACTIVE))).thenReturn(true);

        boolean created = immediateAiCallService.ring(RELATIONSHIP_ID);

        assertThat(created).isFalse();
        verify(callRepository, never()).save(any());
    }

    /** 이 이벤트가 없으면 기기 벨이 울리지 않아 사용자는 폴링 전까지 전화를 모른다. */
    @Test
    void ring은_착신_푸시용_이벤트를_발행한다() {
        Relationship relationship = callableRelationship();
        when(relationship.getMemberId()).thenReturn(MEMBER_ID);
        Character character = relationship.getCharacter();
        when(character.getId()).thenReturn(CHARACTER_ID);
        when(character.getFirstName()).thenReturn(CHARACTER_NAME);
        when(character.getImageUrl()).thenReturn(CHARACTER_IMAGE_URL);
        when(relationshipRepository.findByIdForUpdate(RELATIONSHIP_ID))
                .thenReturn(Optional.of(relationship));
        when(callRepository.existsByRelationshipIdAndStatusIn(
                eq(RELATIONSHIP_ID), eq(CallStatus.ACTIVE))).thenReturn(false);
        when(callRepository.save(any(Call.class))).thenAnswer(invocation -> invocation.getArgument(0));

        immediateAiCallService.ring(RELATIONSHIP_ID);

        ArgumentCaptor<CallRingingEvent> captor = ArgumentCaptor.forClass(CallRingingEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        CallRingingEvent event = captor.getValue();
        assertThat(event.relationshipId()).isEqualTo(RELATIONSHIP_ID);
        assertThat(event.memberId()).isEqualTo(MEMBER_ID);
        assertThat(event.characterId()).isEqualTo(CHARACTER_ID);
        assertThat(event.characterName()).isEqualTo(CHARACTER_NAME);
        assertThat(event.characterImageUrl()).isEqualTo(CHARACTER_IMAGE_URL);
    }

    /** 통화가 없는데 벨만 울리면 사용자가 받을 대상이 없다. */
    @Test
    void ring은_통화를_만들지_못하면_이벤트를_발행하지_않는다() {
        Relationship relationship = callableRelationship();
        when(relationshipRepository.findByIdForUpdate(RELATIONSHIP_ID))
                .thenReturn(Optional.of(relationship));
        when(callRepository.existsByRelationshipIdAndStatusIn(
                eq(RELATIONSHIP_ID), eq(CallStatus.ACTIVE))).thenReturn(true);

        immediateAiCallService.ring(RELATIONSHIP_ID);

        verify(eventPublisher, never()).publishEvent(any(CallRingingEvent.class));
    }

    private Relationship callableRelationship() {
        Character character = mock(Character.class);
        when(character.getDeletedAt()).thenReturn(null);
        Relationship relationship = mock(Relationship.class);
        when(relationship.isMain()).thenReturn(true);
        when(relationship.getCharacter()).thenReturn(character);
        return relationship;
    }
}
