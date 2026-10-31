-- V3__create_budget.sql: from docs/DATABASE.md section 7

create table budgets (
    id                uuid          primary key default gen_random_uuid(),
    user_id           uuid          not null references app_users (id),
    name              text          not null check (btrim(name) <> ''),
    month_label       text          not null default '',
    salary_label      text          not null default 'الراتب',
    salary_amount     numeric(12,2),
    salary_is_approx  boolean       not null default false,
    sort_order        integer       not null default 0,
    last_account_id   uuid,                           -- FK added in V4
    legacy_id         text,
    created_at        timestamptz   not null default now(),
    updated_at        timestamptz   not null default now(),
    deleted_at        timestamptz,
    version           integer       not null default 0,
    unique (id, user_id)
);
create index ix_budgets_user on budgets (user_id, sort_order) where deleted_at is null;
create unique index ux_budgets_legacy on budgets (user_id, legacy_id) where legacy_id is not null;

alter table app_users
    add constraint fk_app_users_last_budget
    foreign key (last_budget_id, id) references budgets (id, user_id)
    on delete set null (last_budget_id);

create table budget_groups (
    id          uuid        primary key default gen_random_uuid(),
    user_id     uuid        not null,
    budget_id   uuid        not null,
    title       text        not null default '',
    sort_order  integer     not null default 0,
    legacy_id   text,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    version     integer     not null default 0,
    unique (id, user_id),
    foreign key (budget_id, user_id) references budgets (id, user_id) on delete cascade
);
create index ix_budget_groups_budget on budget_groups (budget_id, sort_order);
create unique index ux_budget_groups_legacy on budget_groups (budget_id, legacy_id) where legacy_id is not null;

create table budget_items (
    id          uuid          primary key default gen_random_uuid(),
    user_id     uuid          not null,
    group_id    uuid          not null,
    name        text          not null default '',
    amount      numeric(12,2),
    is_approx   boolean       not null default false,
    is_done     boolean       not null default false,
    sort_order  integer       not null default 0,
    legacy_id   text,
    created_at  timestamptz   not null default now(),
    updated_at  timestamptz   not null default now(),
    version     integer       not null default 0,
    foreign key (group_id, user_id) references budget_groups (id, user_id) on delete cascade
);
create index ix_budget_items_group on budget_items (group_id, sort_order);
create unique index ux_budget_items_legacy on budget_items (group_id, legacy_id) where legacy_id is not null;

alter table budgets       enable row level security;
alter table budget_groups enable row level security;
alter table budget_items  enable row level security;
