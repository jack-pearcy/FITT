package com.fitt.identity.repository;

import com.fitt.identity.domain.RecoveryCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCodeEntity, Long> {

    Optional<RecoveryCodeEntity> findByUserIdAndCodeHashAndUsedAtIsNull(Long userId, String codeHash);

    long countByUserIdAndUsedAtIsNull(Long userId);

    @Modifying
    @Query("delete from RecoveryCodeEntity c where c.userId = :userId")
    int deleteAllForUser(@Param("userId") Long userId);
}
