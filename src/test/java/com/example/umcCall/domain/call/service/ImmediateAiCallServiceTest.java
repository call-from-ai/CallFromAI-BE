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

@ExtendWith(MockitoExtension.class)
class ImmediateAiCallServiceTest {

    private static final long RELATIONSHIP_ID = 10L;

    @Mock
    private RelationshipRepository relationshipRepository;

    @Mock
    private CallRepository callRepository;

    @InjectMocks
    private ImmediateAiCallService immediateAiCallService;

    @Test
    void ring은_예약_없이_AI_RINGING_통화를_즉시_생성한다() {
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
        assertThat(captor.getValue().getCallReservation()).isNull();
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

    private Relationship callableRelationship() {
        Character character = mock(Character.class);
        when(character.getDeletedAt()).thenReturn(null);
        Relationship relationship = mock(Relationship.class);
        when(relationship.isMain()).thenReturn(true);
        when(relationship.getCharacter()).thenReturn(character);
        return relationship;
    }
}
