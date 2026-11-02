-- One current recipient per tutor; withdrawals retain their original bank snapshot.
CREATE TABLE payment_payout_account (
  user_id uuid PRIMARY KEY REFERENCES account(user_id),
  id uuid NOT NULL UNIQUE,
  bank_name varchar(100) NOT NULL CHECK (length(trim(bank_name)) > 0),
  bank_bin varchar(6) NOT NULL CHECK (bank_bin ~ '^[0-9]{6}$'),
  bank_account varchar(34) NOT NULL CHECK (bank_account ~ '^[0-9]{5,34}$'),
  bank_holder varchar(150) NOT NULL CHECK (length(trim(bank_holder)) > 0),
  updated_at timestamptz NOT NULL DEFAULT now()
);
