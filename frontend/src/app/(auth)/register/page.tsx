import React, { Suspense } from "react";
import { RegisterForm } from "@/features/auth";

export const metadata = {
  title: "Đăng ký - EduFit",
  description: "Đăng ký tài khoản kết nối gia sư EduFit",
};

/**
 * Trang Đăng ký Tài khoản (App Router Page)
 * Tách biệt theo kiến trúc Feature-based, mã nguồn siêu gọn nhẹ
 */
export default function RegisterPage() {
  return (
    <Suspense
      fallback={
        <div className="w-full max-w-md p-8 bg-white rounded-3xl border border-stone-200 shadow-md text-center text-sm text-neutral-500">
          Đang tải biểu mẫu đăng ký...
        </div>
      }
    >
      <RegisterForm />
    </Suspense>
  );
}
