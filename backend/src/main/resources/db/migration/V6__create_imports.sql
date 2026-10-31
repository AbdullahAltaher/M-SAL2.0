-- V6__create_imports.sql: from docs/DATABASE.md section 7

create table imports (
    id          uuid        primary key default gen_random_uuid(),
    user_id     uuid        not null references app_users (id),
    file_name   text,
    mode        text        not null check (mode in ('MERGE', 'REPLACE')),
    raw_data    jsonb       not null,
    counts      jsonb       not null default '{}',
    warnings    jsonb       not null default '[]',
    created_at  timestamptz not null default now()
);
create index ix_imports_user on imports (user_id, created_at desc);

alter table imports enable row level security;
