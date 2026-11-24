-- V12. A user has at most one unfinished workout session (finished_at null).
-- Starting a session never checked for an open one, and the web app started a
-- new session on every tap, so several open sessions per user can already exist.
--
-- First those are cleaned up. For each user the newest open session stays open:
-- the latest started_at, and on a tie the highest id. Every other open session
-- of that user is stale:
--   * a stale session holding at least one set is finished. set_entries stores
--     no time a set was done, so its finish time is its start time;
--   * a stale session with no sets is deleted, with its exercise entries first.
-- No user data is read out or logged.
--
-- Each statement reads workout_sessions through a derived table with DISTINCT.
-- MySQL refuses a statement that changes workout_sessions while a subquery
-- reads it directly. DISTINCT keeps the derived table from being merged into
-- the outer statement, so MySQL builds it first, as V7 does. The plain
-- "where id in" form, not a multi-table join, also runs on the H2 database the
-- unit tests migrate.

update workout_sessions
set finished_at = started_at
where id in (
    select stale.id from (
    select distinct o.id
    from workout_sessions o
    join workout_sessions n
      on n.owner_user_id = o.owner_user_id
     and n.finished_at is null
     and (n.started_at > o.started_at or (n.started_at = o.started_at and n.id > o.id))
    join exercise_entries e on e.workout_session_id = o.id
    join set_entries x on x.exercise_entry_id = e.id
    where o.finished_at is null
    ) stale
);

-- The stale sessions still open now hold no sets, so no set row points at
-- their entries.
delete from exercise_entries
where workout_session_id in (
    select stale.id from (
    select distinct o.id
    from workout_sessions o
    join workout_sessions n
      on n.owner_user_id = o.owner_user_id
     and n.finished_at is null
     and (n.started_at > o.started_at or (n.started_at = o.started_at and n.id > o.id))
    where o.finished_at is null
    ) stale
);

delete from workout_sessions
where id in (
    select stale.id from (
    select distinct o.id
    from workout_sessions o
    join workout_sessions n
      on n.owner_user_id = o.owner_user_id
     and n.finished_at is null
     and (n.started_at > o.started_at or (n.started_at = o.started_at and n.id > o.id))
    where o.finished_at is null
    ) stale
);

-- Then the guard. MySQL has no partial unique index, so a stored generated column
-- holds the owner while the session is open and null once it is finished, and a
-- unique key covers it. A unique key allows any number of nulls, so finished
-- sessions are not limited. Two starts at the same moment cannot both insert.
--
-- MySQL 8.4 rules checked: the expression may use deterministic operators and
-- base columns, a STORED column can be indexed, and adding a STORED column is
-- not done in place (the table is rebuilt while this runs).
--   https://dev.mysql.com/doc/refman/8.4/en/create-table-generated-columns.html
--   https://dev.mysql.com/doc/refman/8.4/en/alter-table-generated-columns.html
--   https://dev.mysql.com/doc/refman/8.4/en/create-index.html (unique and null)
-- The entity does not map this column. Hibernate never writes it, and schema
-- validation accepts a column the entity does not name.
--
-- STORED sits in a MySQL versioned comment: MySQL 8.0 and later run the text
-- inside, and H2, which the unit tests migrate and which has no STORED keyword,
-- reads it as a comment. H2 also takes one change per alter table, hence two.
alter table workout_sessions
    add column open_owner_id bigint
        generated always as (case when finished_at is null then owner_user_id end) /*!80000 stored */;

alter table workout_sessions
    add constraint uk_workout_sessions_open_owner unique (open_owner_id);
