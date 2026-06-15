package com.rentflow.listing;

import com.rentflow.common.web.PageResponse;
import com.rentflow.file.service.FileService;
import com.rentflow.listing.dto.ListingSearchCriteria;
import com.rentflow.listing.dto.ListingSearchRequest;
import com.rentflow.listing.dto.ListingSearchResponse;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.listing.service.ListingSearchService;
import com.rentflow.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ListingSearchService")
class ListingSearchServiceTest {

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private FileService fileService;

    private ListingSearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new ListingSearchService(listingRepository, vehicleRepository, fileService);
        lenient().when(fileService.getCoverPhotoUrls(any(Map.class))).thenReturn(Map.of());
    }

    private ListingSearchResponse aListing(UUID id, String city) {
        return new ListingSearchResponse(
                id, "Test Listing", city, null, BigDecimal.valueOf(500), "VND",
                null, null, null, null, null);
    }

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("with city — forwards city to repository criteria")
        void search_withCity_forwardsCityToCriteria() {
            ListingSearchRequest request = new ListingSearchRequest(
                    null, "Ho Chi Minh City", null, null, null,
                    null, null, null, null, null, null, null, null, 0, 20);

            ArgumentCaptor<ListingSearchCriteria> criteriaCaptor =
                    ArgumentCaptor.forClass(ListingSearchCriteria.class);
            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

            when(listingRepository.search(criteriaCaptor.capture(), pageableCaptor.capture()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            PageResponse<ListingSearchResponse> result = searchService.search(request);

            assertThat(result.content()).isEmpty();
            assertThat(criteriaCaptor.getValue().city()).isEqualTo("Ho Chi Minh City");
            assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(0);
        }

        @Test
        @DisplayName("with date range — forwards dates to repository criteria")
        void search_withDateRange_forwardsDatesToCriteria() {
            LocalDate pickup = LocalDate.of(2026, 5, 15);
            LocalDate ret    = LocalDate.of(2026, 5, 18);
            ListingSearchRequest request = new ListingSearchRequest(
                    null, null, null, pickup, ret,
                    null, null, null, null, null, null, null, null, 0, 20);

            ArgumentCaptor<ListingSearchCriteria> criteriaCaptor =
                    ArgumentCaptor.forClass(ListingSearchCriteria.class);

            when(listingRepository.search(criteriaCaptor.capture(), any()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            searchService.search(request);

            ListingSearchCriteria captured = criteriaCaptor.getValue();
            assertThat(captured.pickupDate()).isEqualTo(pickup);
            assertThat(captured.returnDate()).isEqualTo(ret);
        }

        @Test
        @DisplayName("with instant book and rating — forwards richer search filters")
        void search_withRicherFilters_forwardsToCriteria() {
            ListingSearchRequest request = new ListingSearchRequest(
                    null, null, null, null, null,
                    null, null, null, null, null,
                    true, BigDecimal.valueOf(4.5), null, 0, 20);

            ArgumentCaptor<ListingSearchCriteria> criteriaCaptor =
                    ArgumentCaptor.forClass(ListingSearchCriteria.class);

            when(listingRepository.search(criteriaCaptor.capture(), any()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            searchService.search(request);

            assertThat(criteriaCaptor.getValue().instantBook()).isTrue();
            assertThat(criteriaCaptor.getValue().minRating()).isEqualByComparingTo("4.5");
        }

        @Test
        @DisplayName("with oversized page size — caps to 100")
        void search_withOversizedPageSize_cappedTo100() {
            // ListingSearchRequest constructor normalises size > 100 to 100
            ListingSearchRequest request = new ListingSearchRequest(
                    null, null, null, null, null,
                    null, null, null, null, null, null, null, null, 0, 200);

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

            when(listingRepository.search(any(), pageableCaptor.capture()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 200), 0));

            searchService.search(request);

            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        }

        @Test
        @DisplayName("with no dates — returns listings without date filter")
        void search_withNoDates_returnsActiveListings() {
            ListingSearchResponse listing = aListing(UUID.randomUUID(), "Hanoi");

            when(listingRepository.search(any(), any()))
                    .thenReturn(new PageImpl<>(List.of(listing), PageRequest.of(0, 20), 1));

            PageResponse<ListingSearchResponse> result = searchService.search(
                    new ListingSearchRequest(
                            null, null, null, null, null,
                            null, null, null, null, null, null, null, null, 0, 20));

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).city()).isEqualTo("Hanoi");
        }

        @Test
        @DisplayName("with pickup but no return — throws validation error")
        void search_withPartialDateRange_throwsValidationError() {
            ListingSearchRequest request = new ListingSearchRequest(
                    null, null, null, LocalDate.of(2026, 5, 15), null,
                    null, null, null, null, null, null, null, null, 0, 20);

            assertThatThrownBy(() -> searchService.search(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Both pickupDate and returnDate must be provided together");
        }
    }
}
