-- V7__add_invitation_auth_user.sql: the Supabase Auth user created with an invitation,
-- so revoking an unused invitation can delete that user (API.md 2.3).

alter table invitations add column auth_user_id uuid;
