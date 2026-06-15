package com.rentflow.support.service;

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
import com.rentflow.notification.entity.NotificationType;
import com.rentflow.notification.service.AdminNotificationService;
import com.rentflow.notification.service.NotificationService;
import com.rentflow.outbox.service.OutboxService;
import com.rentflow.support.dto.CloseSupportCaseRequest;
import com.rentflow.support.dto.CreateSupportCaseMessageRequest;
import com.rentflow.support.dto.CreateSupportCaseRequest;
import com.rentflow.support.dto.SupportCaseMessageResponse;
import com.rentflow.support.dto.SupportCaseResponse;
import com.rentflow.support.entity.SupportCase;
import com.rentflow.support.entity.SupportCaseCategory;
import com.rentflow.support.entity.SupportCaseMessage;
import com.rentflow.support.entity.SupportCaseStatus;
import com.rentflow.support.entity.SupportSenderType;
import com.rentflow.support.repository.SupportCaseMessageRepository;
import com.rentflow.support.repository.SupportCaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SupportCaseService {

    private final SupportCaseRepository supportCaseRepository;
    private final SupportCaseMessageRepository messageRepository;
    private final BookingRepository bookingRepository;
    private final SecurityContext securityContext;
    private final IdempotencyService idempotencyService;
    private final IdempotencyFailureMarker idempotencyFailureMarker;
    private final NotificationService notificationService;
    private final AdminNotificationService adminNotificationService;
    private final BookingTimelineService bookingTimelineService;
    private final AuditLogService auditLogService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SupportCaseService(
            SupportCaseRepository supportCaseRepository,
            SupportCaseMessageRepository messageRepository,
            BookingRepository bookingRepository,
            SecurityContext securityContext,
            IdempotencyService idempotencyService,
            IdempotencyFailureMarker idempotencyFailureMarker,
            NotificationService notificationService,
            AdminNotificationService adminNotificationService,
            BookingTimelineService bookingTimelineService,
            AuditLogService auditLogService,
            OutboxService outboxService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.supportCaseRepository = supportCaseRepository;
        this.messageRepository = messageRepository;
        this.bookingRepository = bookingRepository;
        this.securityContext = securityContext;
        this.idempotencyService = idempotencyService;
        this.idempotencyFailureMarker = idempotencyFailureMarker;
        this.notificationService = notificationService;
        this.adminNotificationService = adminNotificationService;
        this.bookingTimelineService = bookingTimelineService;
        this.auditLogService = auditLogService;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public SupportCaseResponse create(UUID bookingId, String idempotencyKey, CreateSupportCaseRequest request) {
        UUID actorId = securityContext.currentUserId();
        IdempotencyResolution resolution = resolve(actorId, IdempotencyScope.CREATE_SUPPORT_CASE, idempotencyKey,
                new HashInput(bookingId, request));
        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserialize(replay.responseBodyJson());
        }
        UUID keyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
            requireBookingParticipant(booking, actorId);

            SupportCase supportCase = new SupportCase();
            supportCase.setBookingId(bookingId);
            supportCase.setCustomerId(booking.getCustomerId());
            supportCase.setHostId(booking.getHostId());
            supportCase.setOpenedByUserId(actorId);
            supportCase.setCategory(request.category() == null ? SupportCaseCategory.OTHER : request.category());
            supportCase.setStatus(SupportCaseStatus.WAITING_ADMIN);
            supportCase.setSubject(request.subject().trim());
            supportCase = supportCaseRepository.save(supportCase);

            SupportCaseMessage message = createMessage(supportCase, actorId, actorType(booking, actorId), request.body(), false);
            message = messageRepository.save(message);
            emit(booking, supportCase.getId(), message.getId(), "SUPPORT_CASE_CREATED", actorId, actorType(booking, actorId));
            notifyAdmins(
                    NotificationType.SUPPORT_CASE_CREATED,
                    "Support case created",
                    "Booking " + bookingId + " has a new support case.");

            SupportCaseResponse response = response(supportCase, List.of(message));
            idempotencyService.complete(keyId, 201, serialize(response));
            return response;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(keyId);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<SupportCaseResponse> listBookingCases(UUID bookingId, Pageable pageable) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
        UUID actorId = securityContext.currentUserId();
        boolean admin = securityContext.hasRole(Role.ADMIN);
        if (!admin) {
            requireBookingParticipant(booking, actorId);
        }
        return PageResponse.from(supportCaseRepository.findByBookingIdOrderByCreatedAtDesc(bookingId, pageable),
                supportCase -> response(supportCase, List.of()));
    }

    @Transactional(readOnly = true)
    public PageResponse<SupportCaseResponse> listMyCases(Pageable pageable) {
        UUID actorId = securityContext.currentUserId();
        Page<SupportCase> page = supportCaseRepository.findByCustomerIdOrHostIdOrderByCreatedAtDesc(actorId, actorId, pageable);
        return PageResponse.from(page, supportCase -> response(supportCase, List.of()));
    }

    @Transactional(readOnly = true)
    public SupportCaseResponse get(UUID caseId) {
        SupportCase supportCase = requireCase(caseId);
        Booking booking = requireBooking(supportCase.getBookingId());
        UUID actorId = securityContext.currentUserId();
        boolean admin = securityContext.hasRole(Role.ADMIN);
        if (!admin) {
            requireBookingParticipant(booking, actorId);
        }
        return response(supportCase, loadMessages(caseId, admin));
    }

    @Transactional(readOnly = true)
    public PageResponse<SupportCaseResponse> listAdminCases(SupportCaseStatus status, Pageable pageable) {
        securityContext.requireRole(Role.ADMIN);
        Page<SupportCase> page = status == null
                ? supportCaseRepository.findAll(pageable)
                : supportCaseRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return PageResponse.from(page, supportCase -> response(supportCase, List.of()));
    }

    @Transactional
    public SupportCaseResponse addMessage(UUID caseId, String idempotencyKey, CreateSupportCaseMessageRequest request) {
        UUID actorId = securityContext.currentUserId();
        IdempotencyResolution resolution = resolve(actorId, IdempotencyScope.ADD_SUPPORT_CASE_MESSAGE, idempotencyKey,
                new HashInput(caseId, request));
        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserialize(replay.responseBodyJson());
        }
        UUID keyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            SupportCase supportCase = supportCaseRepository.findByIdForUpdate(caseId)
                    .orElseThrow(() -> new ResourceNotFoundException("SUPPORT_CASE_NOT_FOUND", "SupportCase", caseId.toString()));
            Booking booking = requireBooking(supportCase.getBookingId());
            boolean admin = securityContext.hasRole(Role.ADMIN);
            if (!admin) {
                requireBookingParticipant(booking, actorId);
            }
            if (supportCase.getStatus() == SupportCaseStatus.CLOSED) {
                throw new BusinessRuleException("SUPPORT_CASE_CLOSED", "Closed support cases cannot receive messages");
            }
            boolean internal = Boolean.TRUE.equals(request.internalNote());
            if (internal && !admin) {
                throw new BusinessRuleException("SUPPORT_INTERNAL_NOTE_ADMIN_ONLY", "Only admins can create internal notes");
            }
            SupportSenderType senderType = admin ? SupportSenderType.ADMIN : actorType(booking, actorId);
            SupportCaseMessage message = createMessage(supportCase, actorId, senderType, request.body(), internal);
            message = messageRepository.save(message);
            if (!internal) {
                supportCase.setStatus(admin ? SupportCaseStatus.WAITING_PARTICIPANT : SupportCaseStatus.WAITING_ADMIN);
                supportCaseRepository.save(supportCase);
                notifyNextReaders(supportCase, booking, senderType);
            }
            emit(
                    booking,
                    supportCase.getId(),
                    message.getId(),
                    internal ? "SUPPORT_CASE_INTERNAL_NOTE_ADDED" : "SUPPORT_CASE_MESSAGE_ADDED",
                    actorId,
                    senderType);
            SupportCaseResponse response = response(supportCase, loadMessages(caseId, admin));
            idempotencyService.complete(keyId, 200, serialize(response));
            return response;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(keyId);
            throw e;
        }
    }

    @Transactional
    public SupportCaseResponse close(UUID caseId, String idempotencyKey, CloseSupportCaseRequest request) {
        UUID actorId = securityContext.currentUserId();
        securityContext.requireRole(Role.ADMIN);
        IdempotencyResolution resolution = resolve(actorId, IdempotencyScope.CLOSE_SUPPORT_CASE, idempotencyKey,
                new HashInput(caseId, request));
        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserialize(replay.responseBodyJson());
        }
        UUID keyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            SupportCase supportCase = supportCaseRepository.findByIdForUpdate(caseId)
                    .orElseThrow(() -> new ResourceNotFoundException("SUPPORT_CASE_NOT_FOUND", "SupportCase", caseId.toString()));
            if (supportCase.getStatus() == SupportCaseStatus.CLOSED) {
                throw new BusinessRuleException("SUPPORT_CASE_CLOSED", "Support case is already closed");
            }
            supportCase.setStatus(SupportCaseStatus.CLOSED);
            supportCase.setClosedAt(clock.instant());
            supportCase.setClosedBy(actorId);
            supportCase = supportCaseRepository.save(supportCase);
            Booking booking = requireBooking(supportCase.getBookingId());
            if (request != null && request.note() != null && !request.note().isBlank()) {
                messageRepository.save(createMessage(supportCase, actorId, SupportSenderType.ADMIN, request.note(), false));
            }
            emit(booking, supportCase.getId(), null, "SUPPORT_CASE_CLOSED", actorId, SupportSenderType.ADMIN);
            notificationService.create(supportCase.getCustomerId(), NotificationType.SUPPORT_CASE_CLOSED,
                    "Support case closed", "Support case for booking " + supportCase.getBookingId() + " was closed.");
            notificationService.create(supportCase.getHostId(), NotificationType.SUPPORT_CASE_CLOSED,
                    "Support case closed", "Support case for booking " + supportCase.getBookingId() + " was closed.");
            SupportCaseResponse response = response(supportCase, loadMessages(caseId, true));
            idempotencyService.complete(keyId, 200, serialize(response));
            return response;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(keyId);
            throw e;
        }
    }

    private SupportCase requireCase(UUID caseId) {
        return supportCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("SUPPORT_CASE_NOT_FOUND", "SupportCase", caseId.toString()));
    }

    private Booking requireBooking(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
    }

    private void requireBookingParticipant(Booking booking, UUID actorId) {
        if (!booking.getCustomerId().equals(actorId) && !booking.getHostId().equals(actorId)) {
            throw new BookingNotFoundException(booking.getId().toString());
        }
    }

    private SupportCaseMessage createMessage(
            SupportCase supportCase,
            UUID actorId,
            SupportSenderType senderType,
            String body,
            boolean internal) {
        SupportCaseMessage message = new SupportCaseMessage();
        message.setSupportCaseId(supportCase.getId());
        message.setSenderUserId(actorId);
        message.setSenderType(senderType);
        message.setBody(body.trim());
        message.setInternalNote(internal);
        return message;
    }

    private List<SupportCaseMessage> loadMessages(UUID caseId, boolean includeInternal) {
        return includeInternal
                ? messageRepository.findBySupportCaseIdOrderByCreatedAtAsc(caseId)
                : messageRepository.findBySupportCaseIdAndInternalNoteFalseOrderByCreatedAtAsc(caseId);
    }

    private SupportCaseResponse response(SupportCase supportCase, List<SupportCaseMessage> messages) {
        return SupportCaseResponse.from(
                supportCase,
                messages.stream().map(SupportCaseMessageResponse::from).toList());
    }

    private SupportSenderType actorType(Booking booking, UUID actorId) {
        return booking.getHostId().equals(actorId) ? SupportSenderType.HOST : SupportSenderType.CUSTOMER;
    }

    private void notifyNextReaders(SupportCase supportCase, Booking booking, SupportSenderType senderType) {
        if (senderType == SupportSenderType.ADMIN) {
            notificationService.create(booking.getCustomerId(), NotificationType.SUPPORT_CASE_MESSAGE,
                    "Support case updated", "Support replied to booking " + booking.getId() + ".");
            notificationService.create(booking.getHostId(), NotificationType.SUPPORT_CASE_MESSAGE,
                    "Support case updated", "Support replied to booking " + booking.getId() + ".");
            return;
        }
        notifyAdmins(
                NotificationType.SUPPORT_CASE_MESSAGE,
                "Support case message",
                "Booking " + booking.getId() + " has a new support message.");
        UUID otherParticipant = senderType == SupportSenderType.CUSTOMER ? booking.getHostId() : booking.getCustomerId();
        notificationService.create(otherParticipant, NotificationType.SUPPORT_CASE_MESSAGE,
                "Support case message", "Booking " + booking.getId() + " has a new support message.");
    }

    private void notifyAdmins(NotificationType type, String title, String message) {
        for (UUID adminId : adminNotificationService.resolveActiveAdminUserIds()) {
            notificationService.create(adminId, type, title, message);
        }
    }

    private IdempotencyResolution resolve(UUID userId, IdempotencyScope scope, String key, Object request) {
        return idempotencyService.resolve(userId, scope, key, idempotencyService.computeHash(request));
    }

    private void emit(
            Booking booking,
            UUID supportCaseId,
            UUID messageId,
            String eventType,
            UUID actorId,
            SupportSenderType senderType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bookingId", booking.getId());
        payload.put("supportCaseId", supportCaseId);
        payload.put("messageId", messageId);
        payload.put("eventType", eventType);
        String json = serialize(payload);
        bookingTimelineService.append(booking.getId(), eventType, actorId, senderType.name(), json);
        auditLogService.record(actorId, senderType.name(), eventType, "SUPPORT_CASE", supportCaseId, "SUCCEEDED", json);
        outboxService.append("SUPPORT_CASE", supportCaseId, eventType, json);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize support case JSON", e);
        }
    }

    private SupportCaseResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, SupportCaseResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to deserialize support case response", e);
        }
    }

    private record HashInput(Object id, Object request) {
    }
}
