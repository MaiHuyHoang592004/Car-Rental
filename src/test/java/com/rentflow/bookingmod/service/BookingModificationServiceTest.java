package com.rentflow.bookingmod.service;

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
import com.rentflow.common.idempotency.service.IdempotencyFailureMarker;
import com.rentflow.common.idempotency.service.IdempotencyResolution;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import com.rentflow.common.idempotency.service.IdempotencyService;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.deposit.service.DepositService;
import com.rentflow.outbox.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingModificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-07T00:00:00Z");
    private static final UUID BOOKING_ID = UUID.fromString("77777777-7777-4777-9777-777777777777");
    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-4111-9111-111111111111");
    private static final UUID HOST_ID = UUID.fromString("22222222-2222-4222-9222-222222222222");
    private static final UUID LISTING_ID = UUID.fromString("33333333-3333-4333-9333-333333333333");
    private static final UUID REQUEST_ID = UUID.fromString("44444444-4444-4444-9444-444444444444");
    private static final UUID IDEMPOTENCY_ID = UUID.fromString("55555555-5555-4555-9555-555555555555");
    private static final String IDEMPOTENCY_KEY = "8b71f8d2-9e1d-4f7a-bbe6-334c3816df91";
    private static final String HASH = "hash";

    @Mock private BookingModificationRequestRepository requestRepository;
    @Mock private LateReturnFeeRepository lateReturnFeeRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private AvailabilityCalendarRepository availabilityRepository;
    @Mock private SecurityContext securityContext;
    @Mock private IdempotencyService idempotencyService;
    @Mock private IdempotencyFailureMarker idempotencyFailureMarker;
    @Mock private BookingTimelineService bookingTimelineService;
    @Mock private AuditLogService auditLogService;
    @Mock private OutboxService outboxService;
    @Mock private DepositService depositService;

    private BookingModificationService service;

    @BeforeEach
    void setUp() {
        service = new BookingModificationService(
                requestRepository,
                lateReturnFeeRepository,
                bookingRepository,
                availabilityRepository,
                securityContext,
                idempotencyService,
                idempotencyFailureMarker,
                bookingTimelineService,
                auditLogService,
                outboxService,
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        service.setDepositService(depositService);
    }

    @Test
    void previewExtensionReturnsPriceDelta() {
        Booking booking = confirmedBooking();
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

        ModificationPreviewResponse response = service.preview(BOOKING_ID, extensionRequest());

        assertThat(response.eligible()).isTrue();
        assertThat(response.priceDelta()).isEqualByComparingTo("1400000.00");
        assertThat(response.currency()).isEqualTo("VND");
    }

    @Test
    void customerCreatesModificationRequest() {
        Booking booking = confirmedBooking();
        mockIdempotency(CUSTOMER_ID, IdempotencyScope.REQUEST_BOOKING_MODIFICATION);
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(requestRepository.save(any(BookingModificationRequest.class))).thenAnswer(invocation -> {
            BookingModificationRequest request = invocation.getArgument(0);
            request.setId(REQUEST_ID);
            return request;
        });

        BookingModificationResponse response = service.create(BOOKING_ID, IDEMPOTENCY_KEY, extensionRequest());

        assertThat(response.id()).isEqualTo(REQUEST_ID);
        assertThat(response.status()).isEqualTo(BookingModificationStatus.PENDING_HOST_APPROVAL);
        assertThat(response.priceDelta()).isEqualByComparingTo("1400000.00");
        verify(outboxService).append(eq("BOOKING_MODIFICATION"), eq(REQUEST_ID), eq("BOOKING_MODIFICATION_REQUESTED"), anyString());
    }

    @Test
    void hostApprovesExtensionAndBooksAddedAvailability() {
        Booking booking = confirmedBooking();
        BookingModificationRequest request = pendingRequest();
        List<AvailabilityCalendar> rows = List.of(
                new AvailabilityCalendar(LISTING_ID, LocalDate.of(2026, 6, 3)),
                new AvailabilityCalendar(LISTING_ID, LocalDate.of(2026, 6, 4)));
        mockIdempotency(HOST_ID, IdempotencyScope.APPROVE_BOOKING_MODIFICATION);
        when(securityContext.currentUserId()).thenReturn(HOST_ID);
        when(requestRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(request));
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(availabilityRepository.findForBookingRangeForUpdate(
                LISTING_ID,
                LocalDate.of(2026, 6, 3),
                LocalDate.of(2026, 6, 5))).thenReturn(rows);
        when(requestRepository.save(any(BookingModificationRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingModificationResponse response = service.approve(
                REQUEST_ID,
                IDEMPOTENCY_KEY,
                new ModificationDecisionRequest("Approved"));

        assertThat(response.status()).isEqualTo(BookingModificationStatus.APPROVED);
        assertThat(booking.getReturnDate()).isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo(AvailabilityStatus.BOOKED);
            assertThat(row.getBookingId()).isEqualTo(BOOKING_ID);
        });
        verify(availabilityRepository).saveAll(rows);
    }

    @Test
    void hostRejectsRequestWithoutChangingBookingDates() {
        Booking booking = confirmedBooking();
        BookingModificationRequest request = pendingRequest();
        mockIdempotency(HOST_ID, IdempotencyScope.REJECT_BOOKING_MODIFICATION);
        when(securityContext.currentUserId()).thenReturn(HOST_ID);
        when(requestRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(request));
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(requestRepository.save(any(BookingModificationRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingModificationResponse response = service.reject(
                REQUEST_ID,
                IDEMPOTENCY_KEY,
                new ModificationDecisionRequest("Unavailable"));

        assertThat(response.status()).isEqualTo(BookingModificationStatus.REJECTED);
        assertThat(booking.getReturnDate()).isEqualTo(LocalDate.of(2026, 6, 3));
        verify(availabilityRepository, never()).saveAll(any());
    }

    @Test
    void detectLateFeesCreatesSystemEvent() {
        Booking booking = confirmedBooking();
        booking.setStatus(BookingStatus.IN_PROGRESS);
        booking.setReturnDate(LocalDate.of(2026, 6, 5));
        when(bookingRepository.findOverdueInProgressBookings(LocalDate.of(2026, 6, 7), 10))
                .thenReturn(List.of(booking));
        when(lateReturnFeeRepository.existsByBookingIdAndStatus(BOOKING_ID, LateReturnFeeStatus.PENDING))
                .thenReturn(false);
        when(lateReturnFeeRepository.save(any(LateReturnFee.class))).thenAnswer(invocation -> {
            LateReturnFee fee = invocation.getArgument(0);
            fee.setId(UUID.randomUUID());
            return fee;
        });

        int created = service.detectLateFees(10);

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<LateReturnFee> feeCaptor = ArgumentCaptor.forClass(LateReturnFee.class);
        verify(lateReturnFeeRepository).save(feeCaptor.capture());
        assertThat(feeCaptor.getValue().getDaysLate()).isEqualTo(2);
        assertThat(feeCaptor.getValue().getFeeAmount()).isEqualByComparingTo("1680000.00");
        verify(bookingTimelineService).append(eq(BOOKING_ID), eq("LATE_RETURN_FEE_DETECTED"), eq(null), eq("SYSTEM"), anyString());
    }

    @Test
    void adminWaivesPendingLateFee() {
        UUID adminId = UUID.fromString("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa");
        LateReturnFee fee = pendingLateFee();
        mockIdempotency(adminId, IdempotencyScope.WAIVE_LATE_RETURN_FEE);
        when(securityContext.currentUserId()).thenReturn(adminId);
        when(securityContext.hasRole(Role.ADMIN)).thenReturn(true);
        when(lateReturnFeeRepository.findByIdForUpdate(fee.getId())).thenReturn(Optional.of(fee));
        when(lateReturnFeeRepository.save(any(LateReturnFee.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(confirmedBooking()));

        LateReturnFeeResponse response = service.waiveLateFee(
                fee.getId(),
                IDEMPOTENCY_KEY,
                new WaiveLateReturnFeeRequest("Courtesy"));

        assertThat(response.status()).isEqualTo(LateReturnFeeStatus.WAIVED);
        assertThat(response.waivedBy()).isEqualTo(adminId);
        assertThat(response.waiverReason()).isEqualTo("Courtesy");
    }

    @Test
    void adminChargesLateFeeThroughDeposit() {
        UUID adminId = UUID.fromString("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa");
        LateReturnFee fee = pendingLateFee();
        mockIdempotency(adminId, IdempotencyScope.CHARGE_LATE_RETURN_FEE);
        when(securityContext.currentUserId()).thenReturn(adminId);
        when(securityContext.hasRole(Role.ADMIN)).thenReturn(true);
        when(lateReturnFeeRepository.findByIdForUpdate(fee.getId())).thenReturn(Optional.of(fee));
        when(lateReturnFeeRepository.save(any(LateReturnFee.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(confirmedBooking()));

        LateReturnFeeResponse response = service.chargeLateFee(fee.getId(), IDEMPOTENCY_KEY);

        assertThat(response.status()).isEqualTo(LateReturnFeeStatus.CHARGED);
        verify(depositService).deductForLateReturnFee(BOOKING_ID, new BigDecimal("100000.00"));
    }

    private void mockIdempotency(UUID userId, IdempotencyScope scope) {
        when(idempotencyService.computeHash(any())).thenReturn(HASH);
        when(idempotencyService.resolve(eq(userId), eq(scope), eq(IDEMPOTENCY_KEY), eq(HASH)))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_ID));
    }

    private Booking confirmedBooking() {
        Booking booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setCustomerId(CUSTOMER_ID);
        booking.setHostId(HOST_ID);
        booking.setListingId(LISTING_ID);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPickupDate(LocalDate.of(2026, 6, 1));
        booking.setReturnDate(LocalDate.of(2026, 6, 3));
        booking.setPickupLocation("Ha Noi");
        booking.setReturnLocation("Ha Noi");
        booking.setPriceSnapshot("""
                {"basePricePerDay":700000.00,"currency":"VND","totalAmount":1400000.00}
                """);
        booking.setPolicySnapshot("{}");
        return booking;
    }

    private CreateModificationRequest extensionRequest() {
        return new CreateModificationRequest(
                BookingModificationType.TRIP_EXTENSION,
                null,
                LocalDate.of(2026, 6, 5),
                null,
                null,
                "Need two more days");
    }

    private BookingModificationRequest pendingRequest() {
        BookingModificationRequest request = new BookingModificationRequest();
        request.setId(REQUEST_ID);
        request.setBookingId(BOOKING_ID);
        request.setRequesterId(CUSTOMER_ID);
        request.setRequesterRole("CUSTOMER");
        request.setType(BookingModificationType.TRIP_EXTENSION);
        request.setStatus(BookingModificationStatus.PENDING_HOST_APPROVAL);
        request.setCurrentPickupDate(LocalDate.of(2026, 6, 1));
        request.setCurrentReturnDate(LocalDate.of(2026, 6, 3));
        request.setRequestedReturnDate(LocalDate.of(2026, 6, 5));
        request.setPriceDelta(new BigDecimal("1400000.00"));
        request.setFeeAmount(BigDecimal.ZERO);
        request.setCurrency("VND");
        request.setExpiresAt(NOW.plusSeconds(3600));
        return request;
    }

    private LateReturnFee pendingLateFee() {
        LateReturnFee fee = new LateReturnFee();
        fee.setId(UUID.randomUUID());
        fee.setBookingId(BOOKING_ID);
        fee.setStatus(LateReturnFeeStatus.PENDING);
        fee.setDetectedAt(NOW);
        fee.setExpectedReturnDate(LocalDate.of(2026, 6, 5));
        fee.setDaysLate(1);
        fee.setFeeAmount(new BigDecimal("100000.00"));
        fee.setCurrency("VND");
        return fee;
    }
}
