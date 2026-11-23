package com.fitt.identity.repository;

import com.fitt.identity.domain.DeviceLinkCodeEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DeviceLinkCodeRepository extends JpaRepository<DeviceLinkCodeEntity, Long> {

    // Row lock, so one code cannot be traded for two sign-ins.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DeviceLinkCodeEntity c where c.codeHash = :codeHash")
    Optional<DeviceLinkCodeEntity> findByCodeHashForUpdate(@Param("codeHash") String codeHash);

    // A new code replaces the ones before it, and an account that is deleted or
    // changes its password takes its codes with it.
    @Modifying
    @Query("delete from DeviceLinkCodeEntity c where c.userId = :userId")
    int deleteAllForUser(@Param("userId") Long userId);
}
