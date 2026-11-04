export const E2E_ROUTES = {
  HOME: '/',
  LOGIN: '/login',
  REGISTER: '/register',
  FORGOT_PASSWORD: '/forgot-password',
  VERIFY_EMAIL: '/verify-email',
  ONBOARDING: '/onboarding/',
  ADMIN_DASHBOARD: '/admin/dashboard',
  ADMIN_QUEUE: '/admin/verification-queue',
  ADMIN_CATALOGS: '/admin/catalogs',
  ADMIN_LEARNERS: '/admin/learners',
  ADMIN_AUDIT_LOGS: '/admin/audit-logs',
  TUTOR_DASHBOARD: '/tutor/dashboard',
  TUTOR_VERIFICATION: '/tutor/verification',
  TUTOR_PROFILE: '/tutor/profile',
  TUTOR_REQUESTS: '/tutor/requests',
  TUTOR_CLASSES: '/tutor/classes',
  TUTOR_SCHEDULE: '/tutor/schedule',
  TUTOR_EARNINGS: '/tutor/earnings',
  TUTOR_REVIEWS: '/tutor/reviews',
  STUDENT_DASHBOARD: '/student/dashboard',
  STUDENT_PROFILE: '/student/profile',
  STUDENT_FAMILY: '/student/family',
  STUDENT_CLASSES: '/student/classes',
  STUDENT_SCHEDULE: '/student/schedule',
  STUDENT_PROGRESS: '/student/progress',
  STUDENT_TUTORS: '/student/tutors',
  PARENT_DASHBOARD: '/parent/dashboard',
  PARENT_CHILDREN: '/parent/children',
  PARENT_PROGRESS: '/parent/progress',
  PARENT_BILLING: '/parent/billing',
  DISCOVERY: '/tutors',
} as const;

export const E2E_TIMEOUTS = {
  SHORT: 2000,
  MEDIUM: 5000,
  LONG: 15000,
  /** Thời gian tối đa chờ email OTP tới Mailpit. */
  OTP_MAIL: 15000,
} as const;

export const E2E_API_ROUTES = {
  CSRF: '/api/v1/auth/csrf',
  ME: '/api/v1/auth/me',
  REGISTER: '/api/v1/auth/register',
  REGISTER_VERIFY: '/api/v1/auth/register/verify',
  LOGIN: '/api/v1/auth/login',
} as const;

/** Dấu hiệu nhận diện tiêu đề email OTP (khớp OtpEmailTemplates ở backend). */
export const E2E_OTP_MAIL_SUBJECT = {
  REGISTRATION: 'Mã xác thực đăng ký',
  PASSWORD_RESET: 'Mã đặt lại mật khẩu',
} as const;

/** Sinh email duy nhất cho mỗi lần chạy để test không phụ thuộc dữ liệu cũ trong CSDL/Mailpit. */
export function generateUniqueTestEmail(prefix: string): string {
  const unique = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`;
  return `${prefix}-${unique}@example.test`;
}

export const E2E_TEST_DATA = {
  DEFAULT_SUBJECT: 'Toán',
  DEFAULT_GRADE: 'Lớp 9',
  STUDENT_EMAIL: 'student.demo@edufit.vn',
  TUTOR_EMAIL: 'tutor.demo@edufit.vn',
  PARENT_EMAIL: 'parent.demo@edufit.vn',
  ADMIN_EMAIL: 'admin.demo@edufit.vn',
} as const;
