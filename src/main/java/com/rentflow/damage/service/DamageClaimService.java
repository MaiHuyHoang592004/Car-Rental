package com.rentflow.damage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.audit.service.AuditLogService;
import com.rentflow.auth.entity.Role;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.booking.service.BookingTimelineService;
import com.rentflow.common.exception.BookingNotFoundException;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.ResourceNotFoundException;
import com.rentflow.common.idempotency.service.IdempotencyFailureMarker;
import com.rentflow.common.idempotency.service.IdempotencyResolution;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import com.rentflow.common.idempotency.service.IdempotencyService;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.common.web.PageResponse;
import com.rentflow.damage.dto.CreateDamageClaimRequest;
import com.rentflow.damage.dto.DamageClaimEvidenceRequest;
import com.rentflow.damage.dto.DamageClaimEvidenceResponse;
import com.rentflow.damage.dto.DamageClaimResponse;
import com.rentflow.damage.dto.ResolveDamageClaimRequest;
import com.rentflow.damage.dto.RespondDamageClaimRequest;
import com.rentflow.damage.entity.DamageClaim;
import com.rentflow.damage.entity.DamageClaimEvidence;
import com.rentflow.damage.entity.DamageClaimStatus;
import com.rentflow.damage.repository.DamageClaimEvidenceRepository;
import com.rentflow.damage.repository.DamageClaimRepository;
import com.rentflow.deposit.service.DepositService;
import com.rentflow.file.entity.FileMetadata;
import com.rentflow.file.entity.FileStatus;
import com.rentflow.file.repository.FileMetadataRepository;
import com.rentflow.outbox.service.OutboxService;
import com.rentflow.protection.entity.BookingProtectionSnapshot;
import com.rentflow.protection.repository.BookingProtectionSnapshotRepository;
import com.rentflow.trip.entity.TripRecord;
import com.rentflow.trip.repository.TripRecordRepository;
import com.rentflow.tripcondition.entity.TripConditionReport;
import com.rentflow.tripcondition.entity.TripConditionReportType;
import com.rentflow.tripcondition.repository.TripConditionReportRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DamageClaimService {

    private static final Duration CLAIM_WINDOW = Duration.ofHours(48);
    private static final List<DamageClaimStatus> ADMIN_RESOLVABLE = List.of(
            DamageClaimStatus.OPEN,
            DamageClaimStatus.CUSTOMER_RESPONDED,
            DamageClaimStatus.UNDER_REVIEW);

    private final DamageClaimRepository damageClaimRepository;
    private final DamageClaimEvidenceRepository evidenceRepository;
    private final BookingRepository bookingRepository;
    private final TripRecordRepository tripRecordRepository;
    private final TripConditionReportRepository conditionReportRepository;
    private final FileMetadataRepository fileMetadataRepository;
    private final SecurityContext securityContext;
    private final IdempotencyService idempotencyService;
    private final IdempotencyFailureMarker idempotencyFailureMarker;
    private final BookingTimelineService bookingTimelineService;
    private final AuditLogService auditLogService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private DepositService depositService;
    private BookingProtectionSnapshotRepository protectionSnapshotRepository;

    public DamageClaimService(
            DamageClaimRepository damageClaimRepository,
            DamageClaimEvidenceRepository evidenceRepository,
            BookingRepository bookingRepository,
            TripRecordRepository tripRecordRepository,
            TripConditionReportRepository conditionReportRepository,
            FileMetadataRepository fileMetadataRepository,
            SecurityContext securityContext,
            IdempotencyService idempotencyService,
            IdempotencyFailureMarker idempotencyFailureMarker,
            BookingTimelineService bookingTimelineService,
            AuditLogService auditLogService,
            OutboxService outboxService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.damageClaimRepository = damageClaimRepository;
        this.evidenceRepository = evidenceRepository;
        this.bookingRepository = bookingRepository;
        this.tripRecordRepository = tripRecordRepository;
        this.conditionReportRepository = conditionReportRepository;
        this.fileMetadataRepository = fileMetadataRepository;
        this.securityContext = securityContext;
        this.idempotencyService = idempotencyService;
        this.idempotencyFailureMarker = idempotencyFailureMarker;
        this.bookingTimelineService = bookingTimelineService;
        this.auditLogService = auditLogService;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Autowired(required = false)
    void setDepositService(DepositService depositService) {
        this.depositService = depositService;
    }

    @Autowired(required = false)
    void setProtectionSnapshotRepository(BookingProtectionSnapshotRepository protectionSnapshotRepository) {
        this.protectionSnapshotRepository = protectionSnapshotRepository;
    }

    @Transactional
    public DamageClaimResponse createClaim(UUID bookingId, String idempotencyKey, CreateDamageClaimRequest request) {
        UUID hostId = securityContext.currentUserId();
        securityContext.requireRole(Role.HOST);
        IdempotencyResolution resolution = resolve(hostId, IdempotencyScope.CREATE_DAMAGE_CLAIM, idempotencyKey,
                new ClaimHashInput(bookingId, request));
        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserialize(replay.responseBodyJson());
        }
        UUID idempotencyKeyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                    .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
            if (!booking.getHostId().equals(hostId)) {
                throw new BookingNotFoundException(bookingId.toString());
            }
            if (booking.getStatus() != BookingStatus.COMPLETED) {
                throw new BusinessRuleException("DAMAGE_CLAIM_INVALID_BOOKING_STATUS",
                        "Damage claim requires a COMPLETED booking");
            }
            requireWithinClaimWindow(bookingId);
            UUID reportId = validateCheckOutReport(bookingId, request.checkOutReportId());

            DamageClaim claim = new DamageClaim();
            claim.setBookingId(bookingId);
            claim.setHostId(hostId);
            claim.setCustomerId(booking.getCustomerId());
            claim.setCheckOutReportId(reportId);
            claim.setStatus(DamageClaimStatus.OPEN);
            claim.setClaimAmount(request.claimAmount());
            claim.setCurrency(readCurrency(booking));
            claim.setTitle(request.title().trim());
            claim.setDescription(request.description().trim());
            claim.setSubmittedAt(clock.instant());
            claim = damageClaimRepository.save(claim);

            for (DamageClaimEvidenceRequest evidenceRequest : request.evidence()) {
                validateEvidenceFile(evidenceRequest.fileId(), booking);
                DamageClaimEvidence evidence = new DamageClaimEvidence();
                evidence.setClaimId(claim.getId());
                evidence.setFileId(evidenceRequest.fileId());
                evidence.setEvidenceType(evidenceRequest.evidenceType());
                evidence.setNote(normalize(evidenceRequest.note()));
                evidenceRepository.save(evidence);
            }
            DamageClaimResponse response = toResponse(claim);
            emit(booking, claim, "DAMAGE_CLAIM_CREATED", "SUCCEEDED");
            idempotencyService.complete(idempotencyKeyId, 201, serialize(response));
            return response;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(idempotencyKeyId);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<DamageClaimResponse> listBookingClaims(UUID bookingId, Pageable pageable) {
        UUID actorId = securityContext.currentUserId();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
        if (!booking.getCustomerId().equals(actorId) && !securityContext.hasRole(Role.ADMIN)) {
            throw new BookingNotFoundException(bookingId.toString());
        }
        return PageResponse.from(damageClaimRepository.findByBookingIdOrderByCreatedAtDesc(bookingId, pageable), this::toResponse);
    }

    @Transactional
    public DamageClaimResponse respond(UUID claimId, String idempotencyKey, RespondDamageClaimRequest request) {
        UUID customerId = securityContext.currentUserId();
        IdempotencyResolution resolution = resolve(customerId, IdempotencyScope.RESPOND_DAMAGE_CLAIM, idempotencyKey,
                new ClaimHashInput(claimId, request));
        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserialize(replay.responseBodyJson());
        }
        UUID idempotencyKeyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            DamageClaim claim = damageClaimRepository.findByIdForUpdate(claimId)
                    .orElseThrow(() -> new ResourceNotFoundException("DAMAGE_CLAIM_NOT_FOUND", "DamageClaim", claimId.toString()));
            if (!claim.getCustomerId().equals(customerId)) {
                throw new ResourceNotFoundException("DAMAGE_CLAIM_NOT_FOUND", "DamageClaim", claimId.toString());
            }
            if (claim.getStatus() != DamageClaimStatus.OPEN) {
                throw new BusinessRuleException("DAMAGE_CLAIM_INVALID_STATUS",
                        "Customer can respond only to an OPEN damage claim");
            }
            claim.setCustomerResponse(request.response().trim());
            claim.setCustomerRespondedAt(clock.instant());
            claim.setStatus(DamageClaimStatus.CUSTOMER_RESPONDED);
            claim = damageClaimRepository.save(claim);
            Booking booking = requireBooking(claim.getBookingId());
            DamageClaimResponse response = toResponse(claim);
            emit(booking, claim, "DAMAGE_CLAIM_CUSTOMER_RESPONDED", "SUCCEEDED");
            idempotencyService.complete(idempotencyKeyId, 200, serialize(response));
            return response;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(idempotencyKeyId);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<DamageClaimResponse> listHostClaims(DamageClaimStatus status, Pageable pageable) {
        UUID hostId = securityContext.currentUserId();
        securityContext.requireRole(Role.HOST);
        Page<DamageClaim> page = status == null
                ? damageClaimRepository.findByHostIdOrderByCreatedAtDesc(hostId, pageable)
                : damageClaimRepository.findByHostIdAndStatusOrderByCreatedAtDesc(hostId, status, pageable);
        return PageResponse.from(page, this::toResponse);
    }

    @Transactional(readOnly = true)
    public DamageClaimResponse getHostClaim(UUID claimId) {
        UUID hostId = securityContext.currentUserId();
        securityContext.requireRole(Role.HOST);
        DamageClaim claim = damageClaimRepository.findByIdAndHostId(claimId, hostId)
                .orElseThrow(() -> new ResourceNotFoundException("DAMAGE_CLAIM_NOT_FOUND", "DamageClaim", claimId.toString()));
        return toResponse(claim);
    }

    @Transactional(readOnly = true)
    public PageResponse<DamageClaimResponse> listAdminClaims(DamageClaimStatus status, Pageable pageable) {
        securityContext.requireRole(Role.ADMIN);
        Page<DamageClaim> page = status == null
                ? damageClaimRepository.findAll(pageable)
                : damageClaimRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return PageResponse.from(page, this::toResponse);
    }

    @Transactional(readOnly = true)
    public DamageClaimResponse getAdminClaim(UUID claimId) {
        securityContext.requireRole(Role.ADMIN);
        DamageClaim claim = damageClaimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("DAMAGE_CLAIM_NOT_FOUND", "DamageClaim", claimId.toString()));
        return toResponse(claim);
    }

    @Transactional
    public DamageClaimResponse approve(UUID claimId, String idempotencyKey, ResolveDamageClaimRequest request) {
        return resolveAdmin(claimId, idempotencyKey, request, true);
    }

    @Transactional
    public DamageClaimResponse reject(UUID claimId, String idempotencyKey, ResolveDamageClaimRequest request) {
        return resolveAdmin(claimId, idempotencyKey, request, false);
    }

    @Transactional
    public DamageClaimResponse charge(UUID claimId, String idempotencyKey) {
        UUID adminId = securityContext.currentUserId();
        securityContext.requireRole(Role.ADMIN);
        IdempotencyResolution resolution = resolve(adminId, IdempotencyScope.CHARGE_DAMAGE_CLAIM, idempotencyKey, claimId);
        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserialize(replay.responseBodyJson());
        }
        UUID idempotencyKeyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            DamageClaim claim = damageClaimRepository.findByIdForUpdate(claimId)
                    .orElseThrow(() -> new ResourceNotFoundException("DAMAGE_CLAIM_NOT_FOUND", "DamageClaim", claimId.toString()));
            if (claim.getStatus() != DamageClaimStatus.APPROVED
                    && claim.getStatus() != DamageClaimStatus.PARTIALLY_APPROVED) {
                throw new BusinessRuleException("DAMAGE_CLAIM_INVALID_STATUS",
                        "Only approved damage claims can be charged");
            }
            claim.setStatus(DamageClaimStatus.CHARGED);
            claim = damageClaimRepository.save(claim);
            if (depositService != null) {
                depositService.deductForDamageClaim(claim);
            }
            Booking booking = requireBooking(claim.getBookingId());
            DamageClaimResponse response = toResponse(claim);
            emit(booking, claim, "DAMAGE_CLAIM_CHARGED", "SUCCEEDED");
            idempotencyService.complete(idempotencyKeyId, 200, serialize(response));
            return response;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(idempotencyKeyId);
            throw e;
        }
    }

    private DamageClaimResponse resolveAdmin(
            UUID claimId,
            String idempotencyKey,
            ResolveDamageClaimRequest request,
            boolean approved) {
        UUID adminId = securityContext.currentUserId();
        securityContext.requireRole(Role.ADMIN);
        IdempotencyScope scope = approved ? IdempotencyScope.APPROVE_DAMAGE_CLAIM : IdempotencyScope.REJECT_DAMAGE_CLAIM;
        IdempotencyResolution resolution = resolve(adminId, scope, idempotencyKey, new ClaimHashInput(claimId, request));
        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserialize(replay.responseBodyJson());
        }
        UUID idempotencyKeyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            DamageClaim claim = damageClaimRepository.findByIdForUpdate(claimId)
                    .orElseThrow(() -> new ResourceNotFoundException("DAMAGE_CLAIM_NOT_FOUND", "DamageClaim", claimId.toString()));
            if (!ADMIN_RESOLVABLE.contains(claim.getStatus())) {
                throw new BusinessRuleException("DAMAGE_CLAIM_INVALID_STATUS",
                        "Damage claim cannot be resolved from its current status");
            }
            if (approved) {
                BigDecimal approvedAmount = request.approvedAmount() == null
                        ? claim.getClaimAmount()
                        : request.approvedAmount();
                if (approvedAmount.compareTo(BigDecimal.ZERO) <= 0
                        || approvedAmount.compareTo(claim.getClaimAmount()) > 0) {
                    throw new BusinessRuleException("DAMAGE_CLAIM_INVALID_AMOUNT",
                            "Approved amount must be > 0 and <= claim amount");
                }
                enforceProtectionLiabilitySnapshot(claim, approvedAmount);
                claim.setApprovedAmount(approvedAmount);
                claim.setStatus(approvedAmount.compareTo(claim.getClaimAmount()) == 0
                        ? DamageClaimStatus.APPROVED
                        : DamageClaimStatus.PARTIALLY_APPROVED);
                claim.setAdminResolutionNote(normalize(request.note()));
                claim.setResolvedAt(clock.instant());
            } else {
                claim.setApprovedAmount(null);
                claim.setStatus(DamageClaimStatus.REJECTED);
                claim.setAdminResolutionNote(normalize(request.note()));
                claim.setResolvedAt(clock.instant());
            }
            claim = damageClaimRepository.save(claim);
            Booking booking = requireBooking(claim.getBookingId());
            DamageClaimResponse response = toResponse(claim);
            emit(booking, claim, approved ? "DAMAGE_CLAIM_APPROVED" : "DAMAGE_CLAIM_REJECTED", "SUCCEEDED");
            idempotencyService.complete(idempotencyKeyId, 200, serialize(response));
            return response;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(idempotencyKeyId);
            throw e;
        }
    }

    private IdempotencyResolution resolve(UUID userId, IdempotencyScope scope, String key, Object request) {
        return idempotencyService.resolve(userId, scope, key, idempotencyService.computeHash(request));
    }

    private void requireWithinClaimWindow(UUID bookingId) {
        TripRecord tripRecord = tripRecordRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new BusinessRuleException("TRIP_RECORD_NOT_FOUND", "Trip record is required for damage claim"));
        if (tripRecord.getCheckOutAt() == null) {
            throw new BusinessRuleException("TRIP_NOT_CHECKED_OUT", "Damage claim requires check-out");
        }
        if (clock.instant().isAfter(tripRecord.getCheckOutAt().plus(CLAIM_WINDOW))) {
            throw new BusinessRuleException("DAMAGE_CLAIM_WINDOW_CLOSED", "Damage claim window is closed");
        }
    }

    private UUID validateCheckOutReport(UUID bookingId, UUID reportId) {
        if (reportId == null) {
            return null;
        }
        TripConditionReport report = conditionReportRepository.findByIdAndBookingId(reportId, bookingId)
                .orElseThrow(() -> new BusinessRuleException("TRIP_CONDITION_REPORT_NOT_FOUND",
                        "Check-out condition report not found for booking"));
        if (report.getReportType() != TripConditionReportType.CHECK_OUT) {
            throw new BusinessRuleException("TRIP_CONDITION_REPORT_INVALID_TYPE",
                    "Damage claim report reference must be CHECK_OUT");
        }
        return report.getId();
    }

    private void validateEvidenceFile(UUID fileId, Booking booking) {
        FileMetadata file = fileMetadataRepository.findByIdAndStatus(fileId, FileStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("FILE_NOT_FOUND", "File", fileId.toString()));
        if (!file.getOwnerUserId().equals(booking.getHostId()) && !file.getOwnerUserId().equals(booking.getCustomerId())) {
            throw new BusinessRuleException("DAMAGE_CLAIM_EVIDENCE_INVALID", "Evidence file is not attached to a booking participant");
        }
    }

    private void enforceProtectionLiabilitySnapshot(DamageClaim claim, BigDecimal approvedAmount) {
        if (protectionSnapshotRepository == null) {
            return;
        }
        BookingProtectionSnapshot snapshot = protectionSnapshotRepository.findById(claim.getBookingId()).orElse(null);
        if (snapshot == null || snapshot.getDeductibleAmount() == null) {
            return;
        }
        if (approvedAmount.compareTo(snapshot.getDeductibleAmount()) > 0) {
            throw new BusinessRuleException(
                    "DAMAGE_CLAIM_LIABILITY_EXCEEDED",
                    "Approved damage amount exceeds booking protection deductible snapshot");
        }
    }

    private Booking requireBooking(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
    }

    private DamageClaimResponse toResponse(DamageClaim claim) {
        List<DamageClaimEvidenceResponse> evidence = evidenceRepository.findByClaimIdOrderByCreatedAtAsc(claim.getId()).stream()
                .map(DamageClaimEvidenceResponse::from)
                .toList();
        return DamageClaimResponse.from(claim, evidence);
    }

    private void emit(Booking booking, DamageClaim claim, String eventType, String status) {
        UUID actorId = securityContext.currentUserId();
        String actorType = actorType(actorId, booking);
        String details = serialize(Map.of(
                "claimId", claim.getId(),
                "bookingId", claim.getBookingId(),
                "status", claim.getStatus().name(),
                "claimAmount", claim.getClaimAmount(),
                "approvedAmount", claim.getApprovedAmount() == null ? "" : claim.getApprovedAmount(),
                "currency", claim.getCurrency()));
        bookingTimelineService.append(booking.getId(), eventType, actorId, actorType, details);
        auditLogService.record(actorId, actorType, eventType, "DAMAGE_CLAIM", claim.getId(), status, details);
        outboxService.append("DAMAGE_CLAIM", claim.getId(), eventType, details);
    }

    private String actorType(UUID actorId, Booking booking) {
        if (securityContext.hasRole(Role.ADMIN)) {
            return "ADMIN";
        }
        if (booking.getHostId().equals(actorId)) {
            return "HOST";
        }
        return "CUSTOMER";
    }

    private String readCurrency(Booking booking) {
        try {
            return objectMapper.readTree(booking.getPriceSnapshot()).path("currency").asText("VND");
        } catch (JsonProcessingException e) {
            return "VND";
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize damage claim JSON", e);
        }
    }

    private DamageClaimResponse deserialize(String responseJson) {
        try {
            return objectMapper.readValue(responseJson, DamageClaimResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to deserialize damage claim idempotency response", e);
        }
    }

    private record ClaimHashInput(Object id, Object request) {
    }
}
