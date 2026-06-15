package com.rentflow.deposit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.audit.service.AuditLogService;
import com.rentflow.auth.entity.Role;
import com.rentflow.booking.entity.Booking;
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
import com.rentflow.damage.entity.DamageClaim;
import com.rentflow.damage.entity.DamageClaimStatus;
import com.rentflow.damage.repository.DamageClaimRepository;
import com.rentflow.deposit.dto.DeductDepositRequest;
import com.rentflow.deposit.dto.DepositResponse;
import com.rentflow.deposit.dto.DepositTransactionResponse;
import com.rentflow.deposit.entity.BookingDeposit;
import com.rentflow.deposit.entity.DepositStatus;
import com.rentflow.deposit.entity.DepositTransaction;
import com.rentflow.deposit.entity.DepositTransactionStatus;
import com.rentflow.deposit.entity.DepositTransactionType;
import com.rentflow.deposit.repository.BookingDepositRepository;
import com.rentflow.deposit.repository.DepositTransactionRepository;
import com.rentflow.outbox.service.OutboxService;
import com.rentflow.payment.entity.PaymentProviderType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DepositService {

    private static final BigDecimal DEFAULT_DEPOSIT_RATE = new BigDecimal("0.20");
    private static final Duration HOLD_TTL = Duration.ofDays(45);
    private static final List<DamageClaimStatus> OPEN_DAMAGE_STATUSES = List.of(
            DamageClaimStatus.OPEN,
            DamageClaimStatus.CUSTOMER_RESPONDED,
            DamageClaimStatus.UNDER_REVIEW,
            DamageClaimStatus.APPROVED,
            DamageClaimStatus.PARTIALLY_APPROVED);

    private final BookingDepositRepository depositRepository;
    private final DepositTransactionRepository transactionRepository;
    private final BookingRepository bookingRepository;
    private final DamageClaimRepository damageClaimRepository;
    private final SecurityContext securityContext;
    private final IdempotencyService idempotencyService;
    private final IdempotencyFailureMarker idempotencyFailureMarker;
    private final BookingTimelineService bookingTimelineService;
    private final AuditLogService auditLogService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DepositService(
            BookingDepositRepository depositRepository,
            DepositTransactionRepository transactionRepository,
            BookingRepository bookingRepository,
            DamageClaimRepository damageClaimRepository,
            SecurityContext securityContext,
            IdempotencyService idempotencyService,
            IdempotencyFailureMarker idempotencyFailureMarker,
            BookingTimelineService bookingTimelineService,
            AuditLogService auditLogService,
            OutboxService outboxService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.depositRepository = depositRepository;
        this.transactionRepository = transactionRepository;
        this.bookingRepository = bookingRepository;
        this.damageClaimRepository = damageClaimRepository;
        this.securityContext = securityContext;
        this.idempotencyService = idempotencyService;
        this.idempotencyFailureMarker = idempotencyFailureMarker;
        this.bookingTimelineService = bookingTimelineService;
        this.auditLogService = auditLogService;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void createRequirementForBooking(Booking booking, BigDecimal bookingTotalAmount, String currency) {
        if (depositRepository.existsByBookingId(booking.getId())) {
            return;
        }
        BigDecimal amount = bookingTotalAmount == null
                ? BigDecimal.ZERO
                : bookingTotalAmount.multiply(DEFAULT_DEPOSIT_RATE).setScale(2, RoundingMode.HALF_UP);
        BookingDeposit deposit = new BookingDeposit();
        deposit.setBookingId(booking.getId());
        deposit.setCustomerId(booking.getCustomerId());
        deposit.setHostId(booking.getHostId());
        deposit.setAmount(amount);
        deposit.setCurrency(currency == null || currency.isBlank() ? "VND" : currency);
        deposit.setProvider(PaymentProviderType.STUB);
        deposit.setStatus(amount.compareTo(BigDecimal.ZERO) == 0
                ? DepositStatus.NOT_REQUIRED
                : DepositStatus.PENDING_AUTHORIZATION);
        depositRepository.save(deposit);
    }

    @Transactional(readOnly = true)
    public DepositResponse getBookingDeposit(UUID bookingId) {
        UUID actorId = securityContext.currentUserId();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
        if (!canView(booking, actorId)) {
            throw new BookingNotFoundException(bookingId.toString());
        }
        BookingDeposit deposit = depositRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("DEPOSIT_NOT_FOUND", "BookingDeposit", bookingId.toString()));
        return toResponse(deposit);
    }

    @Transactional
    public DepositResponse authorize(UUID bookingId, String idempotencyKey) {
        UUID customerId = securityContext.currentUserId();
        IdempotencyResolution resolution = resolve(customerId, IdempotencyScope.AUTHORIZE_DEPOSIT, idempotencyKey, bookingId);
        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserialize(replay.responseBodyJson());
        }
        UUID idempotencyKeyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
            if (!booking.getCustomerId().equals(customerId)) {
                throw new BookingNotFoundException(bookingId.toString());
            }
            BookingDeposit deposit = depositRepository.findByBookingIdForUpdate(bookingId)
                    .orElseThrow(() -> new ResourceNotFoundException("DEPOSIT_NOT_FOUND", "BookingDeposit", bookingId.toString()));
            if (deposit.getStatus() == DepositStatus.HELD) {
                DepositResponse replayable = toResponse(deposit);
                idempotencyService.complete(idempotencyKeyId, 200, serialize(replayable));
                return replayable;
            }
            if (deposit.getStatus() != DepositStatus.PENDING_AUTHORIZATION) {
                throw new BusinessRuleException("DEPOSIT_INVALID_STATUS", "Deposit cannot be authorized from current status");
            }
            deposit.setHeldAmount(deposit.getAmount());
            deposit.setStatus(DepositStatus.HELD);
            deposit.setProviderHoldId("stub-deposit-hold-" + deposit.getId());
            deposit.setProviderStatus("HELD");
            deposit.setHoldExpiresAt(clock.instant().plus(HOLD_TTL));
            deposit = depositRepository.save(deposit);
            createTransaction(deposit, DepositTransactionType.AUTHORIZE_HOLD, deposit.getAmount(), idempotencyKeyId);
            DepositResponse response = toResponse(deposit);
            emit(booking, deposit, "DEPOSIT_AUTHORIZED", "SUCCEEDED");
            idempotencyService.complete(idempotencyKeyId, 200, serialize(response));
            return response;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(idempotencyKeyId);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<DepositResponse> listAdminDeposits(DepositStatus status, Pageable pageable) {
        securityContext.requireRole(Role.ADMIN);
        Page<BookingDeposit> page = status == null
                ? depositRepository.findAll(pageable)
                : depositRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return PageResponse.from(page, this::toResponse);
    }

    @Transactional
    public DepositResponse deduct(UUID depositId, String idempotencyKey, DeductDepositRequest request) {
        UUID adminId = securityContext.currentUserId();
        securityContext.requireRole(Role.ADMIN);
        IdempotencyResolution resolution = resolve(adminId, IdempotencyScope.DEDUCT_DEPOSIT, idempotencyKey,
                new DepositHashInput(depositId, request));
        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserialize(replay.responseBodyJson());
        }
        UUID idempotencyKeyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            BookingDeposit deposit = depositRepository.findByIdForUpdate(depositId)
                    .orElseThrow(() -> new ResourceNotFoundException("DEPOSIT_NOT_FOUND", "BookingDeposit", depositId.toString()));
            if (request.damageClaimId() == null) {
                throw new BusinessRuleException("DEPOSIT_DEDUCTION_REASON_REQUIRED",
                        "Deposit deduction requires an approved damage claim until late fees are implemented");
            }
            DamageClaim claim = damageClaimRepository.findById(request.damageClaimId())
                    .orElseThrow(() -> new ResourceNotFoundException("DAMAGE_CLAIM_NOT_FOUND", "DamageClaim", request.damageClaimId().toString()));
            requireApprovedClaimForDeposit(deposit, claim);
            DepositResponse response = deductInternal(deposit, request.amount(), idempotencyKeyId, "DEPOSIT_DEDUCTED");
            idempotencyService.complete(idempotencyKeyId, 200, serialize(response));
            return response;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(idempotencyKeyId);
            throw e;
        }
    }

    @Transactional
    public DepositResponse release(UUID depositId, String idempotencyKey) {
        UUID adminId = securityContext.currentUserId();
        securityContext.requireRole(Role.ADMIN);
        IdempotencyResolution resolution = resolve(adminId, IdempotencyScope.RELEASE_DEPOSIT, idempotencyKey, depositId);
        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserialize(replay.responseBodyJson());
        }
        UUID idempotencyKeyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            BookingDeposit deposit = depositRepository.findByIdForUpdate(depositId)
                    .orElseThrow(() -> new ResourceNotFoundException("DEPOSIT_NOT_FOUND", "BookingDeposit", depositId.toString()));
            if (deposit.getStatus() == DepositStatus.RELEASED) {
                DepositResponse replayable = toResponse(deposit);
                idempotencyService.complete(idempotencyKeyId, 200, serialize(replayable));
                return replayable;
            }
            if (deposit.getStatus() != DepositStatus.HELD && deposit.getStatus() != DepositStatus.PARTIALLY_DEDUCTED) {
                throw new BusinessRuleException("DEPOSIT_INVALID_STATUS", "Deposit cannot be released from current status");
            }
            if (damageClaimRepository.existsByBookingIdAndStatusIn(deposit.getBookingId(), OPEN_DAMAGE_STATUSES)) {
                throw new BusinessRuleException("DEPOSIT_RELEASE_BLOCKED", "Open damage claim blocks deposit release");
            }
            BigDecimal releasable = deposit.getHeldAmount().subtract(deposit.getDeductedAmount());
            deposit.setReleasedAmount(releasable.max(BigDecimal.ZERO));
            deposit.setStatus(DepositStatus.RELEASED);
            deposit.setReleasedAt(clock.instant());
            deposit.setProviderStatus("RELEASED");
            deposit = depositRepository.save(deposit);
            createTransaction(deposit, DepositTransactionType.RELEASE, deposit.getReleasedAmount(), idempotencyKeyId);
            Booking booking = requireBooking(deposit.getBookingId());
            DepositResponse response = toResponse(deposit);
            emit(booking, deposit, "DEPOSIT_RELEASED", "SUCCEEDED");
            idempotencyService.complete(idempotencyKeyId, 200, serialize(response));
            return response;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(idempotencyKeyId);
            throw e;
        }
    }

    @Transactional
    public void deductForDamageClaim(DamageClaim claim) {
        if (claim.getApprovedAmount() == null || claim.getApprovedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BookingDeposit deposit = depositRepository.findByBookingIdForUpdate(claim.getBookingId()).orElse(null);
        if (deposit == null || (deposit.getStatus() != DepositStatus.HELD
                && deposit.getStatus() != DepositStatus.PARTIALLY_DEDUCTED)) {
            return;
        }
        requireApprovedClaimForDeposit(deposit, claim);
        deductInternal(deposit, claim.getApprovedAmount(), null, "DEPOSIT_DEDUCTED");
    }

    @Transactional
    public void deductForLateReturnFee(UUID bookingId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BookingDeposit deposit = depositRepository.findByBookingIdForUpdate(bookingId).orElse(null);
        if (deposit == null || (deposit.getStatus() != DepositStatus.HELD
                && deposit.getStatus() != DepositStatus.PARTIALLY_DEDUCTED)) {
            return;
        }
        deductInternal(deposit, amount, null, "DEPOSIT_DEDUCTED");
    }

    private DepositResponse deductInternal(
            BookingDeposit deposit,
            BigDecimal amount,
            UUID idempotencyKeyId,
            String eventType) {
        if (deposit.getStatus() == DepositStatus.RELEASED) {
            throw new BusinessRuleException("DEPOSIT_INVALID_STATUS", "Cannot deduct a released deposit");
        }
        if (deposit.getStatus() != DepositStatus.HELD && deposit.getStatus() != DepositStatus.PARTIALLY_DEDUCTED) {
            throw new BusinessRuleException("DEPOSIT_INVALID_STATUS", "Deposit must be HELD before deduction");
        }
        BigDecimal available = deposit.getHeldAmount().subtract(deposit.getDeductedAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(available) > 0) {
            throw new BusinessRuleException("DEPOSIT_DEDUCTION_AMOUNT_INVALID", "Cannot deduct more than held deposit");
        }
        deposit.setDeductedAmount(deposit.getDeductedAmount().add(amount));
        deposit.setStatus(deposit.getDeductedAmount().compareTo(deposit.getHeldAmount()) < 0
                ? DepositStatus.PARTIALLY_DEDUCTED
                : DepositStatus.RELEASED);
        deposit.setProviderStatus("DEDUCTED");
        if (deposit.getStatus() == DepositStatus.RELEASED) {
            deposit.setReleasedAt(clock.instant());
            deposit.setReleasedAmount(BigDecimal.ZERO);
        }
        deposit = depositRepository.save(deposit);
        createTransaction(deposit, DepositTransactionType.DEDUCT, amount, idempotencyKeyId);
        Booking booking = requireBooking(deposit.getBookingId());
        DepositResponse response = toResponse(deposit);
        emit(booking, deposit, eventType, "SUCCEEDED");
        return response;
    }

    private void requireApprovedClaimForDeposit(BookingDeposit deposit, DamageClaim claim) {
        if (!deposit.getBookingId().equals(claim.getBookingId())) {
            throw new BusinessRuleException("DEPOSIT_DAMAGE_CLAIM_MISMATCH", "Damage claim belongs to a different booking");
        }
        if (claim.getStatus() != DamageClaimStatus.APPROVED
                && claim.getStatus() != DamageClaimStatus.PARTIALLY_APPROVED
                && claim.getStatus() != DamageClaimStatus.CHARGED) {
            throw new BusinessRuleException("DAMAGE_CLAIM_INVALID_STATUS", "Damage claim must be approved before deposit deduction");
        }
    }

    private DepositTransaction createTransaction(
            BookingDeposit deposit,
            DepositTransactionType type,
            BigDecimal amount,
            UUID idempotencyKeyId) {
        DepositTransaction transaction = new DepositTransaction();
        transaction.setBookingDepositId(deposit.getId());
        transaction.setBookingId(deposit.getBookingId());
        transaction.setType(type);
        transaction.setStatus(DepositTransactionStatus.SUCCEEDED);
        transaction.setAmount(amount);
        transaction.setCurrency(deposit.getCurrency());
        transaction.setProvider(deposit.getProvider());
        transaction.setProviderRef(deposit.getProviderHoldId());
        transaction.setIdempotencyKeyId(idempotencyKeyId);
        return transactionRepository.save(transaction);
    }

    private boolean canView(Booking booking, UUID actorId) {
        return booking.getCustomerId().equals(actorId)
                || booking.getHostId().equals(actorId)
                || securityContext.hasRole(Role.ADMIN);
    }

    private Booking requireBooking(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
    }

    private IdempotencyResolution resolve(UUID userId, IdempotencyScope scope, String key, Object request) {
        return idempotencyService.resolve(userId, scope, key, idempotencyService.computeHash(request));
    }

    private DepositResponse toResponse(BookingDeposit deposit) {
        List<DepositTransactionResponse> transactions = transactionRepository
                .findByBookingDepositIdOrderByCreatedAtDesc(deposit.getId())
                .stream()
                .map(DepositTransactionResponse::from)
                .toList();
        return DepositResponse.from(deposit, transactions);
    }

    private void emit(Booking booking, BookingDeposit deposit, String eventType, String status) {
        UUID actorId = securityContext.currentUserId();
        String actorType = securityContext.hasRole(Role.ADMIN)
                ? "ADMIN"
                : booking.getHostId().equals(actorId) ? "HOST" : "CUSTOMER";
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("depositId", deposit.getId());
        details.put("bookingId", deposit.getBookingId());
        details.put("status", deposit.getStatus().name());
        details.put("amount", deposit.getAmount());
        details.put("heldAmount", deposit.getHeldAmount());
        details.put("deductedAmount", deposit.getDeductedAmount());
        details.put("releasedAmount", deposit.getReleasedAmount());
        details.put("currency", deposit.getCurrency());
        String payload = serialize(details);
        bookingTimelineService.append(booking.getId(), eventType, actorId, actorType, payload);
        auditLogService.record(actorId, actorType, eventType, "BOOKING_DEPOSIT", deposit.getId(), status, payload);
        outboxService.append("BOOKING_DEPOSIT", deposit.getId(), eventType, payload);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize deposit JSON", e);
        }
    }

    private DepositResponse deserialize(String responseJson) {
        try {
            return objectMapper.readValue(responseJson, DepositResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to deserialize deposit idempotency response", e);
        }
    }

    private record DepositHashInput(UUID id, Object request) {
    }
}
