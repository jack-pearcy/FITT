-- V4. Indexes for the owner-scoped lookups every request makes.
-- V1 only indexed the unique keys and foreign keys, so queries filtering on
-- owner_user_id (plans, sessions, custom exercises) scanned the whole table.
-- Plain reads; no data or column changes, so safe to apply on a live schema.

create index idx_exercises_owner on exercises (owner_user_id);

create index idx_workout_plans_owner_active on workout_plans (owner_user_id, is_active);

-- Session history, ordered by start time.
create index idx_workout_sessions_owner_started on workout_sessions (owner_user_id, started_at);

-- Next-lift and progression: completed sessions counted per user and plan.
create index idx_workout_sessions_owner_plan_finished on workout_sessions (owner_user_id, plan_id, finished_at);
