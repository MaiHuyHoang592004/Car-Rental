package com.rentflow.tripcondition.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.audit.service.AuditLogService;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.booking.service.BookingTimelineService;
import com.rentflow.common.exception.BookingNotFoundException;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.ValidationException;
import com.rentflow.common.idempotency.service.IdempotencyFailureMarker;
import com.rentflow.common.idempotency.service.IdempotencyResolution;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import com.rentflow.common.idempotency.service.IdempotencyService;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.file.service.FileService;
import com.rentflow.outbox.service.OutboxService;
import com.rentflow.trip.entity.TripRecord;
import com.rentflow.trip.repository.TripRecordRepository;
import com.rentflow.tripcondition.dto.ConditionPhotoRequest;
import com.rentflow.tripcondition.dto.CreateConditionReportRequest;
import com.rentflow.tripcondition.dto.DamageItemRequest;
import com.rentflow.tripcondition.entity.TripConditionPhotoAngle;
import com.rentflow.tripcondition.entity.TripConditionReportType;
import com.rentflow.tripcondition.repository.TripConditionPhotoRepository;
import com.rentflow.tripcondition.repository.TripConditionReportRepository;
import com.rentflow.tripcondition.repository.TripDamageItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripConditionReportServiceTest {

    private static final String IDEMPOTENCY_KEY = "11111111-1111-4111-8111-111111111111";

    @Mock private TripConditionReportRepository reportRepository;
    @Mock private TripConditionPhotoRepository photoRepository;
    @Mock private TripDamageItemRepository damageItemRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private TripRecordRepository tripRecordRepository;
    @Mock private FileService fileService;
    @Mock private SecurityContext securityContext;
    @Mock private IdempotencyService idempotencyService;
    @Mock private IdempotencyFailureMarker idempotencyFailureMarker;
    @Mock private BookingTimelineService bookingTimelineService;
    @Mock private AuditLogService auditLogService;
    @Mock private OutboxService outboxService;

    private TripConditionReportService service;
    private UUID bookingId;
    private UUID customerId;
    private UUID hostId;

    @BeforeEach
    void setUp() {
        service = new TripConditionReportService(
                reportRepository,
                photoRepository,
                damageItemRepository,
                bookingRepository,
                tripRecordRepository,
                fileService,
                securityContext,
                idempotencyService,
                idempotencyFailureMarker,
                bookingTimelineService,
                auditLogService,
                outboxService,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC));
        bookingId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        hostId = UUID.randomUUID();

        lenient().when(securityContext.currentUserId()).thenReturn(customerId);
        lenient().when(idempotencyService.computeHash(any())).thenReturn("hash");
        lenient().when(idempotencyService.resolve(
                        eq(customerId),
                        eq(IdempotencyScope.SUBMIT_TRIP_CONDITION_REPORT),
                        eq(IDEMPOTENCY_KEY),
                        eq("hash")))
                .thenReturn(IdempotencyResolution.proceed(UUID.randomUUID()));
    }

    @Test
    void createReportRejectsMissingRequiredPhotoAngles() {
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking(BookingStatus.CONFIRMED)));

        CreateConditionReportRequest request = new CreateConditionReportRequest(
                TripConditionReportType.CHECK_IN,
                1000,
                80,
                null,
                null,
                false,
                null,
                null,
                null,
                List.of(
                        photo(TripConditionPhotoAngle.FRONT),
                        photo(TripConditionPhotoAngle.REAR),
                        photo(TripConditionPhotoAngle.LEFT),
                        photo(TripConditionPhotoAngle.ODOMETER)),
                List.of());

        assertThatThrownBy(() -> service.createReport(bookingId, IDEMPOTENCY_KEY, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("FRONT, REAR, LEFT and RIGHT");
    }

    @Test
    void createReportRejectsDuplicateReportForSameActorAndType() {
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking(BookingStatus.CONFIRMED)));
        when(reportRepository.existsByBookingIdAndReportTypeAndReporterUserId(
                bookingId,
                TripConditionReportType.CHECK_IN,
                customerId)).thenReturn(true);

        assertThatThrownBy(() -> service.createReport(bookingId, IDEMPOTENCY_KEY, validCheckInRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "TRIP_CONDITION_REPORT_ALREADY_EXISTS");
    }

    @Test
    void createReportHidesBookingFromUnrelatedActor() {
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking(BookingStatus.CONFIRMED)));
        when(securityContext.currentUserId()).thenReturn(UUID.randomUUID());
        when(idempotencyService.resolve(any(), any(), any(), any()))
                .thenReturn(IdempotencyResolution.proceed(UUID.randomUUID()));

        assertThatThrownBy(() -> service.createReport(bookingId, IDEMPOTENCY_KEY, validCheckInRequest()))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void createCheckoutReportRejectsOdometerLowerThanCheckIn() {
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking(BookingStatus.IN_PROGRESS)));
        TripRecord tripRecord = new TripRecord();
        tripRecord.setId(UUID.randomUUID());
        tripRecord.setBookingId(bookingId);
        tripRecord.setCheckInOdometer(1500);
        when(tripRecordRepository.findByBookingIdForUpdate(bookingId)).thenReturn(Optional.of(tripRecord));

        CreateConditionReportRequest request = new CreateConditionReportRequest(
                TripConditionReportType.CHECK_OUT,
                1400,
                70,
                null,
                null,
                false,
                null,
                null,
                null,
                requiredPhotos(),
                List.of());

        assertThatThrownBy(() -> service.createReport(bookingId, IDEMPOTENCY_KEY, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR");
    }

    private Booking booking(BookingStatus status) {
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setCustomerId(customerId);
        booking.setHostId(hostId);
        booking.setStatus(status);
        return booking;
    }

    private CreateConditionReportRequest validCheckInRequest() {
        return new CreateConditionReportRequest(
                TripConditionReportType.CHECK_IN,
                1000,
                80,
                null,
                null,
                false,
                null,
                null,
                null,
                requiredPhotos(),
                List.<DamageItemRequest>of());
    }

    private List<ConditionPhotoRequest> requiredPhotos() {
        return List.of(
                photo(TripConditionPhotoAngle.FRONT),
                photo(TripConditionPhotoAngle.REAR),
                photo(TripConditionPhotoAngle.LEFT),
                photo(TripConditionPhotoAngle.RIGHT));
    }

    private ConditionPhotoRequest photo(TripConditionPhotoAngle angle) {
        return new ConditionPhotoRequest(UUID.randomUUID(), angle, null, null);
    }
}
