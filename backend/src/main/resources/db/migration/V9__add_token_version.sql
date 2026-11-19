-- V9. A version number for a user's access tokens.
-- Every access token carries the version it was issued under, and a request is
-- refused when the two differ. Changing the password adds one, which ends every
-- session of the account at once. Tokens issued before this column existed carry
-- no version and are read as 0, so nobody is signed out by the migration.

alter table users add column token_version int not null default 0;
