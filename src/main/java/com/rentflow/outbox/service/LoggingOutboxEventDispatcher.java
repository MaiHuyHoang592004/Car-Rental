package com.rentflow.outbox.service;

import com.rentflow.outbox.entity.OutboxEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "rentflow.outbox.dispatcher.type", havingValue = "logging", matchIfMissing = true)
public class LoggingOutboxEventDispatcher implements OutboxEventDispatcher {

    @Override
    public void dispatch(OutboxEvent event) {
        // Placeholder publisher for Phase 9.6; can be replaced with Kafka/HTTP integration later.
        log.info("Outbox event dispatched: id={}, type={}, aggregateType={}",
                event.getId(), event.getEventType(), event.getAggregateType());
    }
}
