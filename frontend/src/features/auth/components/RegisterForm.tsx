"use client";

import React from "react";
import Link from "next/link";
import { Lock, Mail, User, Loader2, CheckCircle2, XCircle } from "lucide-react";
import { AuthCardHeader } from "./AuthCardHeader";
import { RoleSelector } from "./RoleSelector";
import { SocialAuthButtons } from "./SocialAuthButtons";
import { useRegisterForm } from "../hooks/useRegisterForm";

export function RegisterForm() {
  const {
    role,
    fullName,
    email,
    password,
    confirmPassword,
    setRole,
    setFullName,
    setEmail,
    setPassword,
    setConfirmPassword,
    passwordCriteria,
    fieldErrors,
    generalError,
    isSubmitting,
    handleSubmit,
  } = useRegisterForm();

  return (
    <div className="w-full max-w-lg bg-surface rounded-3xl border border-line/90 shadow-xl shadow-ink/10 p-6 sm:p-8 space-y-5">
      <AuthCardHeader
        title="Đăng ký tài khoản"
        subtitle="Chọn vai trò và bắt đầu kết nối học tập cá nhân hóa"
      />

      <RoleSelector selectedRole={role} onChange={setRole} />

      {generalError && (
        <div className="p-3.5 bg-danger-soft border border-danger-line text-danger-strong text-xs rounded-2xl animate-in fade-in">
          {generalError}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label htmlFor="fullName" className="block text-xs font-medium text-ink-2 mb-1">
            Họ và tên
          </label>
          <div className="relative">
            <User className="size-4 text-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              id="fullName"
              name="fullName"
              type="text"
              required
              autoComplete="name"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              placeholder="Nguyễn Văn A"
              className="w-full pl-10 pr-4 py-2.5 text-xs bg-paper border border-line rounded-2xl focus:bg-surface focus:outline-hidden focus:border-action focus:ring-2 focus:ring-action/20 transition-all text-ink"
            />
          </div>
          {fieldErrors.fullName && (
            <p className="text-xs text-danger-strong font-medium mt-1 pl-1">
              {fieldErrors.fullName}
            </p>
          )}
        </div>

        <div>
          <label htmlFor="email" className="block text-xs font-medium text-ink-2 mb-1">
            Email tài khoản (Dùng để đăng nhập & nhận thông báo)
          </label>
          <div className="relative">
            <Mail className="size-4 text-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              id="email"
              name="email"
              type="email"
              required
              autoComplete="username"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="name@example.com"
              className="w-full pl-10 pr-4 py-2.5 text-xs bg-paper border border-line rounded-2xl focus:bg-surface focus:outline-hidden focus:border-action focus:ring-2 focus:ring-action/20 transition-all text-ink"
            />
          </div>
          {fieldErrors.email && (
            <p className="text-xs text-danger-strong font-medium mt-1 pl-1">
              {fieldErrors.email}
            </p>
          )}
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label htmlFor="password" className="block text-xs font-medium text-ink-2 mb-1">
              Mật khẩu
            </label>
            <div className="relative">
              <Lock className="size-4 text-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                id="password"
                name="password"
                type="password"
                required
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Ít nhất 8 ký tự"
                className="w-full pl-10 pr-4 py-2.5 text-xs bg-paper border border-line rounded-2xl focus:bg-surface focus:outline-hidden focus:border-action focus:ring-2 focus:ring-action/20 transition-all text-ink"
              />
            </div>
            {fieldErrors.password && (
              <p className="text-xs text-danger-strong font-medium mt-1 pl-1">
                {fieldErrors.password}
              </p>
            )}
          </div>

          <div>
            <label htmlFor="confirmPassword" className="block text-xs font-medium text-ink-2 mb-1">
              Xác nhận mật khẩu
            </label>
            <div className="relative">
              <Lock className="size-4 text-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                id="confirmPassword"
                name="confirmPassword"
                type="password"
                required
                autoComplete="new-password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder="Nhập lại mật khẩu"
                className="w-full pl-10 pr-4 py-2.5 text-xs bg-paper border border-line rounded-2xl focus:bg-surface focus:outline-hidden focus:border-action focus:ring-2 focus:ring-action/20 transition-all text-ink"
              />
            </div>
            {fieldErrors.confirmPassword && (
              <p className="text-xs text-danger-strong font-medium mt-1 pl-1">
                {fieldErrors.confirmPassword}
              </p>
            )}
          </div>
        </div>

        {/* Tiêu chí mật khẩu trực quan BR-02 */}
        <div className="p-3 bg-paper rounded-2xl border border-line/80 space-y-1.5 text-xs">
          <span className="text-xs font-medium text-muted block">
            Yêu cầu mật khẩu:
          </span>
          <div className="flex items-center gap-2">
            {passwordCriteria.hasMinLength ? (
              <CheckCircle2 className="size-3.5 text-success-strong shrink-0" />
            ) : (
              <XCircle className="size-3.5 text-muted shrink-0" />
            )}
            <span className={passwordCriteria.hasMinLength ? "text-success-strong font-medium" : "text-muted"}>
              Tối thiểu 8 ký tự
            </span>
          </div>
          <div className="flex items-center gap-2">
            {passwordCriteria.hasLetter ? (
              <CheckCircle2 className="size-3.5 text-success-strong shrink-0" />
            ) : (
              <XCircle className="size-3.5 text-muted shrink-0" />
            )}
            <span className={passwordCriteria.hasLetter ? "text-success-strong font-medium" : "text-muted"}>
              Ít nhất 1 chữ cái (a-z, A-Z)
            </span>
          </div>
          <div className="flex items-center gap-2">
            {passwordCriteria.hasDigit ? (
              <CheckCircle2 className="size-3.5 text-success-strong shrink-0" />
            ) : (
              <XCircle className="size-3.5 text-muted shrink-0" />
            )}
            <span className={passwordCriteria.hasDigit ? "text-success-strong font-medium" : "text-muted"}>
              Ít nhất 1 chữ số (0-9)
            </span>
          </div>
        </div>

        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full py-3 bg-action hover:bg-action-deep active:scale-[0.99] text-on-action font-medium text-xs rounded-2xl shadow-md shadow-ink/10 transition-all flex items-center justify-center gap-2 cursor-pointer disabled:opacity-50 mt-2"
        >
          {isSubmitting && <Loader2 className="size-4 animate-spin" />}
          <span>{isSubmitting ? "Đang tạo tài khoản..." : "Tạo tài khoản ngay"}</span>
        </button>
      </form>

      <SocialAuthButtons />

      <p className="text-center text-xs text-muted pt-1">
        Đã có tài khoản EduFit?{" "}
        <Link href="/login" className="text-action-strong font-medium hover:underline">
          Đăng nhập
        </Link>
      </p>
    </div>
  );
}
