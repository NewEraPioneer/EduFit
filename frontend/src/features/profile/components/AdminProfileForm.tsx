"use client";

import React, { useState, useEffect } from "react";
import { useAuth } from "@/context/AuthContext";
import { useToast } from "@/shared/ui/Toast";
import { authApi } from "@/features/auth/api/authApi";
import { ApiError } from "@/lib/api";
import {
  ShieldAlert,
  Save,
  Loader2,
  Lock,
  User,
  Mail,
  ShieldCheck,
  Building,
  KeyRound,
} from "lucide-react";

export function AdminProfileForm() {
  const { user, refreshUser, updateUser } = useAuth();
  const { showToast } = useToast();

  const [fullName, setFullName] = useState("");
  const [department, setDepartment] = useState("");
  const [savingInfo, setSavingInfo] = useState(false);
  const [loading, setLoading] = useState(true);

  // Form Đổi mật khẩu
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [changingPassword, setChangingPassword] = useState(false);

  // Nạp dữ liệu hồ sơ quản trị viên từ cache / server khi mount
  useEffect(() => {
    try {
      setLoading(true);
      const savedProfileStr = localStorage.getItem("edufit_admin_profile");
      if (savedProfileStr) {
        try {
          const parsed = JSON.parse(savedProfileStr);
          setFullName(parsed.fullName || user?.fullName || "Quản Trị Viên EduFit");
          setDepartment(
            parsed.department ||
              "Ban Kiểm Duyệt & Vận Hành Hệ Thống (Operations)"
          );
        } catch {
          setFullName(user?.fullName || "Quản Trị Viên EduFit");
          setDepartment("Ban Kiểm Duyệt & Vận Hành Hệ Thống (Operations)");
        }
      } else {
        setFullName(user?.fullName || "Quản Trị Viên EduFit");
        setDepartment("Ban Kiểm Duyệt & Vận Hành Hệ Thống (Operations)");
      }
    } finally {
      setLoading(false);
    }
  }, [user?.fullName]);

  const handleSaveInfo = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!fullName.trim()) {
      showToast("Vui lòng nhập họ và tên hiển thị của Quản trị viên!", "error");
      return;
    }

    setSavingInfo(true);

    try {
      // 1. Lưu cấu hình hồ sơ quản trị viên vào storage bền vững
      const profileData = {
        fullName: fullName.trim(),
        department: department.trim(),
        updatedAt: new Date().toISOString(),
      };
      localStorage.setItem("edufit_admin_profile", JSON.stringify(profileData));

      // 2. Cập nhật state AuthContext và đồng bộ thông tin phiên
      if (user && updateUser) {
        updateUser({
          ...user,
          fullName: fullName.trim(),
        });
      }

      await refreshUser?.();
      showToast("Cập nhật thông tin quản trị viên thành công!", "success");
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Có lỗi xảy ra khi lưu thông tin";
      showToast(message, "error");
    } finally {
      setSavingInfo(false);
    }
  };

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();

    if (newPassword.length < 8) {
      showToast("Mật khẩu mới phải có tối thiểu 8 ký tự!", "error");
      return;
    }

    if (newPassword !== confirmPassword) {
      showToast("Mật khẩu xác nhận không trùng khớp!", "error");
      return;
    }

    setChangingPassword(true);
    try {
      const res = await authApi.changePassword({
        currentPassword,
        newPassword,
      });

      if (res.success) {
        showToast("Đổi mật khẩu tài khoản thành công!", "success");
        setCurrentPassword("");
        setNewPassword("");
        setConfirmPassword("");
      } else {
        showToast(
          res.message || "Không thể đổi mật khẩu, vui lòng kiểm tra lại mật khẩu cũ",
          "error"
        );
      }
    } catch (err: unknown) {
      const message =
        err instanceof ApiError
          ? err.message
          : err instanceof Error
          ? err.message
          : "Không thể đổi mật khẩu, vui lòng kiểm tra lại mật khẩu cũ";
      showToast(message, "error");
    } finally {
      setChangingPassword(false);
    }
  };

  if (loading) {
    return (
      <div className="p-12 flex flex-col items-center justify-center gap-3 bg-white rounded-3xl border border-stone-200">
        <Loader2 className="size-6 text-purple-600 animate-spin" />
        <span className="text-xs text-neutral-500 font-medium">
          Đang nạp dữ liệu hồ sơ quản trị viên...
        </span>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* 1. Thông Tin Quản Trị Viên */}
      <form
        onSubmit={handleSaveInfo}
        className="bg-white p-6 sm:p-8 rounded-3xl border border-stone-200/90 shadow-xs space-y-6"
      >
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-5 border-b border-stone-100">
          <div>
            <div className="inline-flex items-center gap-1.5 px-3 py-1 bg-purple-50 text-purple-900 rounded-full text-xs font-bold mb-2">
              <ShieldAlert className="size-3.5 text-purple-600" />
              <span>Hồ Sơ Quản Trị Viên (System Admin)</span>
            </div>
            <h3 className="font-heading text-xl font-bold text-neutral-900">
              Thông tin phân quyền & Trách nhiệm điều hành
            </h3>
            <p className="text-xs text-neutral-500 mt-0.5">
              Tài khoản nắm quyền cao nhất trong phê duyệt hồ sơ gia sư và xử lý khiếu nại
            </p>
          </div>

          <button
            type="submit"
            disabled={savingInfo}
            className="inline-flex items-center justify-center gap-2 px-5 py-2.5 bg-purple-600 hover:bg-purple-700 active:scale-[0.99] text-white font-bold text-xs rounded-xl shadow-xs transition-all disabled:opacity-50 shrink-0 cursor-pointer"
          >
            {savingInfo ? (
              <Loader2 className="size-4 animate-spin" />
            ) : (
              <Save className="size-4" />
            )}
            <span>{savingInfo ? "Đang lưu..." : "Lưu thay đổi"}</span>
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
          {/* Tên Quản trị viên */}
          <div>
            <label className="block text-xs font-bold text-neutral-700 mb-1.5">
              Họ và tên hiển thị <span className="text-rose-500">*</span>
            </label>
            <div className="relative">
              <User className="size-4 text-neutral-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                required
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                placeholder="VD: Quản Trị Viên EduFit"
                className="w-full pl-10 pr-3.5 py-2.5 text-xs bg-stone-50 border border-stone-200 rounded-xl focus:bg-white focus:outline-hidden focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 text-neutral-900 font-medium"
              />
            </div>
          </div>

          {/* Email quản trị (read-only) */}
          <div>
            <label className="block text-xs font-bold text-neutral-700 mb-1.5">
              Email quản trị chính
            </label>
            <div className="relative">
              <Mail className="size-4 text-neutral-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="email"
                disabled
                value={user?.email ?? ""}
                className="w-full pl-10 pr-3.5 py-2.5 text-xs bg-stone-100 border border-stone-200 rounded-xl text-neutral-500 font-medium cursor-not-allowed"
              />
            </div>
          </div>

          {/* Phòng ban / Bộ phận */}
          <div className="md:col-span-2">
            <label className="block text-xs font-bold text-neutral-700 mb-1.5">
              Bộ phận / Phòng ban phụ trách
            </label>
            <div className="relative">
              <Building className="size-4 text-neutral-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                value={department}
                onChange={(e) => setDepartment(e.target.value)}
                placeholder="VD: Ban Kiểm Duyệt Hồ Sơ Gia Sư & Quản Trị Nền Tảng"
                className="w-full pl-10 pr-3.5 py-2.5 text-xs bg-stone-50 border border-stone-200 rounded-xl focus:bg-white focus:outline-hidden focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 text-neutral-900 font-medium"
              />
            </div>
          </div>
        </div>

        {/* Thẻ quyền hạn hệ thống */}
        <div className="p-4 bg-purple-50/60 rounded-2xl border border-purple-100 flex items-start gap-3">
          <ShieldCheck className="size-5 text-purple-600 shrink-0 mt-0.5" />
          <div className="text-xs">
            <span className="font-bold text-purple-900 block mb-0.5">
              Đặc quyền Quản trị viên (Super Admin Role - RBAC)
            </span>
            <span className="text-purple-700 leading-relaxed block">
              Tài khoản này được cấp quyền truy cập toàn diện: Duyệt hồ sơ xác minh KYC gia sư (UC1.8 / UC1.9), quản lý trạng thái tài khoản, can thiệp giải quyết sự cố lớp học, và xem nhật ký kiểm toán hệ thống.
            </span>
          </div>
        </div>
      </form>

      {/* 2. Đổi Mật Khẩu Bảo Mật */}
      <form
        onSubmit={handleChangePassword}
        className="bg-white p-6 sm:p-8 rounded-3xl border border-stone-200/90 shadow-xs space-y-6"
      >
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-5 border-b border-stone-100">
          <div>
            <div className="inline-flex items-center gap-1.5 px-3 py-1 bg-amber-50 text-amber-900 rounded-full text-xs font-bold mb-2">
              <KeyRound className="size-3.5 text-amber-600" />
              <span>Bảo Mật Tài Khoản (Security Settings)</span>
            </div>
            <h3 className="font-heading text-xl font-bold text-neutral-900">
              Đổi mật khẩu đăng nhập
            </h3>
            <p className="text-xs text-neutral-500 mt-0.5">
              Tuân thủ chuẩn mã hóa Argon2 (NFR-01) bảo vệ chống tấn công Brute-force
            </p>
          </div>

          <button
            type="submit"
            disabled={changingPassword || !currentPassword || !newPassword}
            className="inline-flex items-center justify-center gap-2 px-5 py-2.5 bg-neutral-900 hover:bg-neutral-800 active:scale-[0.99] text-white font-bold text-xs rounded-xl shadow-xs transition-all disabled:opacity-50 shrink-0 cursor-pointer"
          >
            {changingPassword ? (
              <Loader2 className="size-4 animate-spin" />
            ) : (
              <Lock className="size-4" />
            )}
            <span>{changingPassword ? "Đang xử lý..." : "Cập nhật mật khẩu"}</span>
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
          <div>
            <label className="block text-xs font-bold text-neutral-700 mb-1.5">
              Mật khẩu hiện tại <span className="text-rose-500">*</span>
            </label>
            <input
              type="password"
              required
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              placeholder="••••••••"
              className="w-full px-3.5 py-2.5 text-xs bg-stone-50 border border-stone-200 rounded-xl focus:bg-white focus:outline-hidden focus:border-neutral-900 focus:ring-2 focus:ring-neutral-900/10 text-neutral-900 font-medium"
            />
          </div>

          <div>
            <label className="block text-xs font-bold text-neutral-700 mb-1.5">
              Mật khẩu mới <span className="text-rose-500">*</span>
            </label>
            <input
              type="password"
              required
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              placeholder="Tối thiểu 8 ký tự"
              className="w-full px-3.5 py-2.5 text-xs bg-stone-50 border border-stone-200 rounded-xl focus:bg-white focus:outline-hidden focus:border-neutral-900 focus:ring-2 focus:ring-neutral-900/10 text-neutral-900 font-medium"
            />
          </div>

          <div>
            <label className="block text-xs font-bold text-neutral-700 mb-1.5">
              Xác nhận mật khẩu mới <span className="text-rose-500">*</span>
            </label>
            <input
              type="password"
              required
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              placeholder="Nhập lại mật khẩu mới"
              className="w-full px-3.5 py-2.5 text-xs bg-stone-50 border border-stone-200 rounded-xl focus:bg-white focus:outline-hidden focus:border-neutral-900 focus:ring-2 focus:ring-neutral-900/10 text-neutral-900 font-medium"
            />
          </div>
        </div>
      </form>
    </div>
  );
}
