package com.rentflow.savedlisting.repository;

import com.rentflow.savedlisting.entity.SavedListing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SavedListingRepository extends JpaRepository<SavedListing, UUID> {

    boolean existsByUserIdAndListingId(UUID userId, UUID listingId);

    Optional<SavedListing> findByUserIdAndListingId(UUID userId, UUID listingId);

    Page<SavedListing> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
