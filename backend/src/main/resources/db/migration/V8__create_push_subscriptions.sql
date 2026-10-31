-- V8__create_push_subscriptions.sql: the daily reminder (SCREENS.md section 7).
-- daily_reminder_time is in the user's own time zone; last_reminder_on is the user's "today"
-- of the last check, so the reminder runs once a day even if the server restarts.

alter table app_users add column daily_reminder_time time;   -- null = التذكير متوقف
alter table app_users add column last_reminder_on date;

create table push_subscriptions (
    id          uuid        primary key default gen_random_uuid(),
    user_id     uuid        not null references app_users (id) on delete cascade,
    endpoint    text        not null unique,
    p256dh      text        not null,
    auth        text        not null,
    user_agent  text,
    created_at  timestamptz not null default now(),
    last_used_at timestamptz
);
create index ix_push_subscriptions_user on push_subscriptions (user_id);

alter table push_subscriptions enable row level security;
