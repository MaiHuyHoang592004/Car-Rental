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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOperationsServiceTest {

    @Mock private DamageClaimRepository damageClaimRepository;
    @Mock private BookingModificationRequestRepository modificationRepository;
    @Mock private LateReturnFeeRepository lateReturnFeeRepository;
    @Mock private SupportCaseRepository supportCaseRepository;
    @Mock private HostPayoutRepository hostPayoutRepository;
    @Mock private DisputeRepository disputeRepository;
    @Mock private BookingPaymentRepository bookingPaymentRepository;
    @Mock private SecurityContext securityContext;

    private AdminOperationsService service;

    @BeforeEach
    void setUp() {
        service = new AdminOperationsService(
                damageClaimRepository,
                modificationRepository,
                lateReturnFeeRepository,
                supportCaseRepository,
                hostPayoutRepository,
                disputeRepository,
                bookingPaymentRepository,
                securityContext);
    }

    @Test
    void aggregatesQueueCounts() {
        when(damageClaimRepository.countByStatus(DamageClaimStatus.OPEN)).thenReturn(1L);
        when(damageClaimRepository.countByStatus(DamageClaimStatus.CUSTOMER_RESPONDED)).thenReturn(2L);
        when(damageClaimRepository.countByStatus(DamageClaimStatus.UNDER_REVIEW)).thenReturn(3L);
        when(modificationRepository.countByStatus(BookingModificationStatus.PENDING_HOST_APPROVAL)).thenReturn(4L);
        when(lateReturnFeeRepository.countByStatus(LateReturnFeeStatus.PENDING)).thenReturn(5L);
        when(supportCaseRepository.countByStatus(SupportCaseStatus.WAITING_ADMIN)).thenReturn(6L);
        when(supportCaseRepository.countByStatus(SupportCaseStatus.OPEN)).thenReturn(7L);
        when(hostPayoutRepository.countByStatus(HostPayoutStatus.PENDING)).thenReturn(8L);
        when(hostPayoutRepository.countByStatus(HostPayoutStatus.ON_HOLD)).thenReturn(9L);
        when(disputeRepository.countByStatus(DisputeStatus.OPEN)).thenReturn(10L);
        when(bookingPaymentRepository.countByVoidRetryRequiredTrue()).thenReturn(11L);

        AdminOperationsQueueResponse response = service.getQueueCounts();

        verify(securityContext).requireRole(Role.ADMIN);
        assertThat(response.openDamageClaims()).isEqualTo(6);
        assertThat(response.openSupportCases()).isEqualTo(13);
        assertThat(response.totalOpenItems()).isEqualTo(66);
    }
}
