create table accounts (
    id            uuid primary key,
    code          varchar(100) not null,
    type          varchar(20)  not null check (type in ('ASSET', 'LIABILITY', 'REVENUE')),
    currency      char(3)      not null,
    balance_minor bigint       not null default 0,
    unique (code, currency),
    -- lets postings reference (account, currency) so a posting cannot use another currency
    unique (id, currency)
);

create table journal_entries (
    id             uuid primary key,
    reference_type varchar(50)  not null,
    reference_id   varchar(100) not null,
    created_at     timestamptz  not null,
    -- one business operation is booked at most once
    unique (reference_type, reference_id)
);

create table postings (
    id           bigserial primary key,
    entry_id     uuid        not null references journal_entries (id),
    account_id   uuid        not null,
    direction    varchar(6)  not null check (direction in ('DEBIT', 'CREDIT')),
    amount_minor bigint      not null check (amount_minor > 0),
    currency     char(3)     not null,
    foreign key (account_id, currency) references accounts (id, currency)
);

create index postings_entry_id_idx on postings (entry_id);
create index postings_account_id_idx on postings (account_id);

-- Checked at commit, after every posting of the entry is inserted: the entry must have at least
-- two postings and debits must equal credits per currency.
create function assert_journal_entry_balanced(checked_entry uuid) returns void
    language plpgsql as
$$
declare
    posting_count int;
    unbalanced    int;
begin
    select count(*) into posting_count from postings where entry_id = checked_entry;
    if posting_count < 2 then
        raise exception 'journal entry % has % posting(s), needs at least two', checked_entry, posting_count;
    end if;

    select count(*)
    into unbalanced
    from (select currency
          from postings
          where entry_id = checked_entry
          group by currency
          having sum(case direction when 'DEBIT' then amount_minor else -amount_minor end) <> 0) t;
    if unbalanced > 0 then
        raise exception 'journal entry % is not balanced', checked_entry;
    end if;
end;
$$;

create function check_entry_balanced_on_entry() returns trigger
    language plpgsql as
$$
begin
    perform assert_journal_entry_balanced(new.id);
    return null;
end;
$$;

create function check_entry_balanced_on_posting() returns trigger
    language plpgsql as
$$
begin
    perform assert_journal_entry_balanced(new.entry_id);
    return null;
end;
$$;

create constraint trigger journal_entries_balanced
    after insert on journal_entries
    deferrable initially deferred
    for each row execute function check_entry_balanced_on_entry();

create constraint trigger postings_balanced
    after insert on postings
    deferrable initially deferred
    for each row execute function check_entry_balanced_on_posting();

-- Booked entries are never changed; corrections are new entries.
create function reject_ledger_mutation() returns trigger
    language plpgsql as
$$
begin
    raise exception '% is append-only: % rejected', tg_table_name, tg_op;
end;
$$;

create trigger journal_entries_append_only
    before update or delete on journal_entries
    for each row execute function reject_ledger_mutation();

create trigger postings_append_only
    before update or delete on postings
    for each row execute function reject_ledger_mutation();
