package com.rentflow.protection.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.common.exception.ValidationException;
import com.rentflow.listing.entity.PricingType;
import com.rentflow.protection.dto.ProtectionPlanListResponse;
import com.rentflow.protection.dto.ProtectionPlanResponse;
import com.rentflow.protection.entity.BookingProtectionSnapshot;
import com.rentflow.protection.entity.ProtectionPlan;
import com.rentflow.protection.repository.BookingProtectionSnapshotRepository;
import com.rentflow.protection.repository.ProtectionPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ProtectionPlanService {

    private static final String DEFAULT_PLAN_CODE = "BASIC";

    private final ProtectionPlanRepository protectionPlanRepository;
    private final BookingProtectionSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ProtectionPlanService(
            ProtectionPlanRepository protectionPlanRepository,
            BookingProtectionSnapshotRepository snapshotRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.protectionPlanRepository = protectionPlanRepository;
        this.snapshotRepository = snapshotRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProtectionPlanListResponse listActivePlans() {
        return new ProtectionPlanListResponse(
                protectionPlanRepository.findByActiveTrueOrderByPriceAmountAsc().stream()
                        .map(ProtectionPlanResponse::from)
                        .toList());
    }

    @Transactional(readOnly = true)
    public ProtectionQuote quote(String requestedCode, long rentalDays) {
        String code = requestedCode == null || requestedCode.isBlank()
                ? DEFAULT_PLAN_CODE
                : requestedCode.trim();
        ProtectionPlan plan = protectionPlanRepository.findByCodeIgnoreCaseAndActiveTrue(code)
                .orElseThrow(() -> new ValidationException("Protection plan is not available: " + code));
        BigDecimal fee = plan.getPriceType() == PricingType.PER_DAY
                ? plan.getPriceAmount().multiply(BigDecimal.valueOf(rentalDays))
                : plan.getPriceAmount();
        return new ProtectionQuote(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                fee,
                plan.getDeductibleAmount(),
                plan.getMaxCoverageAmount());
    }

    @Transactional
    public void snapshot(UUID bookingId, ProtectionQuote quote) {
        if (quote == null || snapshotRepository.existsById(bookingId)) {
            return;
        }
        BookingProtectionSnapshot snapshot = new BookingProtectionSnapshot();
        snapshot.setBookingId(bookingId);
        snapshot.setProtectionPlanId(quote.planId());
        snapshot.setPlanCode(quote.planCode());
        snapshot.setPlanName(quote.planName());
        snapshot.setPlanFee(quote.planFee());
        snapshot.setDeductibleAmount(quote.deductibleAmount());
        snapshot.setMaxCoverageAmount(quote.maxCoverageAmount());
        snapshot.setSnapshotJson(toJson(quote));
        snapshot.setCreatedAt(Instant.now(clock));
        snapshotRepository.save(snapshot);
    }

    private String toJson(ProtectionQuote quote) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planId", quote.planId());
        payload.put("planCode", quote.planCode());
        payload.put("planName", quote.planName());
        payload.put("planFee", quote.planFee());
        payload.put("deductibleAmount", quote.deductibleAmount());
        payload.put("maxCoverageAmount", quote.maxCoverageAmount());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize protection snapshot", e);
        }
    }
}
