"use client";

import React, { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { LottiePlayer } from "@/shared/motion/LottiePlayer";
import { DashboardShell } from "./DashboardShell";
import type { UserRole } from "./navConfig";

/** Khung chung của bốn vai trò: kiểm tra phiên đăng nhập, chuyển đúng vai trò, rồi hiển thị shell. */
export function RoleLayout({ role, children }: { role: UserRole; children: React.ReactNode }) {
  const router = useRouter();
  const { user, isLoading, logout } = useAuth();
  const matches = user?.role?.toUpperCase() === role;

  useEffect(() => {
    if (isLoading) return;
    if (!user) router.replace("/login");
    else if (!matches) router.replace(`/${user.role?.toLowerCase() || "student"}/dashboard`);
  }, [user, isLoading, matches, router]);

  if (isLoading || !user || !matches) {
    return (
      <div className="flex min-h-dvh flex-col items-center justify-center gap-2 bg-paper font-sans" role="status">
        <LottiePlayer src="/lottie/seed-loading.json" className="size-24" />
        <span className="text-sm text-muted">Đang tải...</span>
      </div>
    );
  }

  return (
    <DashboardShell
      user={user}
      role={role}
      onLogout={async () => {
        await logout();
        router.push("/login");
      }}
    >
      {children}
    </DashboardShell>
  );
}
