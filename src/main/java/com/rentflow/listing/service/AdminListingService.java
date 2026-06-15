package com.rentflow.listing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.availability.repository.AvailabilityCalendarRepository;
import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.ListingNotFoundException;
import com.rentflow.listing.dto.AdminListingDetailResponse;
import com.rentflow.listing.dto.ExtraResponse;
import org.springframework.dao.DataIntegrityViolationException;
import com.rentflow.listing.dto.ListingResponse;
import com.rentflow.listing.dto.ListingSummaryResponse;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.event.ListingApprovedEvent;
import com.rentflow.listing.mapper.ListingMapper;
import com.rentflow.listing.repository.ExtraRepository;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.notification.entity.NotificationType;
import com.rentflow.notification.service.NotificationService;
import com.rentflow.outbox.service.OutboxService;
import com.rentflow.user.repository.UserProfileRepository;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.entity.VehicleStatus;
import com.rentflow.vehicle.repository.VehicleRepository;
import com.rentflow.vehicledocument.service.VehicleComplianceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class AdminListingService {

    private final ListingRepository listingRepository;
    private final VehicleRepository vehicleRepository;
    private final AvailabilityCalendarRepository availabilityRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ListingStateMachine stateMachine;
    private final ListingMapper mapper;
    private final ExtraRepository extraRepository;
    private final UserProfileRepository userProfileRepository;
    private final AuthUserRepository authUserRepository;
    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private VehicleComplianceService vehicleComplianceService;

    @Autowired
    public AdminListingService(ListingRepository listingRepository,
                               VehicleRepository vehicleRepository,
                               AvailabilityCalendarRepository availabilityRepository,
                               ApplicationEventPublisher eventPublisher,
                               ListingStateMachine stateMachine,
                               ListingMapper mapper,
                               ExtraRepository extraRepository,
                               UserProfileRepository userProfileRepository,
                               AuthUserRepository authUserRepository,
                               BookingRepository bookingRepository,
                               NotificationService notificationService,
                               OutboxService outboxService,
                               ObjectMapper objectMapper) {
        this.listingRepository = listingRepository;
        this.vehicleRepository = vehicleRepository;
        this.availabilityRepository = availabilityRepository;
        this.eventPublisher = eventPublisher;
        this.stateMachine = stateMachine;
        this.mapper = mapper;
        this.extraRepository = extraRepository;
        this.userProfileRepository = userProfileRepository;
        this.authUserRepository = authUserRepository;
        this.bookingRepository = bookingRepository;
        this.notificationService = notificationService;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
    }

    @Autowired(required = false)
    void setVehicleComplianceService(VehicleComplianceService vehicleComplianceService) {
        this.vehicleComplianceService = vehicleComplianceService;
    }

    public AdminListingService(ListingRepository listingRepository,
                               VehicleRepository vehicleRepository,
                               AvailabilityCalendarRepository availabilityRepository,
                               ApplicationEventPublisher eventPublisher,
                               ListingStateMachine stateMachine,
                               ListingMapper mapper,
                               ExtraRepository extraRepository,
                               UserProfileRepository userProfileRepository,
                               AuthUserRepository authUserRepository,
                               OutboxService outboxService,
                               ObjectMapper objectMapper) {
        this(
                listingRepository,
                vehicleRepository,
                availabilityRepository,
                eventPublisher,
                stateMachine,
                mapper,
                extraRepository,
                userProfileRepository,
                authUserRepository,
                null,
                null,
                outboxService,
                objectMapper);
    }

    @Transactional(readOnly = true)
    public Page<ListingSummaryResponse> listListings(ListingStatus status, UUID hostId, String city, Pageable pageable) {
        Page<Listing> listings = listingRepository.findByFilters(status, hostId, city, pageable);
        Map<UUID, Vehicle> vehiclesById = loadVehiclesById(listings.getContent());
        return mapper.toSummaryPage(listings, vehiclesById);
    }

    @Transactional(readOnly = true)
    public AdminListingDetailResponse getListing(UUID listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));

        Vehicle vehicle = vehicleRepository.findById(listing.getVehicleId()).orElse(null);
        long generatedDays = availabilityRepository.countByListingId(listingId);

        var profile = userProfileRepository.findByUserId(listing.getHostId()).orElse(null);
        String hostEmail = authUserRepository.findById(listing.getHostId())
                .map(AuthUser::getEmail).orElse(null);
        AdminListingDetailResponse.HostSummary hostSummary = new AdminListingDetailResponse.HostSummary(
                listing.getHostId(),
                profile != null ? profile.getFullName() : null,
                hostEmail,
                (int) listingRepository.countByHostIdAndStatus(listing.getHostId(), ListingStatus.ACTIVE)
        );

        ListingResponse.VehicleSummary vehicleSummary = null;
        List<ExtraResponse> extras = extraRepository.findByListingId(listingId).stream()
                .map(ExtraResponse::from).toList();
        if (vehicle != null) {
            vehicleSummary = new ListingResponse.VehicleSummary(
                    vehicle.getCategory(),
                    vehicle.getMake(),
                    vehicle.getModel(),
                    vehicle.getManufactureYear(),
                    vehicle.getTransmission(),
                    vehicle.getFuelType(),
                    vehicle.getSeats(),
                    vehicle.getStatus()
            );
        }

        ListingResponse listingResponse = ListingResponse.from(listing, vehicleSummary, extras, generatedDays);

        return new AdminListingDetailResponse(
                listingResponse,
                hostSummary,
                vehicle == null ? null : new AdminListingDetailResponse.VehicleRiskSummary(
                        vehicle.getId(),
                        vehicle.getStatus(),
                        (int) listingRepository.countByVehicleIdAndStatus(vehicle.getId(), ListingStatus.ACTIVE)),
                new AdminListingDetailResponse.BookingSummary(
                        bookingRepository == null ? 0 : (int) bookingRepository.countByListingIdAndStatusIn(
                                listingId,
                                List.of(
                                        BookingStatus.HELD,
                                        BookingStatus.PENDING_HOST_APPROVAL,
                                        BookingStatus.CONFIRMED,
                                        BookingStatus.IN_PROGRESS))),
                new AdminListingDetailResponse.ModerationSummary(
                        listing.getSuspensionReason(),
                        listing.getSuspensionSource(),
                        listing.getSuspensionUntil(),
                        listing.getRejectedReason(),
                        listing.getRejectedAt())
        );
    }

    @Transactional
    public ListingResponse approveListing(UUID listingId) {
        // Lock listing AND vehicle with SELECT FOR UPDATE to prevent race conditions
        Listing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));

        Vehicle vehicle = vehicleRepository.findByIdForUpdate(listing.getVehicleId())
                .orElseThrow(() -> new BusinessRuleException("VEHICLE_NOT_FOUND", "Vehicle not found"));

        // Validate preconditions
        if (vehicle.getStatus() != VehicleStatus.ACTIVE) {
            throw new BusinessRuleException("VEHICLE_NOT_ACTIVE",
                    "Vehicle must be ACTIVE before listing can be approved. Current status: " + vehicle.getStatus());
        }

        if (listing.getStatus() != ListingStatus.PENDING_APPROVAL) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "Listing must be PENDING_APPROVAL to be approved. Current status: " + listing.getStatus());
        }

        if (vehicleComplianceService != null) {
            vehicleComplianceService.assertVehicleCompliant(vehicle.getId());
        }

        // Check one ACTIVE listing per vehicle constraint
        boolean hasActive = listingRepository.existsByVehicleIdAndStatus(
                vehicle.getId(), ListingStatus.ACTIVE);
        if (hasActive) {
            throw new BusinessRuleException("ONE_ACTIVE_LISTING_PER_VEHICLE",
                    "Vehicle already has an ACTIVE listing");
        }

        // Transition listing to ACTIVE
        listing.setStatus(ListingStatus.ACTIVE);
        try {
            listingRepository.save(listing);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("ONE_ACTIVE_LISTING_PER_VEHICLE",
                    "Vehicle already has an active listing");
        }

        eventPublisher.publishEvent(new ListingApprovedEvent(listingId));
        outboxService.append(
                "LISTING",
                listing.getId(),
                "LISTING_APPROVED",
                serializePayload(Map.of(
                        "listingId", listing.getId(),
                        "hostId", listing.getHostId(),
                        "approvedAt", Instant.now().toString())));

        log.info("Listing approved: {} (vehicle: {})", listingId, vehicle.getId());

        return mapper.toResponse(listing, vehicle, List.of());
    }

    @Transactional
    public ListingResponse rejectListing(UUID listingId, String reason) {
        Listing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));

        if (listing.getStatus() != ListingStatus.PENDING_APPROVAL) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "Only PENDING_APPROVAL listings can be rejected");
        }

        stateMachine.validateTransition(listing.getStatus(), ListingStatus.DRAFT);
        listing.setStatus(ListingStatus.DRAFT);
        listing.setRejectedReason(reason == null ? null : reason.trim());
        listing.setRejectedAt(Instant.now());
        listingRepository.save(listing);
        outboxService.append(
                "LISTING",
                listing.getId(),
                "LISTING_REJECTED",
                serializePayload(Map.of(
                        "listingId", listing.getId(),
                        "hostId", listing.getHostId(),
                        "reason", reason == null ? "" : reason,
                        "rejectedAt", Instant.now().toString())));
        if (notificationService != null) {
            notificationService.create(
                    listing.getHostId(),
                    NotificationType.LISTING_REJECTED,
                    "Listing rejected",
                    "Your listing was rejected: " + (reason == null || reason.isBlank() ? "No reason provided" : reason.trim()));
        }

        log.info("Listing rejected: {} reason: {}", listingId, reason);

        Vehicle vehicle = vehicleRepository.findById(listing.getVehicleId()).orElse(null);
        return mapper.toResponse(listing, vehicle, List.of());
    }

    @Transactional
    public ListingResponse suspendListing(UUID listingId, String reason, String source, Instant suspensionUntil) {
        Listing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));

        if (!stateMachine.canTransition(listing.getStatus(), ListingStatus.SUSPENDED)) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "Cannot suspend listing from status: " + listing.getStatus());
        }

        listing.setStatus(ListingStatus.SUSPENDED);
        listing.setSuspensionReason(reason == null ? null : reason.trim());
        listing.setSuspensionSource(source == null || source.isBlank() ? "ADMIN" : source.trim());
        listing.setSuspensionUntil(suspensionUntil);
        listingRepository.save(listing);

        log.info("Listing suspended: {} reason: {}", listingId, reason);

        Vehicle vehicle = vehicleRepository.findById(listing.getVehicleId()).orElse(null);
        return mapper.toResponse(listing, vehicle, List.of());
    }

    @Transactional
    public ListingResponse reactivateListing(UUID listingId) {
        Listing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));

        if (listing.getStatus() != ListingStatus.SUSPENDED) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "Only SUSPENDED listings can be reactivated");
        }

        Vehicle vehicle = vehicleRepository.findById(listing.getVehicleId())
                .orElseThrow(() -> new BusinessRuleException("VEHICLE_NOT_FOUND", "Vehicle not found"));

        if (vehicle.getStatus() != VehicleStatus.ACTIVE) {
            throw new BusinessRuleException("VEHICLE_NOT_ACTIVE",
                    "Vehicle must be ACTIVE to reactivate listing");
        }

        boolean hasActive = listingRepository.existsByVehicleIdAndStatus(
                vehicle.getId(), ListingStatus.ACTIVE);
        if (hasActive) {
            throw new BusinessRuleException("ONE_ACTIVE_LISTING_PER_VEHICLE",
                    "Vehicle already has an ACTIVE listing");
        }

        listing.setStatus(ListingStatus.ACTIVE);
        clearSuspensionMetadata(listing);
        listingRepository.save(listing);

        log.info("Listing reactivated: {}", listingId);

        return mapper.toResponse(listing, vehicle, List.of());
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize listing outbox payload", ex);
        }
    }

    private void clearSuspensionMetadata(Listing listing) {
        listing.setSuspensionReason(null);
        listing.setSuspensionSource(null);
        listing.setSuspensionUntil(null);
    }

    private Map<UUID, Vehicle> loadVehiclesById(List<Listing> listings) {
        List<UUID> vehicleIds = listings.stream()
                .map(Listing::getVehicleId)
                .distinct()
                .toList();
        if (vehicleIds.isEmpty()) {
            return Map.of();
        }
        return vehicleRepository.findAllById(vehicleIds).stream()
                .collect(LinkedHashMap::new,
                        (map, vehicle) -> map.put(vehicle.getId(), vehicle),
                        Map::putAll);
    }
}
