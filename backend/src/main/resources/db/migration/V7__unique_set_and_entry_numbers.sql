-- V7. A set number is unique inside its exercise entry, and an order index is
-- unique inside its session.
-- The service read the highest number and inserted the next one with no lock, so
-- two requests at the same moment could store the same number twice.
--
-- Rows that already share a number are renumbered first, or the keys could not be
-- added. Only groups that hold a duplicate are touched: their rows are numbered in
-- their current order (by number, then by id). Sets start at 1, entries at 0.
-- Each subquery reads the table through a derived table because MySQL does not
-- allow an update to select from the table it is changing.

update set_entries s
set set_number = (
    select count(*)
    from (select id, exercise_entry_id, set_number from set_entries) o
    where o.exercise_entry_id = s.exercise_entry_id
      and (o.set_number < s.set_number or (o.set_number = s.set_number and o.id <= s.id))
)
where s.exercise_entry_id in (
    select d.exercise_entry_id
    from (select exercise_entry_id from set_entries
          group by exercise_entry_id, set_number having count(*) > 1) d
);

update exercise_entries e
set order_index = (
    select count(*) - 1
    from (select id, workout_session_id, order_index from exercise_entries) o
    where o.workout_session_id = e.workout_session_id
      and (o.order_index < e.order_index or (o.order_index = e.order_index and o.id <= e.id))
)
where e.workout_session_id in (
    select d.workout_session_id
    from (select workout_session_id from exercise_entries
          group by workout_session_id, order_index having count(*) > 1) d
);

alter table set_entries
    add constraint uk_set_entries_entry_number unique (exercise_entry_id, set_number);

alter table exercise_entries
    add constraint uk_exercise_entries_session_order unique (workout_session_id, order_index);
