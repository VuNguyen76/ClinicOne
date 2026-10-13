CREATE INDEX IF NOT EXISTS idx_access_audit_events_occurred_id_desc
    ON public.access_audit_events (occurred_at DESC, id DESC);
