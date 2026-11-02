-- Preserve V1.16 history; track asynchronous payout without releasing uncertain funds.
ALTER TABLE payment_withdrawal DROP CONSTRAINT payment_withdrawal_status_check;
ALTER TABLE payment_withdrawal ADD CONSTRAINT payment_withdrawal_status_check
  CHECK (status IN ('PENDING','PROCESSING','PAID','REJECTED','FAILED'));
ALTER TABLE payment_withdrawal ADD COLUMN bank_bin varchar(6);
ALTER TABLE payment_withdrawal ADD CONSTRAINT payment_withdrawal_bin_check
  CHECK (bank_bin IS NULL OR bank_bin ~ '^[0-9]{6}$');
ALTER TABLE payment_withdrawal ADD COLUMN provider_id varchar(150) UNIQUE;
ALTER TABLE payment_withdrawal ADD COLUMN provider_state varchar(40);
ALTER TABLE payment_withdrawal ADD COLUMN provider_message varchar(500);
ALTER TABLE payment_withdrawal ADD COLUMN payout_requested_at timestamptz;
ALTER TABLE payment_withdrawal ADD COLUMN reconcile_after timestamptz NOT NULL DEFAULT 'epoch';
DROP INDEX payment_one_pending_withdrawal;
CREATE UNIQUE INDEX payment_one_pending_withdrawal ON payment_withdrawal(user_id)
  WHERE status IN ('PENDING','PROCESSING');
ALTER TABLE payment_topup ADD COLUMN reconcile_after timestamptz NOT NULL DEFAULT 'epoch';
CREATE INDEX payment_withdrawal_reconcile ON payment_withdrawal(reconcile_after)
  WHERE status='PROCESSING';
CREATE INDEX payment_topup_reconcile ON payment_topup(reconcile_after)
  WHERE provider='PAYOS' AND status='PENDING';
