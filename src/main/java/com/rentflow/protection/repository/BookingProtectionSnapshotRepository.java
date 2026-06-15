package com.rentflow.protection.repository;

import com.rentflow.protection.entity.BookingProtectionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BookingProtectionSnapshotRepository extends JpaRepository<BookingProtectionSnapshot, UUID> {
}
