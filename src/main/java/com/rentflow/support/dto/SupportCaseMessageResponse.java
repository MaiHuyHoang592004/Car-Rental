package com.rentflow.support.dto;

import com.rentflow.support.entity.SupportCaseMessage;
import com.rentflow.support.entity.SupportSenderType;

import java.time.Instant;
import java.util.UUID;

public record SupportCaseMessageResponse(
        UUID id,
        UUID supportCaseId,
        UUID senderUserId,
        SupportSenderType senderType,
        String body,
        boolean internalNote,
        Instant createdAt
) {
    public static SupportCaseMessageResponse from(SupportCaseMessage message) {
        return new SupportCaseMessageResponse(
                message.getId(),
                message.getSupportCaseId(),
                message.getSenderUserId(),
                message.getSenderType(),
                message.getBody(),
                message.isInternalNote(),
                message.getCreatedAt());
    }
}
