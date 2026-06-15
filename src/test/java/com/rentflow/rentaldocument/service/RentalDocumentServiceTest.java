package com.rentflow.rentaldocument.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.audit.service.AuditLogService;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.booking.service.BookingTimelineService;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.idempotency.service.IdempotencyFailureMarker;
import com.rentflow.common.idempotency.service.IdempotencyResolution;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import com.rentflow.common.idempotency.service.IdempotencyService;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.damage.entity.DamageClaim;
import com.rentflow.damage.entity.DamageClaimStatus;
import com.rentflow.damage.repository.DamageClaimRepository;
import com.rentflow.outbox.service.OutboxService;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.PaymentMethod;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.rentaldocument.dto.GenerateRentalDocumentRequest;
import com.rentflow.rentaldocument.dto.RentalDocumentResponse;
import com.rentflow.rentaldocument.entity.RentalDocument;
import com.rentflow.rentaldocument.entity.RentalDocumentType;
import com.rentflow.rentaldocument.repository.RentalDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RentalDocumentServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-07T00:00:00Z");
    private static final UUID BOOKING_ID = UUID.fromString("77777777-7777-4777-9777-777777777777");
    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-4111-9111-111111111111");
    private static final UUID HOST_ID = UUID.fromString("22222222-2222-4222-9222-222222222222");
    private static final UUID DOCUMENT_ID = UUID.fromString("33333333-3333-4333-9333-333333333333");
    private static final UUID PAYMENT_ID = UUID.fromString("44444444-4444-4444-9444-444444444444");
    private static final UUID CLAIM_ID = UUID.fromString("55555555-5555-4555-9555-555555555555");
    private static final UUID IDEMPOTENCY_ID = UUID.fromString("66666666-6666-4666-9666-666666666666");
    private static final String IDEMPOTENCY_KEY = "8b71f8d2-9e1d-4f7a-bbe6-334c3816df91";

    @Mock private RentalDocumentRepository documentRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private BookingPaymentRepository paymentRepository;
    @Mock private DamageClaimRepository damageClaimRepository;
    @Mock private SecurityContext securityContext;
    @Mock private IdempotencyService idempotencyService;
    @Mock private IdempotencyFailureMarker idempotencyFailureMarker;
    @Mock private BookingTimelineService bookingTimelineService;
    @Mock private AuditLogService auditLogService;
    @Mock private OutboxService outboxService;

    private RentalDocumentService service;

    @BeforeEach
    void setUp() {
        service = new RentalDocumentService(
                documentRepository,
                bookingRepository,
                paymentRepository,
                damageClaimRepository,
                securityContext,
                idempotencyService,
                idempotencyFailureMarker,
                bookingTimelineService,
                auditLogService,
                outboxService,
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void generatesEscapedRentalAgreement() {
        mockIdempotency(CUSTOMER_ID);
        Booking booking = booking();
        booking.setPickupLocation("<script>alert(1)</script>");
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(documentRepository.save(any(RentalDocument.class))).thenAnswer(invocation -> savedDocument(invocation.getArgument(0)));

        RentalDocumentResponse response = service.generate(
                BOOKING_ID,
                IDEMPOTENCY_KEY,
                new GenerateRentalDocumentRequest(RentalDocumentType.RENTAL_AGREEMENT, null));

        assertThat(response.type()).isEqualTo(RentalDocumentType.RENTAL_AGREEMENT);
        assertThat(response.htmlContent()).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        verify(outboxService).append(eq("RENTAL_DOCUMENT"), eq(DOCUMENT_ID), eq("RENTAL_DOCUMENT_GENERATED"), anyString());
    }

    @Test
    void generatesPaymentReceiptFromCapturedAmount() {
        mockIdempotency(CUSTOMER_ID);
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking()));
        when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(payment(new BigDecimal("900000.00"), BigDecimal.ZERO)));
        when(documentRepository.save(any(RentalDocument.class))).thenAnswer(invocation -> savedDocument(invocation.getArgument(0)));

        RentalDocumentResponse response = service.generate(
                BOOKING_ID,
                IDEMPOTENCY_KEY,
                new GenerateRentalDocumentRequest(RentalDocumentType.PAYMENT_RECEIPT, null));

        assertThat(response.htmlContent()).contains("900000.00");
        assertThat(response.sourceEntityType()).isEqualTo("BOOKING_PAYMENT");
    }

    @Test
    void refundReceiptRequiresRefundedAmount() {
        mockIdempotency(CUSTOMER_ID);
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking()));
        when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(payment(new BigDecimal("900000.00"), BigDecimal.ZERO)));

        assertThatThrownBy(() -> service.generate(
                BOOKING_ID,
                IDEMPOTENCY_KEY,
                new GenerateRentalDocumentRequest(RentalDocumentType.REFUND_RECEIPT, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Refund");
    }

    @Test
    void damageInvoiceRequiresApprovedClaim() {
        mockIdempotency(HOST_ID);
        DamageClaim claim = damageClaim(DamageClaimStatus.APPROVED);
        when(securityContext.currentUserId()).thenReturn(HOST_ID);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking()));
        when(damageClaimRepository.findById(CLAIM_ID)).thenReturn(Optional.of(claim));
        when(documentRepository.save(any(RentalDocument.class))).thenAnswer(invocation -> savedDocument(invocation.getArgument(0)));

        RentalDocumentResponse response = service.generate(
                BOOKING_ID,
                IDEMPOTENCY_KEY,
                new GenerateRentalDocumentRequest(RentalDocumentType.DAMAGE_INVOICE, CLAIM_ID));

        assertThat(response.htmlContent()).contains("Scratch");
        assertThat(response.sourceEntityType()).isEqualTo("DAMAGE_CLAIM");
    }

    private void mockIdempotency(UUID userId) {
        when(idempotencyService.computeHash(any())).thenReturn("hash");
        when(idempotencyService.resolve(eq(userId), eq(IdempotencyScope.GENERATE_RENTAL_DOCUMENT), eq(IDEMPOTENCY_KEY), eq("hash")))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_ID));
    }

    private RentalDocument savedDocument(RentalDocument document) {
        document.setId(DOCUMENT_ID);
        return document;
    }

    private Booking booking() {
        Booking booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setCustomerId(CUSTOMER_ID);
        booking.setHostId(HOST_ID);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPickupDate(LocalDate.of(2026, 6, 1));
        booking.setReturnDate(LocalDate.of(2026, 6, 3));
        booking.setPickupLocation("Ha Noi");
        booking.setReturnLocation("Ha Noi");
        booking.setPriceSnapshot("{}");
        booking.setPolicySnapshot("{}");
        return booking;
    }

    private BookingPayment payment(BigDecimal captured, BigDecimal refunded) {
        BookingPayment payment = new BookingPayment();
        payment.setId(PAYMENT_ID);
        payment.setBookingId(BOOKING_ID);
        payment.setPaymentMethod(PaymentMethod.COREBANK_TRANSFER);
        payment.setProvider(PaymentProviderType.COREBANK);
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setAuthorizedAmount(captured);
        payment.setCapturedAmount(captured);
        payment.setRefundedAmount(refunded);
        payment.setCurrency("VND");
        return payment;
    }

    private DamageClaim damageClaim(DamageClaimStatus status) {
        DamageClaim claim = new DamageClaim();
        claim.setId(CLAIM_ID);
        claim.setBookingId(BOOKING_ID);
        claim.setHostId(HOST_ID);
        claim.setCustomerId(CUSTOMER_ID);
        claim.setStatus(status);
        claim.setTitle("Scratch");
        claim.setDescription("Front bumper scratch");
        claim.setClaimAmount(new BigDecimal("300000.00"));
        claim.setApprovedAmount(new BigDecimal("250000.00"));
        claim.setCurrency("VND");
        claim.setSubmittedAt(NOW);
        return claim;
    }
}
