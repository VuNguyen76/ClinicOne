-- The sign endpoint stores its idempotency key on the examination session.
-- Keep this migration safe for databases that already have the column.
ALTER TABLE public.examination_sessions
    ADD COLUMN IF NOT EXISTS sign_request_key character varying(80);
