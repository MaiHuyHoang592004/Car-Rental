package com.rentflow.tripcondition.repository;

import com.rentflow.tripcondition.entity.TripDamageItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TripDamageItemRepository extends JpaRepository<TripDamageItem, UUID> {

    List<TripDamageItem> findByReportIdOrderByCreatedAtAsc(UUID reportId);

    List<TripDamageItem> findByReportIdInOrderByCreatedAtAsc(Collection<UUID> reportIds);
}
