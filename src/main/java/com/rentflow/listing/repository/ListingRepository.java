package com.rentflow.listing.repository;

import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingSearchRepositoryCustom;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface ListingRepository extends
        JpaRepository<Listing, UUID>,
        ListingSearchRepositoryCustom {

    Optional<Listing> findByIdAndHostId(UUID id, UUID hostId);

    Optional<Listing> findByTitle(String title);

    Page<Listing> findByHostIdAndStatus(UUID hostId, ListingStatus status, Pageable pageable);

    Page<Listing> findByHostId(UUID hostId, Pageable pageable);

    long countByHostIdAndStatus(UUID hostId, ListingStatus status);

    long countByVehicleIdAndStatus(UUID vehicleId, ListingStatus status);

    boolean existsByVehicleIdAndStatus(UUID vehicleId, ListingStatus status);

    List<Listing> findAllByVehicleIdAndStatusNot(UUID vehicleId, ListingStatus excludedStatus);

    long countByVehicleIdAndStatusIn(UUID vehicleId, Set<ListingStatus> statuses);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Listing l SET l.status = :newStatus WHERE l.vehicleId = :vehicleId AND l.status = :currentStatus")
    int updateStatusByVehicleIdAndStatus(
        @Param("vehicleId") UUID vehicleId,
        @Param("currentStatus") ListingStatus currentStatus,
        @Param("newStatus") ListingStatus newStatus
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Listing l SET l.status = 'SUSPENDED', " +
            "l.suspensionReason = :reason, " +
            "l.suspensionSource = :source, " +
            "l.suspensionUntil = null " +
            "WHERE l.vehicleId = :vehicleId AND l.status = 'ACTIVE'")
    int suspendActiveListingsByVehicleId(
            @Param("vehicleId") UUID vehicleId,
            @Param("reason") String reason,
            @Param("source") String source);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Listing l SET l.status = :newStatus WHERE l.vehicleId = :vehicleId AND l.status != :excludedStatus")
    int updateStatusByVehicleIdAndStatusNot(
        @Param("vehicleId") UUID vehicleId,
        @Param("newStatus") ListingStatus newStatus,
        @Param("excludedStatus") ListingStatus excludedStatus
    );

    Page<Listing> findByStatus(ListingStatus status, Pageable pageable);

    Page<Listing> findByHostIdAndStatusIn(UUID hostId, Set<ListingStatus> statuses, Pageable pageable);

    @Query("SELECT l FROM Listing l WHERE l.status = :status AND (:hostId IS NULL OR l.hostId = :hostId) AND (:city IS NULL OR l.city = :city)")
    Page<Listing> findByFilters(
        @Param("status") ListingStatus status,
        @Param("hostId") UUID hostId,
        @Param("city") String city,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {"extras"})
    @Query("SELECT l FROM Listing l WHERE l.id = :id AND l.status = :status")
    Optional<Listing> findByIdAndStatusWithExtras(
        @Param("id") UUID id,
        @Param("status") ListingStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Listing l WHERE l.id = :id")
    Optional<Listing> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT l FROM Listing l WHERE l.id = :id")
    Optional<Listing> findByIdWithHost(@Param("id") UUID id);

    @Query("SELECT CASE WHEN COUNT(ac) > 0 THEN true ELSE false END " +
           "FROM AvailabilityCalendar ac " +
           "JOIN Listing l ON l.id = ac.listingId " +
           "WHERE l.vehicleId = :vehicleId AND ac.status IN ('HOLD', 'BOOKED')")
    boolean hasActiveBookingsForVehicle(@Param("vehicleId") UUID vehicleId);

    @Query("SELECT CASE WHEN COUNT(l) > 0 THEN true ELSE false END " +
           "FROM Listing l " +
           "LEFT JOIN AvailabilityCalendar ac ON l.id = ac.listingId AND ac.status IN ('HOLD', 'BOOKED') " +
           "WHERE l.vehicleId = :vehicleId AND l.status != 'ARCHIVED' AND ac.status IS NOT NULL")
    boolean existsNonArchivedListingsWithActiveAvailability(@Param("vehicleId") UUID vehicleId);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE listings SET status = 'PENDING_APPROVAL', updated_at = NOW() " +
           "WHERE id = :listingId AND status = 'DRAFT'", nativeQuery = true)
    int atomicSubmitListing(@Param("listingId") UUID listingId);
}
