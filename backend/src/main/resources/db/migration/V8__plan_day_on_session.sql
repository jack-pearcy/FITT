-- V8. A session records which day of its plan it trained, as that day's position
-- in the plan (0 for the first).
-- The next day was worked out by counting finished sessions, so deleting an old
-- workout moved the plan back a day and adding a day changed where it pointed.
-- With the day stored, the next day is the one after the last day finished.
-- Null for sessions with no plan and for every session saved before this column;
-- for those the count is still used. No existing row is changed.

alter table workout_sessions add column plan_day_index integer;
