-- created_at ties (same microsecond, or a frozen clock) left publishing order to the random id,
-- so a refund could go out before its capture. The identity column follows insertion order.
alter table outbox_events add column position bigint generated always as identity;

drop index outbox_events_due_idx;
create index outbox_events_pending_idx on outbox_events (position) where status = 'PENDING';

-- Dead events are counted on every metrics scrape; keep that off the full table.
create index outbox_events_dead_idx on outbox_events (created_at) where status = 'DEAD';
