"use client";

import { useCallback, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { authApi } from "../api/authApi";
import { verifyOtpSchema } from "../schemas/authSchemas";
import { resolvePostLoginRedirect } from "../utils/authRedirect";
import { useResendCountdown } from "./useResendCountdown";

/** Đường dẫn màn hình xác thực email sau khi đăng ký. */
export function buildVerifyEmailPath(email: string, resendAvailableAt?: string): string {
  const params = new URLSearchParams({ email });
  if (resendAvailableAt) params.set("resendAt", resendAvailableAt);
  return `/verify-email?${params.toString()}`;
}

function messageOf(err: unknown, fallback: string): string {
  return err instanceof Error && err.message ? err.message : fallback;
}

/**
 * Headless hook màn hình "Xác thực email": nhập mã OTP 6 số, gửi lại mã theo đếm ngược của server.
 * Xác thực đúng thì backend tạo tài khoản + phiên đăng nhập, hook chuyển sang onboarding theo vai trò.
 */
export function useVerifyEmailForm(email: string, initialResendAt: string | null) {
  const router = useRouter();
  const { verifyRegistration } = useAuth();

  const [code, setCode] = useState("");
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isResending, setIsResending] = useState(false);
  const [resendAt, setResendAt] = useState<string | null>(initialResendAt);
  const resendSecondsLeft = useResendCountdown(resendAt);

  const handleCodeChange = useCallback((value: string) => {
    setCode(value.replace(/\D/g, "").slice(0, 6));
    setFieldError(null);
    setGeneralError(null);
  }, []);

  const handleSubmit = useCallback(
    async (e?: React.FormEvent) => {
      if (e) e.preventDefault();
      setFieldError(null);
      setGeneralError(null);
      setNotice(null);

      const parsed = verifyOtpSchema.safeParse({ email, code });
      if (!parsed.success) {
        setFieldError(parsed.error.issues[0]?.message ?? "Mã xác thực không hợp lệ");
        return false;
      }

      setIsSubmitting(true);
      try {
        const user = await verifyRegistration(parsed.data);
        await resolvePostLoginRedirect({ user, router });
        return true;
      } catch (err) {
        setGeneralError(messageOf(err, "Xác thực email thất bại. Vui lòng thử lại."));
        return false;
      } finally {
        setIsSubmitting(false);
      }
    },
    [email, code, verifyRegistration, router]
  );

  const handleResend = useCallback(async () => {
    setGeneralError(null);
    setNotice(null);
    setIsResending(true);
    try {
      const res = await authApi.resendRegistrationCode(email);
      if (res.data) setResendAt(res.data.resendAvailableAt);
      setCode("");
      setNotice("Đã gửi mã mới. Vui lòng kiểm tra hộp thư (kể cả mục Spam).");
    } catch (err) {
      setGeneralError(messageOf(err, "Không thể gửi lại mã lúc này. Vui lòng thử lại sau."));
    } finally {
      setIsResending(false);
    }
  }, [email]);

  return {
    code,
    setCode: handleCodeChange,
    fieldError,
    generalError,
    notice,
    isSubmitting,
    isResending,
    resendSecondsLeft,
    handleSubmit,
    handleResend,
  };
}
