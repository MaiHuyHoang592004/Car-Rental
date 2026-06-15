package com.rentflow.outbox.service;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record OutboxKafkaEnvelope(
        int schemaVersion,
        UUID eventId,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant occurredAt,
        JsonNode payload) {
}
