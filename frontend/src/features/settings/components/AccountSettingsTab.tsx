"use client";

import React, { useState, useMemo } from "react";
import {
  KeyRound,
  ShieldCheck,
  Mail,
  User,
  Loader2,
  CheckCircle2,
  XCircle,
  Lock,
} from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { useToast } from "@/shared/ui/Toast";
import { authApi } from "@/features/auth/api/authApi";
import { evaluatePasswordCriteria } from "@/features/auth/schemas/authSchemas";
import { AvatarUploadCard } from "./AvatarUploadCard";

export function AccountSettingsTab() {
  const { user } = useAuth();
  const { showToast } = useToast();

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const criteria = useMemo(() => {
    return evaluatePasswordCriteria(newPassword);
  }, [newPassword]);

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);

    if (!currentPassword) {
      setErrorMsg("Nhập mật khẩu hiện tại.");
      return;
    }

    if (!criteria.isValid) {
      setErrorMsg("Mật khẩu mới chưa đáp ứng đủ tiêu chuẩn an toàn (tối thiểu 8 ký tự, bao gồm cả chữ và số)!");
      return;
    }

    if (newPassword !== confirmPassword) {
      setErrorMsg("Mật khẩu xác nhận không khớp với mật khẩu mới!");
      return;
    }

    if (currentPassword === newPassword) {
      setErrorMsg("Mật khẩu mới không được trùng với mật khẩu hiện tại!");
      return;
    }

    setSubmitting(true);
    try {
      const res = await authApi.changePassword({
        currentPassword,
        newPassword,
      });

      if (res.success) {
        showToast("Đổi mật khẩu thành công! Mật khẩu mới đã được cập nhật.", "success");
        setCurrentPassword("");
        setNewPassword("");
        setConfirmPassword("");
      } else {
        setErrorMsg(res.message || "Đổi mật khẩu thất bại. Vui lòng kiểm tra lại.");
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Đổi mật khẩu thất bại. Vui lòng thử lại.";
      setErrorMsg(msg);
      showToast(msg, "error");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="pb-4 border-b border-line">
        <h2 className="font-heading text-xl font-bold text-ink">
          Tài khoản và bảo mật
        </h2>
        <p className="text-xs text-muted mt-1">
          Quản lý thông tin đăng nhập, phiên bảo mật và cập nhật mật khẩu tài khoản EduFit của bạn.
        </p>
      </div>

      {/* 1. Thông tin tài khoản định danh */}
      <div className="bg-surface p-6 sm:p-7 rounded-3xl border border-line/90 shadow-xs space-y-4">
        <div className="flex items-center gap-2.5 text-xs font-medium text-ink">
          <ShieldCheck className="size-4 text-success-strong" />
          <span>Thông tin tài khoản hệ thống</span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-1">
          <div className="p-3.5 bg-paper rounded-2xl border border-line space-y-1">
            <div className="flex items-center gap-1.5 text-xs font-medium text-muted">
              <Mail className="size-3.5 text-muted" />
              <span>Email đăng nhập</span>
            </div>
            <p className="text-xs font-medium text-ink truncate">
              {user?.email || "Chưa xác định"}
            </p>
            <span className="inline-block text-xs text-success-strong bg-success-soft px-2 py-0.5 rounded-md font-medium">
              Đã xác thực danh tính
            </span>
          </div>

          <div className="p-3.5 bg-paper rounded-2xl border border-line space-y-1">
            <div className="flex items-center gap-1.5 text-xs font-medium text-muted">
              <User className="size-3.5 text-muted" />
              <span>Họ tên và vai trò</span>
            </div>
            <p className="text-xs font-medium text-ink truncate">
              {user?.fullName || "Người dùng"}
            </p>
            <span className="inline-block text-xs text-action-strong bg-action-soft px-2 py-0.5 rounded-md font-medium">
              Vai trò: {user?.role || "STUDENT"}
            </span>
          </div>
        </div>
      </div>

      {/* 2. Ảnh đại diện người dùng */}
      <AvatarUploadCard />

      {/* 3. Form Đổi Mật Khẩu */}
      <div className="bg-surface p-6 sm:p-7 rounded-3xl border border-line/90 shadow-xs space-y-5">
        <div className="flex items-center justify-between pb-3 border-b border-line">
          <div className="flex items-center gap-2.5 text-xs font-medium text-ink">
            <KeyRound className="size-4 text-action-strong" />
            <span>Đổi mật khẩu tài khoản</span>
          </div>

        </div>

        {errorMsg && (
          <div className="p-3.5 bg-danger-soft border border-danger-line text-danger-strong text-xs rounded-2xl animate-in fade-in">
            {errorMsg}
          </div>
        )}

        <form onSubmit={handleChangePassword} className="space-y-4 max-w-lg">
          <div>
            <label className="block text-xs font-medium text-ink-2 mb-1.5">
              Mật khẩu hiện tại
            </label>
            <div className="relative">
              <Lock className="size-4 text-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="password"
                required
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                placeholder="Nhập mật khẩu bạn đang dùng"
                className="w-full pl-10 pr-4 py-2.5 text-xs bg-paper border border-line rounded-2xl focus:bg-surface focus:outline-hidden focus:border-action focus:ring-2 focus:ring-action/20 transition-all text-ink"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-ink-2 mb-1.5">
              Mật khẩu mới
            </label>
            <div className="relative">
              <KeyRound className="size-4 text-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="password"
                required
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                placeholder="Tối thiểu 8 ký tự gồm chữ và số"
                className="w-full pl-10 pr-4 py-2.5 text-xs bg-paper border border-line rounded-2xl focus:bg-surface focus:outline-hidden focus:border-action focus:ring-2 focus:ring-action/20 transition-all text-ink"
              />
            </div>

            {/* Tiêu chí mật khẩu thời gian thực */}
            {newPassword.length > 0 && (
              <div className="mt-2.5 p-3 bg-paper rounded-xl border border-line space-y-1.5 text-xs">
                <div className="flex items-center gap-1.5">
                  {criteria.hasMinLength ? (
                    <CheckCircle2 className="size-3.5 text-success-strong" />
                  ) : (
                    <XCircle className="size-3.5 text-white/75" />
                  )}
                  <span className={criteria.hasMinLength ? "text-success-strong font-medium" : "text-muted"}>
                    Độ dài tối thiểu 8 ký tự
                  </span>
                </div>
                <div className="flex items-center gap-1.5">
                  {criteria.hasLetter ? (
                    <CheckCircle2 className="size-3.5 text-success-strong" />
                  ) : (
                    <XCircle className="size-3.5 text-white/75" />
                  )}
                  <span className={criteria.hasLetter ? "text-success-strong font-medium" : "text-muted"}>
                    Chứa ít nhất 1 chữ cái
                  </span>
                </div>
                <div className="flex items-center gap-1.5">
                  {criteria.hasDigit ? (
                    <CheckCircle2 className="size-3.5 text-success-strong" />
                  ) : (
                    <XCircle className="size-3.5 text-white/75" />
                  )}
                  <span className={criteria.hasDigit ? "text-success-strong font-medium" : "text-muted"}>
                    Chứa ít nhất 1 chữ số
                  </span>
                </div>
              </div>
            )}
          </div>

          <div>
            <label className="block text-xs font-medium text-ink-2 mb-1.5">
              Xác nhận mật khẩu mới
            </label>
            <div className="relative">
              <Lock className="size-4 text-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="password"
                required
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder="Nhập lại chính xác mật khẩu mới"
                className="w-full pl-10 pr-4 py-2.5 text-xs bg-paper border border-line rounded-2xl focus:bg-surface focus:outline-hidden focus:border-action focus:ring-2 focus:ring-action/20 transition-all text-ink"
              />
            </div>
          </div>

          <div className="pt-2">
            <button
              type="submit"
              disabled={submitting}
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-action hover:bg-action-deep active:scale-[0.99] text-on-action font-medium text-xs rounded-xl shadow-xs transition-all disabled:opacity-50 cursor-pointer"
            >
              {submitting ? (
                <Loader2 className="size-3.5 animate-spin" />
              ) : (
                <CheckCircle2 className="size-3.5" />
              )}
              <span>{submitting ? "Đang cập nhật..." : "Cập nhật mật khẩu mới"}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
