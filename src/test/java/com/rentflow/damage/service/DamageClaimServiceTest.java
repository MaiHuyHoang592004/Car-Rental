package com.rentflow.damage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.audit.service.AuditLogService;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.booking.service.BookingTimelineService;
import com.rentflow.common.exception.BookingNotFoundException;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.idempotency.service.IdempotencyFailureMarker;
import com.rentflow.common.idempotency.service.IdempotencyResolution;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import com.rentflow.common.idempotency.service.IdempotencyService;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.damage.dto.CreateDamageClaimRequest;
import com.rentflow.damage.dto.DamageClaimEvidenceRequest;
import com.rentflow.damage.dto.DamageClaimResponse;
import com.rentflow.damage.dto.ResolveDamageClaimRequest;
import com.rentflow.damage.dto.RespondDamageClaimRequest;
import com.rentflow.damage.entity.DamageClaim;
import com.rentflow.damage.entity.DamageClaimEvidenceType;
import com.rentflow.damage.entity.DamageClaimStatus;
import com.rentflow.damage.repository.DamageClaimEvidenceRepository;
import com.rentflow.damage.repository.DamageClaimRepository;
import com.rentflow.file.entity.FileMetadata;
import com.rentflow.file.entity.FileStatus;
import com.rentflow.file.repository.FileMetadataRepository;
import com.rentflow.outbox.service.OutboxService;
import com.rentflow.trip.entity.TripRecord;
import com.rentflow.trip.repository.TripRecordRepository;
import com.rentflow.tripcondition.entity.TripConditionReport;
import com.rentflow.tripcondition.entity.TripConditionReportType;
import com.rentflow.tripcondition.repository.TripConditionReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DamageClaimServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-07T00:00:00Z");

    @Mock private DamageClaimRepository claimRepository;
    @Mock private DamageClaimEvidenceRepository evidenceRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private TripRecordRepository tripRecordRepository;
    @Mock private TripConditionReportRepository conditionReportRepository;
    @Mock private FileMetadataRepository fileMetadataRepository;
    @Mock private SecurityContext securityContext;
    @Mock private IdempotencyService idempotencyService;
    @Mock private IdempotencyFailureMarker idempotencyFailureMarker;
    @Mock private BookingTimelineService bookingTimelineService;
    @Mock private AuditLogService auditLogService;
    @Mock private OutboxService outboxService;

    private DamageClaimService service;
    private UUID bookingId;
    private UUID hostId;
    private UUID customerId;
    private UUID fileId;
    private UUID reportId;

    @BeforeEach
    void setUp() {
        service = new DamageClaimService(
                claimRepository,
                evidenceRepository,
                bookingRepository,
                tripRecordRepository,
                conditionReportRepository,
                fileMetadataRepository,
                securityContext,
                idempotencyService,
                idempotencyFailureMarker,
                bookingTimelineService,
                auditLogService,
                outboxService,
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        bookingId = UUID.randomUUID();
        hostId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        fileId = UUID.randomUUID();
        reportId = UUID.randomUUID();
    }

    @Test
    void hostCreatesDamageClaimAfterCompletedTrip() {
        Booking booking = completedBooking();
        mockIdempotency(hostId, IdempotencyScope.CREATE_DAMAGE_CLAIM);
        when(securityContext.currentUserId()).thenReturn(hostId);
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(tripRecordRepository.findByBookingId(bookingId)).thenReturn(Optional.of(checkedOutTrip()));
        when(conditionReportRepository.findByIdAndBookingId(reportId, bookingId)).thenReturn(Optional.of(checkOutReport()));
        when(fileMetadataRepository.findByIdAndStatus(fileId, FileStatus.ACTIVE)).thenReturn(Optional.of(file(hostId)));
        when(claimRepository.save(any(DamageClaim.class))).thenAnswer(invocation -> {
            DamageClaim claim = invocation.getArgument(0);
            claim.setId(UUID.randomUUID());
            return claim;
        });
        when(evidenceRepository.findByClaimIdOrderByCreatedAtAsc(any())).thenReturn(List.of());

        DamageClaimResponse response = service.createClaim(bookingId, uuidKey(), createRequest());

        assertThat(response.status()).isEqualTo(DamageClaimStatus.OPEN);
        assertThat(response.claimAmount()).isEqualByComparingTo("1000000");
        verify(outboxService).append(eq("DAMAGE_CLAIM"), eq(response.id()), eq("DAMAGE_CLAIM_CREATED"), anyString());
        verify(bookingTimelineService).append(eq(bookingId), eq("DAMAGE_CLAIM_CREATED"), eq(hostId), eq("HOST"), anyString());
    }

    @Test
    void hostCannotClaimNonCompletedBooking() {
        Booking booking = completedBooking();
        booking.setStatus(BookingStatus.IN_PROGRESS);
        mockIdempotency(hostId, IdempotencyScope.CREATE_DAMAGE_CLAIM);
        when(securityContext.currentUserId()).thenReturn(hostId);
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.createClaim(bookingId, uuidKey(), createRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("COMPLETED");
    }

    @Test
    void unrelatedHostDenied() {
        Booking booking = completedBooking();
        UUID otherHost = UUID.randomUUID();
        mockIdempotency(otherHost, IdempotencyScope.CREATE_DAMAGE_CLAIM);
        when(securityContext.currentUserId()).thenReturn(otherHost);
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.createClaim(bookingId, uuidKey(), createRequest()))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void customerRespondsToOpenClaim() {
        DamageClaim claim = openClaim();
        mockIdempotency(customerId, IdempotencyScope.RESPOND_DAMAGE_CLAIM);
        when(securityContext.currentUserId()).thenReturn(customerId);
        when(claimRepository.findByIdForUpdate(claim.getId())).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(DamageClaim.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(completedBooking()));
        when(evidenceRepository.findByClaimIdOrderByCreatedAtAsc(claim.getId())).thenReturn(List.of());

        DamageClaimResponse response = service.respond(claim.getId(), uuidKey(), new RespondDamageClaimRequest("Khach dong y xem xet"));

        assertThat(response.status()).isEqualTo(DamageClaimStatus.CUSTOMER_RESPONDED);
        assertThat(response.customerResponse()).contains("Khach dong y");
    }

    @Test
    void adminApprovesFullAmount() {
        DamageClaim claim = openClaim();
        mockAdminMutation(IdempotencyScope.APPROVE_DAMAGE_CLAIM, claim);

        DamageClaimResponse response = service.approve(
                claim.getId(),
                uuidKey(),
                new ResolveDamageClaimRequest(new BigDecimal("1000000"), "Approve"));

        assertThat(response.status()).isEqualTo(DamageClaimStatus.APPROVED);
        assertThat(response.approvedAmount()).isEqualByComparingTo("1000000");
    }

    @Test
    void adminApprovesPartialAmount() {
        DamageClaim claim = openClaim();
        mockAdminMutation(IdempotencyScope.APPROVE_DAMAGE_CLAIM, claim);

        DamageClaimResponse response = service.approve(
                claim.getId(),
                uuidKey(),
                new ResolveDamageClaimRequest(new BigDecimal("600000"), "Partial"));

        assertThat(response.status()).isEqualTo(DamageClaimStatus.PARTIALLY_APPROVED);
        assertThat(response.approvedAmount()).isEqualByComparingTo("600000");
    }

    @Test
    void adminRejectsClaim() {
        DamageClaim claim = openClaim();
        mockAdminMutation(IdempotencyScope.REJECT_DAMAGE_CLAIM, claim);

        DamageClaimResponse response = service.reject(
                claim.getId(),
                uuidKey(),
                new ResolveDamageClaimRequest(null, "Rejected"));

        assertThat(response.status()).isEqualTo(DamageClaimStatus.REJECTED);
    }

    @Test
    void rejectedClaimCannotBeCharged() {
        DamageClaim claim = openClaim();
        claim.setStatus(DamageClaimStatus.REJECTED);
        UUID adminId = UUID.randomUUID();
        mockIdempotency(adminId, IdempotencyScope.CHARGE_DAMAGE_CLAIM);
        when(securityContext.currentUserId()).thenReturn(adminId);
        when(claimRepository.findByIdForUpdate(claim.getId())).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> service.charge(claim.getId(), uuidKey()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("approved");
    }

    private void mockAdminMutation(IdempotencyScope scope, DamageClaim claim) {
        UUID adminId = UUID.randomUUID();
        mockIdempotency(adminId, scope);
        when(securityContext.currentUserId()).thenReturn(adminId);
        when(securityContext.hasRole(com.rentflow.auth.entity.Role.ADMIN)).thenReturn(true);
        when(claimRepository.findByIdForUpdate(claim.getId())).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(DamageClaim.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(completedBooking()));
        when(evidenceRepository.findByClaimIdOrderByCreatedAtAsc(claim.getId())).thenReturn(List.of());
    }

    private void mockIdempotency(UUID userId, IdempotencyScope scope) {
        when(idempotencyService.computeHash(any())).thenReturn("hash");
        when(idempotencyService.resolve(eq(userId), eq(scope), anyString(), eq("hash")))
                .thenReturn(IdempotencyResolution.proceed(UUID.randomUUID()));
    }

    private Booking completedBooking() {
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setHostId(hostId);
        booking.setCustomerId(customerId);
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setPriceSnapshot("{\"currency\":\"VND\"}");
        return booking;
    }

    private TripRecord checkedOutTrip() {
        TripRecord tripRecord = new TripRecord();
        tripRecord.setBookingId(bookingId);
        tripRecord.setCheckOutAt(NOW.minus(Duration.ofHours(4)));
        return tripRecord;
    }

    private TripConditionReport checkOutReport() {
        TripConditionReport report = new TripConditionReport();
        report.setId(reportId);
        report.setBookingId(bookingId);
        report.setReportType(TripConditionReportType.CHECK_OUT);
        return report;
    }

    private FileMetadata file(UUID ownerId) {
        FileMetadata file = new FileMetadata();
        file.setId(fileId);
        file.setOwnerUserId(ownerId);
        file.setStatus(FileStatus.ACTIVE);
        return file;
    }

    private CreateDamageClaimRequest createRequest() {
        return new CreateDamageClaimRequest(
                reportId,
                new BigDecimal("1000000"),
                "Can truoc bi xuoc",
                "Mo ta hu hong",
                List.of(new DamageClaimEvidenceRequest(fileId, DamageClaimEvidenceType.PHOTO, "Anh checkout")));
    }

    private DamageClaim openClaim() {
        DamageClaim claim = new DamageClaim();
        claim.setId(UUID.randomUUID());
        claim.setBookingId(bookingId);
        claim.setHostId(hostId);
        claim.setCustomerId(customerId);
        claim.setStatus(DamageClaimStatus.OPEN);
        claim.setClaimAmount(new BigDecimal("1000000"));
        claim.setCurrency("VND");
        claim.setTitle("Can truoc bi xuoc");
        claim.setDescription("Mo ta hu hong");
        claim.setSubmittedAt(NOW.minusSeconds(60));
        return claim;
    }

    private String uuidKey() {
        return UUID.randomUUID().toString();
    }
}
