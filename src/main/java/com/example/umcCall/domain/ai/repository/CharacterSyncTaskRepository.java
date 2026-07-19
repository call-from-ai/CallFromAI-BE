package com.example.umcCall.domain.ai.repository;

import com.example.umcCall.domain.ai.entity.CharacterSyncTask;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CharacterSyncTaskRepository extends JpaRepository<CharacterSyncTask, Long> {
    List<CharacterSyncTask> findTop50ByCompletedAtIsNullAndNextAttemptAtLessThanEqualOrderById(
            LocalDateTime now);
}
