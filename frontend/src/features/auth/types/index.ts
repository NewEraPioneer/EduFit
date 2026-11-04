export type UserRole = "STUDENT" | "PARENT" | "TUTOR" | "ADMIN";
export type AccountStatus = "ACTIVE" | "DEACTIVATED";

export interface AuthUser {
  id: string;
  email: string;
  fullName: string;
  role: UserRole;
  status: AccountStatus;
  avatarUrl?: string | null;
  redirectUrl?: string;
}

export interface OAuthLoginPayload {
  idToken: string;
  provider?: string;
}

export interface OAuthLoginResult {
  needsRoleSelection: boolean;
  onboardingToken?: string | null;
  user?: AuthUser | null;
  email?: string;
  fullName?: string;
  avatarUrl?: string;
}

export interface CompleteOAuthRegistrationPayload {
  onboardingToken: string;
  role: UserRole;
}

/** Mã OTP vừa gửi qua email: hạn mã và thời điểm được bấm "Gửi lại". */
export interface OtpChallenge {
  email: string;
  codeExpiresAt: string;
  resendAvailableAt: string;
}

/** Quyền đặt mật khẩu mới cấp sau khi nhập đúng mã OTP quên mật khẩu. */
export interface PasswordResetGrant {
  resetToken: string;
  expiresAt: string;
}

export type AuthFieldErrors<T> = Partial<Record<keyof T, string>>;

export type {
  RegisterInput,
  LoginInput,
  ForgotPasswordInput,
  ResetPasswordInput,
  VerifyOtpInput,
  ChangePasswordInput,
  PasswordCriteriaResult,
} from "../schemas/authSchemas";
