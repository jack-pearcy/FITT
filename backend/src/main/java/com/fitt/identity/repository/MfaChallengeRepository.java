package com.fitt.identity.repository;

import com.fitt.identity.domain.MfaChallengeEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface MfaChallengeRepository extends JpaRepository<MfaChallengeEntity, Long> {

    // Row lock, so one challenge cannot be traded for two sign-ins.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from MfaChallengeEntity c where c.tokenHash = :tokenHash")
    Optional<MfaChallengeEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("delete from MfaChallengeEntity c where c.userId = :userId and c.expiresAt < :now")
    int deleteExpired(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from MfaChallengeEntity c where c.userId = :userId")
    int deleteAllForUser(@Param("userId") Long userId);
}
