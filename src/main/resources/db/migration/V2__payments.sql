create table payments (
    id            uuid primary key,
    merchant_id   varchar(64)  not null,
    amount_minor  bigint       not null check (amount_minor > 0),
    fee_minor     bigint       not null check (fee_minor >= 0 and fee_minor <= amount_minor),
    currency      char(3)      not null,
    psp_reference varchar(100) not null unique,
    captured_at   timestamptz  not null
);

create index payments_captured_at_idx on payments (captured_at);

create table refunds (
    id                 uuid primary key,
    payment_id         uuid         not null references payments (id),
    amount_minor       bigint       not null check (amount_minor > 0),
    fee_returned_minor bigint       not null check (fee_returned_minor >= 0),
    psp_reference      varchar(100) not null unique,
    created_at         timestamptz  not null
);

create index refunds_payment_id_idx on refunds (payment_id);
create index refunds_created_at_idx on refunds (created_at);
