package com.rentflow.damage.repository;

import com.rentflow.damage.entity.DamageClaimEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DamageClaimEvidenceRepository extends JpaRepository<DamageClaimEvidence, UUID> {

    List<DamageClaimEvidence> findByClaimIdOrderByCreatedAtAsc(UUID claimId);
}
