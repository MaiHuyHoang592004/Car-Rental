package com.rentflow.savedlisting.dto;

import com.rentflow.listing.entity.Listing;
import com.rentflow.savedlisting.entity.SavedListing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SavedListingResponse(
        UUID id,
        UUID listingId,
        String title,
        String city,
        BigDecimal basePricePerDay,
        String currency,
        String coverPhotoUrl,
        Instant createdAt
) {
    public static SavedListingResponse from(SavedListing savedListing, Listing listing, String coverPhotoUrl) {
        return new SavedListingResponse(
                savedListing.getId(),
                savedListing.getListingId(),
                listing == null ? null : listing.getTitle(),
                listing == null ? null : listing.getCity(),
                listing == null ? null : listing.getBasePricePerDay(),
                listing == null ? null : listing.getCurrency(),
                coverPhotoUrl,
                savedListing.getCreatedAt());
    }
}
