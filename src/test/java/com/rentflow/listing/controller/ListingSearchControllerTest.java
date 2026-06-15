package com.rentflow.listing.controller;

import com.rentflow.common.web.PageResponse;
import com.rentflow.listing.dto.ListingSearchRequest;
import com.rentflow.listing.service.ListingSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ListingSearchControllerTest {

    private ListingSearchService listingSearchService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        listingSearchService = mock(ListingSearchService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ListingSearchController(listingSearchService))
                .build();
    }

    @Test
    void search_bindsQueryParametersIntoRequest() throws Exception {
        when(listingSearchService.search(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/listings")
                        .param("city", "Hanoi")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.pageNumber").value(0));

        ArgumentCaptor<ListingSearchRequest> requestCaptor =
                ArgumentCaptor.forClass(ListingSearchRequest.class);
        verify(listingSearchService).search(requestCaptor.capture());
        assertThat(requestCaptor.getValue().city()).isEqualTo("Hanoi");
        assertThat(requestCaptor.getValue().page()).isZero();
        assertThat(requestCaptor.getValue().size()).isEqualTo(20);
    }
}
