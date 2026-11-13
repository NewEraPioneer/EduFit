-- 1.35/1.36 are reserved by the separate defense seed branch. Applied migrations remain unchanged.
CREATE TABLE recurring_schedule_proposal (
  proposal_id uuid PRIMARY KEY,
  class_id uuid NOT NULL REFERENCES class(class_id),
  tutor_id uuid NOT NULL REFERENCES tutor_profile(tutor_id),
  student_id uuid NOT NULL REFERENCES student_profile(student_id),
  proposed_by uuid NOT NULL REFERENCES account(user_id),
  effective_from date NOT NULL,
  effective_to date NOT NULL,
  weekly_slots text[] NOT NULL CHECK (cardinality(weekly_slots)>0),
  mode varchar(10) NOT NULL CHECK (mode IN ('ONLINE','OFFLINE')),
  place_or_link varchar(255),
  message varchar(500),
  status varchar(20) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','REJECTED','EXPIRED','WITHDRAWN')),
  responded_by uuid REFERENCES account(user_id),
  responded_at timestamptz,
  response_reason text,
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL,
  CHECK (effective_to-effective_from BETWEEN 28 AND 56)
);
CREATE UNIQUE INDEX ux_recurring_pending_class ON recurring_schedule_proposal(class_id) WHERE status='PENDING';
ALTER TABLE class ADD COLUMN agreed_slots jsonb;
