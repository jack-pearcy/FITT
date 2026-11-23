package com.fitt.identity.repository;

import com.fitt.identity.domain.TotpCredentialEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TotpCredentialRepository extends JpaRepository<TotpCredentialEntity, Long> {

    // Row lock, so two requests with the same code cannot both pass the reuse
    // check, and the failure count cannot lose an update.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TotpCredentialEntity t where t.userId = :userId")
    Optional<TotpCredentialEntity> findByUserIdForUpdate(@Param("userId") Long userId);

    @Query("select count(t) > 0 from TotpCredentialEntity t where t.userId = :userId and t.confirmedAt is not null")
    boolean isConfirmedFor(@Param("userId") Long userId);

    @Modifying
    @Query("delete from TotpCredentialEntity t where t.userId = :userId")
    int deleteAllForUser(@Param("userId") Long userId);
}
