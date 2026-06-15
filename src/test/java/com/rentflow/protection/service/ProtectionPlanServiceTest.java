package com.rentflow.protection.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.common.exception.ValidationException;
import com.rentflow.listing.entity.PricingType;
import com.rentflow.protection.entity.BookingProtectionSnapshot;
import com.rentflow.protection.entity.ProtectionPlan;
import com.rentflow.protection.repository.BookingProtectionSnapshotRepository;
import com.rentflow.protection.repository.ProtectionPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProtectionPlanServiceTest {

    @Mock private ProtectionPlanRepository planRepository;
    @Mock private BookingProtectionSnapshotRepository snapshotRepository;

    private ProtectionPlanService service;

    @BeforeEach
    void setUp() {
        service = new ProtectionPlanService(
                planRepository,
                snapshotRepository,
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(Instant.parse("2026-06-07T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void selectedPlanIsQuotedWithPerDayFee() {
        ProtectionPlan standard = plan("STANDARD", PricingType.PER_DAY, "75000", "2000000", "15000000");
        when(planRepository.findByCodeIgnoreCaseAndActiveTrue("STANDARD")).thenReturn(Optional.of(standard));

        ProtectionQuote quote = service.quote("STANDARD", 3);

        assertThat(quote.planCode()).isEqualTo("STANDARD");
        assertThat(quote.planFee()).isEqualByComparingTo("225000");
        assertThat(quote.deductibleAmount()).isEqualByComparingTo("2000000");
    }

    @Test
    void defaultBasicPlanUsedWhenRequestOmitsCode() {
        ProtectionPlan basic = plan("BASIC", PricingType.PER_TRIP, "0", "5000000", null);
        when(planRepository.findByCodeIgnoreCaseAndActiveTrue("BASIC")).thenReturn(Optional.of(basic));

        ProtectionQuote quote = service.quote(null, 2);

        assertThat(quote.planCode()).isEqualTo("BASIC");
        assertThat(quote.planFee()).isEqualByComparingTo("0");
    }

    @Test
    void inactiveOrMissingPlanRejected() {
        when(planRepository.findByCodeIgnoreCaseAndActiveTrue("PREMIUM")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.quote("PREMIUM", 2))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void bookingProtectionSnapshotIsPersisted() {
        UUID bookingId = UUID.randomUUID();
        ProtectionQuote quote = new ProtectionQuote(
                UUID.randomUUID(),
                "STANDARD",
                "Standard",
                new BigDecimal("150000"),
                new BigDecimal("2000000"),
                new BigDecimal("15000000"));
        when(snapshotRepository.existsById(bookingId)).thenReturn(false);

        service.snapshot(bookingId, quote);

        verify(snapshotRepository).save(any(BookingProtectionSnapshot.class));
    }

    @Test
    void priceSnapshotCanIncludeProtectionFee() {
        com.rentflow.booking.service.PriceCalculationResult base =
                new com.rentflow.booking.service.PriceCalculationResult(
                        2,
                        new BigDecimal("700000"),
                        new BigDecimal("1400000"),
                        BigDecimal.ZERO,
                        new BigDecimal("1400000"),
                        "VND",
                        java.util.List.of());

        com.rentflow.booking.service.PriceCalculationResult withProtection = base.withProtection(
                "STANDARD",
                new BigDecimal("150000"),
                new BigDecimal("2000000"),
                new BigDecimal("15000000"));

        assertThat(withProtection.protectionFee()).isEqualByComparingTo("150000");
        assertThat(withProtection.totalAmount()).isEqualByComparingTo("1550000");
    }

    private ProtectionPlan plan(
            String code,
            PricingType pricingType,
            String price,
            String deductible,
            String maxCoverage) {
        ProtectionPlan plan = new ProtectionPlan();
        plan.setId(UUID.randomUUID());
        plan.setCode(code);
        plan.setName(code);
        plan.setDescription(code);
        plan.setPriceType(pricingType);
        plan.setPriceAmount(new BigDecimal(price));
        plan.setDeductibleAmount(new BigDecimal(deductible));
        plan.setMaxCoverageAmount(maxCoverage == null ? null : new BigDecimal(maxCoverage));
        plan.setActive(true);
        return plan;
    }
}
