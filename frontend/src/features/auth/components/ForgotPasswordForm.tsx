"use client";

import React from "react";
import Link from "next/link";
import { ArrowLeft, CheckCircle2, Loader2, Lock, Mail, XCircle } from "lucide-react";
import { AuthCardHeader } from "./AuthCardHeader";
import { OtpCodeInput } from "./OtpCodeInput";
import { ResendCodeButton } from "./ResendCodeButton";
import { usePasswordResetFlow, type PasswordResetStep } from "../hooks/usePasswordReset";

const SUBTITLES: Record<PasswordResetStep, string> = {
  email: "Nhập email tài khoản để nhận mã xác thực",
  code: "Nhập mã 6 số đã gửi tới email của bạn",
  password: "Đặt mật khẩu mới an toàn cho tài khoản",
};

const INPUT_CLASS =
  "w-full pl-10 pr-4 py-2.5 text-xs bg-paper border border-line rounded-2xl focus:bg-surface focus:outline-hidden focus:border-action focus:ring-2 focus:ring-action/20 transition-all text-ink placeholder:text-muted";
const SUBMIT_CLASS =
  "w-full py-3 bg-action hover:bg-action-deep active:scale-[0.99] text-on-action font-medium text-xs rounded-2xl shadow-md shadow-ink/10 transition-all flex items-center justify-center gap-2 cursor-pointer disabled:opacity-50";

function Criterion({ ok, label }: { ok: boolean; label: string }) {
  return (
    <div className="flex items-center gap-2">
      {ok ? (
        <CheckCircle2 className="size-3.5 text-success-strong shrink-0" />
      ) : (
        <XCircle className="size-3.5 text-muted shrink-0" />
      )}
      <span className={ok ? "text-success-strong font-medium" : "text-muted"}>{label}</span>
    </div>
  );
}

/**
 * Quên mật khẩu 3 bước trên cùng một màn hình: email → mã OTP → mật khẩu mới (UC1.3).
 */
export function ForgotPasswordForm() {
  const flow = usePasswordResetFlow();
  const { step } = flow;

  return (
    <div className="w-full max-w-md bg-surface rounded-3xl border border-line/90 shadow-xl shadow-ink/10 p-6 sm:p-8 space-y-6">
      <AuthCardHeader title="Quên mật khẩu?" subtitle={SUBTITLES[step]} />

      <ol className="flex items-center justify-center gap-2 text-xs font-medium" aria-label="Các bước">
        {(["email", "code", "password"] as const).map((item, index) => (
          <li
            key={item}
            aria-current={item === step ? "step" : undefined}
            className={item === step ? "text-action-strong" : "text-muted"}
          >
            {index + 1}. {item === "email" ? "Email" : item === "code" ? "Mã xác thực" : "Mật khẩu mới"}
          </li>
        ))}
      </ol>

      {flow.generalError && (
        <div role="alert" className="p-3.5 bg-danger-soft border border-danger-line text-danger-strong text-xs rounded-2xl">
          {flow.generalError}
        </div>
      )}
      {flow.notice && (
        <div role="status" className="p-3.5 bg-success-soft border border-success-line text-success-strong text-xs rounded-2xl">
          {flow.notice}
        </div>
      )}

      {step === "email" && (
        <form onSubmit={flow.handleEmailSubmit} className="space-y-4" noValidate>
          <div>
            <label htmlFor="forgotEmail" className="block text-xs font-medium text-ink-2 mb-1.5">
              Địa chỉ Email đã đăng ký
            </label>
            <div className="relative">
              <Mail className="size-4 text-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                id="forgotEmail"
                type="email"
                required
                autoComplete="email"
                value={flow.email}
                onChange={(e) => flow.setEmail(e.target.value)}
                placeholder="example@edufit.vn"
                className={INPUT_CLASS}
              />
            </div>
            {flow.fieldError && (
              <p className="text-xs text-danger-strong font-medium mt-1 pl-1">{flow.fieldError}</p>
            )}
          </div>
          <button id="forgotEmailSubmit" type="submit" disabled={flow.isSubmitting} className={SUBMIT_CLASS}>
            {flow.isSubmitting && <Loader2 className="size-4 animate-spin" />}
            <span>{flow.isSubmitting ? "Đang gửi mã..." : "Gửi mã xác thực"}</span>
          </button>
        </form>
      )}

      {step === "code" && (
        <form onSubmit={flow.handleCodeSubmit} className="space-y-4" noValidate>
          <p className="text-xs text-ink-2 leading-relaxed">
            Nếu <strong className="text-ink break-all">{flow.email}</strong> đã đăng ký tài khoản EduFit,
            mã 6 số đã được gửi tới hộp thư này. Mã có hiệu lực trong 10 phút.
          </p>
          <OtpCodeInput
            id="resetCode"
            value={flow.code}
            onChange={flow.setCode}
            error={flow.fieldError}
            disabled={flow.isSubmitting}
          />
          <button
            id="resetCodeSubmit"
            type="submit"
            disabled={flow.isSubmitting || flow.code.length !== 6}
            className={SUBMIT_CLASS}
          >
            {flow.isSubmitting && <Loader2 className="size-4 animate-spin" />}
            <span>{flow.isSubmitting ? "Đang xác thực..." : "Xác thực mã"}</span>
          </button>
          <div className="flex items-center justify-between">
            <button
              type="button"
              onClick={flow.restart}
              className="inline-flex items-center gap-1.5 text-xs text-muted hover:text-ink font-medium cursor-pointer"
            >
              <ArrowLeft className="size-3.5" />
              <span>Đổi email</span>
            </button>
            <ResendCodeButton
              secondsLeft={flow.resendSecondsLeft}
              isSending={flow.isSubmitting}
              onResend={flow.handleResend}
            />
          </div>
        </form>
      )}

      {step === "password" && (
        <form onSubmit={flow.handlePasswordSubmit} className="space-y-4" noValidate>
          <div>
            <label htmlFor="newPassword" className="block text-xs font-medium text-ink-2 mb-1.5">
              Mật khẩu mới
            </label>
            <div className="relative">
              <Lock className="size-4 text-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                id="newPassword"
                type="password"
                required
                autoFocus
                autoComplete="new-password"
                value={flow.newPassword}
                onChange={(e) => flow.setNewPassword(e.target.value)}
                placeholder="Tối thiểu 8 ký tự"
                className={INPUT_CLASS}
              />
            </div>
            {flow.passwordErrors.newPassword && (
              <p className="text-xs text-danger-strong font-medium mt-1 pl-1">{flow.passwordErrors.newPassword}</p>
            )}
          </div>

          <div>
            <label htmlFor="confirmNewPassword" className="block text-xs font-medium text-ink-2 mb-1.5">
              Xác nhận mật khẩu mới
            </label>
            <div className="relative">
              <Lock className="size-4 text-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                id="confirmNewPassword"
                type="password"
                required
                autoComplete="new-password"
                value={flow.confirmPassword}
                onChange={(e) => flow.setConfirmPassword(e.target.value)}
                placeholder="Nhập lại mật khẩu"
                className={INPUT_CLASS}
              />
            </div>
            {flow.passwordErrors.confirmPassword && (
              <p className="text-xs text-danger-strong font-medium mt-1 pl-1">
                {flow.passwordErrors.confirmPassword}
              </p>
            )}
          </div>

          <div className="p-3 bg-paper rounded-2xl border border-line/80 space-y-1.5 text-xs">
            <span className="text-xs font-medium text-muted block">
              Yêu cầu mật khẩu:
            </span>
            <Criterion ok={flow.passwordCriteria.hasMinLength} label="Tối thiểu 8 ký tự" />
            <Criterion ok={flow.passwordCriteria.hasLetter} label="Ít nhất 1 chữ cái (a-z, A-Z)" />
            <Criterion ok={flow.passwordCriteria.hasDigit} label="Ít nhất 1 chữ số (0-9)" />
          </div>

          <button
            id="resetPasswordSubmit"
            type="submit"
            disabled={flow.isSubmitting || !flow.passwordCriteria.isValid}
            className={SUBMIT_CLASS}
          >
            {flow.isSubmitting && <Loader2 className="size-4 animate-spin" />}
            <span>{flow.isSubmitting ? "Đang cập nhật..." : "Lưu mật khẩu mới và đăng nhập"}</span>
          </button>
        </form>
      )}

      <div className="text-center">
        <Link
          href="/login"
          className="inline-flex items-center justify-center gap-1.5 text-xs text-muted hover:text-ink font-medium transition-colors"
        >
          <ArrowLeft className="size-3.5" />
          <span>Quay lại trang Đăng nhập</span>
        </Link>
      </div>
    </div>
  );
}
