-- Stores the business result when a queue ticket is closed.
ALTER TABLE public.queue_tickets
    ADD COLUMN IF NOT EXISTS closure_outcome character varying(40);
