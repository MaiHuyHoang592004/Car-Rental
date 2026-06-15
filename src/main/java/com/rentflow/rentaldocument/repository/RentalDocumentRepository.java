package com.rentflow.rentaldocument.repository;

import com.rentflow.rentaldocument.entity.RentalDocument;
import com.rentflow.rentaldocument.entity.RentalDocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RentalDocumentRepository extends JpaRepository<RentalDocument, UUID> {

    Page<RentalDocument> findByBookingIdOrderByCreatedAtDesc(UUID bookingId, Pageable pageable);

    Page<RentalDocument> findByTypeOrderByCreatedAtDesc(RentalDocumentType type, Pageable pageable);

    Page<RentalDocument> findByBookingIdAndTypeOrderByCreatedAtDesc(UUID bookingId, RentalDocumentType type, Pageable pageable);
}
