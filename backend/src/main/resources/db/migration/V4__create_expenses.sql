-- V4__create_expenses.sql: from docs/DATABASE.md section 7

create table category_templates (
    code                 text    primary key,
    name_ar              text    not null,
    emoji                text    not null,
    sort_order           integer not null,
    default_suggestions  text[]  not null default '{}'
);

create table accounts (
    id           uuid        primary key default gen_random_uuid(),
    user_id      uuid        not null,
    budget_id    uuid        not null,
    name         text        not null check (btrim(name) <> ''),
    is_archived  boolean     not null default false,
    sort_order   integer     not null default 0,
    legacy_id    text,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    version      integer     not null default 0,
    unique (id, user_id),
    unique (id, budget_id),
    foreign key (budget_id, user_id) references budgets (id, user_id) on delete cascade
);
create index ix_accounts_budget on accounts (budget_id, sort_order);
create unique index ux_accounts_legacy on accounts (budget_id, legacy_id) where legacy_id is not null;

alter table budgets
    add constraint fk_budgets_last_account
    foreign key (last_account_id, id) references accounts (id, budget_id)
    on delete set null (last_account_id);

create table categories (
    id          uuid        primary key default gen_random_uuid(),
    user_id     uuid        not null,
    budget_id   uuid        not null,
    code        text        references category_templates (code),
    name        text        not null,
    emoji       text        not null default '📦',
    is_hidden   boolean     not null default false,
    sort_order  integer     not null default 0,
    legacy_id   text,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    version     integer     not null default 0,
    unique (id, budget_id),
    foreign key (budget_id, user_id) references budgets (id, user_id) on delete cascade
);
create index ix_categories_budget on categories (budget_id, sort_order);
create unique index ux_categories_code on categories (budget_id, code) where code is not null;
create unique index ux_categories_legacy on categories (budget_id, legacy_id) where legacy_id is not null;

create table salary_cycles (
    id                         uuid          primary key default gen_random_uuid(),
    user_id                    uuid          not null,
    account_id                 uuid          not null,
    start_date                 date          not null,
    allowance                  numeric(12,2) not null default 0 check (allowance >= 0),
    expected_next_salary_date  date,
    legacy_id                  text,
    created_at                 timestamptz   not null default now(),
    updated_at                 timestamptz   not null default now(),
    version                    integer       not null default 0,
    check (expected_next_salary_date is null or expected_next_salary_date > start_date),
    unique (account_id, start_date),
    foreign key (account_id, user_id) references accounts (id, user_id) on delete cascade
);
create unique index ux_salary_cycles_legacy on salary_cycles (account_id, legacy_id) where legacy_id is not null;

create table entries (
    id           uuid          primary key default gen_random_uuid(),
    user_id      uuid          not null,
    budget_id    uuid          not null,
    account_id   uuid          not null,
    category_id  uuid          not null,
    type         text          not null check (type in ('EXPENSE', 'INCOME')),
    amount       numeric(12,2) not null check (amount > 0 and amount <= 999999999),
    name         text          not null default '',
    entry_date   date          not null,
    legacy_id    text,
    created_at   timestamptz   not null default now(),
    updated_at   timestamptz   not null default now(),
    deleted_at   timestamptz,
    version      integer       not null default 0,
    foreign key (budget_id, user_id)     references budgets (id, user_id) on delete cascade,
    foreign key (account_id, budget_id)  references accounts (id, budget_id),
    foreign key (category_id, budget_id) references categories (id, budget_id)
);
create index ix_entries_account_date on entries (account_id, entry_date) where deleted_at is null;
create index ix_entries_deleted on entries (budget_id, deleted_at desc) where deleted_at is not null;
create unique index ux_entries_legacy on entries (budget_id, legacy_id) where legacy_id is not null;

alter table category_templates enable row level security;
alter table accounts           enable row level security;
alter table categories         enable row level security;
alter table salary_cycles      enable row level security;
alter table entries            enable row level security;
