package com.rentflow.vehicledocument.service;

import com.rentflow.auth.entity.Role;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.ResourceNotFoundException;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.common.web.PageResponse;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.repository.VehicleRepository;
import com.rentflow.vehicledocument.dto.ReviewVehicleDocumentRequest;
import com.rentflow.vehicledocument.dto.SubmitVehicleDocumentRequest;
import com.rentflow.vehicledocument.dto.VehicleDocumentResponse;
import com.rentflow.vehicledocument.entity.VehicleDocument;
import com.rentflow.vehicledocument.entity.VehicleDocumentStatus;
import com.rentflow.vehicledocument.repository.VehicleDocumentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class VehicleDocumentService {

    private final VehicleDocumentRepository documentRepository;
    private final VehicleRepository vehicleRepository;
    private final SecurityContext securityContext;
    private final Clock clock;

    public VehicleDocumentService(
            VehicleDocumentRepository documentRepository,
            VehicleRepository vehicleRepository,
            SecurityContext securityContext,
            Clock clock) {
        this.documentRepository = documentRepository;
        this.vehicleRepository = vehicleRepository;
        this.securityContext = securityContext;
        this.clock = clock;
    }

    @Transactional
    public VehicleDocumentResponse submit(UUID vehicleId, SubmitVehicleDocumentRequest request) {
        UUID hostId = securityContext.currentUserId();
        securityContext.requireRole(Role.HOST);
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("VEHICLE_NOT_FOUND", "Vehicle", vehicleId.toString()));
        if (!vehicle.getHostId().equals(hostId)) {
            throw new ResourceNotFoundException("VEHICLE_NOT_FOUND", "Vehicle", vehicleId.toString());
        }
        if (!request.expiresAt().isAfter(java.time.LocalDate.now(clock))) {
            throw new BusinessRuleException("VEHICLE_DOCUMENT_EXPIRED", "Vehicle document expiry must be in the future");
        }
        VehicleDocument document = new VehicleDocument();
        document.setVehicleId(vehicleId);
        document.setHostId(hostId);
        document.setType(request.type());
        document.setStatus(VehicleDocumentStatus.PENDING_REVIEW);
        document.setFileId(request.fileId());
        document.setDocumentNumber(normalize(request.documentNumber()));
        document.setIssuedAt(request.issuedAt());
        document.setExpiresAt(request.expiresAt());
        document = documentRepository.save(document);
        return VehicleDocumentResponse.from(document);
    }

    @Transactional(readOnly = true)
    public java.util.List<VehicleDocumentResponse> listHostVehicleDocuments(UUID vehicleId) {
        UUID hostId = securityContext.currentUserId();
        securityContext.requireRole(Role.HOST);
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("VEHICLE_NOT_FOUND", "Vehicle", vehicleId.toString()));
        if (!vehicle.getHostId().equals(hostId)) {
            throw new ResourceNotFoundException("VEHICLE_NOT_FOUND", "Vehicle", vehicleId.toString());
        }
        return documentRepository.findByVehicleIdOrderByCreatedAtDesc(vehicleId).stream()
                .map(VehicleDocumentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<VehicleDocumentResponse> listAdmin(VehicleDocumentStatus status, Pageable pageable) {
        securityContext.requireRole(Role.ADMIN);
        Page<VehicleDocument> page = status == null
                ? documentRepository.findAll(pageable)
                : documentRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return PageResponse.from(page, VehicleDocumentResponse::from);
    }

    @Transactional
    public VehicleDocumentResponse approve(UUID documentId) {
        UUID adminId = securityContext.currentUserId();
        securityContext.requireRole(Role.ADMIN);
        VehicleDocument document = documentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("VEHICLE_DOCUMENT_NOT_FOUND", "VehicleDocument", documentId.toString()));
        if (document.getExpiresAt().isBefore(java.time.LocalDate.now(clock))) {
            document.setStatus(VehicleDocumentStatus.EXPIRED);
            documentRepository.save(document);
            throw new BusinessRuleException("VEHICLE_DOCUMENT_EXPIRED", "Expired vehicle documents cannot be approved");
        }
        document.setStatus(VehicleDocumentStatus.APPROVED);
        document.setReviewedBy(adminId);
        document.setReviewedAt(clock.instant());
        document.setRejectionReason(null);
        return VehicleDocumentResponse.from(documentRepository.save(document));
    }

    @Transactional
    public VehicleDocumentResponse reject(UUID documentId, ReviewVehicleDocumentRequest request) {
        UUID adminId = securityContext.currentUserId();
        securityContext.requireRole(Role.ADMIN);
        VehicleDocument document = documentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("VEHICLE_DOCUMENT_NOT_FOUND", "VehicleDocument", documentId.toString()));
        document.setStatus(VehicleDocumentStatus.REJECTED);
        document.setReviewedBy(adminId);
        document.setReviewedAt(clock.instant());
        document.setRejectionReason(normalize(request == null ? null : request.reason()));
        return VehicleDocumentResponse.from(documentRepository.save(document));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
