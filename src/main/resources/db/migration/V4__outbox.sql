create table outbox_events (
    id              uuid primary key,
    aggregate_type  varchar(50)  not null,
    aggregate_id    uuid         not null,
    event_type      varchar(100) not null,
    payload         jsonb        not null,
    status          varchar(10)  not null check (status in ('PENDING', 'PUBLISHED', 'DEAD')),
    attempts        int          not null default 0,
    next_attempt_at timestamptz  not null,
    last_error      text,
    created_at      timestamptz  not null,
    published_at    timestamptz
);

create index outbox_events_due_idx on outbox_events (next_attempt_at) where status = 'PENDING';
create index outbox_events_created_at_idx on outbox_events (created_at);
