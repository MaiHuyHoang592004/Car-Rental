package com.rentflow.integration.booking;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.audit.repository.AuditLogRepository;
import com.rentflow.availability.entity.AvailabilityCalendar;
import com.rentflow.availability.entity.AvailabilityId;
import com.rentflow.availability.entity.AvailabilityStatus;
import com.rentflow.availability.repository.AvailabilityCalendarRepository;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.booking.repository.BookingTimelineEntryRepository;
import com.rentflow.common.idempotency.repository.IdempotencyKeyRepository;
import com.rentflow.common.security.JwtTokenProvider;
import com.rentflow.integration.BaseIntegrationTest;
import com.rentflow.listing.entity.CancellationPolicy;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.PaymentMethod;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.entity.PaymentTransaction;
import com.rentflow.payment.entity.PaymentTransactionStatus;
import com.rentflow.payment.entity.PaymentTransactionType;
import com.rentflow.payment.provider.corebank.CoreBankCaptureHoldResponse;
import com.rentflow.payment.provider.corebank.CoreBankCaptureHoldResult;
import com.rentflow.payment.provider.corebank.CoreBankPaymentClient;
import com.rentflow.payment.provider.corebank.CoreBankVoidHoldResponse;
import com.rentflow.payment.provider.corebank.CoreBankVoidHoldResult;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.repository.PaymentTransactionRepository;
import com.rentflow.notification.entity.NotificationType;
import com.rentflow.notification.repository.NotificationRepository;
import com.rentflow.vehicle.entity.FuelType;
import com.rentflow.vehicle.entity.TransmissionType;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.entity.VehicleCategory;
import com.rentflow.vehicle.entity.VehicleStatus;
import com.rentflow.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@TestPropertySource(properties = "rentflow.scheduler.expire-held-bookings.enabled=false")
class BookingCancelIntegrationTest extends BaseIntegrationTest {

    private static final String IDEMPOTENCY_KEY = "8b71f8d2-9e1d-4f7a-bbe6-334c3816df91";

    @Autowired private AuthUserRepository authUserRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingTimelineEntryRepository bookingTimelineEntryRepository;
    @Autowired private AvailabilityCalendarRepository availabilityRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private IdempotencyKeyRepository idempotencyKeyRepository;
    @Autowired private BookingPaymentRepository bookingPaymentRepository;
    @Autowired private PaymentTransactionRepository paymentTransactionRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @MockBean private CoreBankPaymentClient coreBankPaymentClient;

    private AuthUser customer;
    private AuthUser host;
    private AuthUser admin;
    private AuthUser otherCustomer;
    private String customerToken;
    private String hostToken;
    private String otherCustomerToken;
    private Listing listing;

    @BeforeEach
    void setUp() {
        bookingTimelineEntryRepository.deleteAll();
        auditLogRepository.deleteAll();
        paymentTransactionRepository.deleteAll();
        bookingPaymentRepository.deleteAll();
        availabilityRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        bookingRepository.deleteAll();
        listingRepository.deleteAll();
        vehicleRepository.deleteAll();
        authUserRepository.deleteAll();

        customer = saveUser("customer-" + UUID.randomUUID() + "@example.com", Role.CUSTOMER);
        host = saveUser("host-" + UUID.randomUUID() + "@example.com", Role.HOST);
        admin = saveUser("admin-" + UUID.randomUUID() + "@example.com", Role.ADMIN);
        otherCustomer = saveUser("other-" + UUID.randomUUID() + "@example.com", Role.CUSTOMER);
        customerToken = jwtTokenProvider.generateAccessToken(customer.getId(), customer.getEmail(), List.of(Role.CUSTOMER));
        hostToken = jwtTokenProvider.generateAccessToken(host.getId(), host.getEmail(), List.of(Role.HOST));
        otherCustomerToken = jwtTokenProvider.generateAccessToken(
                otherCustomer.getId(),
                otherCustomer.getEmail(),
                List.of(Role.CUSTOMER));
        listing = saveListing(host);
    }

    @Test
    void cancelHeldBookingSucceedsAndReleasesAvailability() throws Exception {
        Booking booking = saveBooking(BookingStatus.HELD);
        saveHeldAvailability(booking);

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"<b>Change</b> of plan"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(booking.getId().toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Change of plan"));

        Booking cancelled = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelled.getCancellationReason()).isEqualTo("Change of plan");

        var first = availabilityRepository.findById(new AvailabilityId(listing.getId(), booking.getPickupDate())).orElseThrow();
        var second = availabilityRepository.findById(new AvailabilityId(listing.getId(), booking.getPickupDate().plusDays(1))).orElseThrow();
        assertThat(List.of(first, second)).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo(AvailabilityStatus.FREE);
            assertThat(row.getBookingId()).isNull();
            assertThat(row.getHoldToken()).isNull();
            assertThat(row.getHoldExpiresAt()).isNull();
        });
    }

    @Test
    void cancelHeldBookingSameKeyReplays() throws Exception {
        Booking booking = saveBooking(BookingStatus.HELD);
        saveHeldAvailability(booking);
        String body = """
                {"reason":"Change of plan"}
                """;

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Change of plan"));
    }

    @Test
    void cancelConfirmedBookingReturnsInvalidStatus() throws Exception {
        Booking booking = saveBooking(BookingStatus.CONFIRMED);
        booking.setPickupDate(LocalDate.now().minusDays(1));
        booking.setReturnDate(booking.getPickupDate().plusDays(2));
        bookingRepository.save(booking);

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Change of plan"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_INVALID_STATUS"));
    }

    @Test
    void cancelPendingHostApprovalVoidsPaymentAndReleasesAvailability() throws Exception {
        Booking booking = saveBooking(BookingStatus.PENDING_HOST_APPROVAL);
        saveHeldAvailability(booking);
        saveAuthorizedPayment(booking.getId());
        when(coreBankPaymentClient.voidHold(any())).thenReturn(new CoreBankVoidHoldResult(
                new CoreBankVoidHoldResponse("hold-1", "VOIDED"),
                "{\"holdId\":\"hold-1\",\"status\":\"VOIDED\"}"));

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Cancel pending"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        BookingPayment payment = bookingPaymentRepository.findByBookingId(booking.getId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.VOIDED);
        assertThat(bookingTimelineEntryRepository.findByBookingIdOrderByCreatedAtAsc(booking.getId()))
                .extracting(entry -> entry.getEventType())
                .contains("PAYMENT_VOIDED", "BOOKING_CANCELLED");
    }

    @Test
    void cancelPendingHostApprovalWhenVoidFailsReturnsAcceptedAndPersistsRetryMetadata() throws Exception {
        Booking booking = saveBooking(BookingStatus.PENDING_HOST_APPROVAL);
        saveHeldAvailability(booking);
        saveAuthorizedPayment(booking.getId());
        when(coreBankPaymentClient.voidHold(any()))
                .thenThrow(new com.rentflow.common.exception.PaymentProviderUnavailableException("void fail"));

        Instant before = Instant.now();
        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Cancel pending"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.voidRetryRequired").value(true))
                .andExpect(jsonPath("$.code").value("PAYMENT_VOID_RETRY_REQUIRED"));

        Booking cancelled = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        BookingPayment payment = bookingPaymentRepository.findByBookingId(booking.getId()).orElseThrow();
        assertThat(payment.isVoidRetryRequired()).isTrue();
        assertThat(payment.getVoidRetryCount()).isEqualTo(1);
        assertThat(payment.getVoidRetryLastError()).contains("void fail");
        assertThat(payment.getVoidRetryNextAt()).isNotNull();
        assertThat(payment.getVoidRetryNextAt()).isAfter(before.plusSeconds(250));
        assertThat(payment.getVoidRetryNextAt()).isBefore(before.plusSeconds(360));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(payment.getProviderStatus()).isEqualTo("VOID_RETRY_REQUIRED");

        List<PaymentTransaction> txns = paymentTransactionRepository.findByBookingPaymentIdOrderByCreatedAtAsc(payment.getId());
        assertThat(txns).hasSize(1);
        assertThat(txns.get(0).getType()).isEqualTo(PaymentTransactionType.VOID);
        assertThat(txns.get(0).getStatus()).isEqualTo(PaymentTransactionStatus.FAILED);
    }

    @Test
    void cancelConfirmedFullRefundVoidsAuthorizationWithoutCapture() throws Exception {
        Booking booking = saveBooking(BookingStatus.CONFIRMED);
        booking.setPickupDate(LocalDate.now().plusDays(3));
        booking.setReturnDate(booking.getPickupDate().plusDays(2));
        booking.setPolicySnapshot("""
                {"cancellationPolicy":"FLEXIBLE","instantBook":true,"dailyKmLimit":200}
                """);
        bookingRepository.save(booking);
        saveBookedAvailability(booking);
        saveAuthorizedPayment(booking.getId());
        when(coreBankPaymentClient.voidHold(any())).thenReturn(new CoreBankVoidHoldResult(
                new CoreBankVoidHoldResponse("hold-1", "VOIDED"),
                "{\"holdId\":\"hold-1\",\"status\":\"VOIDED\"}"));

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Early cancel"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.voidRetryRequired").value(false));

        BookingPayment payment = bookingPaymentRepository.findByBookingId(booking.getId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.VOIDED);
        assertThat(payment.getCapturedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void cancelConfirmedPartialPenaltyCaptureThenVoidSucceeds() throws Exception {
        Booking booking = saveBooking(BookingStatus.CONFIRMED);
        booking.setPickupDate(LocalDate.now().plusDays(2));
        booking.setReturnDate(booking.getPickupDate().plusDays(2));
        booking.setPolicySnapshot("""
                {"cancellationPolicy":"MODERATE","instantBook":true,"dailyKmLimit":200}
                """);
        bookingRepository.save(booking);
        saveBookedAvailability(booking);
        saveAuthorizedPayment(booking.getId());
        when(coreBankPaymentClient.captureHold(any())).thenReturn(new CoreBankCaptureHoldResult(
                new CoreBankCaptureHoldResponse("payment-order-1", "journal-1", "CAPTURED"),
                "{\"paymentOrderId\":\"payment-order-1\",\"journalId\":\"journal-1\",\"status\":\"CAPTURED\"}"));
        when(coreBankPaymentClient.voidHold(any())).thenReturn(new CoreBankVoidHoldResult(
                new CoreBankVoidHoldResponse("hold-1", "VOIDED"),
                "{\"holdId\":\"hold-1\",\"status\":\"VOIDED\"}"));

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Late cancel"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.voidRetryRequired").value(false));

        BookingPayment payment = bookingPaymentRepository.findByBookingId(booking.getId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.getCapturedAmount()).isEqualByComparingTo("750000.00");
        assertThat(payment.getProviderStatus()).isEqualTo("VOIDED");

        List<PaymentTransaction> txns = paymentTransactionRepository.findByBookingPaymentIdOrderByCreatedAtAsc(payment.getId());
        assertThat(txns).hasSize(2);
        assertThat(txns.get(0).getType()).isEqualTo(PaymentTransactionType.CAPTURE);
        assertThat(txns.get(1).getType()).isEqualTo(PaymentTransactionType.VOID);
        assertThat(bookingTimelineEntryRepository.findByBookingIdOrderByCreatedAtAsc(booking.getId()))
                .extracting(entry -> entry.getEventType())
                .containsSubsequence("PAYMENT_CAPTURED", "PAYMENT_VOIDED", "BOOKING_CANCELLED");
    }

    @Test
    void cancelConfirmedPartialPenaltyWhenVoidFailsReturnsAccepted() throws Exception {
        Booking booking = saveBooking(BookingStatus.CONFIRMED);
        booking.setPickupDate(LocalDate.now().plusDays(2));
        booking.setReturnDate(booking.getPickupDate().plusDays(2));
        booking.setPolicySnapshot("""
                {"cancellationPolicy":"MODERATE","instantBook":true,"dailyKmLimit":200}
                """);
        bookingRepository.save(booking);
        saveBookedAvailability(booking);
        saveAuthorizedPayment(booking.getId());
        when(coreBankPaymentClient.captureHold(any())).thenReturn(new CoreBankCaptureHoldResult(
                new CoreBankCaptureHoldResponse("payment-order-1", "journal-1", "CAPTURED"),
                "{\"paymentOrderId\":\"payment-order-1\",\"journalId\":\"journal-1\",\"status\":\"CAPTURED\"}"));
        when(coreBankPaymentClient.voidHold(any())).thenThrow(new com.rentflow.common.exception.PaymentProviderUnavailableException("void fail"));

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Late cancel"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.voidRetryRequired").value(true))
                .andExpect(jsonPath("$.code").value("PAYMENT_VOID_RETRY_REQUIRED"));

        Booking cancelled = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        BookingPayment payment = bookingPaymentRepository.findByBookingId(booking.getId()).orElseThrow();
        assertThat(payment.getAuthorizedAmount()).isEqualByComparingTo("1500000.00");
        assertThat(payment.getCapturedAmount()).isEqualByComparingTo("750000.00");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.getProviderStatus()).isEqualTo("VOID_RETRY_REQUIRED");
        assertThat(payment.isVoidRetryRequired()).isTrue();
        assertThat(payment.getVoidRetryCount()).isEqualTo(1);
        assertThat(payment.getVoidRetryLastError()).contains("void fail");
        assertThat(payment.getVoidRetryNextAt()).isNotNull();

        List<PaymentTransaction> txns = paymentTransactionRepository.findByBookingPaymentIdOrderByCreatedAtAsc(payment.getId());
        assertThat(txns).hasSize(2);
        assertThat(txns.get(0).getType()).isEqualTo(PaymentTransactionType.CAPTURE);
        assertThat(txns.get(0).getStatus()).isEqualTo(PaymentTransactionStatus.SUCCEEDED);
        assertThat(txns.get(0).getAmount()).isEqualByComparingTo("750000.00");
        assertThat(txns.get(1).getType()).isEqualTo(PaymentTransactionType.VOID);
        assertThat(txns.get(1).getStatus()).isEqualTo(PaymentTransactionStatus.FAILED);

        AvailabilityCalendar first = availabilityRepository.findById(new AvailabilityId(listing.getId(), booking.getPickupDate())).orElseThrow();
        AvailabilityCalendar second = availabilityRepository.findById(new AvailabilityId(listing.getId(), booking.getPickupDate().plusDays(1))).orElseThrow();
        assertThat(List.of(first, second)).allSatisfy(row -> assertThat(row.getStatus()).isEqualTo(AvailabilityStatus.FREE));
    }

    @Test
    void cancelConfirmedPartialPenaltyVoidFailureCreatesAdminNotification() throws Exception {
        Booking booking = saveBooking(BookingStatus.CONFIRMED);
        booking.setPickupDate(LocalDate.now().plusDays(2));
        booking.setReturnDate(booking.getPickupDate().plusDays(2));
        booking.setPolicySnapshot("""
                {"cancellationPolicy":"MODERATE","instantBook":true,"dailyKmLimit":200}
                """);
        bookingRepository.save(booking);
        saveBookedAvailability(booking);
        saveAuthorizedPayment(booking.getId());
        when(coreBankPaymentClient.captureHold(any())).thenReturn(new CoreBankCaptureHoldResult(
                new CoreBankCaptureHoldResponse("payment-order-1", "journal-1", "CAPTURED"),
                "{\"paymentOrderId\":\"payment-order-1\",\"journalId\":\"journal-1\",\"status\":\"CAPTURED\"}"));
        when(coreBankPaymentClient.voidHold(any())).thenThrow(new com.rentflow.common.exception.PaymentProviderUnavailableException("void fail"));

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Late cancel"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("PAYMENT_VOID_RETRY_REQUIRED"));

        var notifications = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(admin.getId(), PageRequest.of(0, 20))
                .getContent();
        assertThat(notifications)
                .extracting(n -> n.getType().name())
                .contains(NotificationType.PAYMENT_VOID_RETRY_REQUIRED.name());
    }

    @Test
    void cancelConfirmedPartialPenaltyAuditDetailsAreSanitized() throws Exception {
        Booking booking = saveBooking(BookingStatus.CONFIRMED);
        booking.setPickupDate(LocalDate.now().plusDays(2));
        booking.setReturnDate(booking.getPickupDate().plusDays(2));
        booking.setPolicySnapshot("""
                {"cancellationPolicy":"MODERATE","instantBook":true,"dailyKmLimit":200}
                """);
        bookingRepository.save(booking);
        saveBookedAvailability(booking);
        BookingPayment payment = saveAuthorizedPayment(booking.getId());
        when(coreBankPaymentClient.captureHold(any())).thenReturn(new CoreBankCaptureHoldResult(
                new CoreBankCaptureHoldResponse("payment-order-1", "journal-1", "CAPTURED"),
                "{\"paymentOrderId\":\"payment-order-1\",\"journalId\":\"journal-1\",\"status\":\"CAPTURED\"}"));
        when(coreBankPaymentClient.voidHold(any())).thenReturn(new CoreBankVoidHoldResult(
                new CoreBankVoidHoldResponse("hold-1", "VOIDED"),
                "{\"holdId\":\"hold-1\",\"status\":\"VOIDED\"}"));

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Late cancel"}
                                """))
                .andExpect(status().isOk());

        assertThat(auditLogRepository.findAll())
                .filteredOn(log -> payment.getId().equals(log.getTargetId()))
                .extracting(log -> log.getDetails())
                .allSatisfy(details -> {
                    assertThat((String) details).contains("\"providerPaymentOrderId\": \"[REDACTED]\"");
                    assertThat((String) details).contains("\"providerHoldId\": \"[REDACTED]\"");
                });
    }

    @Test
    void cancelConfirmedPartialPenaltyWhenVoidFailsThenReplayReturns202WithoutReinvokingPayments() throws Exception {
        Booking booking = saveBooking(BookingStatus.CONFIRMED);
        booking.setPickupDate(LocalDate.now().plusDays(2));
        booking.setReturnDate(booking.getPickupDate().plusDays(2));
        booking.setPolicySnapshot("""
                {"cancellationPolicy":"MODERATE","instantBook":true,"dailyKmLimit":200}
                """);
        bookingRepository.save(booking);
        saveBookedAvailability(booking);
        saveAuthorizedPayment(booking.getId());
        when(coreBankPaymentClient.captureHold(any())).thenReturn(new CoreBankCaptureHoldResult(
                new CoreBankCaptureHoldResponse("payment-order-1", "journal-1", "CAPTURED"),
                "{\"paymentOrderId\":\"payment-order-1\",\"journalId\":\"journal-1\",\"status\":\"CAPTURED\"}"));
        when(coreBankPaymentClient.voidHold(any())).thenThrow(new com.rentflow.common.exception.PaymentProviderUnavailableException("void fail"));

        String key = UUID.randomUUID().toString();
        String body = """
                {"reason":"Late cancel"}
                """;

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.voidRetryRequired").value(true))
                .andExpect(jsonPath("$.code").value("PAYMENT_VOID_RETRY_REQUIRED"));

        BookingPayment afterFirstAttempt = bookingPaymentRepository.findByBookingId(booking.getId()).orElseThrow();
        Instant retryNextAt = afterFirstAttempt.getVoidRetryNextAt();
        Integer retryCount = afterFirstAttempt.getVoidRetryCount();

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.voidRetryRequired").value(true))
                .andExpect(jsonPath("$.code").value("PAYMENT_VOID_RETRY_REQUIRED"));

        BookingPayment payment = bookingPaymentRepository.findByBookingId(booking.getId()).orElseThrow();
        assertThat(payment.getCapturedAmount()).isEqualByComparingTo("750000.00");
        assertThat(payment.isVoidRetryRequired()).isTrue();
        assertThat(payment.getVoidRetryCount()).isEqualTo(retryCount);
        assertThat(payment.getVoidRetryNextAt()).isEqualTo(retryNextAt);
        assertThat(payment.getVoidRetryLastError()).contains("void fail");
        List<PaymentTransaction> txns = paymentTransactionRepository.findByBookingPaymentIdOrderByCreatedAtAsc(payment.getId());
        assertThat(txns).hasSize(2);
    }

    @Test
    void cancelConfirmedPartialPenaltyCallsProvidersOutsideDatabaseTransaction() throws Exception {
        Booking booking = saveBooking(BookingStatus.CONFIRMED);
        booking.setPickupDate(LocalDate.now().plusDays(2));
        booking.setReturnDate(booking.getPickupDate().plusDays(2));
        booking.setPolicySnapshot("""
                {"cancellationPolicy":"MODERATE","instantBook":true,"dailyKmLimit":200}
                """);
        bookingRepository.save(booking);
        saveBookedAvailability(booking);
        saveAuthorizedPayment(booking.getId());
        AtomicBoolean captureCalledOutsideTx = new AtomicBoolean(false);
        AtomicBoolean voidCalledOutsideTx = new AtomicBoolean(false);
        when(coreBankPaymentClient.captureHold(any())).thenAnswer(invocation -> {
            captureCalledOutsideTx.set(!TransactionSynchronizationManager.isActualTransactionActive());
            return new CoreBankCaptureHoldResult(
                    new CoreBankCaptureHoldResponse("payment-order-1", "journal-1", "CAPTURED"),
                    "{\"paymentOrderId\":\"payment-order-1\",\"journalId\":\"journal-1\",\"status\":\"CAPTURED\"}");
        });
        when(coreBankPaymentClient.voidHold(any())).thenAnswer(invocation -> {
            voidCalledOutsideTx.set(!TransactionSynchronizationManager.isActualTransactionActive());
            return new CoreBankVoidHoldResult(
                    new CoreBankVoidHoldResponse("hold-1", "VOIDED"),
                    "{\"holdId\":\"hold-1\",\"status\":\"VOIDED\"}");
        });

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Late cancel"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(captureCalledOutsideTx).isTrue();
        assertThat(voidCalledOutsideTx).isTrue();
    }

    @Test
    void cancelFullRefundDetectsBookingDriftAfterVoidProviderSuccess() throws Exception {
        Booking booking = saveBooking(BookingStatus.CONFIRMED);
        booking.setPickupDate(LocalDate.now().plusDays(3));
        booking.setReturnDate(booking.getPickupDate().plusDays(2));
        bookingRepository.save(booking);
        saveBookedAvailability(booking);
        BookingPayment payment = saveAuthorizedPayment(booking.getId());
        when(coreBankPaymentClient.voidHold(any())).thenAnswer(invocation -> {
            Booking racedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
            racedBooking.setStatus(BookingStatus.REJECTED);
            bookingRepository.save(racedBooking);
            return new CoreBankVoidHoldResult(
                    new CoreBankVoidHoldResponse("hold-1", "VOIDED"),
                    "{\"holdId\":\"hold-1\",\"status\":\"VOIDED\"}");
        });

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Race cancel"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_FINALIZATION_UNSAFE"));

        Booking updatedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(updatedBooking.getStatus()).isEqualTo(BookingStatus.REJECTED);
        BookingPayment updatedPayment = bookingPaymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        PaymentTransaction voidTx = onlyTransaction(payment.getId(), PaymentTransactionType.VOID);
        assertThat(voidTx.getStatus()).isEqualTo(PaymentTransactionStatus.FAILED);
        assertThat(voidTx.getProviderErrorCode()).isEqualTo("PAYMENT_FINALIZATION_UNSAFE");
    }

    @Test
    void cancelFullRefundDetectsPaymentProviderReferenceDriftAfterVoidProviderSuccess() throws Exception {
        Booking booking = saveBooking(BookingStatus.CONFIRMED);
        booking.setPickupDate(LocalDate.now().plusDays(3));
        booking.setReturnDate(booking.getPickupDate().plusDays(2));
        bookingRepository.save(booking);
        saveBookedAvailability(booking);
        BookingPayment payment = saveAuthorizedPayment(booking.getId());
        when(coreBankPaymentClient.voidHold(any())).thenAnswer(invocation -> {
            BookingPayment racedPayment = bookingPaymentRepository.findById(payment.getId()).orElseThrow();
            racedPayment.setProviderHoldId("changed-hold");
            bookingPaymentRepository.save(racedPayment);
            return new CoreBankVoidHoldResult(
                    new CoreBankVoidHoldResponse("hold-1", "VOIDED"),
                    "{\"holdId\":\"hold-1\",\"status\":\"VOIDED\"}");
        });

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Race payment"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_FINALIZATION_UNSAFE"));

        Booking updatedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(updatedBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        BookingPayment updatedPayment = bookingPaymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updatedPayment.getProviderHoldId()).isEqualTo("changed-hold");
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        PaymentTransaction voidTx = onlyTransaction(payment.getId(), PaymentTransactionType.VOID);
        assertThat(voidTx.getStatus()).isEqualTo(PaymentTransactionStatus.FAILED);
        assertThat(voidTx.getProviderErrorCode()).isEqualTo("PAYMENT_FINALIZATION_UNSAFE");
    }

    @Test
    void cancelPartialPenaltyDetectsCaptureTransactionDriftAfterCaptureProviderSuccess() throws Exception {
        Booking booking = saveBooking(BookingStatus.CONFIRMED);
        booking.setPickupDate(LocalDate.now().plusDays(2));
        booking.setReturnDate(booking.getPickupDate().plusDays(2));
        booking.setPolicySnapshot("""
                {"cancellationPolicy":"MODERATE","instantBook":true,"dailyKmLimit":200}
                """);
        bookingRepository.save(booking);
        saveBookedAvailability(booking);
        BookingPayment payment = saveAuthorizedPayment(booking.getId());
        when(coreBankPaymentClient.captureHold(any())).thenAnswer(invocation -> {
            PaymentTransaction captureTx = onlyTransaction(payment.getId(), PaymentTransactionType.CAPTURE);
            captureTx.setStatus(PaymentTransactionStatus.SUCCEEDED);
            paymentTransactionRepository.save(captureTx);
            return new CoreBankCaptureHoldResult(
                    new CoreBankCaptureHoldResponse("payment-order-1", "journal-1", "CAPTURED"),
                    "{\"paymentOrderId\":\"payment-order-1\",\"journalId\":\"journal-1\",\"status\":\"CAPTURED\"}");
        });

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Capture drift"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_FINALIZATION_UNSAFE"));

        verify(coreBankPaymentClient, never()).voidHold(any());
        Booking updatedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(updatedBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        BookingPayment updatedPayment = bookingPaymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updatedPayment.getCapturedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        PaymentTransaction captureTx = onlyTransaction(payment.getId(), PaymentTransactionType.CAPTURE);
        assertThat(captureTx.getStatus()).isEqualTo(PaymentTransactionStatus.FAILED);
        assertThat(captureTx.getProviderErrorCode()).isEqualTo("PAYMENT_FINALIZATION_UNSAFE");
    }

    @Test
    void cancelFullRefundDetectsVoidTransactionDriftAfterVoidProviderSuccess() throws Exception {
        Booking booking = saveBooking(BookingStatus.CONFIRMED);
        booking.setPickupDate(LocalDate.now().plusDays(3));
        booking.setReturnDate(booking.getPickupDate().plusDays(2));
        bookingRepository.save(booking);
        saveBookedAvailability(booking);
        BookingPayment payment = saveAuthorizedPayment(booking.getId());
        when(coreBankPaymentClient.voidHold(any())).thenAnswer(invocation -> {
            PaymentTransaction voidTx = onlyTransaction(payment.getId(), PaymentTransactionType.VOID);
            voidTx.setStatus(PaymentTransactionStatus.SUCCEEDED);
            paymentTransactionRepository.save(voidTx);
            return new CoreBankVoidHoldResult(
                    new CoreBankVoidHoldResponse("hold-1", "VOIDED"),
                    "{\"holdId\":\"hold-1\",\"status\":\"VOIDED\"}");
        });

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Void drift"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_FINALIZATION_UNSAFE"));

        Booking updatedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(updatedBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        BookingPayment updatedPayment = bookingPaymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        PaymentTransaction voidTx = onlyTransaction(payment.getId(), PaymentTransactionType.VOID);
        assertThat(voidTx.getStatus()).isEqualTo(PaymentTransactionStatus.FAILED);
        assertThat(voidTx.getProviderErrorCode()).isEqualTo("PAYMENT_FINALIZATION_UNSAFE");
    }

    @Test
    void cancelByNonOwnerReturnsNotFound() throws Exception {
        Booking booking = saveBooking(BookingStatus.HELD);
        saveHeldAvailability(booking);

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + otherCustomerToken)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Change of plan"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void hostCannotUseCustomerCancelEndpoint() throws Exception {
        Booking booking = saveBooking(BookingStatus.CONFIRMED);
        booking.setPickupDate(LocalDate.now().plusDays(2));
        booking.setReturnDate(booking.getPickupDate().plusDays(2));
        bookingRepository.save(booking);
        saveBookedAvailability(booking);
        saveAuthorizedPayment(booking.getId());

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + hostToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Host tries customer cancel"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void cancelMissingIdempotencyKeyReturnsRequired() throws Exception {
        Booking booking = saveBooking(BookingStatus.HELD);

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Change of plan"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    private AuthUser saveUser(String email, Role role) {
        AuthUser user = new AuthUser(email, "hash", UserStatus.ACTIVE, true);
        user.addRole(role);
        return authUserRepository.save(user);
    }

    private Listing saveListing(AuthUser hostUser) {
        Vehicle vehicle = new Vehicle();
        vehicle.setHostId(hostUser.getId());
        vehicle.setCategory(VehicleCategory.SEDAN);
        vehicle.setMake("Toyota");
        vehicle.setModel("Vios");
        vehicle.setManufactureYear(2022);
        vehicle.setTransmission(TransmissionType.AUTO);
        vehicle.setFuelType(FuelType.PETROL);
        vehicle.setSeats(5);
        vehicle.setCity("Hanoi");
        vehicle.setStatus(VehicleStatus.ACTIVE);
        vehicle = vehicleRepository.save(vehicle);

        Listing newListing = new Listing();
        newListing.setHostId(hostUser.getId());
        newListing.setVehicleId(vehicle.getId());
        newListing.setTitle("Toyota Vios 2022");
        newListing.setCity("Hanoi");
        newListing.setBasePricePerDay(new BigDecimal("700000.00"));
        newListing.setCurrency("VND");
        newListing.setInstantBook(true);
        newListing.setCancellationPolicy(CancellationPolicy.FLEXIBLE);
        newListing.setStatus(ListingStatus.ACTIVE);
        return listingRepository.save(newListing);
    }

    private Booking saveBooking(BookingStatus status) {
        Booking booking = new Booking();
        booking.setCustomerId(customer.getId());
        booking.setHostId(host.getId());
        booking.setListingId(listing.getId());
        booking.setPickupDate(LocalDate.of(2026, 6, 1));
        booking.setReturnDate(LocalDate.of(2026, 6, 3));
        booking.setStatus(status);
        booking.setHoldToken(UUID.randomUUID());
        booking.setHoldExpiresAt(Instant.parse("2026-05-11T00:15:00Z"));
        booking.setPriceSnapshot("""
                {"totalAmount":1500000.00,"currency":"VND","extras":[]}
                """);
        booking.setPolicySnapshot("""
                {"cancellationPolicy":"FLEXIBLE","instantBook":true,"dailyKmLimit":200}
                """);
        return bookingRepository.save(booking);
    }

    private void saveHeldAvailability(Booking booking) {
        AvailabilityCalendar first = new AvailabilityCalendar(listing.getId(), booking.getPickupDate());
        first.setStatus(AvailabilityStatus.HOLD);
        first.setBookingId(booking.getId());
        first.setHoldToken(booking.getHoldToken());
        first.setHoldExpiresAt(booking.getHoldExpiresAt());
        AvailabilityCalendar second = new AvailabilityCalendar(listing.getId(), booking.getPickupDate().plusDays(1));
        second.setStatus(AvailabilityStatus.HOLD);
        second.setBookingId(booking.getId());
        second.setHoldToken(booking.getHoldToken());
        second.setHoldExpiresAt(booking.getHoldExpiresAt());
        availabilityRepository.saveAll(List.of(first, second));
    }

    private void saveBookedAvailability(Booking booking) {
        AvailabilityCalendar first = new AvailabilityCalendar(listing.getId(), booking.getPickupDate());
        first.setStatus(AvailabilityStatus.BOOKED);
        first.setBookingId(booking.getId());
        AvailabilityCalendar second = new AvailabilityCalendar(listing.getId(), booking.getPickupDate().plusDays(1));
        second.setStatus(AvailabilityStatus.BOOKED);
        second.setBookingId(booking.getId());
        availabilityRepository.saveAll(List.of(first, second));
    }

    private BookingPayment saveAuthorizedPayment(UUID bookingId) {
        BookingPayment payment = new BookingPayment();
        payment.setBookingId(bookingId);
        payment.setSelectedBankId(UUID.fromString("00000000-0000-0000-0000-000000000111"));
        payment.setPaymentMethod(PaymentMethod.COREBANK_TRANSFER);
        payment.setProvider(PaymentProviderType.COREBANK);
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setAuthorizedAmount(new BigDecimal("1500000.00"));
        payment.setCapturedAmount(BigDecimal.ZERO);
        payment.setRefundedAmount(BigDecimal.ZERO);
        payment.setCurrency("VND");
        payment.setExternalOrderRef("rentflow:booking:" + bookingId);
        payment.setProviderPaymentOrderId("payment-order-1");
        payment.setProviderHoldId("hold-1");
        payment.setProviderStatus("AUTHORIZED");
        return bookingPaymentRepository.save(payment);
    }

    private PaymentTransaction onlyTransaction(UUID paymentId, PaymentTransactionType type) {
        return paymentTransactionRepository.findByBookingPaymentIdOrderByCreatedAtAsc(paymentId).stream()
                .filter(tx -> tx.getType() == type)
                .reduce((first, second) -> {
                    throw new AssertionError("Expected one " + type + " transaction, found multiple");
                })
                .orElseThrow(() -> new AssertionError("Expected one " + type + " transaction"));
    }
}
