-- Run after 20261122_email_verification.sql in Supabase SQL Editor.
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS auth_version BIGINT NOT NULL DEFAULT 0;
-- Includes soft-deleted accounts: an email remains bound until deliberately released.
CREATE UNIQUE INDEX IF NOT EXISTS users_email_normalized_key
    ON public.users (LOWER(TRIM(email))) WHERE email IS NOT NULL AND TRIM(email) <> '';
