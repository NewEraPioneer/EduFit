"use client";

import React from "react";
import Link from "next/link";
import { ArrowLeft, CheckCircle2, Loader2, MailCheck } from "lucide-react";
import { AuthCardHeader } from "./AuthCardHeader";
import { OtpCodeInput } from "./OtpCodeInput";
import { ResendCodeButton } from "./ResendCodeButton";
import { useVerifyEmailForm } from "../hooks/useVerifyEmailForm";

interface VerifyEmailFormProps {
  email: string;
  resendAvailableAt: string | null;
}

/**
 * Màn hình "Xác thực email" sau khi đăng ký: nhập mã OTP gửi tới email.
 * Xác thực đúng thì tài khoản được tạo và chuyển thẳng sang onboarding.
 */
export function VerifyEmailForm({ email, resendAvailableAt }: VerifyEmailFormProps) {
  const {
    code,
    setCode,
    fieldError,
    generalError,
    notice,
    isSubmitting,
    isResending,
    resendSecondsLeft,
    handleSubmit,
    handleResend,
  } = useVerifyEmailForm(email, resendAvailableAt);

  return (
    <div className="w-full max-w-md bg-surface rounded-3xl border border-line/90 shadow-xl shadow-ink/10 p-6 sm:p-8 space-y-6">
      <AuthCardHeader title="Xác thực email" subtitle="Bước cuối để hoàn tất đăng ký tài khoản EduFit" />

      <div className="flex items-start gap-3 p-3.5 bg-action-soft border border-action-line rounded-2xl text-xs text-ink-2">
        <MailCheck className="size-5 text-action-strong shrink-0" aria-hidden="true" />
        <p className="leading-relaxed">
          Chúng tôi đã gửi mã 6 số tới <strong id="verifyEmailAddress" className="text-ink break-all">{email}</strong>.
          Mã có hiệu lực trong 10 phút. Tài khoản chỉ được tạo sau khi bạn nhập đúng mã.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        {generalError && (
          <div role="alert" className="p-3.5 bg-danger-soft border border-danger-line text-danger-strong text-xs rounded-2xl">
            {generalError}
          </div>
        )}
        {notice && (
          <div role="status" className="flex items-center gap-2 p-3.5 bg-success-soft border border-success-line text-success-strong text-xs rounded-2xl">
            <CheckCircle2 className="size-4 shrink-0" aria-hidden="true" />
            <span>{notice}</span>
          </div>
        )}

        <OtpCodeInput id="otpCode" value={code} onChange={setCode} error={fieldError} disabled={isSubmitting} />

        <button
          id="verifyEmailSubmit"
          type="submit"
          disabled={isSubmitting || code.length !== 6}
          className="w-full py-3 bg-action hover:bg-action-deep active:scale-[0.99] text-on-action font-medium text-xs rounded-2xl shadow-md shadow-ink/10 transition-all flex items-center justify-center gap-2 cursor-pointer disabled:opacity-50"
        >
          {isSubmitting && <Loader2 className="size-4 animate-spin" />}
          <span>{isSubmitting ? "Đang xác thực..." : "Xác thực và tiếp tục"}</span>
        </button>

        <div className="flex items-center justify-between pt-1">
          <Link
            href="/register"
            className="inline-flex items-center gap-1.5 text-xs text-muted hover:text-ink font-medium"
          >
            <ArrowLeft className="size-3.5" />
            <span>Sửa thông tin đăng ký</span>
          </Link>
          <ResendCodeButton secondsLeft={resendSecondsLeft} isSending={isResending} onResend={handleResend} />
        </div>
      </form>
    </div>
  );
}
