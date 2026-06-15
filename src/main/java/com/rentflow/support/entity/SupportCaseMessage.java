package com.rentflow.support.entity;

import com.rentflow.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "support_case_messages")
@Getter
@Setter
public class SupportCaseMessage extends BaseEntity {

    @Column(name = "support_case_id", nullable = false)
    private UUID supportCaseId;

    @Column(name = "sender_user_id")
    private UUID senderUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 20)
    private SupportSenderType senderType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "internal_note", nullable = false)
    private boolean internalNote;
}
