-- Set when a relay claims an event; its outcome is only recorded while the token still matches,
-- so a relay whose lease ran out cannot overwrite the result of the relay that took over.
alter table outbox_events add column lease_token uuid;

create index outbox_events_published_idx on outbox_events (published_at) where status = 'PUBLISHED';
create index idempotency_keys_created_at_idx on idempotency_keys (created_at);
