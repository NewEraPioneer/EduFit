-- Display fixture only: this verifies audit log retrieval, not audit event emission.
BEGIN;
DO $$ BEGIN
  IF current_database() <> 'edufit_e2e_e' THEN
    RAISE EXCEPTION 'This fixture requires the isolated edufit_e2e_e database';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM account WHERE email = 'admin.demo@edufit.vn') THEN
    RAISE EXCEPTION 'Start backend with SPRING_PROFILES_ACTIVE=dev first';
  END IF;
END $$;
INSERT INTO audit_log (audit_id, actor_user_id, action, target_type, target_id, reason)
SELECT 'e2480000-0000-4000-8000-000000000001', a.user_id, 'E2E_ADMIN_DISPLAY', 'ACCOUNT', s.user_id,
       'E2E #48: bản ghi mẫu để xác minh giao diện nhật ký kiểm toán.'
FROM account a CROSS JOIN account s
WHERE a.email = 'admin.demo@edufit.vn' AND s.email = 'student.demo@edufit.vn'
ON CONFLICT (audit_id) DO UPDATE SET reason = EXCLUDED.reason, created_at = now();
COMMIT;
