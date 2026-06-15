package com.rentflow.support.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.audit.service.AuditLogService;
import com.rentflow.auth.entity.Role;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.booking.service.BookingTimelineService;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.idempotency.service.IdempotencyFailureMarker;
import com.rentflow.common.idempotency.service.IdempotencyResolution;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import com.rentflow.common.idempotency.service.IdempotencyService;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.notification.entity.NotificationType;
import com.rentflow.notification.service.AdminNotificationService;
import com.rentflow.notification.service.NotificationService;
import com.rentflow.outbox.service.OutboxService;
import com.rentflow.support.dto.CloseSupportCaseRequest;
import com.rentflow.support.dto.CreateSupportCaseMessageRequest;
import com.rentflow.support.dto.CreateSupportCaseRequest;
import com.rentflow.support.dto.SupportCaseResponse;
import com.rentflow.support.entity.SupportCase;
import com.rentflow.support.entity.SupportCaseCategory;
import com.rentflow.support.entity.SupportCaseMessage;
import com.rentflow.support.entity.SupportCaseStatus;
import com.rentflow.support.entity.SupportSenderType;
import com.rentflow.support.repository.SupportCaseMessageRepository;
import com.rentflow.support.repository.SupportCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class SupportCaseServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-07T00:00:00Z");
    private static final UUID BOOKING_ID = UUID.fromString("77777777-7777-4777-9777-777777777777");
    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-4111-9111-111111111111");
    private static final UUID HOST_ID = UUID.fromString("22222222-2222-4222-9222-222222222222");
    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CASE_ID = UUID.fromString("33333333-3333-4333-9333-333333333333");
    private static final UUID MESSAGE_ID = UUID.fromString("44444444-4444-4444-9444-444444444444");
    private static final UUID IDEMPOTENCY_ID = UUID.fromString("55555555-5555-4555-9555-555555555555");
    private static final String IDEMPOTENCY_KEY = "8b71f8d2-9e1d-4f7a-bbe6-334c3816df91";

    @Mock private SupportCaseRepository supportCaseRepository;
    @Mock private SupportCaseMessageRepository messageRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private SecurityContext securityContext;
    @Mock private IdempotencyService idempotencyService;
    @Mock private IdempotencyFailureMarker idempotencyFailureMarker;
    @Mock private NotificationService notificationService;
    @Mock private AdminNotificationService adminNotificationService;
    @Mock private BookingTimelineService bookingTimelineService;
    @Mock private AuditLogService auditLogService;
    @Mock private OutboxService outboxService;

    private SupportCaseService service;

    @BeforeEach
    void setUp() {
        service = new SupportCaseService(
                supportCaseRepository,
                messageRepository,
                bookingRepository,
                securityContext,
                idempotencyService,
                idempotencyFailureMarker,
                notificationService,
                adminNotificationService,
                bookingTimelineService,
                auditLogService,
                outboxService,
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void customerCreatesSupportCaseAndNotifiesAdmins() {
        mockIdempotency(CUSTOMER_ID, IdempotencyScope.CREATE_SUPPORT_CASE);
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking()));
        when(supportCaseRepository.save(any(SupportCase.class))).thenAnswer(invocation -> {
            SupportCase supportCase = invocation.getArgument(0);
            supportCase.setId(CASE_ID);
            return supportCase;
        });
        when(messageRepository.save(any(SupportCaseMessage.class))).thenAnswer(invocation -> {
            SupportCaseMessage message = invocation.getArgument(0);
            message.setId(MESSAGE_ID);
            return message;
        });
        when(adminNotificationService.resolveActiveAdminUserIds()).thenReturn(List.of(ADMIN_ID));

        SupportCaseResponse response = service.create(BOOKING_ID, IDEMPOTENCY_KEY, createRequest());

        assertThat(response.id()).isEqualTo(CASE_ID);
        assertThat(response.status()).isEqualTo(SupportCaseStatus.WAITING_ADMIN);
        assertThat(response.messages()).hasSize(1);
        verify(notificationService).create(
                ADMIN_ID,
                NotificationType.SUPPORT_CASE_CREATED,
                "Support case created",
                "Booking " + BOOKING_ID + " has a new support case.");
        verify(outboxService).append(eq("SUPPORT_CASE"), eq(CASE_ID), eq("SUPPORT_CASE_CREATED"), anyString());
    }

    @Test
    void participantGetDoesNotReturnInternalMessages() {
        SupportCase supportCase = supportCase();
        SupportCaseMessage publicMessage = message(false);
        when(supportCaseRepository.findById(CASE_ID)).thenReturn(Optional.of(supportCase));
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking()));
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(securityContext.hasRole(Role.ADMIN)).thenReturn(false);
        when(messageRepository.findBySupportCaseIdAndInternalNoteFalseOrderByCreatedAtAsc(CASE_ID))
                .thenReturn(List.of(publicMessage));

        SupportCaseResponse response = service.get(CASE_ID);

        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().get(0).internalNote()).isFalse();
    }

    @Test
    void adminCanAddInternalNote() {
        SupportCase supportCase = supportCase();
        SupportCaseMessage internal = message(true);
        mockIdempotency(ADMIN_ID, IdempotencyScope.ADD_SUPPORT_CASE_MESSAGE);
        when(securityContext.currentUserId()).thenReturn(ADMIN_ID);
        when(securityContext.hasRole(Role.ADMIN)).thenReturn(true);
        when(supportCaseRepository.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(supportCase));
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking()));
        when(messageRepository.save(any(SupportCaseMessage.class))).thenAnswer(invocation -> {
            SupportCaseMessage message = invocation.getArgument(0);
            message.setId(MESSAGE_ID);
            return message;
        });
        when(messageRepository.findBySupportCaseIdOrderByCreatedAtAsc(CASE_ID)).thenReturn(List.of(internal));

        SupportCaseResponse response = service.addMessage(
                CASE_ID,
                IDEMPOTENCY_KEY,
                new CreateSupportCaseMessageRequest("Internal review", true));

        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().get(0).internalNote()).isTrue();
        assertThat(supportCase.getStatus()).isEqualTo(SupportCaseStatus.WAITING_ADMIN);
    }

    @Test
    void participantCannotAddInternalNote() {
        SupportCase supportCase = supportCase();
        mockIdempotency(CUSTOMER_ID, IdempotencyScope.ADD_SUPPORT_CASE_MESSAGE);
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(supportCaseRepository.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(supportCase));
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking()));

        assertThatThrownBy(() -> service.addMessage(
                CASE_ID,
                IDEMPOTENCY_KEY,
                new CreateSupportCaseMessageRequest("Internal", true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("internal");
    }

    @Test
    void adminClosesSupportCase() {
        SupportCase supportCase = supportCase();
        mockIdempotency(ADMIN_ID, IdempotencyScope.CLOSE_SUPPORT_CASE);
        when(securityContext.currentUserId()).thenReturn(ADMIN_ID);
        when(supportCaseRepository.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(supportCase));
        when(supportCaseRepository.save(any(SupportCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking()));
        when(messageRepository.save(any(SupportCaseMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.findBySupportCaseIdOrderByCreatedAtAsc(CASE_ID)).thenReturn(List.of());

        SupportCaseResponse response = service.close(
                CASE_ID,
                IDEMPOTENCY_KEY,
                new CloseSupportCaseRequest("Resolved"));

        assertThat(response.status()).isEqualTo(SupportCaseStatus.CLOSED);
        assertThat(response.closedBy()).isEqualTo(ADMIN_ID);
        verify(notificationService).create(eq(CUSTOMER_ID), eq(NotificationType.SUPPORT_CASE_CLOSED), anyString(), anyString());
        verify(notificationService).create(eq(HOST_ID), eq(NotificationType.SUPPORT_CASE_CLOSED), anyString(), anyString());
    }

    private void mockIdempotency(UUID userId, IdempotencyScope scope) {
        when(idempotencyService.computeHash(any())).thenReturn("hash");
        when(idempotencyService.resolve(eq(userId), eq(scope), eq(IDEMPOTENCY_KEY), eq("hash")))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_ID));
    }

    private Booking booking() {
        Booking booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setCustomerId(CUSTOMER_ID);
        booking.setHostId(HOST_ID);
        return booking;
    }

    private SupportCase supportCase() {
        SupportCase supportCase = new SupportCase();
        supportCase.setId(CASE_ID);
        supportCase.setBookingId(BOOKING_ID);
        supportCase.setCustomerId(CUSTOMER_ID);
        supportCase.setHostId(HOST_ID);
        supportCase.setOpenedByUserId(CUSTOMER_ID);
        supportCase.setCategory(SupportCaseCategory.BOOKING);
        supportCase.setStatus(SupportCaseStatus.WAITING_ADMIN);
        supportCase.setSubject("Need help");
        return supportCase;
    }

    private SupportCaseMessage message(boolean internal) {
        SupportCaseMessage message = new SupportCaseMessage();
        message.setId(MESSAGE_ID);
        message.setSupportCaseId(CASE_ID);
        message.setSenderUserId(internal ? ADMIN_ID : CUSTOMER_ID);
        message.setSenderType(internal ? SupportSenderType.ADMIN : SupportSenderType.CUSTOMER);
        message.setBody(internal ? "Internal review" : "Need help");
        message.setInternalNote(internal);
        return message;
    }

    private CreateSupportCaseRequest createRequest() {
        return new CreateSupportCaseRequest(SupportCaseCategory.BOOKING, "Need help", "Please check this booking");
    }
}
