package com.rentflow.tripcondition.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.auth.entity.Role;
import com.rentflow.audit.service.AuditLogService;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.booking.service.BookingTimelineService;
import com.rentflow.common.exception.BookingNotFoundException;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.ResourceNotFoundException;
import com.rentflow.common.exception.ValidationException;
import com.rentflow.common.idempotency.service.IdempotencyFailureMarker;
import com.rentflow.common.idempotency.service.IdempotencyResolution;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import com.rentflow.common.idempotency.service.IdempotencyService;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.file.dto.CreatePhotoUploadIntentRequest;
import com.rentflow.file.dto.FileUploadIntentResponse;
import com.rentflow.file.dto.SignedFileUrlResponse;
import com.rentflow.file.service.FileService;
import com.rentflow.outbox.service.OutboxService;
import com.rentflow.trip.entity.TripRecord;
import com.rentflow.trip.repository.TripRecordRepository;
import com.rentflow.tripcondition.dto.ConditionPhotoRequest;
import com.rentflow.tripcondition.dto.CreateConditionReportRequest;
import com.rentflow.tripcondition.dto.DamageItemRequest;
import com.rentflow.tripcondition.dto.TripConditionPhotoResponse;
import com.rentflow.tripcondition.dto.TripConditionReportResponse;
import com.rentflow.tripcondition.dto.TripDamageItemResponse;
import com.rentflow.tripcondition.entity.TripConditionPhoto;
import com.rentflow.tripcondition.entity.TripConditionPhotoAngle;
import com.rentflow.tripcondition.entity.TripConditionReport;
import com.rentflow.tripcondition.entity.TripConditionReporterRole;
import com.rentflow.tripcondition.entity.TripConditionReportType;
import com.rentflow.tripcondition.entity.TripDamageItem;
import com.rentflow.tripcondition.repository.TripConditionPhotoRepository;
import com.rentflow.tripcondition.repository.TripConditionReportRepository;
import com.rentflow.tripcondition.repository.TripDamageItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TripConditionReportService {

    private static final Set<TripConditionPhotoAngle> REQUIRED_ANGLES = EnumSet.of(
            TripConditionPhotoAngle.FRONT,
            TripConditionPhotoAngle.REAR,
            TripConditionPhotoAngle.LEFT,
            TripConditionPhotoAngle.RIGHT);

    private final TripConditionReportRepository reportRepository;
    private final TripConditionPhotoRepository photoRepository;
    private final TripDamageItemRepository damageItemRepository;
    private final BookingRepository bookingRepository;
    private final TripRecordRepository tripRecordRepository;
    private final FileService fileService;
    private final SecurityContext securityContext;
    private final IdempotencyService idempotencyService;
    private final IdempotencyFailureMarker idempotencyFailureMarker;
    private final BookingTimelineService bookingTimelineService;
    private final AuditLogService auditLogService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TripConditionReportService(
            TripConditionReportRepository reportRepository,
            TripConditionPhotoRepository photoRepository,
            TripDamageItemRepository damageItemRepository,
            BookingRepository bookingRepository,
            TripRecordRepository tripRecordRepository,
            FileService fileService,
            SecurityContext securityContext,
            IdempotencyService idempotencyService,
            IdempotencyFailureMarker idempotencyFailureMarker,
            BookingTimelineService bookingTimelineService,
            AuditLogService auditLogService,
            OutboxService outboxService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.reportRepository = reportRepository;
        this.photoRepository = photoRepository;
        this.damageItemRepository = damageItemRepository;
        this.bookingRepository = bookingRepository;
        this.tripRecordRepository = tripRecordRepository;
        this.fileService = fileService;
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
    public FileUploadIntentResponse createTripPhotoUploadIntent(
            UUID bookingId,
            String idempotencyKey,
            CreatePhotoUploadIntentRequest request) {
        UUID actorId = securityContext.currentUserId();
        String requestHash = idempotencyService.computeHash(new UploadIntentHashInput(bookingId, request));
        IdempotencyResolution resolution = idempotencyService.resolve(
                actorId,
                IdempotencyScope.CREATE_TRIP_PHOTO_UPLOAD_INTENT,
                idempotencyKey,
                requestHash);
        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserialize(replay.responseBodyJson(), FileUploadIntentResponse.class);
        }

        UUID idempotencyKeyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            FileUploadIntentResponse response = fileService.createTripPhotoUploadIntent(bookingId, request);
            idempotencyService.complete(idempotencyKeyId, 200, serialize(response));
            return response;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(idempotencyKeyId);
            throw e;
        }
    }

    @Transactional
    public TripConditionReportResponse createReport(
            UUID bookingId,
            String idempotencyKey,
            CreateConditionReportRequest request) {
        UUID actorId = securityContext.currentUserId();
        String requestHash = idempotencyService.computeHash(new ReportHashInput(bookingId, request));
        IdempotencyResolution resolution = idempotencyService.resolve(
                actorId,
                IdempotencyScope.SUBMIT_TRIP_CONDITION_REPORT,
                idempotencyKey,
                requestHash);
        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserialize(replay.responseBodyJson(), TripConditionReportResponse.class);
        }

        UUID idempotencyKeyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            TripConditionReportResponse response = doCreateReport(bookingId, actorId, request);
            idempotencyService.complete(idempotencyKeyId, 201, serialize(response));
            return response;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(idempotencyKeyId);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<TripConditionReportResponse> listReports(UUID bookingId) {
        UUID actorId = securityContext.currentUserId();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
        requireCanView(booking, actorId);
        return reportRepository.findByBookingIdOrderBySubmittedAtAsc(bookingId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TripConditionReportResponse getReport(UUID bookingId, UUID reportId) {
        UUID actorId = securityContext.currentUserId();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
        requireCanView(booking, actorId);
        TripConditionReport report = reportRepository.findByIdAndBookingId(reportId, bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TRIP_CONDITION_REPORT_NOT_FOUND",
                        "TripConditionReport",
                        reportId.toString()));
        return toResponse(report);
    }

    @Transactional
    public UUID requireMatchingReportForTripTransition(
            UUID bookingId,
            TripConditionReportType reportType,
            UUID actorId,
            Integer odometer,
            Integer fuelLevel,
            UUID expectedTripRecordId) {
        TripConditionReport report = reportRepository
                .findFirstByBookingIdAndReportTypeAndReporterUserIdOrderBySubmittedAtAsc(
                        bookingId,
                        reportType,
                        actorId)
                .orElseThrow(() -> new BusinessRuleException(
                        "TRIP_CONDITION_REPORT_REQUIRED",
                        reportType + " condition report is required before this trip transition"));

        if (!report.getOdometer().equals(odometer) || !report.getFuelLevel().equals(fuelLevel)) {
            throw new BusinessRuleException(
                    "TRIP_CONDITION_REPORT_MISMATCH",
                    "Trip transition odometer and fuel level must match the submitted condition report");
        }
        if (expectedTripRecordId != null) {
            if (report.getTripRecordId() != null && !report.getTripRecordId().equals(expectedTripRecordId)) {
                throw new BusinessRuleException(
                        "TRIP_CONDITION_REPORT_MISMATCH",
                        "Condition report belongs to a different trip record");
            }
            if (report.getTripRecordId() == null) {
                report.setTripRecordId(expectedTripRecordId);
                reportRepository.save(report);
            }
        }
        return report.getId();
    }

    @Transactional
    public void attachTripRecord(UUID reportId, UUID tripRecordId) {
        TripConditionReport report = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TRIP_CONDITION_REPORT_NOT_FOUND",
                        "TripConditionReport",
                        reportId.toString()));
        if (report.getTripRecordId() != null && !report.getTripRecordId().equals(tripRecordId)) {
            throw new BusinessRuleException(
                    "TRIP_CONDITION_REPORT_MISMATCH",
                    "Condition report belongs to a different trip record");
        }
        report.setTripRecordId(tripRecordId);
        reportRepository.save(report);
    }

    private TripConditionReportResponse doCreateReport(
            UUID bookingId,
            UUID actorId,
            CreateConditionReportRequest request) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
        TripConditionReporterRole reporterRole = requireParticipant(booking, actorId);
        validateStatusForReport(booking, request.reportType());
        if (reportRepository.existsByBookingIdAndReportTypeAndReporterUserId(
                bookingId,
                request.reportType(),
                actorId)) {
            throw new BusinessRuleException(
                    "TRIP_CONDITION_REPORT_ALREADY_EXISTS",
                    "Condition report has already been submitted for this booking and report type");
        }

        List<ConditionPhotoRequest> photoRequests = request.photos() == null ? List.of() : request.photos();
        List<DamageItemRequest> damageRequests = request.damageItems() == null ? List.of() : request.damageItems();
        validatePhotos(photoRequests);
        validateDamageFlag(request.hasVisibleDamage(), damageRequests);
        photoRequests.forEach(photo -> fileService.requireAttachableTripPhotoFile(photo.fileId(), actorId));

        UUID tripRecordId = null;
        if (request.reportType() == TripConditionReportType.CHECK_OUT) {
            TripRecord tripRecord = tripRecordRepository.findByBookingIdForUpdate(bookingId)
                    .orElseThrow(() -> new BusinessRuleException(
                            "BOOKING_INVALID_STATUS",
                            "Trip record is required before CHECK_OUT condition report"));
            if (request.odometer() < tripRecord.getCheckInOdometer()) {
                throw new BusinessRuleException(
                        "VALIDATION_ERROR",
                        "Check-out odometer must be >= check-in odometer");
            }
            tripRecordId = tripRecord.getId();
        }

        TripConditionReport report = new TripConditionReport();
        report.setBookingId(bookingId);
        report.setTripRecordId(tripRecordId);
        report.setReporterUserId(actorId);
        report.setReporterRole(reporterRole);
        report.setReportType(request.reportType());
        report.setOdometer(request.odometer());
        report.setFuelLevel(request.fuelLevel());
        report.setExteriorCleanliness(blankToNull(request.exteriorCleanliness()));
        report.setInteriorCleanliness(blankToNull(request.interiorCleanliness()));
        report.setHasVisibleDamage(Boolean.TRUE.equals(request.hasVisibleDamage()));
        report.setNote(blankToNull(request.note()));
        report.setLatitude(request.latitude());
        report.setLongitude(request.longitude());
        report.setSubmittedAt(clock.instant());
        TripConditionReport savedReport = reportRepository.save(report);

        List<TripConditionPhoto> photos = savePhotos(savedReport.getId(), photoRequests);
        List<TripDamageItem> damageItems = saveDamageItems(savedReport, photos, damageRequests);
        emitReportSubmitted(savedReport, damageItems.size());
        damageItems.forEach(item -> emitDamageItemReported(savedReport, item));
        return toResponse(savedReport, photos, damageItems);
    }

    private List<TripConditionPhoto> savePhotos(UUID reportId, List<ConditionPhotoRequest> requests) {
        List<TripConditionPhoto> photos = new ArrayList<>();
        int index = 0;
        for (ConditionPhotoRequest request : requests) {
            TripConditionPhoto photo = new TripConditionPhoto();
            photo.setReportId(reportId);
            photo.setFileId(request.fileId());
            photo.setAngle(request.angle());
            photo.setDisplayOrder(request.displayOrder() == null ? index : request.displayOrder());
            photo.setNote(blankToNull(request.note()));
            photos.add(photoRepository.save(photo));
            index++;
        }
        return photos;
    }

    private List<TripDamageItem> saveDamageItems(
            TripConditionReport report,
            List<TripConditionPhoto> photos,
            List<DamageItemRequest> requests) {
        Map<UUID, TripConditionPhoto> photosByFileId = photos.stream()
                .collect(Collectors.toMap(TripConditionPhoto::getFileId, Function.identity()));
        List<TripDamageItem> damageItems = new ArrayList<>();
        for (DamageItemRequest request : requests) {
            UUID photoId = null;
            if (request.photoFileId() != null) {
                TripConditionPhoto photo = photosByFileId.get(request.photoFileId());
                if (photo == null) {
                    throw new ValidationException("Damage item photoFileId must reference a photo in this report");
                }
                photoId = photo.getId();
            }

            TripDamageItem item = new TripDamageItem();
            item.setReportId(report.getId());
            item.setLocation(request.location().trim());
            item.setSeverity(request.severity());
            item.setDescription(request.description().trim());
            item.setPhotoId(photoId);
            item.setPreExisting(Boolean.TRUE.equals(request.preExisting()));
            damageItems.add(damageItemRepository.save(item));
        }
        return damageItems;
    }

    private void validateStatusForReport(Booking booking, TripConditionReportType reportType) {
        if (reportType == TripConditionReportType.CHECK_IN && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleException(
                    "BOOKING_INVALID_STATUS",
                    "Booking must be CONFIRMED for CHECK_IN condition report");
        }
        if (reportType == TripConditionReportType.CHECK_OUT && booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new BusinessRuleException(
                    "BOOKING_INVALID_STATUS",
                    "Booking must be IN_PROGRESS for CHECK_OUT condition report");
        }
    }

    private void validatePhotos(List<ConditionPhotoRequest> photos) {
        Set<TripConditionPhotoAngle> angles = EnumSet.noneOf(TripConditionPhotoAngle.class);
        Set<UUID> fileIds = new LinkedHashSet<>();
        for (ConditionPhotoRequest photo : photos) {
            angles.add(photo.angle());
            if (!fileIds.add(photo.fileId())) {
                throw new ValidationException("A trip photo file can only appear once in a condition report");
            }
        }
        if (!angles.containsAll(REQUIRED_ANGLES)) {
            throw new ValidationException("Condition report requires FRONT, REAR, LEFT and RIGHT photos");
        }
    }

    private void validateDamageFlag(Boolean hasVisibleDamage, List<DamageItemRequest> damageItems) {
        if (!damageItems.isEmpty() && !Boolean.TRUE.equals(hasVisibleDamage)) {
            throw new ValidationException("hasVisibleDamage must be true when damage items are provided");
        }
    }

    private TripConditionReporterRole requireParticipant(Booking booking, UUID actorId) {
        if (booking.getCustomerId().equals(actorId)) {
            return TripConditionReporterRole.CUSTOMER;
        }
        if (booking.getHostId().equals(actorId)) {
            return TripConditionReporterRole.HOST;
        }
        throw new BookingNotFoundException(booking.getId().toString());
    }

    private void requireCanView(Booking booking, UUID actorId) {
        if (booking.getCustomerId().equals(actorId)
                || booking.getHostId().equals(actorId)
                || securityContext.hasRole(Role.ADMIN)) {
            return;
        }
        throw new BookingNotFoundException(booking.getId().toString());
    }

    private TripConditionReportResponse toResponse(TripConditionReport report) {
        return toResponse(
                report,
                photoRepository.findByReportIdOrderByDisplayOrderAsc(report.getId()),
                damageItemRepository.findByReportIdOrderByCreatedAtAsc(report.getId()));
    }

    private TripConditionReportResponse toResponse(
            TripConditionReport report,
            List<TripConditionPhoto> photos,
            List<TripDamageItem> damageItems) {
        return new TripConditionReportResponse(
                report.getId(),
                report.getBookingId(),
                report.getTripRecordId(),
                report.getReporterUserId(),
                report.getReporterRole(),
                report.getReportType(),
                report.getOdometer(),
                report.getFuelLevel(),
                report.getExteriorCleanliness(),
                report.getInteriorCleanliness(),
                report.isHasVisibleDamage(),
                report.getNote(),
                report.getLatitude(),
                report.getLongitude(),
                report.getSubmittedAt(),
                photos.stream().map(this::toPhotoResponse).toList(),
                damageItems.stream().map(this::toDamageItemResponse).toList());
    }

    private TripConditionPhotoResponse toPhotoResponse(TripConditionPhoto photo) {
        SignedFileUrlResponse signedUrl = fileService.getTripPhotoSignedUrlForAuthorizedViewer(photo.getFileId());
        return new TripConditionPhotoResponse(
                photo.getId(),
                photo.getFileId(),
                photo.getAngle(),
                photo.getDisplayOrder(),
                photo.getNote(),
                signedUrl.signedUrl(),
                signedUrl.expiresAt());
    }

    private TripDamageItemResponse toDamageItemResponse(TripDamageItem item) {
        return new TripDamageItemResponse(
                item.getId(),
                item.getLocation(),
                item.getSeverity(),
                item.getDescription(),
                item.getPhotoId(),
                item.isPreExisting());
    }

    private void emitReportSubmitted(TripConditionReport report, int damageItemCount) {
        String eventType = report.getReportType() == TripConditionReportType.CHECK_IN
                ? "TRIP_CONDITION_CHECK_IN_SUBMITTED"
                : "TRIP_CONDITION_CHECK_OUT_SUBMITTED";
        Map<String, Object> details = new HashMap<>();
        details.put("bookingId", report.getBookingId());
        details.put("reportId", report.getId());
        details.put("reportType", report.getReportType().name());
        details.put("reporterRole", report.getReporterRole().name());
        details.put("damageItemCount", damageItemCount);
        String payload = toJson(details);

        bookingTimelineService.append(
                report.getBookingId(),
                eventType,
                report.getReporterUserId(),
                report.getReporterRole().name(),
                payload);
        auditLogService.record(
                report.getReporterUserId(),
                report.getReporterRole().name(),
                "TRIP_CONDITION_REPORT",
                "BOOKING",
                report.getBookingId(),
                "SUBMITTED",
                payload);
        outboxService.append("BOOKING", report.getBookingId(), eventType, payload);
    }

    private void emitDamageItemReported(TripConditionReport report, TripDamageItem item) {
        String payload = toJson(Map.of(
                "bookingId", report.getBookingId(),
                "reportId", report.getId(),
                "damageItemId", item.getId(),
                "severity", item.getSeverity().name(),
                "preExisting", item.isPreExisting()));
        bookingTimelineService.append(
                report.getBookingId(),
                "TRIP_DAMAGE_ITEM_REPORTED",
                report.getReporterUserId(),
                report.getReporterRole().name(),
                payload);
        auditLogService.record(
                report.getReporterUserId(),
                report.getReporterRole().name(),
                "TRIP_DAMAGE_ITEM_REPORTED",
                "BOOKING",
                report.getBookingId(),
                "SUBMITTED",
                payload);
        outboxService.append("BOOKING", report.getBookingId(), "TRIP_DAMAGE_ITEM_REPORTED", payload);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize trip condition response", e);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize trip condition response", e);
        }
    }

    private String toJson(Map<String, ?> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize trip condition details", e);
        }
    }

    private record UploadIntentHashInput(UUID bookingId, CreatePhotoUploadIntentRequest request) {
    }

    private record ReportHashInput(UUID bookingId, CreateConditionReportRequest request) {
    }
}
