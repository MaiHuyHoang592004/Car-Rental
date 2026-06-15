package com.rentflow.scheduler;

import com.rentflow.payout.service.HostPayoutService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HostPayoutQueueJob {

    private final HostPayoutService hostPayoutService;
    private final boolean enabled;
    private final int batchSize;

    public HostPayoutQueueJob(
            HostPayoutService hostPayoutService,
            @Value("${rentflow.scheduler.host-payouts.enabled:true}") boolean enabled,
            @Value("${rentflow.scheduler.host-payouts.batch-size:100}") int batchSize) {
        this.hostPayoutService = hostPayoutService;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${rentflow.scheduler.host-payouts.cron:0 30 0 * * *}")
    public void run() {
        if (!enabled) {
            return;
        }
        hostPayoutService.createPayoutQueue(batchSize);
    }
}
