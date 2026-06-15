package com.rentflow.savedlisting.service;

import com.rentflow.common.exception.ListingNotFoundException;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.common.web.PageResponse;
import com.rentflow.file.service.FileService;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.savedlisting.dto.SavedListingResponse;
import com.rentflow.savedlisting.entity.SavedListing;
import com.rentflow.savedlisting.repository.SavedListingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SavedListingService {

    private final SavedListingRepository savedListingRepository;
    private final ListingRepository listingRepository;
    private final FileService fileService;
    private final SecurityContext securityContext;

    public SavedListingService(
            SavedListingRepository savedListingRepository,
            ListingRepository listingRepository,
            FileService fileService,
            SecurityContext securityContext) {
        this.savedListingRepository = savedListingRepository;
        this.listingRepository = listingRepository;
        this.fileService = fileService;
        this.securityContext = securityContext;
    }

    @Transactional
    public SavedListingResponse save(UUID listingId) {
        UUID userId = securityContext.currentUserId();
        Listing listing = listingRepository.findByIdAndStatusWithExtras(listingId, ListingStatus.ACTIVE)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));
        SavedListing savedListing = savedListingRepository.findByUserIdAndListingId(userId, listingId)
                .orElseGet(() -> {
                    SavedListing created = new SavedListing();
                    created.setUserId(userId);
                    created.setListingId(listingId);
                    return savedListingRepository.save(created);
                });
        String coverUrl = fileService.getCoverPhotoUrls(Map.of(listing.getId(), listing.getVehicleId())).get(listing.getId());
        return SavedListingResponse.from(savedListing, listing, coverUrl);
    }

    @Transactional
    public void unsave(UUID listingId) {
        UUID userId = securityContext.currentUserId();
        savedListingRepository.findByUserIdAndListingId(userId, listingId)
                .ifPresent(savedListingRepository::delete);
    }

    @Transactional(readOnly = true)
    public PageResponse<SavedListingResponse> list(Pageable pageable) {
        UUID userId = securityContext.currentUserId();
        Page<SavedListing> page = savedListingRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        Map<UUID, Listing> listings = listingRepository.findAllById(
                        page.getContent().stream().map(SavedListing::getListingId).toList())
                .stream()
                .collect(Collectors.toMap(Listing::getId, Function.identity()));
        Map<UUID, UUID> listingVehicleIds = listings.values().stream()
                .collect(Collectors.toMap(Listing::getId, Listing::getVehicleId));
        Map<UUID, String> coverUrls = listingVehicleIds.isEmpty()
                ? Map.of()
                : fileService.getCoverPhotoUrls(listingVehicleIds);
        return PageResponse.from(page, savedListing ->
                SavedListingResponse.from(
                        savedListing,
                        listings.get(savedListing.getListingId()),
                        coverUrls.get(savedListing.getListingId())));
    }
}
