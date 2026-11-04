"use client";

import { useCallback, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { authApi } from "../api/authApi";
import {
  evaluatePasswordCriteria,
  forgotPasswordSchema,
  resetPasswordSchema,
  verifyOtpSchema,
} from "../schemas/authSchemas";
import { resolvePostLoginRedirect } from "../utils/authRedirect";
import { useResendCountdown } from "./useResendCountdown";

export type PasswordResetStep = "email" | "code" | "password";

interface PasswordFieldErrors {
  newPassword?: string;
  confirmPassword?: string;
}

function messageOf(err: unknown, fallback: string): string {
  return err instanceof Error && err.message ? err.message : fallback;
}

/**
 * Headless hook luồng Quên mật khẩu 3 bước (UC1.3):
 * 1. Nhập email → backend gửi mã OTP (phản hồi luôn trung tính, không lộ email có tồn tại hay không).
 * 2. Nhập mã OTP → nhận token đặt mật khẩu mới (chỉ giữ trong bộ nhớ, không đưa lên URL).
 * 3. Đặt mật khẩu mới → backend thu hồi phiên cũ, đăng nhập luôn; hook điều hướng theo vai trò.
 */
export function usePasswordResetFlow() {
  const router = useRouter();
  const { resetPassword } = useAuth();

  const [step, setStep] = useState<PasswordResetStep>("email");
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [resetToken, setResetToken] = useState<string | null>(null);
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const [fieldError, setFieldError] = useState<string | null>(null);
  const [passwordErrors, setPasswordErrors] = useState<PasswordFieldErrors>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [resendAt, setResendAt] = useState<string | null>(null);
  const resendSecondsLeft = useResendCountdown(resendAt);

  const passwordCriteria = useMemo(() => evaluatePasswordCriteria(newPassword), [newPassword]);

  const clearMessages = useCallback(() => {
    setFieldError(null);
    setGeneralError(null);
    setNotice(null);
  }, []);

  const requestCode = useCallback(async () => {
    const parsed = forgotPasswordSchema.safeParse({ email: email.trim() });
    if (!parsed.success) {
      setFieldError(parsed.error.issues[0]?.message ?? "Email không hợp lệ");
      return false;
    }
    setIsSubmitting(true);
    try {
      const res = await authApi.forgotPassword(parsed.data);
      setEmail(parsed.data.email);
      setResendAt(res.data?.resendAvailableAt ?? null);
      setStep("code");
      return true;
    } catch (err) {
      setGeneralError(messageOf(err, "Không thể gửi mã xác thực lúc này. Vui lòng thử lại sau."));
      return false;
    } finally {
      setIsSubmitting(false);
    }
  }, [email]);

  const handleEmailSubmit = useCallback(
    async (e?: React.FormEvent) => {
      if (e) e.preventDefault();
      clearMessages();
      return requestCode();
    },
    [clearMessages, requestCode]
  );

  const handleResend = useCallback(async () => {
    clearMessages();
    setCode("");
    if (await requestCode()) {
      setNotice("Nếu email đã đăng ký, mã mới đã được gửi. Vui lòng kiểm tra hộp thư (kể cả mục Spam).");
    }
  }, [clearMessages, requestCode]);

  const handleCodeSubmit = useCallback(
    async (e?: React.FormEvent) => {
      if (e) e.preventDefault();
      clearMessages();
      const parsed = verifyOtpSchema.safeParse({ email, code });
      if (!parsed.success) {
        setFieldError(parsed.error.issues[0]?.message ?? "Mã xác thực không hợp lệ");
        return false;
      }
      setIsSubmitting(true);
      try {
        const res = await authApi.verifyPasswordResetCode(parsed.data);
        if (!res.data?.resetToken) throw new Error(res.message || "Xác thực mã thất bại");
        setResetToken(res.data.resetToken);
        setStep("password");
        return true;
      } catch (err) {
        setGeneralError(messageOf(err, "Mã xác thực không đúng hoặc đã hết hạn."));
        return false;
      } finally {
        setIsSubmitting(false);
      }
    },
    [clearMessages, email, code]
  );

  const handlePasswordSubmit = useCallback(
    async (e?: React.FormEvent) => {
      if (e) e.preventDefault();
      clearMessages();
      setPasswordErrors({});
      const parsed = resetPasswordSchema.safeParse({ token: resetToken ?? "", newPassword, confirmPassword });
      if (!parsed.success) {
        const errors: PasswordFieldErrors = {};
        for (const issue of parsed.error.issues) {
          const field = issue.path[0];
          if ((field === "newPassword" || field === "confirmPassword") && !errors[field]) {
            errors[field] = issue.message;
          } else if (field === "token") {
            setGeneralError("Phiên đặt lại mật khẩu không hợp lệ. Vui lòng yêu cầu mã mới.");
          }
        }
        setPasswordErrors(errors);
        return false;
      }
      setIsSubmitting(true);
      try {
        const user = await resetPassword({ token: parsed.data.token, newPassword: parsed.data.newPassword });
        await resolvePostLoginRedirect({ user, router });
        return true;
      } catch (err) {
        setGeneralError(messageOf(err, "Không thể đặt lại mật khẩu. Vui lòng yêu cầu mã mới."));
        return false;
      } finally {
        setIsSubmitting(false);
      }
    },
    [clearMessages, resetToken, newPassword, confirmPassword, resetPassword, router]
  );

  const restart = useCallback(() => {
    clearMessages();
    setCode("");
    setResetToken(null);
    setNewPassword("");
    setConfirmPassword("");
    setStep("email");
  }, [clearMessages]);

  const handleEmailChange = useCallback((value: string) => {
    setEmail(value);
    setFieldError(null);
    setGeneralError(null);
  }, []);

  const handleCodeChange = useCallback((value: string) => {
    setCode(value.replace(/\D/g, "").slice(0, 6));
    setFieldError(null);
    setGeneralError(null);
  }, []);

  const handleNewPasswordChange = useCallback((value: string) => {
    setNewPassword(value);
    setPasswordErrors((prev) => ({ ...prev, newPassword: undefined }));
    setGeneralError(null);
  }, []);

  const handleConfirmPasswordChange = useCallback((value: string) => {
    setConfirmPassword(value);
    setPasswordErrors((prev) => ({ ...prev, confirmPassword: undefined }));
    setGeneralError(null);
  }, []);

  return {
    step,
    email,
    setEmail: handleEmailChange,
    code,
    setCode: handleCodeChange,
    newPassword,
    setNewPassword: handleNewPasswordChange,
    confirmPassword,
    setConfirmPassword: handleConfirmPasswordChange,
    passwordCriteria,
    fieldError,
    passwordErrors,
    generalError,
    notice,
    isSubmitting,
    resendSecondsLeft,
    handleEmailSubmit,
    handleCodeSubmit,
    handlePasswordSubmit,
    handleResend,
    restart,
  };
}
