-- Keep the queue status constraint in sync with QueueTicketStatus.
-- LEFT_BEFORE_EXAM is persisted when reception closes a patient who left
-- before the doctor started the examination.
ALTER TABLE public.queue_tickets
    DROP CONSTRAINT IF EXISTS queue_tickets_status_check;

ALTER TABLE public.queue_tickets
    ADD CONSTRAINT queue_tickets_status_check CHECK (status IN (
        'WAITING',
        'CALLED',
        'IN_SERVICE',
        'SKIPPED',
        'LEFT_BEFORE_EXAM',
        'COMPLETED'
    ));
