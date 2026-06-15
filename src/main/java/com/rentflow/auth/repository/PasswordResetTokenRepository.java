package com.rentflow.auth.repository;

import com.rentflow.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update PasswordResetToken token
               set token.usedAt = :usedAt
             where token.userId = :userId
               and token.usedAt is null
            """)
    int markUnusedTokensAsUsed(@Param("userId") UUID userId, @Param("usedAt") Instant usedAt);
}
