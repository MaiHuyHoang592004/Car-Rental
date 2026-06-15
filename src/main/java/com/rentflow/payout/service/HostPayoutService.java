package com.rentflow.payout.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.audit.service.AuditLogService;
import com.rentflow.auth.entity.Role;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.exception.BookingNotFoundException;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.ResourceNotFoundException;
import com.rentflow.common.idempotency.service.IdempotencyFailureMarker;
import com.rentflow.common.idempotency.service.IdempotencyResolution;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import com.rentflow.common.idempotency.service.IdempotencyService;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.common.web.PageResponse;
import com.rentflow.notification.entity.NotificationType;
import com.rentflow.notification.service.NotificationService;
import com.rentflow.outbox.service.OutboxService;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payout.dto.HostPayoutAccountRequest;
import com.rentflow.payout.dto.HostPayoutAccountResponse;
import com.rentflow.payout.dto.HostPayoutResponse;
import com.rentflow.payout.dto.HostPayoutTransitionRequest;
import com.rentflow.payout.entity.HostPayout;
import com.rentflow.payout.entity.HostPayoutAccount;
import com.rentflow.payout.entity.HostPayoutAccountProvider;
import com.rentflow.payout.entity.HostPayoutAccountStatus;
import com.rentflow.payout.entity.HostPayoutStatus;
import com.rentflow.payout.repository.HostPayoutAccountRepository;
import com.rentflow.payout.repository.HostPayoutRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class HostPayoutService {

    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.15");

    private final HostPayoutAccountRepository accountRepository;
    private final HostPayoutRepository payoutRepository;
    private final BookingRepository bookingRepository;
    private final BookingPaymentRepository paymentRepository;
    private final SecurityContext securityContext;
    private final IdempotencyService idempotencyService;
    private final IdempotencyFailureMarker idempotencyFailureMarker;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public HostPayoutService(
            HostPayoutAccountRepository accountRepository,
            HostPayoutRepository payoutRepository,
            BookingRepository bookingRepository,
            BookingPaymentRepository paymentRepository,
            SecurityContext securityContext,
            IdempotencyService idempotencyService,
            IdempotencyFailureMarker idempotencyFailureMarker,
            NotificationService notificationService,
            AuditLogService auditLogService,
            OutboxService outboxService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.payoutRepository = payoutRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.securityContext = securityContext;
        this.idempotencyService = idempotencyService;
        this.idempotencyFailureMarker = idempotencyFailureMarker;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public HostPayoutAccountResponse upsertAccount(String idempotencyKey, HostPayoutAccountRequest request) {
        UUID hostId = securityContext.currentUserId();
        securityContext.requireRole(Role.HOST);
        IdempotencyResolution resolution = resolve(hostId, IdempotencyScope.UPSERT_HOST_PAYOUT_ACCOUNT, idempotencyKey, request);
        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserializeAccount(replay.responseBodyJson());
        }
        UUID keyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            HostPayoutAccount account = accountRepository.findByHostIdForUpdate(hostId).orElseGet(HostPayoutAccount::new);
            account.setHostId(hostId);
            account.setProvider(HostPayoutAccountProvider.MANUAL_BANK);
            account.setStatus(HostPayoutAccountStatus.ACTIVE);
            account.setAccountHolderName(request.accountHolderName().trim());
            account.setBankName(request.bankName().trim());
            account.setAccountLast4(request.accountLast4());
            account = accountRepository.save(account);
            HostPayoutAccountResponse response = HostPayoutAccountResponse.from(account);
            idempotencyService.complete(keyId, 200, serialize(response));
            return response;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(keyId);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public HostPayoutAccountResponse getMyAccount() {
        UUID hostId = securityContext.currentUserId();
        securityContext.requireRole(Role.HOST);
        return accountRepository.findByHostId(hostId)
                .map(HostPayoutAccountResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("HOST_PAYOUT_ACCOUNT_NOT_FOUND", "HostPayoutAccount", hostId.toString()));
    }

    @Transactional(readOnly = true)
    public PageResponse<HostPayoutResponse> listMyPayouts(Pageable pageable) {
        UUID hostId = securityContext.currentUserId();
        securityContext.requireRole(Role.HOST);
        return PageResponse.from(payoutRepository.findByHostIdOrderByCreatedAtDesc(hostId, pageable), HostPayoutResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<HostPayoutResponse> listAdminPayouts(HostPayoutStatus status, Pageable pageable) {
        securityContext.requireRole(Role.ADMIN);
        Page<HostPayout> page = status == null
                ? payoutRepository.findAll(pageable)
                : payoutRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return PageResponse.from(page, HostPayoutResponse::from);
    }

    @Transactional
    public int createPayoutQueue(int batchSize) {
        List<BookingPayment> payments = paymentRepository.findPayoutEligibleCapturedPaymentsForUpdate(batchSize);
        int created = 0;
        for (BookingPayment payment : payments) {
            if (payoutRepository.existsByBookingId(payment.getBookingId())) {
                continue;
            }
            Booking booking = bookingRepository.findById(payment.getBookingId())
                    .orElseThrow(() -> new BookingNotFoundException(payment.getBookingId().toString()));
            HostPayoutAccount account = accountRepository.findByHostId(booking.getHostId()).orElse(null);
            HostPayout payout = new HostPayout();
            payout.setBookingId(booking.getId());
            payout.setHostId(booking.getHostId());
            payout.setPayoutAccountId(account == null ? null : account.getId());
            payout.setStatus(account == null || account.getStatus() != HostPayoutAccountStatus.ACTIVE
                    ? HostPayoutStatus.ON_HOLD
                    : HostPayoutStatus.PENDING);
            payout.setHoldReason(account == null ? "HOST_PAYOUT_ACCOUNT_REQUIRED" : null);
            payout.setGrossAmount(payment.getCapturedAmount());
            payout.setPlatformFeeAmount(payment.getCapturedAmount().multiply(PLATFORM_FEE_RATE).setScale(2, RoundingMode.HALF_UP));
            payout.setNetAmount(payment.getCapturedAmount().subtract(payout.getPlatformFeeAmount()));
            payout.setCurrency(payment.getCurrency());
            payout = payoutRepository.save(payout);
            emit(null, "SYSTEM", "HOST_PAYOUT_CREATED", payout);
            notificationService.create(booking.getHostId(), NotificationType.HOST_PAYOUT_CREATED,
                    "Payout queued", "Payout for booking " + booking.getId() + " was queued.");
            created++;
        }
        return created;
    }

    @Transactional
    public HostPayoutResponse approve(UUID payoutId, String idempotencyKey, HostPayoutTransitionRequest request) {
        return transition(payoutId, idempotencyKey, request, IdempotencyScope.APPROVE_HOST_PAYOUT, HostPayoutStatus.APPROVED);
    }

    @Transactional
    public HostPayoutResponse hold(UUID payoutId, String idempotencyKey, HostPayoutTransitionRequest request) {
        return transition(payoutId, idempotencyKey, request, IdempotencyScope.HOLD_HOST_PAYOUT, HostPayoutStatus.ON_HOLD);
    }

    @Transactional
    public HostPayoutResponse markPaid(UUID payoutId, String idempotencyKey, HostPayoutTransitionRequest request) {
        return transition(payoutId, idempotencyKey, request, IdempotencyScope.MARK_HOST_PAYOUT_PAID, HostPayoutStatus.PAID);
    }

    @Transactional
    public HostPayoutResponse fail(UUID payoutId, String idempotencyKey, HostPayoutTransitionRequest request) {
        return transition(payoutId, idempotencyKey, request, IdempotencyScope.FAIL_HOST_PAYOUT, HostPayoutStatus.FAILED);
    }

    private HostPayoutResponse transition(
            UUID payoutId,
            String idempotencyKey,
            HostPayoutTransitionRequest request,
            IdempotencyScope scope,
            HostPayoutStatus targetStatus) {
        UUID adminId = securityContext.currentUserId();
        securityContext.requireRole(Role.ADMIN);
        IdempotencyResolution resolution = resolve(adminId, scope, idempotencyKey, new HashInput(payoutId, request));
        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserializePayout(replay.responseBodyJson());
        }
        UUID keyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            HostPayout payout = payoutRepository.findByIdForUpdate(payoutId)
                    .orElseThrow(() -> new ResourceNotFoundException("HOST_PAYOUT_NOT_FOUND", "HostPayout", payoutId.toString()));
            validateTransition(payout, targetStatus);
            payout.setStatus(targetStatus);
            payout.setAdminNote(normalize(request == null ? null : request.note()));
            if (targetStatus == HostPayoutStatus.ON_HOLD) {
                payout.setHoldReason(normalize(request == null ? null : request.holdReason()));
            } else if (targetStatus == HostPayoutStatus.APPROVED) {
                payout.setApprovedBy(adminId);
                payout.setApprovedAt(clock.instant());
            } else if (targetStatus == HostPayoutStatus.PAID) {
                payout.setPaidBy(adminId);
                payout.setPaidAt(clock.instant());
            } else if (targetStatus == HostPayoutStatus.FAILED) {
                payout.setFailedBy(adminId);
                payout.setFailedAt(clock.instant());
            }
            payout = payoutRepository.save(payout);
            emit(adminId, "ADMIN", "HOST_PAYOUT_" + targetStatus.name(), payout);
            notificationService.create(payout.getHostId(), NotificationType.HOST_PAYOUT_UPDATED,
                    "Payout updated", "Payout for booking " + payout.getBookingId() + " is " + targetStatus + ".");
            HostPayoutResponse response = HostPayoutResponse.from(payout);
            idempotencyService.complete(keyId, 200, serialize(response));
            return response;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(keyId);
            throw e;
        }
    }

    private void validateTransition(HostPayout payout, HostPayoutStatus targetStatus) {
        if (payout.getStatus() == HostPayoutStatus.PAID || payout.getStatus() == HostPayoutStatus.CANCELLED) {
            throw new BusinessRuleException("HOST_PAYOUT_FINAL", "Finalized payouts cannot transition");
        }
        if (targetStatus == HostPayoutStatus.PAID && payout.getStatus() != HostPayoutStatus.APPROVED) {
            throw new BusinessRuleException("HOST_PAYOUT_APPROVAL_REQUIRED", "Payout must be approved before marking paid");
        }
    }

    private IdempotencyResolution resolve(UUID userId, IdempotencyScope scope, String key, Object request) {
        return idempotencyService.resolve(userId, scope, key, idempotencyService.computeHash(request));
    }

    private void emit(UUID actorId, String actorType, String eventType, HostPayout payout) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("payoutId", payout.getId());
        payload.put("bookingId", payout.getBookingId());
        payload.put("hostId", payout.getHostId());
        payload.put("status", payout.getStatus());
        String json = serialize(payload);
        auditLogService.record(actorId, actorType, eventType, "HOST_PAYOUT", payout.getId(), "SUCCEEDED", json);
        outboxService.append("HOST_PAYOUT", payout.getId(), eventType, json);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize payout JSON", e);
        }
    }

    private HostPayoutResponse deserializePayout(String json) {
        try {
            return objectMapper.readValue(json, HostPayoutResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to deserialize payout response", e);
        }
    }

    private HostPayoutAccountResponse deserializeAccount(String json) {
        try {
            return objectMapper.readValue(json, HostPayoutAccountResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to deserialize payout account response", e);
        }
    }

    private record HashInput(Object id, Object request) {
    }
}
