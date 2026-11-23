-- V10. Two-step sign-in with an authenticator app, plus recovery codes.
-- New tables only, safe to apply on a live schema. No row exists for a user
-- until that user turns the feature on, so nobody's sign-in changes with this.

-- One row per user who has started or finished setting up an authenticator app.
-- The shared secret has to be read back to check a code, so it cannot be hashed.
-- It is stored encrypted (AES-256-GCM) with a key that lives outside the database.
-- confirmed_at is null until the user has proved the app shows the right code.
-- last_used_step stops the same code being accepted twice.
-- failed_attempts and locked_until slow down guessing of the six digits.
create table user_totp (
                           user_id bigint not null,
                           secret_encrypted varbinary(128) not null,
                           confirmed_at datetime(6),
                           last_used_step bigint not null default 0,
                           failed_attempts int not null default 0,
                           locked_until datetime(6),
                           created_at datetime(6) not null,
                           primary key (user_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

-- One-time codes for when the phone with the app is gone. Only the SHA-256 hash
-- is stored. A code carries 80 random bits, so a plain hash is enough.
create table mfa_recovery_codes (
                                    id bigint not null auto_increment,
                                    user_id bigint not null,
                                    code_hash varchar(64) not null,
                                    used_at datetime(6),
                                    created_at datetime(6) not null,
                                    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create index idx_mfa_recovery_codes_user on mfa_recovery_codes (user_id, code_hash);

-- The gap between "password was right" and "code was right". The client holds a
-- random token for a few minutes; only its hash is stored here.
create table mfa_challenges (
                                id bigint not null auto_increment,
                                user_id bigint not null,
                                token_hash varchar(64) not null,
                                expires_at datetime(6) not null,
                                created_at datetime(6) not null,
                                primary key (id),
                                constraint uk_mfa_challenges_hash unique (token_hash)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create index idx_mfa_challenges_user on mfa_challenges (user_id);
