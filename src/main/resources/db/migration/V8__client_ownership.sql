-- Every payment and idempotency key now belongs to the API client that created it. The service
-- has not been deployed without authentication, so there are no rows lacking an owner.
alter table payments add column client_id varchar(64) not null;

alter table idempotency_keys add column client_id varchar(64) not null;
alter table idempotency_keys drop constraint idempotency_keys_pkey;
alter table idempotency_keys add primary key (client_id, scope, key);
