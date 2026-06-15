package com.rentflow.outbox.service;

import com.rentflow.outbox.config.OutboxKafkaProperties;
import com.rentflow.outbox.entity.OutboxEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@ConditionalOnProperty(name = "rentflow.outbox.dispatcher.type", havingValue = "kafka")
public class KafkaOutboxEventDispatcher implements OutboxEventDispatcher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxKafkaRecordFactory recordFactory;
    private final OutboxKafkaProperties properties;
    private final MeterRegistry meterRegistry;
    private final Counter sentCounter;
    private final Counter failedCounter;
    private final Timer sendLatency;

    public KafkaOutboxEventDispatcher(
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxKafkaRecordFactory recordFactory,
            OutboxKafkaProperties properties,
            MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.recordFactory = recordFactory;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.sentCounter = Counter.builder("rentflow.outbox.kafka.sent")
                .description("Kafka outbox events acknowledged by the broker")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("rentflow.outbox.kafka.failed")
                .description("Kafka outbox dispatch attempts that failed before broker acknowledgement")
                .register(meterRegistry);
        this.sendLatency = Timer.builder("rentflow.outbox.kafka.send.latency")
                .description("Kafka outbox send latency until broker acknowledgement")
                .register(meterRegistry);
    }

    @Override
    public void dispatch(OutboxEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        ProducerRecord<String, String> record = null;
        try {
            record = recordFactory.createRecord(event);
            SendResult<String, String> result = kafkaTemplate.send(record)
                    .get(properties.getSendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            sentCounter.increment();
            sample.stop(sendLatency);
            log.info("Kafka outbox event dispatched: eventId={}, topic={}, key={}, partition={}, offset={}",
                    event.getId(),
                    record.topic(),
                    record.key(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sample.stop(sendLatency);
            throw fail(event, record, e);
        } catch (ExecutionException e) {
            sample.stop(sendLatency);
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw fail(event, record, cause);
        } catch (TimeoutException | RuntimeException e) {
            sample.stop(sendLatency);
            throw fail(event, record, e);
        }
    }

    private IllegalStateException fail(OutboxEvent event, ProducerRecord<String, String> record, Throwable cause) {
        failedCounter.increment();
        String topic = record == null ? properties.getTopic() : record.topic();
        String key = record == null ? null : record.key();
        log.warn("Kafka outbox event dispatch failed: eventId={}, topic={}, key={}, error={}",
                event.getId(),
                topic,
                key,
                cause.getMessage());
        return new IllegalStateException("Kafka outbox dispatch failed for event " + event.getId(), cause);
    }
}
