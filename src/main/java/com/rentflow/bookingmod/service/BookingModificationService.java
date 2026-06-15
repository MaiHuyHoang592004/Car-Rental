package com.rentflow.bookingmod.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.audit.service.AuditLogService;
import com.rentflow.auth.entity.Role;
import com.rentflow.availability.entity.AvailabilityCalendar;
import com.rentflow.availability.entity.AvailabilityStatus;
import com.rentflow.availability.repository.AvailabilityCalendarRepository;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.booking.service.BookingTimelineService;
import com.rentflow.bookingmod.dto.BookingModificationResponse;
import com.rentflow.bookingmod.dto.CreateModificationRequest;
import com.rentflow.bookingmod.dto.LateReturnFeeResponse;
import com.rentflow.bookingmod.dto.ModificationDecisionRequest;
import com.rentflow.bookingmod.dto.ModificationPreviewResponse;
import com.rentflow.bookingmod.dto.WaiveLateReturnFeeRequest;
import com.rentflow.bookingmod.entity.BookingModificationRequest;
import com.rentflow.bookingmod.entity.BookingModificationStatus;
import com.rentflow.bookingmod.entity.BookingModificationType;
import com.rentflow.bookingmod.entity.LateReturnFee;
import com.rentflow.bookingmod.entity.LateReturnFeeStatus;
import com.rentflow.bookingmod.repository.BookingModificationRequestRepository;
import com.rentflow.bookingmod.repository.LateReturnFeeRepository;
import com.rentflow.common.exception.BookingNotFoundException;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.ResourceNotFoundException;
import com.rentflow.common.idempotency.service.IdempotencyFailureMarker;
import com.rentflow.common.idempotency.service.IdempotencyResolution;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import com.rentflow.common.idempotency.service.IdempotencyService;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.common.web.PageResponse;
import com.rentflow.deposit.service.DepositService;
import com.rentflow.outbox.service.OutboxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingModificationService {

    private static final BigDecimal LATE_FEE_MULTIPLIER = new BigDecimal("1.20");

    private final BookingModificationRequestRepository requestRepository;
    private final LateReturnFeeRepository lateReturnFeeRepository;
    private final BookingRepository bookingRepository;
    private final AvailabilityCalendarRepository availabilityRepository;
    private final SecurityContext securityContext;
    private final IdempotencyService idempotencyService;
    private final IdempotencyFailureMarker idempotencyFailureMarker;
    private final BookingTimelineService bookingTimelineService;
    private final AuditLogService auditLogService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private DepositService depositService;

    public BookingModificationService(
            BookingModificationRequestRepository requestRepository,
            LateReturnFeeRepository lateReturnFeeRepository,
            BookingRepository bookingRepository,
            AvailabilityCalendarRepository availabilityRepository,
            SecurityContext securityContext,
            IdempotencyService idempotencyService,
            IdempotencyFailureMarker idempotencyFailureMarker,
            BookingTimelineService bookingTimelineService,
            AuditLogService auditLogService,
            OutboxService outboxService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.requestRepository = requestRepository;
        this.lateReturnFeeRepository = lateReturnFeeRepository;
        this.bookingRepository = bookingRepository;
        this.availabilityRepository = availabilityRepository;
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

    @Transactional(readOnly = true)
    public ModificationPreviewResponse preview(UUID bookingId, CreateModificationRequest request) {
        Booking booking = requireParticipantBooking(bookingId);
        validateModificationAllowed(booking, request);
        return new ModificationPreviewResponse(
                true,
                calculateDelta(booking, request),
                BigDecimal.ZERO,
                readCurrency(booking),
                "Modification can be requested");
    }

    @Transactional
    public BookingModificationResponse create(UUID bookingId, String idempotencyKey, CreateModificationRequest request) {
        UUID actorId = securityContext.currentUserId();
        IdempotencyResolution resolution = resolve(actorId, IdempotencyScope.REQUEST_BOOKING_MODIFICATION, idempotencyKey,
                new HashInput(bookingId, request));
        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserializeModification(replay.responseBodyJson());
        }
        UUID keyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                    .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
            if (!booking.getCustomerId().equals(actorId)) {
                throw new BookingNotFoundException(bookingId.toString());
            }
            validateModificationAllowed(booking, request);
            BookingModificationRequest entity = new BookingModificationRequest();
            entity.setBookingId(bookingId);
            entity.setRequesterId(actorId);
            entity.setRequesterRole("CUSTOMER");
            entity.setType(request.type());
            entity.setStatus(BookingModificationStatus.PENDING_HOST_APPROVAL);
            entity.setCurrentPickupDate(booking.getPickupDate());
            entity.setCurrentReturnDate(booking.getReturnDate());
            entity.setRequestedPickupDate(request.requestedPickupDate());
            entity.setRequestedReturnDate(request.requestedReturnDate());
            entity.setCurrentPickupLocation(booking.getPickupLocation());
            entity.setCurrentReturnLocation(booking.getReturnLocation());
            entity.setRequestedPickupLocation(normalize(request.requestedPickupLocation()));
            entity.setRequestedReturnLocation(normalize(request.requestedReturnLocation()));
            entity.setPriceDelta(calculateDelta(booking, request));
            entity.setCurrency(readCurrency(booking));
            entity.setReason(normalize(request.reason()));
            entity.setExpiresAt(clock.instant().plus(ChronoUnit.HOURS.getDuration().multipliedBy(24)));
            entity = requestRepository.save(entity);
            BookingModificationResponse response = BookingModificationResponse.from(entity);
            emit(booking, entity.getId(), "BOOKING_MODIFICATION_REQUESTED", "BOOKING_MODIFICATION", "SUCCEEDED");
            idempotencyService.complete(keyId, 201, serialize(response));
            return response;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(keyId);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingModificationResponse> listForBooking(UUID bookingId, Pageable pageable) {
        requireParticipantBooking(bookingId);
        return PageResponse.from(requestRepository.findByBookingIdOrderByCreatedAtDesc(bookingId, pageable),
                BookingModificationResponse::from);
    }

    @Transactional
    public BookingModificationResponse cancel(UUID requestId, String idempotencyKey) {
        UUID actorId = securityContext.currentUserId();
        IdempotencyResolution resolution = resolve(actorId, IdempotencyScope.CANCEL_BOOKING_MODIFICATION, idempotencyKey, requestId);
        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserializeModification(replay.responseBodyJson());
        }
        UUID keyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            BookingModificationRequest request = requestRepository.findByIdForUpdate(requestId)
                    .orElseThrow(() -> new ResourceNotFoundException("BOOKING_MODIFICATION_NOT_FOUND", "BookingModification", requestId.toString()));
            if (!request.getRequesterId().equals(actorId)) {
                throw new ResourceNotFoundException("BOOKING_MODIFICATION_NOT_FOUND", "BookingModification", requestId.toString());
            }
            requirePending(request);
            request.setStatus(BookingModificationStatus.CANCELLED);
            request.setDecidedAt(clock.instant());
            request = requestRepository.save(request);
            Booking booking = requireBooking(request.getBookingId());
            BookingModificationResponse response = BookingModificationResponse.from(request);
            emit(booking, request.getId(), "BOOKING_MODIFICATION_CANCELLED", "BOOKING_MODIFICATION", "SUCCEEDED");
            idempotencyService.complete(keyId, 200, serialize(response));
            return response;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(keyId);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingModificationResponse> listHostRequests(BookingModificationStatus status, Pageable pageable) {
        UUID hostId = securityContext.currentUserId();
        securityContext.requireRole(Role.HOST);
        return PageResponse.from(requestRepository.findHostRequests(hostId, status, pageable), BookingModificationResponse::from);
    }

    @Transactional
    public BookingModificationResponse approve(UUID requestId, String idempotencyKey, ModificationDecisionRequest decision) {
        return decideHost(requestId, idempotencyKey, decision, true);
    }

    @Transactional
    public BookingModificationResponse reject(UUID requestId, String idempotencyKey, ModificationDecisionRequest decision) {
        return decideHost(requestId, idempotencyKey, decision, false);
    }

    @Transactional(readOnly = true)
    public PageResponse<LateReturnFeeResponse> listLateFees(LateReturnFeeStatus status, Pageable pageable) {
        securityContext.requireRole(Role.ADMIN);
        Page<LateReturnFee> page = status == null
                ? lateReturnFeeRepository.findAll(pageable)
                : lateReturnFeeRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return PageResponse.from(page, LateReturnFeeResponse::from);
    }

    @Transactional
    public LateReturnFeeResponse waiveLateFee(UUID feeId, String idempotencyKey, WaiveLateReturnFeeRequest request) {
        UUID adminId = securityContext.currentUserId();
        securityContext.requireRole(Role.ADMIN);
        IdempotencyResolution resolution = resolve(adminId, IdempotencyScope.WAIVE_LATE_RETURN_FEE, idempotencyKey,
                new HashInput(feeId, request));
        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserializeLateFee(replay.responseBodyJson());
        }
        UUID keyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            LateReturnFee fee = lateReturnFeeRepository.findByIdForUpdate(feeId)
                    .orElseThrow(() -> new ResourceNotFoundException("LATE_RETURN_FEE_NOT_FOUND", "LateReturnFee", feeId.toString()));
            if (fee.getStatus() != LateReturnFeeStatus.PENDING) {
                throw new BusinessRuleException("LATE_FEE_INVALID_STATUS", "Only pending late fees can be waived");
            }
            fee.setStatus(LateReturnFeeStatus.WAIVED);
            fee.setWaivedBy(adminId);
            fee.setWaiverReason(normalize(request.reason()));
            fee = lateReturnFeeRepository.save(fee);
            LateReturnFeeResponse response = LateReturnFeeResponse.from(fee);
            emit(requireBooking(fee.getBookingId()), fee.getId(), "LATE_RETURN_FEE_WAIVED", "LATE_RETURN_FEE", "SUCCEEDED");
            idempotencyService.complete(keyId, 200, serialize(response));
            return response;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(keyId);
            throw e;
        }
    }

    @Transactional
    public LateReturnFeeResponse chargeLateFee(UUID feeId, String idempotencyKey) {
        UUID adminId = securityContext.currentUserId();
        securityContext.requireRole(Role.ADMIN);
        IdempotencyResolution resolution = resolve(adminId, IdempotencyScope.CHARGE_LATE_RETURN_FEE, idempotencyKey, feeId);
        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserializeLateFee(replay.responseBodyJson());
        }
        UUID keyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            LateReturnFee fee = lateReturnFeeRepository.findByIdForUpdate(feeId)
                    .orElseThrow(() -> new ResourceNotFoundException("LATE_RETURN_FEE_NOT_FOUND", "LateReturnFee", feeId.toString()));
            if (fee.getStatus() != LateReturnFeeStatus.PENDING) {
                throw new BusinessRuleException("LATE_FEE_INVALID_STATUS", "Only pending late fees can be charged");
            }
            if (depositService != null) {
                depositService.deductForLateReturnFee(fee.getBookingId(), fee.getFeeAmount());
            }
            fee.setStatus(LateReturnFeeStatus.CHARGED);
            fee = lateReturnFeeRepository.save(fee);
            LateReturnFeeResponse response = LateReturnFeeResponse.from(fee);
            emit(requireBooking(fee.getBookingId()), fee.getId(), "LATE_RETURN_FEE_CHARGED", "LATE_RETURN_FEE", "SUCCEEDED");
            idempotencyService.complete(keyId, 200, serialize(response));
            return response;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(keyId);
            throw e;
        }
    }

    @Transactional
    public int detectLateFees(int batchSize) {
        LocalDate today = LocalDate.now(clock);
        List<Booking> bookings = bookingRepository.findOverdueInProgressBookings(today, batchSize);
        int created = 0;
        for (Booking booking : bookings) {
            if (lateReturnFeeRepository.existsByBookingIdAndStatus(booking.getId(), LateReturnFeeStatus.PENDING)) {
                continue;
            }
            long daysLate = Math.max(1, ChronoUnit.DAYS.between(booking.getReturnDate(), today));
            LateReturnFee fee = new LateReturnFee();
            fee.setBookingId(booking.getId());
            fee.setStatus(LateReturnFeeStatus.PENDING);
            fee.setDetectedAt(clock.instant());
            fee.setExpectedReturnDate(booking.getReturnDate());
            fee.setDaysLate((int) daysLate);
            fee.setFeeAmount(readBasePricePerDay(booking).multiply(LATE_FEE_MULTIPLIER).multiply(BigDecimal.valueOf(daysLate)));
            fee.setCurrency(readCurrency(booking));
            lateReturnFeeRepository.save(fee);
            emitSystem(booking, fee.getId(), "LATE_RETURN_FEE_DETECTED", "LATE_RETURN_FEE", "SUCCEEDED");
            created++;
        }
        return created;
    }

    private BookingModificationResponse decideHost(
            UUID requestId,
            String idempotencyKey,
            ModificationDecisionRequest decision,
            boolean approve) {
        UUID hostId = securityContext.currentUserId();
        securityContext.requireRole(Role.HOST);
        IdempotencyScope scope = approve
                ? IdempotencyScope.APPROVE_BOOKING_MODIFICATION
                : IdempotencyScope.REJECT_BOOKING_MODIFICATION;
        IdempotencyResolution resolution = resolve(hostId, scope, idempotencyKey, new HashInput(requestId, decision));
        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserializeModification(replay.responseBodyJson());
        }
        UUID keyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            BookingModificationRequest request = requestRepository.findByIdForUpdate(requestId)
                    .orElseThrow(() -> new ResourceNotFoundException("BOOKING_MODIFICATION_NOT_FOUND", "BookingModification", requestId.toString()));
            UUID bookingId = request.getBookingId();
            Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                    .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
            if (!booking.getHostId().equals(hostId)) {
                throw new ResourceNotFoundException("BOOKING_MODIFICATION_NOT_FOUND", "BookingModification", requestId.toString());
            }
            requirePending(request);
            if (approve) {
                applyApprovedModification(booking, request);
                request.setStatus(BookingModificationStatus.APPROVED);
            } else {
                request.setStatus(BookingModificationStatus.REJECTED);
            }
            request.setHostResponseNote(decision == null ? null : normalize(decision.note()));
            request.setDecidedAt(clock.instant());
            request = requestRepository.save(request);
            BookingModificationResponse response = BookingModificationResponse.from(request);
            emit(booking, request.getId(),
                    approve ? "BOOKING_MODIFICATION_APPROVED" : "BOOKING_MODIFICATION_REJECTED",
                    "BOOKING_MODIFICATION",
                    "SUCCEEDED");
            idempotencyService.complete(keyId, 200, serialize(response));
            return response;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(keyId);
            throw e;
        }
    }

    private void applyApprovedModification(Booking booking, BookingModificationRequest request) {
        if ((request.getType() == BookingModificationType.TRIP_EXTENSION
                || request.getType() == BookingModificationType.DATE_CHANGE)
                && request.getRequestedReturnDate() != null
                && request.getRequestedReturnDate().isAfter(booking.getReturnDate())) {
            List<AvailabilityCalendar> rows = availabilityRepository.findForBookingRangeForUpdate(
                    booking.getListingId(),
                    booking.getReturnDate(),
                    request.getRequestedReturnDate());
            long expected = ChronoUnit.DAYS.between(booking.getReturnDate(), request.getRequestedReturnDate());
            if (rows.size() != expected || rows.stream().anyMatch(row -> row.getStatus() != AvailabilityStatus.FREE)) {
                throw new BusinessRuleException("LISTING_NOT_AVAILABLE", "Extension dates are not available");
            }
            rows.forEach(row -> {
                row.setStatus(AvailabilityStatus.BOOKED);
                row.setBookingId(booking.getId());
            });
            availabilityRepository.saveAll(rows);
            booking.setReturnDate(request.getRequestedReturnDate());
        }
        if (request.getRequestedPickupLocation() != null) {
            booking.setPickupLocation(request.getRequestedPickupLocation());
        }
        if (request.getRequestedReturnLocation() != null) {
            booking.setReturnLocation(request.getRequestedReturnLocation());
        }
        bookingRepository.save(booking);
    }

    private void validateModificationAllowed(Booking booking, CreateModificationRequest request) {
        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Booking modification requires CONFIRMED or IN_PROGRESS booking");
        }
        if ((request.type() == BookingModificationType.TRIP_EXTENSION
                || request.type() == BookingModificationType.DATE_CHANGE)
                && (request.requestedReturnDate() == null || !request.requestedReturnDate().isAfter(booking.getReturnDate()))) {
            throw new BusinessRuleException("BOOKING_MODIFICATION_INVALID", "Extension requires a later return date");
        }
        if (request.type() == BookingModificationType.LOCATION_CHANGE
                && request.requestedPickupLocation() == null
                && request.requestedReturnLocation() == null) {
            throw new BusinessRuleException("BOOKING_MODIFICATION_INVALID", "Location change requires a requested location");
        }
    }

    private void requirePending(BookingModificationRequest request) {
        if (request.getStatus() != BookingModificationStatus.PENDING_HOST_APPROVAL) {
            throw new BusinessRuleException("BOOKING_MODIFICATION_INVALID_STATUS", "Modification request is not pending");
        }
        if (request.getExpiresAt().isBefore(clock.instant())) {
            request.setStatus(BookingModificationStatus.EXPIRED);
            throw new BusinessRuleException("BOOKING_MODIFICATION_EXPIRED", "Modification request expired");
        }
    }

    private Booking requireParticipantBooking(UUID bookingId) {
        UUID actorId = securityContext.currentUserId();
        Booking booking = requireBooking(bookingId);
        if (!booking.getCustomerId().equals(actorId)
                && !booking.getHostId().equals(actorId)
                && !securityContext.hasRole(Role.ADMIN)) {
            throw new BookingNotFoundException(bookingId.toString());
        }
        return booking;
    }

    private Booking requireBooking(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
    }

    private BigDecimal calculateDelta(Booking booking, CreateModificationRequest request) {
        if (request.requestedReturnDate() == null || !request.requestedReturnDate().isAfter(booking.getReturnDate())) {
            return BigDecimal.ZERO;
        }
        long extraDays = ChronoUnit.DAYS.between(booking.getReturnDate(), request.requestedReturnDate());
        return readBasePricePerDay(booking).multiply(BigDecimal.valueOf(extraDays));
    }

    private BigDecimal readBasePricePerDay(Booking booking) {
        try {
            JsonNode root = objectMapper.readTree(booking.getPriceSnapshot());
            return root.path("basePricePerDay").decimalValue();
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private String readCurrency(Booking booking) {
        try {
            return objectMapper.readTree(booking.getPriceSnapshot()).path("currency").asText("VND");
        } catch (Exception e) {
            return "VND";
        }
    }

    private IdempotencyResolution resolve(UUID userId, IdempotencyScope scope, String key, Object request) {
        return idempotencyService.resolve(userId, scope, key, idempotencyService.computeHash(request));
    }

    private void emit(Booking booking, UUID targetId, String eventType, String targetType, String status) {
        UUID actorId = securityContext.currentUserId();
        String actorType = securityContext.hasRole(Role.ADMIN)
                ? "ADMIN"
                : booking.getHostId().equals(actorId) ? "HOST" : "CUSTOMER";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bookingId", booking.getId());
        payload.put("targetId", targetId);
        payload.put("eventType", eventType);
        String json = serialize(payload);
        bookingTimelineService.append(booking.getId(), eventType, actorId, actorType, json);
        auditLogService.record(actorId, actorType, eventType, targetType, targetId, status, json);
        outboxService.append(targetType, targetId, eventType, json);
    }

    private void emitSystem(Booking booking, UUID targetId, String eventType, String targetType, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bookingId", booking.getId());
        payload.put("targetId", targetId);
        payload.put("eventType", eventType);
        String json = serialize(payload);
        bookingTimelineService.append(booking.getId(), eventType, null, "SYSTEM", json);
        auditLogService.record(null, "SYSTEM", eventType, targetType, targetId, status, json);
        outboxService.append(targetType, targetId, eventType, json);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize booking modification JSON", e);
        }
    }

    private BookingModificationResponse deserializeModification(String json) {
        try {
            return objectMapper.readValue(json, BookingModificationResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to deserialize booking modification response", e);
        }
    }

    private LateReturnFeeResponse deserializeLateFee(String json) {
        try {
            return objectMapper.readValue(json, LateReturnFeeResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to deserialize late fee response", e);
        }
    }

    private record HashInput(Object id, Object request) {
    }
}
