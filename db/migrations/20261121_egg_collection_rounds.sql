-- Run once in Supabase SQL Editor before using the egg collection screen.
BEGIN;
ALTER TABLE public.daily_egg_logs
    ADD COLUMN IF NOT EXISTS collection_round VARCHAR(20) NOT NULL DEFAULT 'DAILY';
-- DAILY preserves existing daily records. New entries use MORNING / AFTERNOON / EVENING.
CREATE UNIQUE INDEX IF NOT EXISTS uq_daily_egg_flock_date_round_active
    ON public.daily_egg_logs (flock_id, record_date, collection_round)
    WHERE deleted_at IS NULL;
-- Replace the old one-record-per-day rule from the original database schema.
DROP INDEX IF EXISTS public.uq_daily_egg_flock_date_active;
COMMIT;
