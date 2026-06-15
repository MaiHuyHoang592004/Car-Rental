package com.rentflow.vehicledocument.service;

import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.vehicledocument.entity.VehicleDocumentStatus;
import com.rentflow.vehicledocument.entity.VehicleDocumentType;
import com.rentflow.vehicledocument.repository.VehicleDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleComplianceServiceTest {

    private static final UUID VEHICLE_ID = UUID.fromString("33333333-3333-4333-9333-333333333333");
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 7);

    @Mock private VehicleDocumentRepository documentRepository;

    private VehicleComplianceService service;

    @BeforeEach
    void setUp() {
        service = new VehicleComplianceService(
                documentRepository,
                Clock.fixed(Instant.parse("2026-06-07T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void acceptsVehicleWithAllApprovedUnexpiredDocuments() {
        for (VehicleDocumentType type : VehicleDocumentType.values()) {
            when(documentRepository.existsByVehicleIdAndTypeAndStatusAndExpiresAtGreaterThanEqual(
                    VEHICLE_ID, type, VehicleDocumentStatus.APPROVED, TODAY)).thenReturn(true);
        }

        service.assertVehicleCompliant(VEHICLE_ID);
    }

    @Test
    void rejectsMissingRequiredDocument() {
        when(documentRepository.existsByVehicleIdAndTypeAndStatusAndExpiresAtGreaterThanEqual(
                VEHICLE_ID, VehicleDocumentType.REGISTRATION, VehicleDocumentStatus.APPROVED, TODAY)).thenReturn(false);

        assertThatThrownBy(() -> service.assertVehicleCompliant(VEHICLE_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("REGISTRATION");
    }
}
