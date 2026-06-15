package com.rentflow.outbox.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.outbox.config.OutboxKafkaProperties;
import com.rentflow.outbox.entity.OutboxEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxKafkaRecordFactoryTest {

    private ObjectMapper objectMapper;
    private OutboxKafkaRecordFactory factory;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        OutboxKafkaProperties properties = new OutboxKafkaProperties();
        properties.setTopic("rentflow.outbox.v1");
        factory = new OutboxKafkaRecordFactory(
                objectMapper,
                properties,
                Clock.fixed(Instant.parse("2026-06-07T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void createRecordBuildsEnvelopeKeyAndHeaders() throws Exception {
        UUID eventId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID aggregateId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        OutboxEvent event = event(eventId, aggregateId);

        ProducerRecord<String, String> record = factory.createRecord(event);

        assertThat(record.topic()).isEqualTo("rentflow.outbox.v1");
        assertThat(record.key()).isEqualTo(aggregateId.toString());
        assertThat(header(record, OutboxKafkaRecordFactory.EVENT_ID_HEADER)).isEqualTo(eventId.toString());
        assertThat(header(record, OutboxKafkaRecordFactory.EVENT_TYPE_HEADER)).isEqualTo("BOOKING_HELD");
        assertThat(header(record, OutboxKafkaRecordFactory.AGGREGATE_TYPE_HEADER)).isEqualTo("BOOKING");
        assertThat(header(record, OutboxKafkaRecordFactory.SCHEMA_VERSION_HEADER)).isEqualTo("1");
        assertThat(header(record, OutboxKafkaRecordFactory.CONTENT_TYPE_HEADER)).isEqualTo("application/json");

        JsonNode value = objectMapper.readTree(record.value());
        assertThat(value.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(value.path("eventId").asText()).isEqualTo(eventId.toString());
        assertThat(value.path("aggregateType").asText()).isEqualTo("BOOKING");
        assertThat(value.path("aggregateId").asText()).isEqualTo(aggregateId.toString());
        assertThat(value.path("eventType").asText()).isEqualTo("BOOKING_HELD");
        assertThat(value.path("occurredAt").asText()).isEqualTo("2026-06-06T23:59:00Z");
        assertThat(value.path("payload").path("bookingId").asText()).isEqualTo("bk-1");
    }

    @Test
    void keyFallsBackToEventIdWhenAggregateIdIsMissing() {
        UUID eventId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        OutboxEvent event = event(eventId, null);

        assertThat(factory.keyFor(event)).isEqualTo(eventId.toString());
    }

    @Test
    void nullPayloadIsSerializedAsNull() throws Exception {
        OutboxEvent event = event(UUID.randomUUID(), UUID.randomUUID());
        event.setPayload(null);

        JsonNode value = objectMapper.readTree(factory.createRecord(event).value());

        assertThat(value.get("payload").isNull()).isTrue();
    }

    private OutboxEvent event(UUID eventId, UUID aggregateId) {
        OutboxEvent event = new OutboxEvent();
        event.setId(eventId);
        event.setAggregateType("BOOKING");
        event.setAggregateId(aggregateId);
        event.setEventType("BOOKING_HELD");
        event.setPayload("{\"bookingId\":\"bk-1\"}");
        event.setCreatedAt(Instant.parse("2026-06-06T23:59:00Z"));
        return event;
    }

    private String header(ProducerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
