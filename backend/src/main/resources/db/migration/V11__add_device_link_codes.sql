-- V11. Connecting a second device, first the Apple Watch, to an account.
-- New table only, safe to apply on a live schema.

-- A signed in phone asks for a code and hands it to the watch, and the watch
-- trades it for a sign-in of its own. The code lives for 60 seconds and is used
-- once: the row is deleted when it is redeemed. Only the SHA-256 hash is
-- stored. A code carries 256 random bits, so a plain hash is enough.
create table device_link_codes (
                                   id bigint not null auto_increment,
                                   user_id bigint not null,
                                   code_hash varchar(64) not null,
                                   expires_at datetime(6) not null,
                                   created_at datetime(6) not null,
                                   primary key (id),
                                   constraint uk_device_link_codes_hash unique (code_hash)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create index idx_device_link_codes_user on device_link_codes (user_id);
