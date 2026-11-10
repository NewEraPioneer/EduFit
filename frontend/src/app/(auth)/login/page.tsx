import React, { Suspense } from "react";
import { LoginForm } from "@/features/auth";

export const metadata = {
  title: "Đăng nhập - EduFit",
  description: "Đăng nhập hệ thống kết nối gia sư EduFit",
};

/**
 * Trang Đăng nhập (App Router Page)
 * Tách biệt theo kiến trúc Feature-based, mã nguồn siêu gọn nhẹ
 */
export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <div className="w-full max-w-md p-8 bg-surface rounded-3xl border border-line shadow-md text-center text-sm text-muted">
          Đang tải biểu mẫu đăng nhập...
        </div>
      }
    >
      <LoginForm />
    </Suspense>
  );
}
