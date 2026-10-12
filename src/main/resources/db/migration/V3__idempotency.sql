create table idempotency_keys (
    scope         varchar(50)  not null,
    key           varchar(255) not null,
    request_hash  char(64)     not null,
    response_body text,
    created_at    timestamptz  not null,
    primary key (scope, key)
);
