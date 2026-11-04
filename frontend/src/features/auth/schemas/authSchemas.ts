import { z } from "zod";

/**
 * Schema xác thực đăng ký tài khoản (UC1.1 - BR-01, BR-02, BR-03)
 */
export const registerSchema = z.object({
  email: z.string().trim().toLowerCase().email("Email không hợp lệ"),
  password: z
    .string()
    .min(8, "Mật khẩu tối thiểu 8 ký tự")
    .max(64, "Mật khẩu tối đa 64 ký tự")
    .regex(/[A-Za-z]/, "Phải chứa ít nhất 1 chữ cái")
    .regex(/[0-9]/, "Phải chứa ít nhất 1 chữ số"),
  fullName: z.string().trim().min(2, "Họ tên quá ngắn"),
  role: z.enum(["STUDENT", "PARENT", "TUTOR"]),
});

/**
 * Schema xác thực đăng nhập (UC1.2 - NFR-06)
 */
export const loginSchema = z.object({
  email: z.string().trim().toLowerCase().email("Email không hợp lệ"),
  password: z.string().min(1, "Vui lòng nhập mật khẩu"),
});

/**
 * Schema yêu cầu gửi mã OTP quên mật khẩu (UC1.3)
 */
export const forgotPasswordSchema = z.object({
  email: z.string().trim().toLowerCase().email("Email không hợp lệ"),
});

/**
 * Schema nhập mã OTP 6 số gửi qua email (xác thực đăng ký, quên mật khẩu)
 */
export const verifyOtpSchema = z.object({
  email: z.string().trim().toLowerCase().email("Email không hợp lệ"),
  code: z.string().trim().regex(/^[0-9]{6}$/, "Mã xác thực gồm đúng 6 chữ số"),
});

/**
 * Schema đặt mật khẩu mới bằng token cấp sau khi xác thực mã OTP (UC1.3 - BR-02)
 */
export const resetPasswordSchema = z
  .object({
    token: z.string().min(1, "Mã token không hợp lệ"),
    newPassword: z
      .string()
      .min(8, "Mật khẩu tối thiểu 8 ký tự")
      .max(64, "Mật khẩu tối đa 64 ký tự")
      .regex(/[A-Za-z]/, "Phải chứa ít nhất 1 chữ cái")
      .regex(/[0-9]/, "Phải chứa ít nhất 1 chữ số"),
    confirmPassword: z.string().min(1, "Vui lòng xác nhận mật khẩu mới"),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: "Mật khẩu xác nhận không khớp!",
    path: ["confirmPassword"],
  });

/**
 * Schema đổi mật khẩu tài khoản đang đăng nhập (UC1.4 - BR-02)
 */
export const changePasswordSchema = z.object({
  currentPassword: z.string().min(1, "Vui lòng nhập mật khẩu hiện tại"),
  newPassword: z
    .string()
    .min(8, "Mật khẩu tối thiểu 8 ký tự")
    .max(64, "Mật khẩu tối đa 64 ký tự")
    .regex(/[A-Za-z]/, "Phải chứa ít nhất 1 chữ cái")
    .regex(/[0-9]/, "Phải chứa ít nhất 1 chữ số"),
});

export interface PasswordCriteriaResult {
  hasMinLength: boolean;
  hasLetter: boolean;
  hasDigit: boolean;
  isValid: boolean;
}

/**
 * Hàm đánh giá tiêu chí mật khẩu trực quan theo thời gian thực (BR-02)
 */
export function evaluatePasswordCriteria(password: string): PasswordCriteriaResult {
  const hasMinLength = password.length >= 8 && password.length <= 64;
  const hasLetter = /[A-Za-z]/.test(password);
  const hasDigit = /[0-9]/.test(password);
  return {
    hasMinLength,
    hasLetter,
    hasDigit,
    isValid: hasMinLength && hasLetter && hasDigit,
  };
}

export type RegisterInput = z.infer<typeof registerSchema>;
export type LoginInput = z.infer<typeof loginSchema>;
export type ForgotPasswordInput = z.infer<typeof forgotPasswordSchema>;
export type ResetPasswordInput = z.infer<typeof resetPasswordSchema>;
export type VerifyOtpInput = z.infer<typeof verifyOtpSchema>;
export type ChangePasswordInput = z.infer<typeof changePasswordSchema>;
