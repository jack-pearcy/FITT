package com.fitt.measurements.service;

import com.fitt.shared.account.UserDataEraser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

// Removes everything the measurements context holds for one user.
@Component
public class MeasurementsDataEraser implements UserDataEraser {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void eraseUserData(Long userId) {
        entityManager.flush();
        entityManager.clear();
        entityManager.createQuery("delete from BodyWeightEntryEntity b where b.ownerUserId = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
    }
}
