package com.fitt.workouts.service;

import com.fitt.shared.account.UserDataEraser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

// Removes everything the workouts context holds for one user.
//
// Bulk statements, not entity deletes: a long training history would otherwise be
// loaded row by row just to be thrown away. The order follows the foreign keys,
// children first. Custom exercises go last, after the plan slots and session
// entries that point at them. Library exercises have no owner and are never touched.
@Component
public class WorkoutsDataEraser implements UserDataEraser {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void eraseUserData(Long userId) {
        // Pending changes reach the database first, and nothing loaded earlier in
        // this transaction is left pointing at a deleted row.
        entityManager.flush();
        entityManager.clear();

        delete("delete from SetEntryEntity s where s.exerciseEntry.id in ("
                + "select e.id from ExerciseEntryEntity e where e.workoutSession.ownerUserId = :userId)", userId);
        delete("delete from ExerciseEntryEntity e where e.workoutSession.id in ("
                + "select s.id from WorkoutSessionEntity s where s.ownerUserId = :userId)", userId);
        delete("delete from WorkoutSessionEntity s where s.ownerUserId = :userId", userId);

        delete("delete from PlanDayExerciseEntity x where x.workoutPlanDay.id in ("
                + "select d.id from WorkoutPlanDayEntity d where d.workoutPlan.ownerUserId = :userId)", userId);
        delete("delete from WorkoutPlanDayEntity d where d.workoutPlan.id in ("
                + "select p.id from WorkoutPlanEntity p where p.ownerUserId = :userId)", userId);
        delete("delete from WorkoutPlanEntity p where p.ownerUserId = :userId", userId);

        delete("delete from ExerciseEntity e where e.ownerUserId = :userId", userId);
        delete("delete from UserProgressionPreferenceEntity p where p.ownerUserId = :userId", userId);
    }

    private void delete(String jpql, Long userId) {
        entityManager.createQuery(jpql).setParameter("userId", userId).executeUpdate();
    }
}
