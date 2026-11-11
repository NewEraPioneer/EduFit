"use client";

import React, { Suspense } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { VerifyEmailForm } from "@/features/auth";

function VerifyEmailContent() {
  const searchParams = useSearchParams();
  const email = searchParams.get("email")?.trim() ?? "";
  const resendAt = searchParams.get("resendAt");

  if (!email) {
    return (
      <div className="w-full max-w-md bg-surface rounded-3xl border border-line/90 shadow-xl p-8 text-center space-y-3">
        <h1 className="font-heading text-lg font-bold text-danger-strong">Không có yêu cầu đăng ký</h1>
        <p className="text-xs text-muted leading-relaxed">
          Đăng ký tài khoản trước. Mã xác thực sẽ được gửi tới email bạn đăng ký.
        </p>
        <Link href="/register" className="inline-block text-xs font-medium text-action-strong hover:underline">
          Đi tới trang Đăng ký
        </Link>
      </div>
    );
  }

  return <VerifyEmailForm email={email} resendAvailableAt={resendAt} />;
}

/**
 * Trang Xác thực email sau khi đăng ký (App Router Page).
 */
export default function VerifyEmailPage() {
  return (
    <Suspense
      fallback={
        <div className="w-full max-w-md bg-surface rounded-3xl border border-line p-8 text-center text-xs text-muted">
          Đang tải...
        </div>
      }
    >
      <VerifyEmailContent />
    </Suspense>
  );
}
