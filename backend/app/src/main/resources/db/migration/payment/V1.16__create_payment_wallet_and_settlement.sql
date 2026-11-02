-- Payment money mutations serialize through this row, including jobs on other instances.
CREATE TABLE payment_mutex (id boolean PRIMARY KEY CHECK (id));
INSERT INTO payment_mutex VALUES (true);
CREATE TABLE payment_wallet (
  user_id uuid PRIMARY KEY REFERENCES account(user_id),
  available bigint NOT NULL DEFAULT 0 CHECK (available >= 0),
  held bigint NOT NULL DEFAULT 0 CHECK (held >= 0)
);
CREATE TABLE payment_charge (
  session_id uuid PRIMARY KEY REFERENCES tutoring_session(session_id),
  class_id uuid NOT NULL REFERENCES class(class_id),
  student_id uuid NOT NULL REFERENCES student_profile(student_id),
  tutor_user_id uuid NOT NULL REFERENCES account(user_id),
  payer_user_id uuid NOT NULL REFERENCES account(user_id),
  amount bigint NOT NULL CHECK (amount > 0 AND amount <= 100000000),
  commission_bps integer NOT NULL CHECK (commission_bps BETWEEN 0 AND 10000),
  end_at timestamptz NOT NULL,
  status varchar(30) NOT NULL CHECK (status IN ('FUNDED','AWAITING_CONFIRMATION','DISPUTED','AWAITING_ADMIN','SETTLED','REFUNDED','EXPIRED')),
  learner_accepted boolean NOT NULL DEFAULT false,
  tutor_accepted boolean NOT NULL DEFAULT false,
  auto_accepted boolean NOT NULL DEFAULT false,
  dispute_reason varchar(2000), dispute_opened_at timestamptz,
  learner_report varchar(4000), tutor_report varchar(4000),
  learner_amount bigint, tutor_amount bigint,
  settled_amount bigint NOT NULL DEFAULT 0 CHECK (settled_amount >= 0 AND settled_amount <= amount),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (learner_amount IS NULL OR learner_amount BETWEEN 0 AND amount),
  CHECK (tutor_amount IS NULL OR tutor_amount BETWEEN 0 AND amount)
);
CREATE INDEX payment_charge_deadlines ON payment_charge(status, end_at);
CREATE INDEX payment_charge_payer ON payment_charge(payer_user_id);
CREATE INDEX payment_charge_tutor ON payment_charge(tutor_user_id);
CREATE TABLE payment_topup (
  id uuid PRIMARY KEY, order_code bigserial UNIQUE NOT NULL,
  user_id uuid NOT NULL REFERENCES account(user_id), amount bigint NOT NULL CHECK (amount BETWEEN 10000 AND 100000000),
  provider varchar(20) NOT NULL CHECK (provider IN ('MANUAL','PAYOS')),
  status varchar(20) NOT NULL CHECK (status IN ('PENDING','PAID','CANCELLED')),
  checkout_url text, provider_id varchar(150), transaction_reference varchar(200) UNIQUE,
  expires_at timestamptz NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE payment_withdrawal (
  id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES account(user_id),
  amount bigint NOT NULL CHECK (amount BETWEEN 10000 AND 100000000),
  status varchar(20) NOT NULL CHECK (status IN ('PENDING','PAID','REJECTED')),
  bank_name varchar(100) NOT NULL, bank_account varchar(50) NOT NULL, bank_holder varchar(150) NOT NULL,
  transaction_reference varchar(200) UNIQUE, admin_note varchar(2000), created_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX payment_one_pending_withdrawal ON payment_withdrawal(user_id) WHERE status='PENDING';
CREATE TABLE payment_entry (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid REFERENCES account(user_id),
  event_key varchar(250) NOT NULL UNIQUE, kind varchar(40) NOT NULL,
  available_delta bigint NOT NULL, held_delta bigint NOT NULL,
  reference_id uuid NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX payment_entry_owner ON payment_entry(user_id,created_at DESC);
CREATE TABLE payment_audit (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), actor_id uuid REFERENCES account(user_id),
  reference_id uuid NOT NULL, action varchar(60) NOT NULL, detail varchar(4000) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
