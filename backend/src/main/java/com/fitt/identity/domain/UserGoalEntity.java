package com.fitt.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "user_goals")
public class UserGoalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GoalDirection goalDirection;

    // Nullable. User can skip target weight during onboarding.
    @Column
    private BigDecimal targetWeightKg;

    // Nullable. User can skip timeline.
    @Column
    private Integer timelineWeeks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CalorieGoalMode calorieGoalMode;

    // Set to TDEE when mode is AUTO. Set to user input when mode is MANUAL.
    @Column
    private Integer calorieGoal;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected UserGoalEntity() {}

    public UserGoalEntity(
            Long ownerUserId,
            GoalDirection goalDirection,
            BigDecimal targetWeightKg,
            Integer timelineWeeks,
            CalorieGoalMode calorieGoalMode,
            Integer calorieGoal
    ) {
        this.ownerUserId = ownerUserId;
        this.goalDirection = goalDirection;
        this.targetWeightKg = targetWeightKg;
        this.timelineWeeks = timelineWeeks;
        this.calorieGoalMode = calorieGoalMode;
        this.calorieGoal = calorieGoal;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            GoalDirection goalDirection,
            BigDecimal targetWeightKg,
            Integer timelineWeeks,
            CalorieGoalMode calorieGoalMode,
            Integer calorieGoal
    ) {
        this.goalDirection = goalDirection;
        this.targetWeightKg = targetWeightKg;
        this.timelineWeeks = timelineWeeks;
        this.calorieGoalMode = calorieGoalMode;
        this.calorieGoal = calorieGoal;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public GoalDirection getGoalDirection() { return goalDirection; }
    public BigDecimal getTargetWeightKg() { return targetWeightKg; }
    public Integer getTimelineWeeks() { return timelineWeeks; }
    public CalorieGoalMode getCalorieGoalMode() { return calorieGoalMode; }
    public Integer getCalorieGoal() { return calorieGoal; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}