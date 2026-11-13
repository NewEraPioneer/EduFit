"use client";

import React from "react";
import Link from "next/link";
import { Lock, Mail, Loader2 } from "lucide-react";
import { AuthCardHeader } from "./AuthCardHeader";
import { QuickLoginPills } from "./QuickLoginPills";
import { SocialAuthButtons } from "./SocialAuthButtons";
import { useLoginForm } from "../hooks/useLoginForm";

export function LoginForm() {
  const {
    email,
    password,
    setEmail,
    setPassword,
    fieldErrors,
    generalError,
    isSubmitting,
    fillQuickLogin,
    handleSubmit,
  } = useLoginForm();

  return (
    <div className="w-full max-w-md bg-surface rounded-3xl border border-line/90 shadow-xl shadow-ink/10 p-6 sm:p-8 space-y-6">
      <AuthCardHeader
        title="Chào mừng trở lại!"
        subtitle="Đăng nhập để tiếp tục học cùng EduFit"
      />

      {/* Demo 1-Click login pills */}
      <QuickLoginPills onSelect={fillQuickLogin} />

      {generalError && (
        <div className="p-3.5 bg-danger-soft border border-danger-line text-danger-strong text-xs rounded-2xl animate-in fade-in">
          {generalError}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label htmlFor="loginEmail" className="block text-xs font-medium text-ink-2 mb-1.5">
            Email tài khoản
          </label>
          <div className="relative">
            <Mail className="size-4 text-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              id="loginEmail"
              name="email"
              type="email"
              required
              autoComplete="username"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="example@edufit.vn"
              className="w-full pl-10 pr-4 py-2.5 text-xs bg-paper border border-line rounded-2xl focus:bg-surface focus:outline-hidden focus:border-action focus:ring-2 focus:ring-action/20 transition-all text-ink placeholder:text-muted"
            />
          </div>
          {fieldErrors.email && (
            <p className="text-xs text-danger-strong font-medium mt-1 pl-1">
              {fieldErrors.email}
            </p>
          )}
        </div>

        <div>
          <div className="flex justify-between items-center mb-1.5">
            <label htmlFor="loginPassword" className="text-xs font-medium text-ink-2">
              Mật khẩu
            </label>
            <Link href="/forgot-password" className="text-xs text-action-strong hover:underline font-medium">
              Quên mật khẩu?
            </Link>
          </div>
          <div className="relative">
            <Lock className="size-4 text-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              id="loginPassword"
              name="password"
              type="password"
              required
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              className="w-full pl-10 pr-4 py-2.5 text-xs bg-paper border border-line rounded-2xl focus:bg-surface focus:outline-hidden focus:border-action focus:ring-2 focus:ring-action/20 transition-all text-ink placeholder:text-muted"
            />
          </div>
          {fieldErrors.password && (
            <p className="text-xs text-danger-strong font-medium mt-1 pl-1">
              {fieldErrors.password}
            </p>
          )}
        </div>

        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full py-3 bg-action hover:bg-action-deep active:scale-[0.99] text-on-action font-medium text-xs rounded-2xl shadow-md shadow-ink/10 transition-all flex items-center justify-center gap-2 cursor-pointer disabled:opacity-50"
        >
          {isSubmitting && <Loader2 className="size-4 animate-spin" />}
          <span>{isSubmitting ? "Đang đăng nhập..." : "Đăng nhập ngay"}</span>
        </button>
      </form>

      <SocialAuthButtons />

      <p className="text-center text-xs text-muted pt-1">
        Chưa có tài khoản EduFit?{" "}
        <Link href="/register" className="text-action-strong font-medium hover:underline">
          Đăng ký miễn phí
        </Link>
      </p>
    </div>
  );
}
