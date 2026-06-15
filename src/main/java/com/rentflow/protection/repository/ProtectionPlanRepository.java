package com.rentflow.protection.repository;

import com.rentflow.protection.entity.ProtectionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProtectionPlanRepository extends JpaRepository<ProtectionPlan, UUID> {

    Optional<ProtectionPlan> findByCodeIgnoreCaseAndActiveTrue(String code);

    List<ProtectionPlan> findByActiveTrueOrderByPriceAmountAsc();
}
