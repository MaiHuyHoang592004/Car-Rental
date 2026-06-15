package com.rentflow.scheduler;

import com.rentflow.bookingmod.service.BookingModificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LateReturnFeeJob {

    private final BookingModificationService bookingModificationService;
    private final boolean enabled;
    private final int batchSize;

    public LateReturnFeeJob(
            BookingModificationService bookingModificationService,
            @Value("${rentflow.scheduler.late-return-fees.enabled:true}") boolean enabled,
            @Value("${rentflow.scheduler.late-return-fees.batch-size:100}") int batchSize) {
        this.bookingModificationService = bookingModificationService;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${rentflow.scheduler.late-return-fees.cron:0 15 0 * * *}")
    public void run() {
        if (!enabled) {
            return;
        }
        bookingModificationService.detectLateFees(batchSize);
    }
}
