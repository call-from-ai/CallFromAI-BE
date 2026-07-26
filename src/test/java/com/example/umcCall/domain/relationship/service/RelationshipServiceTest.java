package com.example.umcCall.domain.relationship.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.umcCall.domain.ai.enums.CharacterSyncOperation;
import com.example.umcCall.domain.ai.service.CharacterSyncTaskService;
import com.example.umcCall.domain.character.entity.Character;
import com.example.umcCall.domain.character.enums.Job;
import com.example.umcCall.domain.character.enums.PreferTime;
import com.example.umcCall.domain.relationship.entity.Relationship;
import com.example.umcCall.domain.relationship.repository.RelationshipRepository;
import com.example.umcCall.domain.relationship.repository.RelationshipStatusRepository;
import com.example.umcCall.domain.proactive.service.ProactiveScheduleCoordinator;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RelationshipServiceTest {

    @Mock
    private RelationshipRepository relationshipRepository;

    @Mock
    private RelationshipStatusRepository relationshipStatusRepository;

    @Mock
    private CharacterSyncTaskService characterSyncTaskService;

    @Mock
    private ProactiveScheduleCoordinator proactiveScheduleCoordinator;

    @InjectMocks
    private RelationshipService relationshipService;

    @Test
    void updatesPreferenceAndEnqueuesAiSnapshotSync() {
        Long memberId = 1L;
        Character character = Character.builder()
                .firstName("수현")
                .job(Job.EMPLOYED)
                .preferTime(PreferTime.MORNING)
                .build();
        ReflectionTestUtils.setField(character, "id", 10L);
        Relationship relationship = Relationship.builder()
                .memberId(memberId)
                .character(character)
                .main(true)
                .build();

        when(relationshipRepository.findByMemberIdAndMainTrueAndCharacterDeletedAtIsNull(memberId))
                .thenReturn(Optional.of(relationship));

        var response = relationshipService.updateContactPreference(memberId, PreferTime.LATE_EVENING);

        assertThat(response.preferTime()).isEqualTo(PreferTime.LATE_EVENING);
        assertThat(character.getPreferTime()).isEqualTo(PreferTime.LATE_EVENING);
        verify(proactiveScheduleCoordinator).reschedule(relationship);
        verify(characterSyncTaskService).enqueue(10L, CharacterSyncOperation.UPSERT);
    }
}
