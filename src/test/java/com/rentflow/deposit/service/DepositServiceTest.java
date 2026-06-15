package com.rentflow.deposit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.audit.service.AuditLogService;
import com.rentflow.booking.entity.Booking;
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
import com.rentflow.deposit.dto.DeductDepositRequest;
import com.rentflow.deposit.dto.DepositResponse;
import com.rentflow.deposit.entity.BookingDeposit;
import com.rentflow.deposit.entity.DepositStatus;
import com.rentflow.deposit.repository.BookingDepositRepository;
import com.rentflow.deposit.repository.DepositTransactionRepository;
import com.rentflow.outbox.service.OutboxService;
import com.rentflow.payment.entity.PaymentProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class DepositServiceTest {

    @Mock private BookingDepositRepository depositRepository;
    @Mock private DepositTransactionRepository transactionRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private DamageClaimRepository damageClaimRepository;
    @Mock private SecurityContext securityContext;
    @Mock private IdempotencyService idempotencyService;
    @Mock private IdempotencyFailureMarker idempotencyFailureMarker;
    @Mock private BookingTimelineService bookingTimelineService;
    @Mock private AuditLogService auditLogService;
    @Mock private OutboxService outboxService;

    private DepositService service;
    private UUID bookingId;
    private UUID customerId;
    private UUID hostId;

    @BeforeEach
    void setUp() {
        service = new DepositService(
                depositRepository,
                transactionRepository,
                bookingRepository,
                damageClaimRepository,
                securityContext,
                idempotencyService,
                idempotencyFailureMarker,
                bookingTimelineService,
                auditLogService,
                outboxService,
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(Instant.parse("2026-06-07T00:00:00Z"), ZoneOffset.UTC));
        bookingId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        hostId = UUID.randomUUID();
    }

    @Test
    void bookingCreatesDepositRecord() {
        when(depositRepository.existsByBookingId(bookingId)).thenReturn(false);
        when(depositRepository.save(any(BookingDeposit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createRequirementForBooking(booking(), new BigDecimal("1000000"), "VND");

        verify(depositRepository).save(any(BookingDeposit.class));
    }

    @Test
    void authorizeDepositIsIdempotentAndMovesToHeld() {
        BookingDeposit deposit = pendingDeposit();
        mockIdempotency(customerId, IdempotencyScope.AUTHORIZE_DEPOSIT);
        when(securityContext.currentUserId()).thenReturn(customerId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking()));
        when(depositRepository.findByBookingIdForUpdate(bookingId)).thenReturn(Optional.of(deposit));
        when(depositRepository.save(any(BookingDeposit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.findByBookingDepositIdOrderByCreatedAtDesc(deposit.getId())).thenReturn(List.of());

        DepositResponse response = service.authorize(bookingId, uuidKey());

        assertThat(response.status()).isEqualTo(DepositStatus.HELD);
        assertThat(response.heldAmount()).isEqualByComparingTo("200000");
        verify(outboxService).append(eq("BOOKING_DEPOSIT"), eq(deposit.getId()), eq("DEPOSIT_AUTHORIZED"), anyString());
    }

    @Test
    void releaseDeposit() {
        BookingDeposit deposit = heldDeposit();
        UUID adminId = UUID.randomUUID();
        mockIdempotency(adminId, IdempotencyScope.RELEASE_DEPOSIT);
        when(securityContext.currentUserId()).thenReturn(adminId);
        when(securityContext.hasRole(com.rentflow.auth.entity.Role.ADMIN)).thenReturn(true);
        when(depositRepository.findByIdForUpdate(deposit.getId())).thenReturn(Optional.of(deposit));
        when(damageClaimRepository.existsByBookingIdAndStatusIn(eq(bookingId), any())).thenReturn(false);
        when(depositRepository.save(any(BookingDeposit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking()));
        when(transactionRepository.findByBookingDepositIdOrderByCreatedAtDesc(deposit.getId())).thenReturn(List.of());

        DepositResponse response = service.release(deposit.getId(), uuidKey());

        assertThat(response.status()).isEqualTo(DepositStatus.RELEASED);
        assertThat(response.releasedAmount()).isEqualByComparingTo("200000");
    }

    @Test
    void deductApprovedDamageClaim() {
        BookingDeposit deposit = heldDeposit();
        DamageClaim claim = approvedClaim(new BigDecimal("100000"));
        UUID adminId = UUID.randomUUID();
        mockIdempotency(adminId, IdempotencyScope.DEDUCT_DEPOSIT);
        when(securityContext.currentUserId()).thenReturn(adminId);
        when(securityContext.hasRole(com.rentflow.auth.entity.Role.ADMIN)).thenReturn(true);
        when(depositRepository.findByIdForUpdate(deposit.getId())).thenReturn(Optional.of(deposit));
        when(damageClaimRepository.findById(claim.getId())).thenReturn(Optional.of(claim));
        when(depositRepository.save(any(BookingDeposit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking()));
        when(transactionRepository.findByBookingDepositIdOrderByCreatedAtDesc(deposit.getId())).thenReturn(List.of());

        DepositResponse response = service.deduct(
                deposit.getId(),
                uuidKey(),
                new DeductDepositRequest(new BigDecimal("100000"), claim.getId(), null, "Damage"));

        assertThat(response.status()).isEqualTo(DepositStatus.PARTIALLY_DEDUCTED);
        assertThat(response.deductedAmount()).isEqualByComparingTo("100000");
    }

    @Test
    void overDeductRejected() {
        BookingDeposit deposit = heldDeposit();
        DamageClaim claim = approvedClaim(new BigDecimal("300000"));
        UUID adminId = UUID.randomUUID();
        mockIdempotency(adminId, IdempotencyScope.DEDUCT_DEPOSIT);
        when(securityContext.currentUserId()).thenReturn(adminId);
        when(depositRepository.findByIdForUpdate(deposit.getId())).thenReturn(Optional.of(deposit));
        when(damageClaimRepository.findById(claim.getId())).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> service.deduct(
                deposit.getId(),
                uuidKey(),
                new DeductDepositRequest(new BigDecimal("300000"), claim.getId(), null, "Damage")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("held deposit");
    }

    @Test
    void cannotDeductReleasedDeposit() {
        BookingDeposit deposit = heldDeposit();
        deposit.setStatus(DepositStatus.RELEASED);
        DamageClaim claim = approvedClaim(new BigDecimal("100000"));
        UUID adminId = UUID.randomUUID();
        mockIdempotency(adminId, IdempotencyScope.DEDUCT_DEPOSIT);
        when(securityContext.currentUserId()).thenReturn(adminId);
        when(depositRepository.findByIdForUpdate(deposit.getId())).thenReturn(Optional.of(deposit));
        when(damageClaimRepository.findById(claim.getId())).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> service.deduct(
                deposit.getId(),
                uuidKey(),
                new DeductDepositRequest(new BigDecimal("100000"), claim.getId(), null, "Damage")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("released");
    }

    private void mockIdempotency(UUID userId, IdempotencyScope scope) {
        when(idempotencyService.computeHash(any())).thenReturn("hash");
        when(idempotencyService.resolve(eq(userId), eq(scope), anyString(), eq("hash")))
                .thenReturn(IdempotencyResolution.proceed(UUID.randomUUID()));
    }

    private Booking booking() {
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setCustomerId(customerId);
        booking.setHostId(hostId);
        return booking;
    }

    private BookingDeposit pendingDeposit() {
        BookingDeposit deposit = new BookingDeposit();
        deposit.setId(UUID.randomUUID());
        deposit.setBookingId(bookingId);
        deposit.setCustomerId(customerId);
        deposit.setHostId(hostId);
        deposit.setStatus(DepositStatus.PENDING_AUTHORIZATION);
        deposit.setAmount(new BigDecimal("200000"));
        deposit.setCurrency("VND");
        deposit.setProvider(PaymentProviderType.STUB);
        return deposit;
    }

    private BookingDeposit heldDeposit() {
        BookingDeposit deposit = pendingDeposit();
        deposit.setStatus(DepositStatus.HELD);
        deposit.setHeldAmount(new BigDecimal("200000"));
        deposit.setProviderHoldId("hold-1");
        return deposit;
    }

    private DamageClaim approvedClaim(BigDecimal amount) {
        DamageClaim claim = new DamageClaim();
        claim.setId(UUID.randomUUID());
        claim.setBookingId(bookingId);
        claim.setStatus(DamageClaimStatus.APPROVED);
        claim.setClaimAmount(amount);
        claim.setApprovedAmount(amount);
        return claim;
    }

    private String uuidKey() {
        return UUID.randomUUID().toString();
    }
}
