package com.rentflow.operations.service;

import com.rentflow.auth.entity.Role;
import com.rentflow.bookingmod.entity.BookingModificationStatus;
import com.rentflow.bookingmod.entity.LateReturnFeeStatus;
import com.rentflow.bookingmod.repository.BookingModificationRequestRepository;
import com.rentflow.bookingmod.repository.LateReturnFeeRepository;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.damage.entity.DamageClaimStatus;
import com.rentflow.damage.repository.DamageClaimRepository;
import com.rentflow.dispute.entity.DisputeStatus;
import com.rentflow.dispute.repository.DisputeRepository;
import com.rentflow.operations.dto.AdminOperationsQueueResponse;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payout.entity.HostPayoutStatus;
import com.rentflow.payout.repository.HostPayoutRepository;
import com.rentflow.support.entity.SupportCaseStatus;
import com.rentflow.support.repository.SupportCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOperationsService {

    private final DamageClaimRepository damageClaimRepository;
    private final BookingModificationRequestRepository modificationRepository;
    private final LateReturnFeeRepository lateReturnFeeRepository;
    private final SupportCaseRepository supportCaseRepository;
    private final HostPayoutRepository hostPayoutRepository;
    private final DisputeRepository disputeRepository;
    private final BookingPaymentRepository bookingPaymentRepository;
    private final SecurityContext securityContext;

    public AdminOperationsService(
            DamageClaimRepository damageClaimRepository,
            BookingModificationRequestRepository modificationRepository,
            LateReturnFeeRepository lateReturnFeeRepository,
            SupportCaseRepository supportCaseRepository,
            HostPayoutRepository hostPayoutRepository,
            DisputeRepository disputeRepository,
            BookingPaymentRepository bookingPaymentRepository,
            SecurityContext securityContext) {
        this.damageClaimRepository = damageClaimRepository;
        this.modificationRepository = modificationRepository;
        this.lateReturnFeeRepository = lateReturnFeeRepository;
        this.supportCaseRepository = supportCaseRepository;
        this.hostPayoutRepository = hostPayoutRepository;
        this.disputeRepository = disputeRepository;
        this.bookingPaymentRepository = bookingPaymentRepository;
        this.securityContext = securityContext;
    }

    @Transactional(readOnly = true)
    public AdminOperationsQueueResponse getQueueCounts() {
        securityContext.requireRole(Role.ADMIN);
        long openDamageClaims = damageClaimRepository.countByStatus(DamageClaimStatus.OPEN)
                + damageClaimRepository.countByStatus(DamageClaimStatus.CUSTOMER_RESPONDED)
                + damageClaimRepository.countByStatus(DamageClaimStatus.UNDER_REVIEW);
        long pendingBookingModifications = modificationRepository.countByStatus(BookingModificationStatus.PENDING_HOST_APPROVAL);
        long pendingLateReturnFees = lateReturnFeeRepository.countByStatus(LateReturnFeeStatus.PENDING);
        long openSupportCases = supportCaseRepository.countByStatus(SupportCaseStatus.WAITING_ADMIN)
                + supportCaseRepository.countByStatus(SupportCaseStatus.OPEN);
        long pendingHostPayouts = hostPayoutRepository.countByStatus(HostPayoutStatus.PENDING);
        long heldHostPayouts = hostPayoutRepository.countByStatus(HostPayoutStatus.ON_HOLD);
        long openDisputes = disputeRepository.countByStatus(DisputeStatus.OPEN);
        long paymentVoidRetries = bookingPaymentRepository.countByVoidRetryRequiredTrue();
        long total = openDamageClaims
                + pendingBookingModifications
                + pendingLateReturnFees
                + openSupportCases
                + pendingHostPayouts
                + heldHostPayouts
                + openDisputes
                + paymentVoidRetries;
        return new AdminOperationsQueueResponse(
                openDamageClaims,
                pendingBookingModifications,
                pendingLateReturnFees,
                openSupportCases,
                pendingHostPayouts,
                heldHostPayouts,
                openDisputes,
                paymentVoidRetries,
                total);
    }
}
