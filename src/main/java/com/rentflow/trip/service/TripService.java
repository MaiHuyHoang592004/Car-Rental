package com.rentflow.trip.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.audit.service.AuditLogService;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.exception.BookingNotFoundException;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.RentFlowException;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.outbox.service.OutboxService;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.service.TripPaymentCaptureService;
import com.rentflow.booking.service.BookingTimelineService;
import com.rentflow.trip.dto.CheckInRequest;
import com.rentflow.trip.dto.CheckOutRequest;
import com.rentflow.trip.dto.TripRecordResponse;
import com.rentflow.trip.entity.TripCheckoutFinalizationFailure;
import com.rentflow.trip.entity.TripCheckoutFinalizationFailureStatus;
import com.rentflow.trip.entity.TripRecord;
import com.rentflow.trip.repository.TripCheckoutFinalizationFailureRepository;
import com.rentflow.trip.repository.TripRecordRepository;
import com.rentflow.tripcondition.entity.TripConditionReportType;
import com.rentflow.tripcondition.service.TripConditionReportService;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.entity.VehicleStatus;
import com.rentflow.vehicle.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class TripService {

    private final BookingRepository bookingRepository;
    private final ListingRepository listingRepository;
    private final VehicleRepository vehicleRepository;
    private final TripRecordRepository tripRecordRepository;
    private final TripCheckoutFinalizationFailureRepository checkoutFailureRepository;
    private final BookingPaymentRepository bookingPaymentRepository;
    private final TripPaymentCaptureService tripPaymentCaptureService;
    private final TripConditionReportService tripConditionReportService;
    private final SecurityContext securityContext;
    private final BookingTimelineService bookingTimelineService;
    private final AuditLogService auditLogService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public TripService(
            BookingRepository bookingRepository,
            ListingRepository listingRepository,
            VehicleRepository vehicleRepository,
            TripRecordRepository tripRecordRepository,
            TripCheckoutFinalizationFailureRepository checkoutFailureRepository,
            BookingPaymentRepository bookingPaymentRepository,
            TripPaymentCaptureService tripPaymentCaptureService,
            TripConditionReportService tripConditionReportService,
            SecurityContext securityContext,
            BookingTimelineService bookingTimelineService,
            AuditLogService auditLogService,
            OutboxService outboxService,
            ObjectMapper objectMapper,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.bookingRepository = bookingRepository;
        this.listingRepository = listingRepository;
        this.vehicleRepository = vehicleRepository;
        this.tripRecordRepository = tripRecordRepository;
        this.checkoutFailureRepository = checkoutFailureRepository;
        this.bookingPaymentRepository = bookingPaymentRepository;
        this.tripPaymentCaptureService = tripPaymentCaptureService;
        this.tripConditionReportService = tripConditionReportService;
        this.securityContext = securityContext;
        this.bookingTimelineService = bookingTimelineService;
        this.auditLogService = auditLogService;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public TripRecordResponse checkIn(UUID bookingId, CheckInRequest request) {
        UUID actorId = securityContext.currentUserId();
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
        requireCustomerOwner(booking, actorId);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Booking must be CONFIRMED for check-in");
        }

        Listing listing = listingRepository.findById(booking.getListingId())
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Listing must be ACTIVE for check-in");
        }
        Vehicle vehicle = vehicleRepository.findById(listing.getVehicleId())
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
        if (vehicle.getStatus() != VehicleStatus.ACTIVE) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Vehicle must be ACTIVE for check-in");
        }

        if (tripRecordRepository.findByBookingId(bookingId).isPresent()) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Trip record already exists");
        }
        UUID conditionReportId = tripConditionReportService.requireMatchingReportForTripTransition(
                bookingId,
                TripConditionReportType.CHECK_IN,
                actorId,
                request.odometer(),
                request.fuelLevel(),
                null);

        Instant now = clock.instant();
        TripRecord tripRecord = new TripRecord();
        tripRecord.setBookingId(bookingId);
        tripRecord.setCustomerId(actorId);
        tripRecord.setCheckInAt(now);
        tripRecord.setCheckInOdometer(request.odometer());
        tripRecord.setCheckInFuelLevel(request.fuelLevel());
        tripRecord.setNotes(request.note());
        tripRecordRepository.save(tripRecord);
        tripConditionReportService.attachTripRecord(conditionReportId, tripRecord.getId());

        booking.setStatus(BookingStatus.IN_PROGRESS);
        bookingRepository.save(booking);
        return toResponse(booking, tripRecord);
    }

    public TripRecordResponse checkOut(UUID bookingId, CheckOutRequest request) {
        UUID actorId = securityContext.currentUserId();
        PreparedCheckOut prepared = required(transactionTemplate.execute(status ->
                prepareCheckOut(bookingId, request, actorId)));

        tripPaymentCaptureService.captureRemainingForBooking(prepared.bookingId());

        try {
            return required(transactionTemplate.execute(status ->
                    finalizeCheckOut(prepared, request, actorId)));
        } catch (RuntimeException e) {
            transactionTemplate.executeWithoutResult(status ->
                    recordCheckoutFinalizationFailure(prepared, request, actorId, e));
            throw e;
        }
    }

    public TripRecordResponse repairFailedCheckOut(UUID bookingId) {
        return required(transactionTemplate.execute(status -> {
            TripCheckoutFinalizationFailure failure = checkoutFailureRepository
                    .findFirstByBookingIdAndStatusOrderByCreatedAtDesc(
                            bookingId,
                            TripCheckoutFinalizationFailureStatus.PENDING)
                    .orElseThrow(() -> new BusinessRuleException(
                            "TRIP_CHECKOUT_REPAIR_NOT_FOUND",
                            "No pending check-out finalization failure found"));
            requireCapturedPaymentForRepair(failure);
            CheckOutRequest request = new CheckOutRequest(
                    failure.getCheckOutOdometer(),
                    failure.getCheckOutFuelLevel(),
                    failure.getCheckOutNote());
            PreparedCheckOut prepared = new PreparedCheckOut(
                    failure.getBookingId(),
                    failure.getTripRecordId(),
                    failure.getCheckInOdometer());
            try {
                TripRecordResponse response = finalizeCheckOut(prepared, request, failure.getActorUserId());
                failure.setStatus(TripCheckoutFinalizationFailureStatus.RESOLVED);
                failure.setAttempts(failure.getAttempts() + 1);
                failure.setFailureCode(null);
                failure.setFailureMessage(null);
                checkoutFailureRepository.save(failure);
                emitCheckoutRepairSignals(failure, "RESOLVED", null);
                return response;
            } catch (RuntimeException e) {
                failure.setAttempts(failure.getAttempts() + 1);
                failure.setFailureCode(errorCode(e));
                failure.setFailureMessage(e.getMessage());
                checkoutFailureRepository.save(failure);
                emitCheckoutRepairSignals(failure, "FAILED", e);
                throw e;
            }
        }));
    }

    private PreparedCheckOut prepareCheckOut(UUID bookingId, CheckOutRequest request, UUID actorId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
        requireCustomerOwner(booking, actorId);
        if (booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Booking must be IN_PROGRESS for check-out");
        }

        TripRecord tripRecord = tripRecordRepository.findByBookingIdForUpdate(bookingId)
                .orElseThrow(() -> new BusinessRuleException("BOOKING_INVALID_STATUS", "Trip record not found for check-out"));
        if (tripRecord.getCheckOutAt() != null) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Booking already checked out");
        }
        if (request.odometer() < tripRecord.getCheckInOdometer()) {
            throw new BusinessRuleException("VALIDATION_ERROR", "Check-out odometer must be >= check-in odometer");
        }
        tripConditionReportService.requireMatchingReportForTripTransition(
                bookingId,
                TripConditionReportType.CHECK_OUT,
                actorId,
                request.odometer(),
                request.fuelLevel(),
                tripRecord.getId());
        return new PreparedCheckOut(booking.getId(), tripRecord.getId(), tripRecord.getCheckInOdometer());
    }

    private TripRecordResponse finalizeCheckOut(PreparedCheckOut prepared, CheckOutRequest request, UUID actorId) {
        Booking booking = bookingRepository.findByIdForUpdate(prepared.bookingId())
                .orElseThrow(() -> new BookingNotFoundException(prepared.bookingId().toString()));
        requireCustomerOwner(booking, actorId);
        if (booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Booking must be IN_PROGRESS for check-out");
        }

        TripRecord tripRecord = tripRecordRepository.findByBookingIdForUpdate(prepared.bookingId())
                .orElseThrow(() -> new BusinessRuleException("BOOKING_INVALID_STATUS", "Trip record not found for check-out"));
        if (!Objects.equals(prepared.tripRecordId(), tripRecord.getId())) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Trip record changed during check-out");
        }
        if (tripRecord.getCheckOutAt() != null) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Booking already checked out");
        }
        if (!prepared.checkInOdometer().equals(tripRecord.getCheckInOdometer())
                || request.odometer() < tripRecord.getCheckInOdometer()) {
            throw new BusinessRuleException("VALIDATION_ERROR", "Check-out odometer must be >= check-in odometer");
        }

        tripRecord.setCheckOutAt(clock.instant());
        tripRecord.setCheckOutOdometer(request.odometer());
        tripRecord.setCheckOutFuelLevel(request.fuelLevel());
        if (request.note() != null && !request.note().isBlank()) {
            tripRecord.setNotes(request.note());
        }
        tripRecordRepository.save(tripRecord);

        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(booking);
        return toResponse(booking, tripRecord);
    }

    private void recordCheckoutFinalizationFailure(
            PreparedCheckOut prepared,
            CheckOutRequest request,
            UUID actorId,
            RuntimeException error) {
        TripCheckoutFinalizationFailure failure = new TripCheckoutFinalizationFailure();
        failure.setBookingId(prepared.bookingId());
        failure.setTripRecordId(prepared.tripRecordId());
        failure.setActorUserId(actorId);
        failure.setCapturedPaymentId(bookingPaymentRepository.findByBookingId(prepared.bookingId())
                .map(BookingPayment::getId)
                .orElse(null));
        failure.setCheckInOdometer(prepared.checkInOdometer());
        failure.setCheckOutOdometer(request.odometer());
        failure.setCheckOutFuelLevel(request.fuelLevel());
        failure.setCheckOutNote(request.note());
        failure.setStatus(TripCheckoutFinalizationFailureStatus.PENDING);
        failure.setAttempts(0);
        failure.setFailureCode(errorCode(error));
        failure.setFailureMessage(error.getMessage());
        checkoutFailureRepository.save(failure);
        emitCheckoutFailureSignals(failure);
    }

    private void requireCapturedPaymentForRepair(TripCheckoutFinalizationFailure failure) {
        UUID paymentId = failure.getCapturedPaymentId();
        if (paymentId == null) {
            throw new BusinessRuleException(
                    "TRIP_CHECKOUT_REPAIR_UNSAFE",
                    "Captured payment id is missing from check-out failure marker");
        }
        BookingPayment payment = bookingPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessRuleException(
                        "TRIP_CHECKOUT_REPAIR_UNSAFE",
                        "Captured payment no longer exists"));
        if (!failure.getBookingId().equals(payment.getBookingId())
                || payment.getStatus() != PaymentStatus.CAPTURED) {
            throw new BusinessRuleException(
                    "TRIP_CHECKOUT_REPAIR_UNSAFE",
                    "Payment is not captured for this failed check-out");
        }
    }

    private void emitCheckoutFailureSignals(TripCheckoutFinalizationFailure failure) {
        String details = toJson(Map.of(
                "bookingId", failure.getBookingId(),
                "tripRecordId", failure.getTripRecordId(),
                "capturedPaymentId", failure.getCapturedPaymentId() == null ? "" : failure.getCapturedPaymentId(),
                "failureCode", failure.getFailureCode() == null ? "" : failure.getFailureCode(),
                "failureMessage", failure.getFailureMessage() == null ? "" : failure.getFailureMessage()));
        bookingTimelineService.append(
                failure.getBookingId(),
                "TRIP_CHECKOUT_FINALIZATION_FAILED_AFTER_CAPTURE",
                failure.getActorUserId(),
                "CUSTOMER",
                details);
        auditLogService.record(
                failure.getActorUserId(),
                "CUSTOMER",
                "TRIP_CHECKOUT_FINALIZATION",
                "BOOKING",
                failure.getBookingId(),
                "FAILED",
                details);
        outboxService.append(
                "BOOKING",
                failure.getBookingId(),
                "TRIP_CHECKOUT_FINALIZATION_FAILED_AFTER_CAPTURE",
                details);
    }

    private void emitCheckoutRepairSignals(
            TripCheckoutFinalizationFailure failure,
            String status,
            RuntimeException error) {
        String details = toJson(Map.of(
                "bookingId", failure.getBookingId(),
                "tripRecordId", failure.getTripRecordId(),
                "failureId", failure.getId(),
                "attempts", failure.getAttempts(),
                "status", status,
                "error", error == null || error.getMessage() == null ? "" : error.getMessage()));
        bookingTimelineService.append(
                failure.getBookingId(),
                "TRIP_CHECKOUT_FINALIZATION_REPAIR_" + status,
                failure.getActorUserId(),
                "SYSTEM",
                details);
        auditLogService.record(
                null,
                "SYSTEM",
                "TRIP_CHECKOUT_FINALIZATION_REPAIR",
                "BOOKING",
                failure.getBookingId(),
                status,
                details);
        outboxService.append(
                "BOOKING",
                failure.getBookingId(),
                "TRIP_CHECKOUT_FINALIZATION_REPAIR_" + status,
                details);
    }

    private String errorCode(RuntimeException error) {
        if (error instanceof RentFlowException rentFlowException) {
            return rentFlowException.getCode();
        }
        return error.getClass().getSimpleName();
    }

    private String toJson(Map<String, ?> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize trip details", e);
        }
    }

    private <T> T required(T value) {
        if (value == null) {
            throw new IllegalStateException("Transactional callback returned null unexpectedly");
        }
        return value;
    }

    private void requireCustomerOwner(Booking booking, UUID actorId) {
        if (!booking.getCustomerId().equals(actorId)) {
            throw new BookingNotFoundException(booking.getId().toString());
        }
    }

    private TripRecordResponse toResponse(Booking booking, TripRecord tripRecord) {
        return new TripRecordResponse(
                booking.getId(),
                booking.getStatus(),
                tripRecord.getCheckInAt(),
                tripRecord.getCheckOutAt(),
                tripRecord.getCheckInOdometer(),
                tripRecord.getCheckOutOdometer(),
                tripRecord.getCheckInFuelLevel(),
                tripRecord.getCheckOutFuelLevel(),
                tripRecord.getNotes());
    }

    private record PreparedCheckOut(
            UUID bookingId,
            UUID tripRecordId,
            Integer checkInOdometer
    ) {
    }
}
