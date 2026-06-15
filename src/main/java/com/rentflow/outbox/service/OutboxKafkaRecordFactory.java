package com.rentflow.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.outbox.config.OutboxKafkaProperties;
import com.rentflow.outbox.entity.OutboxEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

@Component
public class OutboxKafkaRecordFactory {

    static final int SCHEMA_VERSION = 1;
    static final String EVENT_ID_HEADER = "rentflow-event-id";
    static final String EVENT_TYPE_HEADER = "rentflow-event-type";
    static final String AGGREGATE_TYPE_HEADER = "rentflow-aggregate-type";
    static final String SCHEMA_VERSION_HEADER = "rentflow-schema-version";
    static final String CONTENT_TYPE_HEADER = "content-type";
    static final String JSON_CONTENT_TYPE = "application/json";

    private final ObjectMapper objectMapper;
    private final OutboxKafkaProperties properties;
    private final Clock clock;

    public OutboxKafkaRecordFactory(
            ObjectMapper objectMapper,
            OutboxKafkaProperties properties,
            Clock clock) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    public ProducerRecord<String, String> createRecord(OutboxEvent event) {
        String key = keyFor(event);
        String value = toJson(envelopeFor(event));
        ProducerRecord<String, String> record = new ProducerRecord<>(properties.getTopic(), key, value);
        addHeader(record, EVENT_ID_HEADER, String.valueOf(event.getId()));
        addHeader(record, EVENT_TYPE_HEADER, event.getEventType());
        addHeader(record, AGGREGATE_TYPE_HEADER, event.getAggregateType());
        addHeader(record, SCHEMA_VERSION_HEADER, String.valueOf(SCHEMA_VERSION));
        addHeader(record, CONTENT_TYPE_HEADER, JSON_CONTENT_TYPE);
        return record;
    }

    public String keyFor(OutboxEvent event) {
        return event.getAggregateId() == null
                ? String.valueOf(event.getId())
                : String.valueOf(event.getAggregateId());
    }

    OutboxKafkaEnvelope envelopeFor(OutboxEvent event) {
        return new OutboxKafkaEnvelope(
                SCHEMA_VERSION,
                event.getId(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getEventType(),
                occurredAt(event),
                parsePayload(event));
    }

    private Instant occurredAt(OutboxEvent event) {
        return event.getCreatedAt() == null ? clock.instant() : event.getCreatedAt();
    }

    private JsonNode parsePayload(OutboxEvent event) {
        String payload = event.getPayload();
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox payload is not valid JSON for event " + event.getId(), e);
        }
    }

    private String toJson(OutboxKafkaEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize outbox Kafka envelope " + envelope.eventId(), e);
        }
    }

    private void addHeader(ProducerRecord<String, String> record, String name, String value) {
        if (value != null) {
            record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
