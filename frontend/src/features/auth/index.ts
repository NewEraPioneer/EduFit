// API Gateway
export { authApi, authApi as authService } from "./api/authApi";
export type {
  RegisterPayload,
  LoginPayload,
  ForgotPasswordPayload,
  ResetPasswordPayload,
  VerifyOtpPayload,
  ChangePasswordPayload,
} from "./api/authApi";

// Components
export { LoginForm } from "./components/LoginForm";
export { RegisterForm } from "./components/RegisterForm";
export { ForgotPasswordForm } from "./components/ForgotPasswordForm";
export { VerifyEmailForm } from "./components/VerifyEmailForm";
export { OtpCodeInput } from "./components/OtpCodeInput";
export { QuickLoginPills } from "./components/QuickLoginPills";
export { RoleSelector, type RoleType } from "./components/RoleSelector";
export { SocialAuthButtons } from "./components/SocialAuthButtons";
export { AuthCardHeader } from "./components/AuthCardHeader";

// Headless Hooks
export { useLoginForm } from "./hooks/useLoginForm";
export { useRegisterForm, type RegisterFormErrors } from "./hooks/useRegisterForm";
export { usePasswordResetFlow, type PasswordResetStep } from "./hooks/usePasswordReset";
export { useVerifyEmailForm, buildVerifyEmailPath } from "./hooks/useVerifyEmailForm";

// Schemas & Criteria Evaluator
export {
  loginSchema,
  registerSchema,
  forgotPasswordSchema,
  resetPasswordSchema,
  verifyOtpSchema,
  changePasswordSchema,
  evaluatePasswordCriteria,
} from "./schemas/authSchemas";

// Domain Types
export type {
  AuthUser,
  UserRole,
  AccountStatus,
  AuthFieldErrors,
  LoginInput,
  RegisterInput,
  ForgotPasswordInput,
  ResetPasswordInput,
  VerifyOtpInput,
  OtpChallenge,
  PasswordResetGrant,
  ChangePasswordInput,
  PasswordCriteriaResult,
} from "./types";
