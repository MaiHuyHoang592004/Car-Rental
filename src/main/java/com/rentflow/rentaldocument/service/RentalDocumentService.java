package com.rentflow.rentaldocument.service;

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
import com.rentflow.outbox.service.OutboxService;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.rentaldocument.dto.GenerateRentalDocumentRequest;
import com.rentflow.rentaldocument.dto.RentalDocumentResponse;
import com.rentflow.rentaldocument.entity.RentalDocument;
import com.rentflow.rentaldocument.entity.RentalDocumentStatus;
import com.rentflow.rentaldocument.entity.RentalDocumentType;
import com.rentflow.rentaldocument.repository.RentalDocumentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RentalDocumentService {

    private final RentalDocumentRepository documentRepository;
    private final BookingRepository bookingRepository;
    private final BookingPaymentRepository paymentRepository;
    private final DamageClaimRepository damageClaimRepository;
    private final SecurityContext securityContext;
    private final IdempotencyService idempotencyService;
    private final IdempotencyFailureMarker idempotencyFailureMarker;
    private final BookingTimelineService bookingTimelineService;
    private final AuditLogService auditLogService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RentalDocumentService(
            RentalDocumentRepository documentRepository,
            BookingRepository bookingRepository,
            BookingPaymentRepository paymentRepository,
            DamageClaimRepository damageClaimRepository,
            SecurityContext securityContext,
            IdempotencyService idempotencyService,
            IdempotencyFailureMarker idempotencyFailureMarker,
            BookingTimelineService bookingTimelineService,
            AuditLogService auditLogService,
            OutboxService outboxService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.documentRepository = documentRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
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
    public RentalDocumentResponse generate(UUID bookingId, String idempotencyKey, GenerateRentalDocumentRequest request) {
        UUID actorId = securityContext.currentUserId();
        IdempotencyResolution resolution = idempotencyService.resolve(
                actorId,
                IdempotencyScope.GENERATE_RENTAL_DOCUMENT,
                idempotencyKey,
                idempotencyService.computeHash(new HashInput(bookingId, request)));
        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserialize(replay.responseBodyJson());
        }
        UUID keyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            Booking booking = requireBookingVisible(bookingId, actorId);
            DocumentBuild build = buildDocument(booking, request);
            RentalDocument document = new RentalDocument();
            document.setBookingId(bookingId);
            document.setType(request.type());
            document.setStatus(RentalDocumentStatus.GENERATED);
            document.setTitle(build.title());
            document.setHtmlContent(build.html());
            document.setSourceEntityType(build.sourceEntityType());
            document.setSourceEntityId(build.sourceEntityId());
            document.setGeneratedBy(actorId);
            document.setGeneratedAt(clock.instant());
            document = documentRepository.save(document);
            RentalDocumentResponse response = RentalDocumentResponse.from(document);
            emit(booking, document.getId(), request.type(), actorId);
            idempotencyService.complete(keyId, 201, serialize(response));
            return response;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(keyId);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<RentalDocumentResponse> listForBooking(UUID bookingId, Pageable pageable) {
        UUID actorId = securityContext.currentUserId();
        requireBookingVisible(bookingId, actorId);
        return PageResponse.from(documentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId, pageable),
                RentalDocumentResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<RentalDocumentResponse> listAdmin(UUID bookingId, RentalDocumentType type, Pageable pageable) {
        securityContext.requireRole(Role.ADMIN);
        Page<RentalDocument> page;
        if (bookingId != null && type != null) {
            page = documentRepository.findByBookingIdAndTypeOrderByCreatedAtDesc(bookingId, type, pageable);
        } else if (bookingId != null) {
            page = documentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId, pageable);
        } else if (type != null) {
            page = documentRepository.findByTypeOrderByCreatedAtDesc(type, pageable);
        } else {
            page = documentRepository.findAll(pageable);
        }
        return PageResponse.from(page, RentalDocumentResponse::from);
    }

    @Transactional(readOnly = true)
    public RentalDocumentResponse get(UUID documentId) {
        RentalDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("RENTAL_DOCUMENT_NOT_FOUND", "RentalDocument", documentId.toString()));
        UUID actorId = securityContext.currentUserId();
        requireBookingVisible(document.getBookingId(), actorId);
        return RentalDocumentResponse.from(document);
    }

    @Transactional(readOnly = true)
    public String printHtml(UUID documentId) {
        return get(documentId).htmlContent();
    }

    private Booking requireBookingVisible(UUID bookingId, UUID actorId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
        if (!booking.getCustomerId().equals(actorId)
                && !booking.getHostId().equals(actorId)
                && !securityContext.hasRole(Role.ADMIN)) {
            throw new BookingNotFoundException(bookingId.toString());
        }
        return booking;
    }

    private DocumentBuild buildDocument(Booking booking, GenerateRentalDocumentRequest request) {
        return switch (request.type()) {
            case RENTAL_AGREEMENT -> rentalAgreement(booking);
            case PAYMENT_RECEIPT -> paymentReceipt(booking);
            case REFUND_RECEIPT -> refundReceipt(booking);
            case DAMAGE_INVOICE -> damageInvoice(booking, request.sourceEntityId());
        };
    }

    private DocumentBuild rentalAgreement(Booking booking) {
        String title = "Rental Agreement - Booking " + booking.getId();
        String html = wrap(title, """
                <h1>%s</h1>
                <dl>
                  <dt>Booking ID</dt><dd>%s</dd>
                  <dt>Customer ID</dt><dd>%s</dd>
                  <dt>Host ID</dt><dd>%s</dd>
                  <dt>Pickup</dt><dd>%s at %s</dd>
                  <dt>Return</dt><dd>%s at %s</dd>
                  <dt>Status</dt><dd>%s</dd>
                </dl>
                """.formatted(
                esc(title),
                esc(booking.getId()),
                esc(booking.getCustomerId()),
                esc(booking.getHostId()),
                esc(booking.getPickupDate()),
                esc(booking.getPickupLocation()),
                esc(booking.getReturnDate()),
                esc(booking.getReturnLocation()),
                esc(booking.getStatus())));
        return new DocumentBuild(title, html, "BOOKING", booking.getId());
    }

    private DocumentBuild paymentReceipt(Booking booking) {
        BookingPayment payment = paymentRepository.findByBookingId(booking.getId())
                .orElseThrow(() -> new BusinessRuleException("PAYMENT_NOT_FOUND", "Payment is required for receipt generation"));
        BigDecimal amount = payment.getCapturedAmount().signum() > 0
                ? payment.getCapturedAmount()
                : payment.getAuthorizedAmount();
        if (amount.signum() <= 0) {
            throw new BusinessRuleException("PAYMENT_RECEIPT_NOT_READY", "Payment receipt requires authorized or captured amount");
        }
        String title = "Payment Receipt - Booking " + booking.getId();
        String html = wrap(title, amountTable(title, payment, amount, "Paid/authorized amount"));
        return new DocumentBuild(title, html, "BOOKING_PAYMENT", payment.getId());
    }

    private DocumentBuild refundReceipt(Booking booking) {
        BookingPayment payment = paymentRepository.findByBookingId(booking.getId())
                .orElseThrow(() -> new BusinessRuleException("PAYMENT_NOT_FOUND", "Payment is required for refund receipt generation"));
        if (payment.getRefundedAmount().signum() <= 0) {
            throw new BusinessRuleException("REFUND_RECEIPT_NOT_READY", "Refund receipt requires refunded amount");
        }
        String title = "Refund Receipt - Booking " + booking.getId();
        String html = wrap(title, amountTable(title, payment, payment.getRefundedAmount(), "Refunded amount"));
        return new DocumentBuild(title, html, "BOOKING_PAYMENT", payment.getId());
    }

    private DocumentBuild damageInvoice(Booking booking, UUID claimId) {
        if (claimId == null) {
            throw new BusinessRuleException("DAMAGE_CLAIM_REQUIRED", "sourceEntityId is required for damage invoices");
        }
        DamageClaim claim = damageClaimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("DAMAGE_CLAIM_NOT_FOUND", "DamageClaim", claimId.toString()));
        if (!booking.getId().equals(claim.getBookingId())) {
            throw new ResourceNotFoundException("DAMAGE_CLAIM_NOT_FOUND", "DamageClaim", claimId.toString());
        }
        if (claim.getStatus() != DamageClaimStatus.APPROVED
                && claim.getStatus() != DamageClaimStatus.PARTIALLY_APPROVED
                && claim.getStatus() != DamageClaimStatus.CHARGED) {
            throw new BusinessRuleException("DAMAGE_CLAIM_NOT_APPROVED", "Damage invoice requires an approved damage claim");
        }
        BigDecimal amount = claim.getApprovedAmount() == null ? claim.getClaimAmount() : claim.getApprovedAmount();
        String title = "Damage Invoice - Booking " + booking.getId();
        String html = wrap(title, """
                <h1>%s</h1>
                <dl>
                  <dt>Damage claim</dt><dd>%s</dd>
                  <dt>Title</dt><dd>%s</dd>
                  <dt>Status</dt><dd>%s</dd>
                  <dt>Approved amount</dt><dd>%s %s</dd>
                </dl>
                <p>%s</p>
                """.formatted(
                esc(title),
                esc(claim.getId()),
                esc(claim.getTitle()),
                esc(claim.getStatus()),
                esc(amount),
                esc(claim.getCurrency()),
                esc(claim.getDescription())));
        return new DocumentBuild(title, html, "DAMAGE_CLAIM", claim.getId());
    }

    private String amountTable(String title, BookingPayment payment, BigDecimal amount, String label) {
        return """
                <h1>%s</h1>
                <dl>
                  <dt>Payment ID</dt><dd>%s</dd>
                  <dt>Status</dt><dd>%s</dd>
                  <dt>Provider</dt><dd>%s</dd>
                  <dt>%s</dt><dd>%s %s</dd>
                </dl>
                """.formatted(
                esc(title),
                esc(payment.getId()),
                esc(payment.getStatus()),
                esc(payment.getProvider()),
                esc(label),
                esc(amount),
                esc(payment.getCurrency()));
    }

    private String wrap(String title, String body) {
        return """
                <!doctype html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <title>%s</title>
                  <style>
                    body { font-family: Arial, sans-serif; color: #111827; margin: 40px; line-height: 1.5; }
                    h1 { font-size: 24px; margin-bottom: 24px; }
                    dl { display: grid; grid-template-columns: 180px 1fr; gap: 8px 16px; }
                    dt { font-weight: 700; color: #374151; }
                    dd { margin: 0; }
                    @media print { body { margin: 20mm; } }
                  </style>
                </head>
                <body>
                  <div class="document-meta">Generated at %s</div>
                  %s
                </body>
                </html>
                """.formatted(
                esc(title),
                DateTimeFormatter.ISO_INSTANT.format(clock.instant()),
                body);
    }

    private String esc(Object value) {
        return HtmlUtils.htmlEscape(value == null ? "" : String.valueOf(value));
    }

    private void emit(Booking booking, UUID documentId, RentalDocumentType type, UUID actorId) {
        String actorType = securityContext.hasRole(Role.ADMIN)
                ? "ADMIN"
                : booking.getHostId().equals(actorId) ? "HOST" : "CUSTOMER";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bookingId", booking.getId());
        payload.put("documentId", documentId);
        payload.put("documentType", type);
        String json = serialize(payload);
        bookingTimelineService.append(booking.getId(), "RENTAL_DOCUMENT_GENERATED", actorId, actorType, json);
        auditLogService.record(actorId, actorType, "RENTAL_DOCUMENT_GENERATED", "RENTAL_DOCUMENT", documentId, "SUCCEEDED", json);
        outboxService.append("RENTAL_DOCUMENT", documentId, "RENTAL_DOCUMENT_GENERATED", json);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize rental document JSON", e);
        }
    }

    private RentalDocumentResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, RentalDocumentResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to deserialize rental document response", e);
        }
    }

    private record DocumentBuild(String title, String html, String sourceEntityType, UUID sourceEntityId) {
    }

    private record HashInput(Object id, Object request) {
    }
}
