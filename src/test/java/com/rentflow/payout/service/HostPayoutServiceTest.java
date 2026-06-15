package com.rentflow.payout.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.audit.service.AuditLogService;
import com.rentflow.auth.entity.Role;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.idempotency.service.IdempotencyFailureMarker;
import com.rentflow.common.idempotency.service.IdempotencyResolution;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import com.rentflow.common.idempotency.service.IdempotencyService;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.notification.entity.NotificationType;
import com.rentflow.notification.service.NotificationService;
import com.rentflow.outbox.service.OutboxService;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.PaymentMethod;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payout.dto.HostPayoutAccountRequest;
import com.rentflow.payout.dto.HostPayoutAccountResponse;
import com.rentflow.payout.dto.HostPayoutResponse;
import com.rentflow.payout.dto.HostPayoutTransitionRequest;
import com.rentflow.payout.entity.HostPayout;
import com.rentflow.payout.entity.HostPayoutAccount;
import com.rentflow.payout.entity.HostPayoutStatus;
import com.rentflow.payout.repository.HostPayoutAccountRepository;
import com.rentflow.payout.repository.HostPayoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
class HostPayoutServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-07T00:00:00Z");
    private static final UUID BOOKING_ID = UUID.fromString("77777777-7777-4777-9777-777777777777");
    private static final UUID HOST_ID = UUID.fromString("22222222-2222-4222-9222-222222222222");
    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-4111-9111-111111111111");
    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ACCOUNT_ID = UUID.fromString("33333333-3333-4333-9333-333333333333");
    private static final UUID PAYOUT_ID = UUID.fromString("44444444-4444-4444-9444-444444444444");
    private static final UUID IDEMPOTENCY_ID = UUID.fromString("55555555-5555-4555-9555-555555555555");
    private static final String IDEMPOTENCY_KEY = "8b71f8d2-9e1d-4f7a-bbe6-334c3816df91";

    @Mock private HostPayoutAccountRepository accountRepository;
    @Mock private HostPayoutRepository payoutRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private BookingPaymentRepository paymentRepository;
    @Mock private SecurityContext securityContext;
    @Mock private IdempotencyService idempotencyService;
    @Mock private IdempotencyFailureMarker idempotencyFailureMarker;
    @Mock private NotificationService notificationService;
    @Mock private AuditLogService auditLogService;
    @Mock private OutboxService outboxService;

    private HostPayoutService service;

    @BeforeEach
    void setUp() {
        service = new HostPayoutService(
                accountRepository,
                payoutRepository,
                bookingRepository,
                paymentRepository,
                securityContext,
                idempotencyService,
                idempotencyFailureMarker,
                notificationService,
                auditLogService,
                outboxService,
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void hostUpsertsPayoutAccount() {
        mockIdempotency(HOST_ID, IdempotencyScope.UPSERT_HOST_PAYOUT_ACCOUNT);
        when(securityContext.currentUserId()).thenReturn(HOST_ID);
        when(accountRepository.findByHostIdForUpdate(HOST_ID)).thenReturn(Optional.empty());
        when(accountRepository.save(any(HostPayoutAccount.class))).thenAnswer(invocation -> {
            HostPayoutAccount account = invocation.getArgument(0);
            account.setId(ACCOUNT_ID);
            return account;
        });

        HostPayoutAccountResponse response = service.upsertAccount(
                IDEMPOTENCY_KEY,
                new HostPayoutAccountRequest("Nguyen Van A", "VCB", "1234"));

        assertThat(response.hostId()).isEqualTo(HOST_ID);
        assertThat(response.accountLast4()).isEqualTo("1234");
    }

    @Test
    void queueCreatesPendingPayoutWhenAccountExists() {
        when(paymentRepository.findPayoutEligibleCapturedPaymentsForUpdate(10)).thenReturn(List.of(payment()));
        when(payoutRepository.existsByBookingId(BOOKING_ID)).thenReturn(false);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking()));
        when(accountRepository.findByHostId(HOST_ID)).thenReturn(Optional.of(account()));
        when(payoutRepository.save(any(HostPayout.class))).thenAnswer(invocation -> {
            HostPayout payout = invocation.getArgument(0);
            payout.setId(PAYOUT_ID);
            return payout;
        });

        int created = service.createPayoutQueue(10);

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<HostPayout> captor = ArgumentCaptor.forClass(HostPayout.class);
        verify(payoutRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(HostPayoutStatus.PENDING);
        assertThat(captor.getValue().getPlatformFeeAmount()).isEqualByComparingTo("150000.00");
        assertThat(captor.getValue().getNetAmount()).isEqualByComparingTo("850000.00");
        verify(notificationService).create(eq(HOST_ID), eq(NotificationType.HOST_PAYOUT_CREATED), anyString(), anyString());
    }

    @Test
    void queueHoldsPayoutWhenAccountMissing() {
        when(paymentRepository.findPayoutEligibleCapturedPaymentsForUpdate(10)).thenReturn(List.of(payment()));
        when(payoutRepository.existsByBookingId(BOOKING_ID)).thenReturn(false);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking()));
        when(accountRepository.findByHostId(HOST_ID)).thenReturn(Optional.empty());
        when(payoutRepository.save(any(HostPayout.class))).thenAnswer(invocation -> {
            HostPayout payout = invocation.getArgument(0);
            payout.setId(PAYOUT_ID);
            return payout;
        });

        service.createPayoutQueue(10);

        ArgumentCaptor<HostPayout> captor = ArgumentCaptor.forClass(HostPayout.class);
        verify(payoutRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(HostPayoutStatus.ON_HOLD);
        assertThat(captor.getValue().getHoldReason()).isEqualTo("HOST_PAYOUT_ACCOUNT_REQUIRED");
    }

    @Test
    void adminApprovesThenMarksPaid() {
        HostPayout payout = payout(HostPayoutStatus.PENDING);
        mockIdempotency(ADMIN_ID, IdempotencyScope.APPROVE_HOST_PAYOUT);
        when(securityContext.currentUserId()).thenReturn(ADMIN_ID);
        when(payoutRepository.findByIdForUpdate(PAYOUT_ID)).thenReturn(Optional.of(payout));
        when(payoutRepository.save(any(HostPayout.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HostPayoutResponse approved = service.approve(
                PAYOUT_ID,
                IDEMPOTENCY_KEY,
                new HostPayoutTransitionRequest("ok", null));

        assertThat(approved.status()).isEqualTo(HostPayoutStatus.APPROVED);
        assertThat(approved.approvedBy()).isEqualTo(ADMIN_ID);

        mockIdempotency(ADMIN_ID, IdempotencyScope.MARK_HOST_PAYOUT_PAID);
        HostPayoutResponse paid = service.markPaid(
                PAYOUT_ID,
                IDEMPOTENCY_KEY,
                new HostPayoutTransitionRequest("paid", null));

        assertThat(paid.status()).isEqualTo(HostPayoutStatus.PAID);
        assertThat(paid.paidBy()).isEqualTo(ADMIN_ID);
    }

    @Test
    void markPaidRequiresApproval() {
        HostPayout payout = payout(HostPayoutStatus.PENDING);
        mockIdempotency(ADMIN_ID, IdempotencyScope.MARK_HOST_PAYOUT_PAID);
        when(securityContext.currentUserId()).thenReturn(ADMIN_ID);
        when(payoutRepository.findByIdForUpdate(PAYOUT_ID)).thenReturn(Optional.of(payout));

        assertThatThrownBy(() -> service.markPaid(
                PAYOUT_ID,
                IDEMPOTENCY_KEY,
                new HostPayoutTransitionRequest("paid", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("approved");
    }

    private void mockIdempotency(UUID userId, IdempotencyScope scope) {
        when(idempotencyService.computeHash(any())).thenReturn("hash");
        when(idempotencyService.resolve(eq(userId), eq(scope), eq(IDEMPOTENCY_KEY), eq("hash")))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_ID));
    }

    private Booking booking() {
        Booking booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setHostId(HOST_ID);
        booking.setCustomerId(CUSTOMER_ID);
        return booking;
    }

    private BookingPayment payment() {
        BookingPayment payment = new BookingPayment();
        payment.setId(UUID.randomUUID());
        payment.setBookingId(BOOKING_ID);
        payment.setPaymentMethod(PaymentMethod.COREBANK_TRANSFER);
        payment.setProvider(PaymentProviderType.COREBANK);
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setAuthorizedAmount(new BigDecimal("1000000.00"));
        payment.setCapturedAmount(new BigDecimal("1000000.00"));
        payment.setRefundedAmount(BigDecimal.ZERO);
        payment.setCurrency("VND");
        return payment;
    }

    private HostPayoutAccount account() {
        HostPayoutAccount account = new HostPayoutAccount();
        account.setId(ACCOUNT_ID);
        account.setHostId(HOST_ID);
        account.setAccountHolderName("Nguyen Van A");
        account.setBankName("VCB");
        account.setAccountLast4("1234");
        return account;
    }

    private HostPayout payout(HostPayoutStatus status) {
        HostPayout payout = new HostPayout();
        payout.setId(PAYOUT_ID);
        payout.setBookingId(BOOKING_ID);
        payout.setHostId(HOST_ID);
        payout.setPayoutAccountId(ACCOUNT_ID);
        payout.setStatus(status);
        payout.setGrossAmount(new BigDecimal("1000000.00"));
        payout.setPlatformFeeAmount(new BigDecimal("150000.00"));
        payout.setNetAmount(new BigDecimal("850000.00"));
        payout.setCurrency("VND");
        return payout;
    }
}
