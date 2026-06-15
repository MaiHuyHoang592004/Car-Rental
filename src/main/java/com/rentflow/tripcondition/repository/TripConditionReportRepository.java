package com.rentflow.tripcondition.repository;

import com.rentflow.tripcondition.entity.TripConditionReport;
import com.rentflow.tripcondition.entity.TripConditionReportType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripConditionReportRepository extends JpaRepository<TripConditionReport, UUID> {

    List<TripConditionReport> findByBookingIdOrderBySubmittedAtAsc(UUID bookingId);

    Optional<TripConditionReport> findByIdAndBookingId(UUID id, UUID bookingId);

    Optional<TripConditionReport> findFirstByBookingIdAndReportTypeAndReporterUserIdOrderBySubmittedAtAsc(
            UUID bookingId,
            TripConditionReportType reportType,
            UUID reporterUserId);

    Optional<TripConditionReport> findFirstByBookingIdAndReportTypeOrderBySubmittedAtAsc(
            UUID bookingId,
            TripConditionReportType reportType);

    boolean existsByBookingIdAndReportTypeAndReporterUserId(
            UUID bookingId,
            TripConditionReportType reportType,
            UUID reporterUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM TripConditionReport r WHERE r.id = :id")
    Optional<TripConditionReport> findByIdForUpdate(@Param("id") UUID id);
}
