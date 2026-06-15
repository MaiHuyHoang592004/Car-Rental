package com.rentflow.savedlisting.service;

import com.rentflow.common.security.SecurityContext;
import com.rentflow.file.service.FileService;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.savedlisting.dto.SavedListingResponse;
import com.rentflow.savedlisting.entity.SavedListing;
import com.rentflow.savedlisting.repository.SavedListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavedListingServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-4111-9111-111111111111");
    private static final UUID LISTING_ID = UUID.fromString("22222222-2222-4222-9222-222222222222");
    private static final UUID VEHICLE_ID = UUID.fromString("33333333-3333-4333-9333-333333333333");

    @Mock private SavedListingRepository savedListingRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private FileService fileService;
    @Mock private SecurityContext securityContext;

    private SavedListingService service;

    @BeforeEach
    void setUp() {
        service = new SavedListingService(savedListingRepository, listingRepository, fileService, securityContext);
    }

    @Test
    void saveCreatesSavedListingForActiveListing() {
        when(securityContext.currentUserId()).thenReturn(USER_ID);
        when(listingRepository.findByIdAndStatusWithExtras(LISTING_ID, ListingStatus.ACTIVE)).thenReturn(Optional.of(listing()));
        when(savedListingRepository.findByUserIdAndListingId(USER_ID, LISTING_ID)).thenReturn(Optional.empty());
        when(savedListingRepository.save(any(SavedListing.class))).thenAnswer(invocation -> {
            SavedListing saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.parse("2026-06-07T00:00:00Z"));
            return saved;
        });
        when(fileService.getCoverPhotoUrls(Map.of(LISTING_ID, VEHICLE_ID))).thenReturn(Map.of(LISTING_ID, "/car.jpg"));

        SavedListingResponse response = service.save(LISTING_ID);

        assertThat(response.listingId()).isEqualTo(LISTING_ID);
        assertThat(response.title()).isEqualTo("Toyota Vios");
        assertThat(response.coverPhotoUrl()).isEqualTo("/car.jpg");
    }

    @Test
    void listHydratesListingSummary() {
        SavedListing saved = new SavedListing();
        saved.setId(UUID.randomUUID());
        saved.setUserId(USER_ID);
        saved.setListingId(LISTING_ID);
        saved.setCreatedAt(Instant.parse("2026-06-07T00:00:00Z"));
        when(securityContext.currentUserId()).thenReturn(USER_ID);
        when(savedListingRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(saved), PageRequest.of(0, 20), 1));
        when(listingRepository.findAllById(List.of(LISTING_ID))).thenReturn(List.of(listing()));
        when(fileService.getCoverPhotoUrls(Map.of(LISTING_ID, VEHICLE_ID))).thenReturn(Map.of(LISTING_ID, "/car.jpg"));

        var response = service.list(PageRequest.of(0, 20));

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).title()).isEqualTo("Toyota Vios");
    }

    @Test
    void unsaveDeletesExistingSavedListing() {
        SavedListing saved = new SavedListing();
        saved.setId(UUID.randomUUID());
        when(securityContext.currentUserId()).thenReturn(USER_ID);
        when(savedListingRepository.findByUserIdAndListingId(USER_ID, LISTING_ID)).thenReturn(Optional.of(saved));

        service.unsave(LISTING_ID);

        verify(savedListingRepository).delete(saved);
    }

    private Listing listing() {
        Listing listing = new Listing();
        listing.setId(LISTING_ID);
        listing.setVehicleId(VEHICLE_ID);
        listing.setHostId(UUID.randomUUID());
        listing.setTitle("Toyota Vios");
        listing.setCity("Ha Noi");
        listing.setBasePricePerDay(new BigDecimal("700000.00"));
        listing.setCurrency("VND");
        listing.setStatus(ListingStatus.ACTIVE);
        return listing;
    }
}
