"use client";

import React, { useState, useEffect, useCallback } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { EduFitLogo } from "@/shared/ui/EduFitLogo";
import { useAuth } from "@/context/AuthContext";
import { markOnboardingDone, sanitizeReturnUrl } from "@/features/auth/utils/authRedirect";
import { useToast } from "@/shared/ui/Toast";
import { connectionApi } from "@/features/connection/api/connectionApi";
import type { LinkInvitation, StudentLink } from "@/features/connection/types";
import {
  Users,
  CheckCircle2,
  Lightbulb,
  ArrowRight,
  Send,
  Loader2,
  Trash2,
  Mail,
  Clock,
  GraduationCap,
} from "lucide-react";

export function ParentOnboardingWizard() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { user, refreshUser } = useAuth();
  const { showToast } = useToast();

  const safeReturnUrl = sanitizeReturnUrl(searchParams.get("returnUrl"));

  const [inputEmail, setInputEmail] = useState<string>("");
  const [links, setLinks] = useState<StudentLink[]>([]);
  const [invitations, setInvitations] = useState<LinkInvitation[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [sending, setSending] = useState<boolean>(false);
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [busyId, setBusyId] = useState<string | null>(null);

  const loadConnections = useCallback(async () => {
    try {
      setLoading(true);
      const [confirmedLinks, allInvitations] = await Promise.all([
        connectionApi.getLinks().catch(() => []),
        connectionApi.getLinkInvitations().catch(() => []),
      ]);
      setLinks(confirmedLinks);
      setInvitations(
        allInvitations.filter(
          (item) => item.status === "PENDING" && item.inviterUserId === user?.id
        )
      );
    } catch (err: unknown) {
      console.error("Lỗi khi tải danh sách liên kết:", err);
    } finally {
      setLoading(false);
    }
  }, [user?.id]);

  useEffect(() => {
    void loadConnections();
  }, [loadConnections]);

  const handleSendInvitation = async (e: React.FormEvent) => {
    e.preventDefault();
    const targetEmail = inputEmail.trim().toLowerCase();
    if (!targetEmail) {
      showToast("Nhập email tài khoản học sinh của con.", "error");
      return;
    }

    if (!targetEmail.includes("@")) {
      showToast("Địa chỉ email không đúng định dạng!", "error");
      return;
    }

    setSending(true);
    try {
      await connectionApi.inviteParentStudent(targetEmail);
      showToast(`Đã gửi lời mời liên kết tới ${targetEmail} thành công!`, "success");
      setInputEmail("");
      await loadConnections();
    } catch (err: unknown) {
      const message =
        err instanceof Error
          ? err.message
          : "Không thể gửi lời mời liên kết. Vui lòng kiểm tra lại email tài khoản học sinh.";
      showToast(message, "error");
    } finally {
      setSending(false);
    }
  };

  const handleWithdrawInvitation = async (invitationId: string) => {
    setBusyId(invitationId);
    try {
      await connectionApi.withdrawLinkInvitation(invitationId);
      showToast("Đã rút lời mời liên kết thành công.", "success");
      await loadConnections();
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Có lỗi xảy ra khi rút lời mời liên kết.";
      showToast(message, "error");
    } finally {
      setBusyId(null);
    }
  };

  const handleComplete = async () => {
    setSubmitting(true);
    try {
      // Đánh dấu phụ huynh đã hoàn tất onboarding theo user.id
      markOnboardingDone("parent", user?.id);

      showToast("Thiết lập tài khoản phụ huynh thành công! Chào mừng bạn đến với EduFit.", "success");
      await refreshUser?.();
      router.push(safeReturnUrl || "/parent/dashboard");
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Có lỗi xảy ra khi hoàn tất thiết lập";
      showToast(message, "error");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-paper font-sans flex flex-col justify-between">
      {/* 1. Header Bar */}
      <header className="bg-surface border-b border-line sticky top-0 z-30 px-4 sm:px-8 py-3.5 flex items-center justify-between">
        <div className="flex items-center gap-6">
          <EduFitLogo href="/" />
          <div className="hidden sm:flex items-center gap-2 pl-6 border-l border-line text-xs text-muted">
            <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 bg-success-soft text-success-strong font-medium rounded-full">
              THIẾT LẬP NHANH
            </span>
            <span>Liên kết tài khoản của con</span>
          </div>
        </div>

        <div className="flex items-center gap-4">
          <Link
            href={safeReturnUrl || "/parent/dashboard"}
            onClick={() => markOnboardingDone("parent", user?.id)}
            className="text-xs text-muted hover:text-ink transition-colors"
          >
            Bỏ qua để vào sau
          </Link>
        </div>
      </header>

      {/* 2. Main Container */}
      <main className="flex-1 max-w-4xl w-full mx-auto p-4 sm:p-8 space-y-6">
        {/* Left/Top Tip Box */}
        <div className="bg-success-soft rounded-3xl p-5 sm:p-6 border border-success-line flex items-start gap-4">
          <div className="size-10 rounded-2xl bg-success-soft text-success-strong flex items-center justify-center shrink-0">
            <Lightbulb className="size-5" />
          </div>
          <div className="space-y-1">
            <h3 className="text-xs font-medium text-success-strong">
              Cách liên kết tài khoản con em
            </h3>
            <p className="text-xs text-success-strong leading-relaxed">
              Nhập chính xác địa chỉ email tài khoản học sinh của con em bạn. Hệ thống sẽ gửi lời mời trực tiếp đến tài khoản học sinh. Con bạn chỉ cần mở ứng dụng và xác nhận <strong>"Đồng ý liên kết"</strong> là hoàn tất!
            </p>
          </div>
        </div>

        {/* Main Linking Card */}
        <div className="bg-surface rounded-3xl p-6 sm:p-8 border border-line/90 shadow-xs space-y-6">
          <div className="space-y-1">
            <span className="text-xs font-medium text-success-strong">
              BƯỚC 1: GỬI LỜI MỜI LIÊN KẾT HỌC SINH
            </span>
            <h2 className="font-heading text-2xl font-bold text-ink">
              Kết nối với tài khoản học sinh của con
            </h2>
            <p className="text-xs text-muted">
              Liên kết giúp phụ huynh đồng bộ tiến độ học tập, theo dõi thời khóa biểu và nhận phản hồi sau mỗi buổi dạy của gia sư.
            </p>
          </div>

          {/* Form Gửi Lời Mời */}
          <form
            onSubmit={handleSendInvitation}
            className="p-5 bg-paper border border-line rounded-3xl space-y-3"
          >
            <label className="block text-xs font-medium text-ink-2">
              EMAIL TÀI KHOẢN HỌC SINH CỦA CON <span className="text-danger-strong">*</span>
            </label>

            <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
              <div className="relative flex-1">
                <Mail className="size-4 text-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
                <input
                  type="email"
                  required
                  value={inputEmail}
                  onChange={(e) => setInputEmail(e.target.value)}
                  placeholder="VD: email.cua.con@gmail.com"
                  className="w-full pl-10 pr-4 py-3 bg-surface border border-line-strong rounded-2xl text-xs font-medium text-ink placeholder:text-muted focus:outline-hidden focus:border-success focus:ring-2 focus:ring-success/20"
                />
              </div>

              <button
                type="submit"
                disabled={sending || !inputEmail.trim()}
                className="inline-flex items-center justify-center gap-2 px-6 py-3 bg-success hover:bg-success-deep active:scale-[0.99] text-white font-medium text-xs rounded-2xl shadow-xs transition-all disabled:opacity-50 shrink-0 cursor-pointer"
              >
                {sending ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  <Send className="size-4" />
                )}
                <span>{sending ? "Đang gửi..." : "Gửi lời mời liên kết"}</span>
              </button>
            </div>
            <p className="text-xs text-muted">
              Lưu ý: Học sinh phải có tài khoản EduFit đang kích hoạt để nhận lời mời.
            </p>
          </form>

          {/* Danh sách học sinh đã kết nối & Lời mời đang chờ */}
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <label className="text-xs font-medium text-ink-2">
                Trạng thái liên kết hồ sơ con em ({links.length} đã kết nối, {invitations.length} đang chờ)
              </label>
            </div>

            {loading ? (
              <div className="p-8 bg-paper rounded-2xl border border-line flex flex-col items-center justify-center gap-2 text-muted">
                <Loader2 className="size-5 animate-spin text-success-strong" />
                <span className="text-xs">Đang tải danh sách liên kết...</span>
              </div>
            ) : links.length === 0 && invitations.length === 0 ? (
              <div className="p-8 text-center bg-paper border border-dashed border-line-strong rounded-3xl space-y-2">
                <Users className="size-8 text-white/75 mx-auto" />
                <p className="text-xs font-medium text-ink-2">
                  Chưa có tài khoản con em nào được kết nối
                </p>
                <p className="text-xs text-muted max-w-sm mx-auto">
                  Nhập email học sinh ở trên để gửi lời mời liên kết, hoặc bỏ qua và làm sau.
                </p>
              </div>
            ) : (
              <div className="space-y-3">
                {/* 1. Danh sách đã xác nhận */}
                {links.map((link) => (
                  <div
                    key={link.linkId}
                    className="p-4 bg-success-soft/50 border border-success-line rounded-2xl flex items-center justify-between gap-4"
                  >
                    <div className="flex items-center gap-3">
                      <div className="size-10 rounded-xl bg-success text-white font-medium flex items-center justify-center">
                        <GraduationCap className="size-5" />
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <h4 className="text-xs font-medium text-ink">
                            Học sinh mã: {link.studentId}
                          </h4>
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-success-soft text-success-strong font-medium rounded-full text-xs">
                            <CheckCircle2 className="size-3" />
                            Đã liên kết
                          </span>
                        </div>
                        <p className="text-xs text-muted mt-0.5">
                          {link.confirmedAt
                            ? `Đã xác nhận ngày: ${new Date(link.confirmedAt).toLocaleDateString("vi-VN")}`
                            : "Đã sẵn sàng đồng bộ lịch học & kết quả"}
                        </p>
                      </div>
                    </div>
                  </div>
                ))}

                {/* 2. Danh sách lời mời đang chờ */}
                {invitations.map((item) => (
                  <div
                    key={item.invitationId}
                    className="p-4 bg-action-soft/50 border border-action-line rounded-2xl flex items-center justify-between gap-4"
                  >
                    <div className="flex items-center gap-3">
                      <div className="size-10 rounded-xl bg-action text-on-action font-medium flex items-center justify-center">
                        <Clock className="size-5" />
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <h4 className="text-xs font-medium text-ink">
                            Đang chờ học sinh chấp nhận
                          </h4>
                          <span className="inline-flex items-center px-2 py-0.5 bg-action-soft text-action-strong font-medium rounded-full text-xs">
                            Đang chờ
                          </span>
                        </div>
                        <p className="text-xs text-muted mt-0.5">
                          Mã lời mời: {item.invitationId.slice(0, 8)} · Hết hạn:{" "}
                          {new Date(item.expiresAt).toLocaleDateString("vi-VN")}
                        </p>
                      </div>
                    </div>

                    <button
                      type="button"
                      disabled={busyId === item.invitationId}
                      onClick={() => handleWithdrawInvitation(item.invitationId)}
                      className="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-danger-strong hover:bg-danger-soft rounded-xl border border-danger-line transition-colors disabled:opacity-50 cursor-pointer"
                    >
                      {busyId === item.invitationId ? (
                        <Loader2 className="size-3.5 animate-spin" />
                      ) : (
                        <Trash2 className="size-3.5" />
                      )}
                      <span>Rút lời mời</span>
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Action Footer */}
          <div className="flex items-center justify-end pt-4 border-t border-line">
            <button
              type="button"
              disabled={submitting}
              onClick={handleComplete}
              className="inline-flex items-center gap-2 px-7 py-3 bg-success hover:bg-success-deep active:scale-[0.99] text-white font-medium text-xs rounded-xl shadow-xs transition-all disabled:opacity-50 cursor-pointer"
            >
              {submitting ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <ArrowRight className="size-4" />
              )}
              <span>{submitting ? "Đang hoàn tất..." : "Hoàn tất và vào trang chủ"}</span>
            </button>
          </div>
        </div>
      </main>

      {/* 3. Footer */}
      <footer className="border-t border-line py-4 px-6 text-center text-xs text-muted">
        © 2026 EduFit Platform. Đồng hành cùng phụ huynh và học sinh trên con đường tri thức.
      </footer>
    </div>
  );
}
