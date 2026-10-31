-- V1__create_system.sql: from docs/DATABASE.md section 7

create table services (
    code        varchar(7)  primary key check (code ~ '^SRV-[0-9]{3}$'),
    name_ar     text        not null,
    name_en     text        not null,
    enabled     boolean     not null default true,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create table roles (
    code     varchar(20) primary key,
    name_ar  text        not null
);

create table role_services (
    role_code     varchar(20) not null references roles (code),
    service_code  varchar(7)  not null references services (code),
    can_read      boolean     not null default true,
    can_write     boolean     not null default false,
    primary key (role_code, service_code)
);

create table app_users (
    id              uuid        primary key,          -- = Supabase Auth user id (JWT sub)
    email           text        not null,
    display_name    text,
    time_zone       text        not null default 'Asia/Dubai',
    status          text        not null default 'ACTIVE' check (status in ('ACTIVE', 'DISABLED')),
    last_budget_id  uuid,                             -- FK added in V3
    last_export_at  timestamptz,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    version         integer     not null default 0
);
create unique index ux_app_users_email on app_users (lower(email));

create table user_roles (
    user_id    uuid        not null references app_users (id) on delete cascade,
    role_code  varchar(20) not null references roles (code),
    primary key (user_id, role_code)
);

create table invitations (
    id           uuid        primary key default gen_random_uuid(),
    email        text        not null,
    role_code    varchar(20) not null default 'USER' references roles (code),
    status       text        not null default 'PENDING'
                             check (status in ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    invited_by   uuid        not null references app_users (id),
    expires_at   timestamptz not null,
    accepted_at  timestamptz,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);
create unique index ux_invitations_pending_email on invitations (lower(email)) where status = 'PENDING';

create table audit_log (
    id            bigint      generated always as identity primary key,
    occurred_at   timestamptz not null default now(),
    user_id       uuid        references app_users (id),
    service_code  varchar(7)  not null references services (code),
    action        text        not null,
    entity_type   text,
    entity_id     uuid,
    before_data   jsonb,
    after_data    jsonb,
    request_id    text
);
create index ix_audit_log_user_time on audit_log (user_id, occurred_at desc);
create index ix_audit_log_entity on audit_log (entity_type, entity_id);

alter table services      enable row level security;
alter table roles         enable row level security;
alter table role_services enable row level security;
alter table app_users     enable row level security;
alter table user_roles    enable row level security;
alter table invitations   enable row level security;
alter table audit_log     enable row level security;
