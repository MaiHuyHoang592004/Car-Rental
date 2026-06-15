package com.rentflow.listing.service;

import com.rentflow.common.exception.ListingNotFoundException;
import com.rentflow.common.web.PageResponse;
import com.rentflow.file.service.FileService;
import com.rentflow.listing.dto.*;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListingSearchService {

    private final ListingRepository listingRepository;
    private final VehicleRepository vehicleRepository;
    private final FileService fileService;

    public PageResponse<ListingSearchResponse> search(ListingSearchRequest request) {
        validateDateRange(request);

        ListingSearchCriteria criteria = new ListingSearchCriteria(
            normalizeBlank(request.query()),
            request.city(),
            request.categories() != null && request.categories().isEmpty() ? null : request.categories(),
            request.minPrice(),
            request.maxPrice(),
            request.seats(),
            request.transmission(),
            request.fuelType(),
            request.instantBook(),
            request.minRating(),
            request.pickupDate(),
            request.returnDate(),
            request.sort()
        );

        Pageable pageable = PageRequest.of(request.page(), request.size());
        Page<ListingSearchResponse> page = listingRepository.search(criteria, pageable);
        Map<UUID, UUID> listingVehicleIds = listingRepository.findAllById(
                        page.getContent().stream().map(ListingSearchResponse::id).toList())
                .stream()
                .collect(Collectors.toMap(Listing::getId, Listing::getVehicleId));
        Map<UUID, String> coverUrls = fileService.getCoverPhotoUrls(listingVehicleIds);
        Page<ListingSearchResponse> hydratedPage = page.map(response ->
                withCoverPhoto(response, coverUrls.get(response.id())));

        return new PageResponse<>(
            hydratedPage.getContent(),
            hydratedPage.getNumber(),
            hydratedPage.getSize(),
            hydratedPage.getTotalElements(),
            hydratedPage.getTotalPages()
        );
    }

    public ListingDetailResponse getListingDetail(UUID listingId) {
        Listing listing = listingRepository
            .findByIdAndStatusWithExtras(listingId, ListingStatus.ACTIVE)
            .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));

        return ListingDetailResponse.from(
            listing,
            vehicleRepository.findById(listing.getVehicleId()).orElse(null),
            listing.getExtras(),
            fileService.getListingPhotoUrls(listing.getId(), listing.getVehicleId())
        );
    }

    private ListingSearchResponse withCoverPhoto(ListingSearchResponse response, String coverPhotoUrl) {
        if (coverPhotoUrl == null) {
            return response;
        }
        return new ListingSearchResponse(
                response.id(),
                response.title(),
                response.city(),
                response.category(),
                response.basePricePerDay(),
                response.currency(),
                response.seats(),
                response.transmission(),
                response.fuelType(),
                coverPhotoUrl,
                response.ratingAverage());
    }

    private void validateDateRange(ListingSearchRequest request) {
        boolean hasPickup = request.pickupDate() != null;
        boolean hasReturn = request.returnDate() != null;
        if (hasPickup != hasReturn) {
            throw new IllegalArgumentException(
                "Both pickupDate and returnDate must be provided together, or neither");
        }
        if (hasPickup && !request.pickupDate().isBefore(request.returnDate())) {
            throw new IllegalArgumentException("pickupDate must be strictly before returnDate");
        }
    }

    private String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
