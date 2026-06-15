package com.rentflow.savedlisting.controller;

import com.rentflow.common.web.PageResponse;
import com.rentflow.common.web.PageableValidation;
import com.rentflow.savedlisting.dto.SavedListingResponse;
import com.rentflow.savedlisting.service.SavedListingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SavedListingController {

    private final SavedListingService savedListingService;

    @GetMapping("/api/v1/me/saved-listings")
    public ResponseEntity<PageResponse<SavedListingResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableValidation.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(savedListingService.list(pageable));
    }

    @PostMapping("/api/v1/listings/{listingId}/save")
    public ResponseEntity<SavedListingResponse> save(@PathVariable UUID listingId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(savedListingService.save(listingId));
    }

    @DeleteMapping("/api/v1/listings/{listingId}/save")
    public ResponseEntity<Void> unsave(@PathVariable UUID listingId) {
        savedListingService.unsave(listingId);
        return ResponseEntity.noContent().build();
    }
}
