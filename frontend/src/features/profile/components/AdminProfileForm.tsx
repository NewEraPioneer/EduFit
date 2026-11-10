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
          setFullName(parsed.fullName || user?.fullName || "");
          setDepartment(
            parsed.department || ""
          );
        } catch {
          setFullName(user?.fullName || "");
          setDepartment("");
        }
      } else {
        setFullName(user?.fullName || "");
        setDepartment("");
      }
    } finally {
      setLoading(false);
    }
  }, [user?.fullName]);

  const handleSaveInfo = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!fullName.trim()) {
      showToast("Nhập họ và tên hiển thị của quản trị viên.", "error");
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
      <div className="p-12 flex flex-col items-center justify-center gap-3 bg-surface rounded-3xl border border-line">
        <Loader2 className="size-6 text-brand-strong animate-spin" />
        <span className="text-xs text-muted font-medium">
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
        className="bg-surface p-6 sm:p-8 rounded-3xl border border-line/90 shadow-xs space-y-6"
      >
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-5 border-b border-line">
          <div>
            <div className="inline-flex items-center gap-1.5 px-3 py-1 bg-brand-soft text-brand-strong rounded-full text-xs font-medium mb-2">
              <ShieldAlert className="size-3.5 text-brand-strong" />
              <span>Hồ Sơ Quản Trị Viên (System Admin)</span>
            </div>
            <h3 className="font-heading text-xl font-bold text-ink">
              Phân quyền và trách nhiệm điều hành
            </h3>
            <p className="text-xs text-muted mt-0.5">
              Tài khoản nắm quyền cao nhất trong phê duyệt hồ sơ gia sư và xử lý khiếu nại
            </p>
          </div>

          <button
            type="submit"
            disabled={savingInfo}
            className="inline-flex items-center justify-center gap-2 px-5 py-2.5 bg-brand hover:bg-brand-deep active:scale-[0.99] text-white font-medium text-xs rounded-xl shadow-xs transition-all disabled:opacity-50 shrink-0 cursor-pointer"
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
            <label className="block text-xs font-medium text-ink-2 mb-1.5">
              Họ và tên hiển thị <span className="text-danger-strong">*</span>
            </label>
            <div className="relative">
              <User className="size-4 text-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                required
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                placeholder="VD: Quản Trị Viên EduFit"
                className="w-full pl-10 pr-3.5 py-2.5 text-xs bg-paper border border-line rounded-xl focus:bg-surface focus:outline-hidden focus:border-brand focus:ring-2 focus:ring-brand/20 text-ink font-medium"
              />
            </div>
          </div>

          {/* Email quản trị (read-only) */}
          <div>
            <label className="block text-xs font-medium text-ink-2 mb-1.5">
              Email quản trị chính
            </label>
            <div className="relative">
              <Mail className="size-4 text-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="email"
                disabled
                value={user?.email ?? ""}
                className="w-full pl-10 pr-3.5 py-2.5 text-xs bg-sunken border border-line rounded-xl text-muted font-medium cursor-not-allowed"
              />
            </div>
          </div>

          {/* Phòng ban / Bộ phận */}
          <div className="md:col-span-2">
            <label className="block text-xs font-medium text-ink-2 mb-1.5">
              Bộ phận / Phòng ban phụ trách
            </label>
            <div className="relative">
              <Building className="size-4 text-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                value={department}
                onChange={(e) => setDepartment(e.target.value)}
                placeholder="VD: Ban kiểm duyệt hồ sơ gia sư và quản trị nền tảng"
                className="w-full pl-10 pr-3.5 py-2.5 text-xs bg-paper border border-line rounded-xl focus:bg-surface focus:outline-hidden focus:border-brand focus:ring-2 focus:ring-brand/20 text-ink font-medium"
              />
            </div>
          </div>
        </div>

        {/* Thẻ quyền hạn hệ thống */}
        <div className="p-4 bg-brand-soft/60 rounded-2xl border border-brand-line flex items-start gap-3">
          <ShieldCheck className="size-5 text-brand-strong shrink-0 mt-0.5" />
          <div className="text-xs">
            <span className="font-medium text-brand-strong block mb-0.5">
              Đặc quyền Quản trị viên (Super Admin Role - RBAC)
            </span>
            <span className="text-brand-strong leading-relaxed block">
              Tài khoản này có quyền duyệt hồ sơ xác minh KYC gia sư, quản lý trạng thái tài khoản, giải quyết sự cố lớp học và xem nhật ký kiểm toán.
            </span>
          </div>
        </div>
      </form>

      {/* 2. Đổi Mật Khẩu Bảo Mật */}
      <form
        onSubmit={handleChangePassword}
        className="bg-surface p-6 sm:p-8 rounded-3xl border border-line/90 shadow-xs space-y-6"
      >
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-5 border-b border-line">
          <div>
            <div className="inline-flex items-center gap-1.5 px-3 py-1 bg-action-soft text-action-strong rounded-full text-xs font-medium mb-2">
              <KeyRound className="size-3.5 text-action-strong" />
              <span>Bảo Mật Tài Khoản (Security Settings)</span>
            </div>
            <h3 className="font-heading text-xl font-bold text-ink">
              Đổi mật khẩu đăng nhập
            </h3>
            <p className="text-xs text-muted mt-0.5">
              Tuân thủ chuẩn mã hóa Argon2 (NFR-01) bảo vệ chống tấn công Brute-force
            </p>
          </div>

          <button
            type="submit"
            disabled={changingPassword || !currentPassword || !newPassword}
            className="inline-flex items-center justify-center gap-2 px-5 py-2.5 bg-night hover:bg-night active:scale-[0.99] text-white font-medium text-xs rounded-xl shadow-xs transition-all disabled:opacity-50 shrink-0 cursor-pointer"
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
            <label className="block text-xs font-medium text-ink-2 mb-1.5">
              Mật khẩu hiện tại <span className="text-danger-strong">*</span>
            </label>
            <input
              type="password"
              required
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              placeholder="••••••••"
              className="w-full px-3.5 py-2.5 text-xs bg-paper border border-line rounded-xl focus:bg-surface focus:outline-hidden focus:border-line-strong focus:ring-2 focus:ring-line-strong/10 text-ink font-medium"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-ink-2 mb-1.5">
              Mật khẩu mới <span className="text-danger-strong">*</span>
            </label>
            <input
              type="password"
              required
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              placeholder="Tối thiểu 8 ký tự"
              className="w-full px-3.5 py-2.5 text-xs bg-paper border border-line rounded-xl focus:bg-surface focus:outline-hidden focus:border-line-strong focus:ring-2 focus:ring-line-strong/10 text-ink font-medium"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-ink-2 mb-1.5">
              Xác nhận mật khẩu mới <span className="text-danger-strong">*</span>
            </label>
            <input
              type="password"
              required
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              placeholder="Nhập lại mật khẩu mới"
              className="w-full px-3.5 py-2.5 text-xs bg-paper border border-line rounded-xl focus:bg-surface focus:outline-hidden focus:border-line-strong focus:ring-2 focus:ring-line-strong/10 text-ink font-medium"
            />
          </div>
        </div>
      </form>
    </div>
  );
}
