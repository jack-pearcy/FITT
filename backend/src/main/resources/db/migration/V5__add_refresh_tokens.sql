-- V5. Refresh tokens, so a client can get a new access token without the password.
-- Only the SHA-256 hash of a token is stored; the token itself exists on the client alone.
-- family_id groups every token descended from one login, so a replayed token can
-- revoke the whole chain at once. New table only, safe to apply on a live schema.

create table refresh_tokens (
                                id bigint not null auto_increment,
                                owner_user_id bigint not null,
                                token_hash varchar(64) not null,
                                family_id varchar(36) not null,
                                expires_at datetime(6) not null,
                                revoked_at datetime(6),
                                created_at datetime(6) not null,
                                primary key (id),
                                constraint uk_refresh_tokens_hash unique (token_hash)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

-- Revoking a family, and clearing a user's expired tokens at login.
create index idx_refresh_tokens_family on refresh_tokens (family_id);
create index idx_refresh_tokens_owner_expires on refresh_tokens (owner_user_id, expires_at);
