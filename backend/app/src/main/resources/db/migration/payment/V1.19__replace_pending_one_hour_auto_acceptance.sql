-- The 24-hour settlement policy must not reuse a learner consent inferred by
-- the old one-hour timer. Explicit confirmations and terminal history stay intact.
UPDATE payment_charge
SET learner_accepted = false, auto_accepted = false, updated_at = now()
WHERE auto_accepted = true AND status IN ('FUNDED', 'AWAITING_CONFIRMATION');
