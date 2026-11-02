"use client";

import React from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { LogIn, UserPlus, Compass, Home, LogOut, LayoutDashboard } from "lucide-react";
import { EduFitLogo } from "./EduFitLogo";
import { useAuth } from "@/context/AuthContext";

export function PublicNavbar() {
  const pathname = usePathname();
  const { user, logout } = useAuth();

  const getDashboardPath = () => {
    const role = user?.role?.toUpperCase();
    if (role === "STUDENT") return "/student/dashboard";
    if (role === "PARENT") return "/parent/dashboard";
    if (role === "TUTOR") return "/tutor/dashboard";
    if (role === "ADMIN") return "/admin/dashboard";
    return "/student/dashboard";
  };

  return (
    <header className="sticky top-0 z-40 bg-white/90 dark:bg-stone-900/90 backdrop-blur-md border-b border-stone-200/80 dark:border-stone-800 transition-colors">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between gap-4">
        {/* Left: Brand Logo & Links */}
        <div className="flex items-center gap-6 sm:gap-8">
          <EduFitLogo href="/" size="md" />

          <nav className="hidden md:flex items-center gap-5 text-xs font-semibold">
            <Link
              href="/"
              className={`inline-flex items-center gap-1.5 transition-colors ${
                pathname === "/"
                  ? "text-amber-600 font-bold"
                  : "text-neutral-600 dark:text-stone-300 hover:text-neutral-900 dark:hover:text-white"
              }`}
            >
              <Home className="size-3.5" />
              <span>Trang chủ</span>
            </Link>

            <Link
              href="/tutors"
              className={`inline-flex items-center gap-1.5 transition-colors ${
                pathname?.startsWith("/tutors")
                  ? "text-amber-600 font-bold"
                  : "text-neutral-600 dark:text-stone-300 hover:text-neutral-900 dark:hover:text-white"
              }`}
            >
              <Compass className="size-3.5" />
              <span>Danh bạ gia sư</span>
            </Link>
          </nav>
        </div>

        {/* Right: Auth State */}
        <div className="flex items-center gap-3">
          {user ? (
            <div className="flex items-center gap-2 sm:gap-3">
              <Link
                href={getDashboardPath()}
                className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-bold text-amber-800 dark:text-amber-300 bg-amber-50 dark:bg-amber-950/60 border border-amber-200 dark:border-amber-900/50 hover:bg-amber-100 transition-colors"
              >
                <LayoutDashboard className="size-3.5" />
                <span className="hidden sm:inline">Vào Dashboard</span>
                <span className="sm:hidden">Dashboard</span>
              </Link>

              <button
                type="button"
                onClick={() => logout()}
                title="Đăng xuất"
                className="p-2 rounded-xl text-neutral-400 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-950/40 transition-colors cursor-pointer"
              >
                <LogOut className="size-4" />
              </button>
            </div>
          ) : (
            <div className="flex items-center gap-2">
              <Link
                href={`/login${pathname ? `?returnUrl=${encodeURIComponent(pathname)}` : ""}`}
                className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-semibold text-neutral-700 dark:text-stone-200 bg-stone-100 dark:bg-stone-800 hover:bg-stone-200 dark:hover:bg-stone-700 transition-colors"
              >
                <LogIn className="size-3.5" />
                <span>Đăng nhập</span>
              </Link>

              <Link
                href="/register"
                className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl text-xs font-bold text-white bg-amber-600 hover:bg-amber-700 shadow-xs active:scale-95 transition-all"
              >
                <UserPlus className="size-3.5" />
                <span>Đăng ký</span>
              </Link>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
