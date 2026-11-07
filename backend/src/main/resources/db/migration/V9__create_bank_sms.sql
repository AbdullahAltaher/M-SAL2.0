-- V9__create_bank_sms.sql: bank messages to entries waiting for review (docs/BANK-SMS.md section 4.2).
-- An iPhone shortcut posts the bank's SMS with a personal token; the server keeps a pending entry
-- that the user approves (it becomes a normal entry) or ignores. Nothing is added on its own (B-01).

insert into services (code, name_ar, name_en) values
    ('SRV-011', 'رسائل البنك', 'Bank SMS');

insert into role_services (role_code, service_code, can_read, can_write) values
    ('USER', 'SRV-011', true, true);

-- The digits after "a/c *" in the bank's message, linked to an account of the app (B-11, B-19).
-- A message for digits that are not linked is ignored.
create table sms_account_links (
    id          uuid        primary key default gen_random_uuid(),
    user_id     uuid        not null,
    account_id  uuid        not null,
    suffix      text        not null check (suffix ~ '^[0-9]{3,6}$'),
    created_at  timestamptz not null default now(),
    unique (user_id, suffix),
    foreign key (account_id, user_id) references accounts (id, user_id) on delete cascade
);
create index ix_sms_account_links_account on sms_account_links (account_id);

-- The shortcut's personal token. Only its SHA-256 is kept, never the token itself, and one token
-- is active per user: a new one revokes the old.
create table sms_tokens (
    id            uuid        primary key default gen_random_uuid(),
    user_id       uuid        not null references app_users (id) on delete cascade,
    token_hash    text        not null unique,
    hint          text        not null,              -- the token's last 4 characters, to recognise it
    created_at    timestamptz not null default now(),
    last_used_at  timestamptz,
    revoked_at    timestamptz
);
create unique index ux_sms_tokens_active on sms_tokens (user_id) where revoked_at is null;

-- Words that suggest a category for a merchant (B-10, B-13 to B-16). Per budget, because the
-- categories are. category_id null = "اختر الفئة كل مرة" (B-15).
create table merchant_keywords (
    id           uuid        primary key default gen_random_uuid(),
    user_id      uuid        not null,
    budget_id    uuid        not null,
    category_id  uuid,
    keyword      text        not null check (keyword <> '' and length(keyword) <= 40),
    created_at   timestamptz not null default now(),
    unique (budget_id, keyword),
    foreign key (budget_id, user_id)     references budgets (id, user_id) on delete cascade,
    foreign key (category_id, budget_id) references categories (id, budget_id) on delete cascade
);

-- A debit read from a message, waiting for review (B-01, B-02). Deleted for good when approved
-- or ignored; audit_log keeps a copy without sms_text, which holds the balance (B-07).
create table pending_entries (
    id                     uuid          primary key default gen_random_uuid(),
    user_id                uuid          not null,
    budget_id              uuid          not null,
    account_id             uuid          not null,
    amount                 numeric(12,2) not null check (amount > 0 and amount <= 999999999),
    merchant               text          not null,
    suggested_category_id  uuid,
    received_at            timestamptz   not null,
    entry_date             date          not null,   -- received_at's date in the user's time zone (rule 14)
    sms_text               text          not null,
    sms_hash               text          not null,
    created_at             timestamptz   not null default now(),
    unique (user_id, sms_hash),
    foreign key (budget_id, user_id)    references budgets (id, user_id) on delete cascade,
    foreign key (account_id, budget_id) references accounts (id, budget_id) on delete cascade,
    foreign key (suggested_category_id, budget_id) references categories (id, budget_id)
        on delete set null (suggested_category_id)
);
create index ix_pending_entries_budget on pending_entries (budget_id, received_at desc);

alter table sms_account_links enable row level security;
alter table sms_tokens        enable row level security;
alter table merchant_keywords enable row level security;
alter table pending_entries   enable row level security;
