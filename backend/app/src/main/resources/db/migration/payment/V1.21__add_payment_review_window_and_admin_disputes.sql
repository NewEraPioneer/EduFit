-- New pending charges get a full, notified review window. Do not rewrite terminal history.
ALTER TABLE payment_charge ADD COLUMN review_started_at timestamptz;
ALTER TABLE payment_charge ADD COLUMN dispute_category varchar(30) NOT NULL DEFAULT 'OTHER';
ALTER TABLE payment_charge ADD COLUMN dispute_evidence varchar(4000);
ALTER TABLE payment_charge ADD COLUMN dispute_reporter_id uuid REFERENCES account(user_id);
ALTER TABLE payment_charge ADD COLUMN resolution_note varchar(2000);
ALTER TABLE payment_charge ADD COLUMN resolved_at timestamptz;
ALTER TABLE payment_charge DROP CONSTRAINT payment_charge_status_check;
ALTER TABLE payment_charge ADD CONSTRAINT payment_charge_status_check CHECK
  (status IN ('FUNDED','AWAITING_CONFIRMATION','DISPUTED','AWAITING_ADMIN','ADMIN_REVIEW','SETTLED','REFUNDED','EXPIRED'));
ALTER TABLE payment_charge ADD CONSTRAINT payment_dispute_category_check CHECK
  (dispute_category IN ('DURATION','NO_SHOW','TECHNICAL','QUALITY','OTHER'));
CREATE TABLE payment_case_message (
  id uuid PRIMARY KEY,
  session_id uuid NOT NULL REFERENCES payment_charge(session_id),
  author_id uuid NOT NULL REFERENCES account(user_id),
  side varchar(10) NOT NULL CHECK (side IN ('STUDENT','PAYER','TUTOR')),
  message varchar(4000) NOT NULL CHECK (length(trim(message)) > 0),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX payment_case_message_session ON payment_case_message(session_id,created_at);
