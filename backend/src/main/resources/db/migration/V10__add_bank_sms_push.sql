-- V10__add_bank_sms_push.sql: the "Notifications" settings (DESIGN.md D-41).
-- Whether a new bank message waiting for review notifies the user's devices with the count on
-- the app icon (BANK-SMS.md B-22). On by default, as before this column.

alter table app_users add column bank_sms_push boolean not null default true;
