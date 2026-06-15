package com.rentflow.vehicledocument.service;

import com.rentflow.auth.entity.Role;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.repository.VehicleRepository;
import com.rentflow.vehicledocument.dto.ReviewVehicleDocumentRequest;
import com.rentflow.vehicledocument.dto.SubmitVehicleDocumentRequest;
import com.rentflow.vehicledocument.dto.VehicleDocumentResponse;
import com.rentflow.vehicledocument.entity.VehicleDocument;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleDocumentServiceTest {

    private static final UUID VEHICLE_ID = UUID.fromString("33333333-3333-4333-9333-333333333333");
    private static final UUID HOST_ID = UUID.fromString("22222222-2222-4222-9222-222222222222");
    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa");
    private static final UUID DOCUMENT_ID = UUID.fromString("44444444-4444-4444-9444-444444444444");
    private static final UUID FILE_ID = UUID.fromString("55555555-5555-4555-9555-555555555555");

    @Mock private VehicleDocumentRepository documentRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private SecurityContext securityContext;

    private VehicleDocumentService service;

    @BeforeEach
    void setUp() {
        service = new VehicleDocumentService(
                documentRepository,
                vehicleRepository,
                securityContext,
                Clock.fixed(Instant.parse("2026-06-07T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void hostSubmitsVehicleDocument() {
        when(securityContext.currentUserId()).thenReturn(HOST_ID);
        when(vehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle()));
        when(documentRepository.save(any(VehicleDocument.class))).thenAnswer(invocation -> {
            VehicleDocument document = invocation.getArgument(0);
            document.setId(DOCUMENT_ID);
            return document;
        });

        VehicleDocumentResponse response = service.submit(VEHICLE_ID, submitRequest(LocalDate.of(2027, 1, 1)));

        verify(securityContext).requireRole(Role.HOST);
        assertThat(response.status()).isEqualTo(VehicleDocumentStatus.PENDING_REVIEW);
        assertThat(response.fileId()).isEqualTo(FILE_ID);
    }

    @Test
    void rejectsExpiredSubmit() {
        when(securityContext.currentUserId()).thenReturn(HOST_ID);
        when(vehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle()));

        assertThatThrownBy(() -> service.submit(VEHICLE_ID, submitRequest(LocalDate.of(2026, 1, 1))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("expiry");
    }

    @Test
    void adminApprovesDocument() {
        VehicleDocument document = document(LocalDate.of(2027, 1, 1));
        when(securityContext.currentUserId()).thenReturn(ADMIN_ID);
        when(documentRepository.findByIdForUpdate(DOCUMENT_ID)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(VehicleDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VehicleDocumentResponse response = service.approve(DOCUMENT_ID);

        verify(securityContext).requireRole(Role.ADMIN);
        assertThat(response.status()).isEqualTo(VehicleDocumentStatus.APPROVED);
        assertThat(response.reviewedBy()).isEqualTo(ADMIN_ID);
    }

    @Test
    void adminRejectsDocument() {
        VehicleDocument document = document(LocalDate.of(2027, 1, 1));
        when(securityContext.currentUserId()).thenReturn(ADMIN_ID);
        when(documentRepository.findByIdForUpdate(DOCUMENT_ID)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(VehicleDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VehicleDocumentResponse response = service.reject(DOCUMENT_ID, new ReviewVehicleDocumentRequest("Blurred"));

        assertThat(response.status()).isEqualTo(VehicleDocumentStatus.REJECTED);
        assertThat(response.rejectionReason()).isEqualTo("Blurred");
    }

    private SubmitVehicleDocumentRequest submitRequest(LocalDate expiresAt) {
        return new SubmitVehicleDocumentRequest(
                VehicleDocumentType.REGISTRATION,
                FILE_ID,
                "REG-1",
                LocalDate.of(2025, 1, 1),
                expiresAt);
    }

    private Vehicle vehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(VEHICLE_ID);
        vehicle.setHostId(HOST_ID);
        return vehicle;
    }

    private VehicleDocument document(LocalDate expiresAt) {
        VehicleDocument document = new VehicleDocument();
        document.setId(DOCUMENT_ID);
        document.setVehicleId(VEHICLE_ID);
        document.setHostId(HOST_ID);
        document.setType(VehicleDocumentType.REGISTRATION);
        document.setStatus(VehicleDocumentStatus.PENDING_REVIEW);
        document.setFileId(FILE_ID);
        document.setExpiresAt(expiresAt);
        return document;
    }
}
