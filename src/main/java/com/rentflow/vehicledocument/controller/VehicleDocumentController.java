package com.rentflow.vehicledocument.controller;

import com.rentflow.common.exception.ValidationException;
import com.rentflow.common.web.PageResponse;
import com.rentflow.common.web.PageableValidation;
import com.rentflow.vehicledocument.dto.ReviewVehicleDocumentRequest;
import com.rentflow.vehicledocument.dto.SubmitVehicleDocumentRequest;
import com.rentflow.vehicledocument.dto.VehicleDocumentResponse;
import com.rentflow.vehicledocument.entity.VehicleDocumentStatus;
import com.rentflow.vehicledocument.service.VehicleDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class VehicleDocumentController {

    private final VehicleDocumentService vehicleDocumentService;

    @PostMapping("/api/v1/host/vehicles/{vehicleId}/documents")
    public ResponseEntity<VehicleDocumentResponse> submit(
            @PathVariable UUID vehicleId,
            @Valid @RequestBody SubmitVehicleDocumentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vehicleDocumentService.submit(vehicleId, request));
    }

    @GetMapping("/api/v1/host/vehicles/{vehicleId}/documents")
    public ResponseEntity<List<VehicleDocumentResponse>> listHostVehicleDocuments(@PathVariable UUID vehicleId) {
        return ResponseEntity.ok(vehicleDocumentService.listHostVehicleDocuments(vehicleId));
    }

    @GetMapping("/api/v1/admin/vehicle-documents")
    public ResponseEntity<PageResponse<VehicleDocumentResponse>> listAdmin(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableValidation.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(vehicleDocumentService.listAdmin(parseStatus(status), pageable));
    }

    @PostMapping("/api/v1/admin/vehicle-documents/{documentId}/approve")
    public ResponseEntity<VehicleDocumentResponse> approve(@PathVariable UUID documentId) {
        return ResponseEntity.ok(vehicleDocumentService.approve(documentId));
    }

    @PostMapping("/api/v1/admin/vehicle-documents/{documentId}/reject")
    public ResponseEntity<VehicleDocumentResponse> reject(
            @PathVariable UUID documentId,
            @Valid @RequestBody(required = false) ReviewVehicleDocumentRequest request) {
        return ResponseEntity.ok(vehicleDocumentService.reject(documentId, request));
    }

    private VehicleDocumentStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return VehicleDocumentStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid vehicle document status: " + status);
        }
    }
}
