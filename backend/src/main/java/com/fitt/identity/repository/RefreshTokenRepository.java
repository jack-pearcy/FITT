package com.fitt.identity.repository;

import com.fitt.identity.domain.RefreshTokenEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    // Row lock, so two requests presenting the same token cannot both rotate it.
    // The second waits, then sees the token already revoked.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshTokenEntity t where t.tokenHash = :tokenHash")
    Optional<RefreshTokenEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("update RefreshTokenEntity t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") String familyId, @Param("now") Instant now);

    @Modifying
    @Query("delete from RefreshTokenEntity t where t.ownerUserId = :ownerUserId and t.expiresAt < :now")
    int deleteExpired(@Param("ownerUserId") Long ownerUserId, @Param("now") Instant now);
}
