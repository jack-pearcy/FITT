-- V13. Ids the client makes, and the time a set was done.
--
-- client_id is a random UUID the app makes once for each start, added exercise
-- and logged set, and sends again on every retry. The unique keys let the server
-- answer a retry with the row it already stored, even when two copies of the
-- same request arrive at the same moment. Each key starts with the parent, so
-- an id only has to be unique inside its own user, session or entry.
--
-- performed_at is when the set was done on the device, which can be well before
-- the request arrives when the phone was offline.
--
-- All four columns are nullable. Rows saved before this migration keep nulls,
-- and a unique key allows any number of nulls. One change per statement, as in
-- V12, so the file reads the same on every database that runs it.

alter table workout_sessions
    add column client_id char(36) null;

alter table workout_sessions
    add constraint uk_workout_sessions_owner_client unique (owner_user_id, client_id);

alter table exercise_entries
    add column client_id char(36) null;

alter table exercise_entries
    add constraint uk_exercise_entries_session_client unique (workout_session_id, client_id);

alter table set_entries
    add column client_id char(36) null;

alter table set_entries
    add column performed_at datetime(6) null;

alter table set_entries
    add constraint uk_set_entries_entry_client unique (exercise_entry_id, client_id);
