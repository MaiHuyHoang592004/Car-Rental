package com.rentflow.vehicledocument.service;

import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.vehicledocument.entity.VehicleDocumentStatus;
import com.rentflow.vehicledocument.entity.VehicleDocumentType;
import com.rentflow.vehicledocument.repository.VehicleDocumentRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class VehicleComplianceService {

    private final VehicleDocumentRepository documentRepository;
    private final Clock clock;

    public VehicleComplianceService(VehicleDocumentRepository documentRepository, Clock clock) {
        this.documentRepository = documentRepository;
        this.clock = clock;
    }

    public void assertVehicleCompliant(UUID vehicleId) {
        LocalDate today = LocalDate.now(clock);
        for (VehicleDocumentType type : VehicleDocumentType.values()) {
            boolean approved = documentRepository.existsByVehicleIdAndTypeAndStatusAndExpiresAtGreaterThanEqual(
                    vehicleId,
                    type,
                    VehicleDocumentStatus.APPROVED,
                    today);
            if (!approved) {
                throw new BusinessRuleException(
                        "VEHICLE_COMPLIANCE_DOCUMENT_REQUIRED",
                        "Vehicle requires approved, unexpired " + type + " document before listing approval");
            }
        }
    }
}
