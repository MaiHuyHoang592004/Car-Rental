package com.rentflow.support.repository;

import com.rentflow.support.entity.SupportCaseMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupportCaseMessageRepository extends JpaRepository<SupportCaseMessage, UUID> {

    List<SupportCaseMessage> findBySupportCaseIdOrderByCreatedAtAsc(UUID supportCaseId);

    List<SupportCaseMessage> findBySupportCaseIdAndInternalNoteFalseOrderByCreatedAtAsc(UUID supportCaseId);
}
