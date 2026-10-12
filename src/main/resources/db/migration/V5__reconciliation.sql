create table reconciliation_runs (
    id              uuid primary key,
    period_start    date        not null,
    period_end      date        not null,
    statement_lines int         not null,
    matched         int         not null,
    created_at      timestamptz not null,
    check (period_start < period_end)
);

create table reconciliation_discrepancies (
    id             bigserial primary key,
    run_id         uuid         not null references reconciliation_runs (id),
    type           varchar(30)  not null,
    psp_reference  varchar(100) not null,
    operation_type varchar(10)  not null,
    detail         text         not null
);

create index reconciliation_discrepancies_run_id_idx on reconciliation_discrepancies (run_id);
