ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS email_verification_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS email_verification_expires_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS users_email_verification_hash_key
    ON public.users (email_verification_hash);
