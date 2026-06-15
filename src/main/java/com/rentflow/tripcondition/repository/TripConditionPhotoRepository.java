package com.rentflow.tripcondition.repository;

import com.rentflow.tripcondition.entity.TripConditionPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TripConditionPhotoRepository extends JpaRepository<TripConditionPhoto, UUID> {

    List<TripConditionPhoto> findByReportIdOrderByDisplayOrderAsc(UUID reportId);

    List<TripConditionPhoto> findByReportIdInOrderByDisplayOrderAsc(Collection<UUID> reportIds);

    boolean existsByFileId(UUID fileId);
}
