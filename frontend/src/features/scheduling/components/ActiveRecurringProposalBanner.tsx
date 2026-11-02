"use client";

import React, { useState, useEffect, useCallback } from "react";
import {
  CalendarDays,
  Clock,
  Video,
  MapPin,
  CheckCircle,
  XCircle,
  AlertTriangle,
  Send,
  Loader2,
} from "lucide-react";
import { schedulingApi } from "../api/schedulingApi";
import type { RecurringProposalResponse } from "../types";
import { useToast } from "@/shared/ui/Toast";

export interface ActiveRecurringProposalBannerProps {
  classId: string;
  currentUserId?: string;
  userRole?: string;
  onUpdated?: () => void;
}

const DAY_LABELS: Record<string, string> = {
  MONDAY: "Thứ Hai",
  TUESDAY: "Thứ Ba",
  WEDNESDAY: "Thứ Tư",
  THURSDAY: "Thứ Năm",
  FRIDAY: "Thứ Sáu",
  SATURDAY: "Thứ Bảy",
  SUNDAY: "Chủ Nhật",
};

function formatSlotDisplay(raw: string): string {
  const parts = raw.trim().split(/\s+/);
  if (parts.length >= 2) {
    const day = parts[0].toUpperCase();
    const time = parts[1];
    const dayLabel = DAY_LABELS[day] || day;
    return `${dayLabel}: ${time.replace("-", " - ")}`;
  }
  return raw;
}

function formatDate(isoStr: string): string {
  try {
    const d = new Date(isoStr);
    return d.toLocaleDateString("vi-VN", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
    });
  } catch {
    return isoStr;
  }
}

export function ActiveRecurringProposalBanner({
  classId,
  currentUserId,
  userRole: _userRole,
  onUpdated,
}: ActiveRecurringProposalBannerProps) {
  const { showToast } = useToast();
  const [proposal, setProposal] = useState<RecurringProposalResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [acting, setActing] = useState<boolean>(false);
  const [showRejectInput, setShowRejectInput] = useState<boolean>(false);
  const [rejectReason, setRejectReason] = useState<string>("");

  const loadProposal = useCallback(async () => {
    try {
      setLoading(true);
      const res = await schedulingApi.getActiveRecurringProposal(classId);
      if (res.data && res.data.status === "PENDING") {
        setProposal(res.data);
      } else {
        setProposal(null);
      }
    } catch {
      setProposal(null);
    } finally {
      setLoading(false);
    }
  }, [classId]);

  useEffect(() => {
    void loadProposal();
  }, [loadProposal]);

  if (loading || !proposal) {
    return null;
  }

  const isProposer = Boolean(currentUserId && proposal.proposedBy === currentUserId);

  const handleRespond = async (accept: boolean) => {
    if (!proposal) return;
    try {
      setActing(true);
      await schedulingApi.respondRecurringSchedule({
        proposalId: proposal.proposalId,
        accept,
        reason: !accept && rejectReason.trim() ? rejectReason.trim() : undefined,
      });

      if (typeof window !== "undefined") {
        window.dispatchEvent(new CustomEvent("edufit:schedule-updated"));
        try {
          localStorage.setItem("edufit:schedule-updated", String(Date.now()));
        } catch {
          // ignore
        }
      }

      showToast(
        accept
          ? "Đã đồng ý áp dụng lịch học định kỳ mới!"
          : "Đã từ chối đề xuất lịch học định kỳ.",
        accept ? "success" : "info"
      );
      setProposal(null);
      setShowRejectInput(false);
      setRejectReason("");
      onUpdated?.();
    } catch (err: unknown) {
      showToast(
        err instanceof Error
          ? err.message
          : "Không thể xử lý phản hồi lịch định kỳ. Vui lòng thử lại.",
        "error"
      );
    } finally {
      setActing(false);
    }
  };

  return (
    <div className="mt-3 overflow-hidden rounded-2xl border border-blue-200 bg-gradient-to-r from-blue-50/90 to-indigo-50/70 p-3.5 shadow-xs dark:border-blue-900/50 dark:from-blue-950/30 dark:to-indigo-950/20">
      {/* Header */}
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-blue-600 text-white shadow-xs">
            <CalendarDays className="h-3.5 w-3.5" />
          </span>
          <span className="text-xs font-bold text-blue-900 dark:text-blue-200">
            Đề xuất lịch định kỳ mới (UC4.8)
          </span>
        </div>
        <span className="rounded-full bg-blue-100 px-2 py-0.5 text-[10px] font-bold text-blue-700 dark:bg-blue-900/60 dark:text-blue-300">
          Chờ phản hồi
        </span>
      </div>

      {/* Info details */}
      <div className="mt-2.5 space-y-1.5 text-xs text-zinc-700 dark:text-zinc-300">
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
          <div className="flex items-center gap-1 font-medium">
            <Clock className="h-3.5 w-3.5 text-blue-600 shrink-0 dark:text-blue-400" />
            <span>
              Chu kỳ: <b>{formatDate(proposal.effectiveFrom)}</b> → <b>{formatDate(proposal.effectiveTo)}</b>
            </span>
          </div>
          {proposal.defaultMode && (
            <div className="flex items-center gap-1 text-zinc-600 dark:text-zinc-400">
              {proposal.defaultMode === "ONLINE" ? (
                <>
                  <Video className="h-3.5 w-3.5 text-blue-600 shrink-0 dark:text-blue-400" />
                  <span>Trực tuyến</span>
                </>
              ) : (
                <>
                  <MapPin className="h-3.5 w-3.5 text-emerald-600 shrink-0 dark:text-emerald-400" />
                  <span>Trực tiếp</span>
                </>
              )}
            </div>
          )}
        </div>

        {/* Weekly Slots */}
        <div className="flex flex-wrap items-center gap-1.5 pt-1">
          {proposal.newWeeklySlots?.map((slot, idx) => (
            <span
              key={idx}
              className="inline-flex items-center gap-1 rounded-lg border border-blue-200/80 bg-white/80 px-2 py-0.5 text-[11px] font-semibold text-blue-800 shadow-2xs dark:border-blue-900/40 dark:bg-zinc-800 dark:text-blue-300"
            >
              {formatSlotDisplay(slot)}
            </span>
          ))}
        </div>

        {proposal.placeOrLink && (
          <div className="text-[11px] text-zinc-600 dark:text-zinc-400 truncate">
            {proposal.defaultMode === "ONLINE" ? "Link: " : "Địa chỉ: "}
            <span className="font-medium text-zinc-800 dark:text-zinc-200">{proposal.placeOrLink}</span>
          </div>
        )}

        {proposal.message && (
          <p className="italic text-[11px] text-zinc-500 dark:text-zinc-400 bg-white/50 dark:bg-zinc-800/40 rounded-lg p-1.5">
            &ldquo;{proposal.message}&rdquo;
          </p>
        )}
      </div>

      {/* Proposer perspective vs Recipient perspective */}
      <div className="mt-3 pt-2.5 border-t border-blue-200/60 dark:border-blue-900/40">
        {isProposer ? (
          <div className="flex items-center gap-1.5 text-xs text-blue-700 dark:text-blue-300">
            <Send className="h-3.5 w-3.5 shrink-0" />
            <span>
              Bạn đã gửi đề xuất này. Đang chờ đối phương xác nhận trước{" "}
              {formatDate(proposal.expiresAt)}.
            </span>
          </div>
        ) : (
          <div className="space-y-2.5">
            {/* Cảnh báo hủy lịch cũ cho đối phương nắm rõ */}
            <div
              role="alert"
              className="rounded-xl border border-amber-300 bg-amber-50 p-2.5 text-xs text-amber-900 dark:border-amber-900/50 dark:bg-amber-950/40 dark:text-amber-300 flex items-start gap-2"
            >
              <AlertTriangle className="size-4 shrink-0 text-amber-600 dark:text-amber-400 mt-0.5" />
              <div>
                <span className="font-bold">Lưu ý quan trọng:</span> Khi bạn bấm đồng ý,{" "}
                <strong>toàn bộ lịch học cũ trong tương lai của lớp sẽ tự động bị hủy</strong> và được
                thay thế bằng loạt buổi học mới theo khung giờ thỏa thuận này.
              </div>
            </div>

            {!showRejectInput ? (
              <div className="flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  disabled={acting}
                  onClick={() => void handleRespond(true)}
                  className="inline-flex items-center justify-center gap-1.5 rounded-xl bg-emerald-600 px-3 py-1.5 text-xs font-semibold text-white shadow-xs hover:bg-emerald-700 active:scale-95 disabled:opacity-50 transition-all cursor-pointer"
                >
                  {acting ? (
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  ) : (
                    <CheckCircle className="h-3.5 w-3.5" />
                  )}
                  Đồng ý lịch định kỳ
                </button>
                <button
                  type="button"
                  disabled={acting}
                  onClick={() => setShowRejectInput(true)}
                  className="inline-flex items-center justify-center gap-1 rounded-xl border border-rose-300 bg-white px-3 py-1.5 text-xs font-semibold text-rose-700 shadow-xs hover:bg-rose-50 active:scale-95 disabled:opacity-50 transition-all cursor-pointer dark:bg-zinc-800 dark:border-rose-900/50 dark:text-rose-300"
                >
                  <XCircle className="h-3.5 w-3.5" />
                  Từ chối
                </button>
              </div>
            ) : (
              <div className="space-y-2 pt-1">
                <div className="flex items-center gap-1.5 text-xs font-medium text-rose-700 dark:text-rose-300">
                  <AlertTriangle className="h-3.5 w-3.5" />
                  <span>Lý do từ chối (tùy chọn):</span>
                </div>
                <input
                  type="text"
                  value={rejectReason}
                  onChange={(e) => setRejectReason(e.target.value)}
                  placeholder="Nhập lý do hoặc khung giờ mong muốn khác..."
                  className="w-full rounded-xl border border-rose-200 bg-white px-3 py-1.5 text-xs text-zinc-900 focus:border-rose-400 focus:outline-none dark:border-rose-900/50 dark:bg-zinc-800 dark:text-zinc-100"
                />
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    disabled={acting}
                    onClick={() => void handleRespond(false)}
                    className="rounded-xl bg-rose-600 px-3 py-1 text-xs font-semibold text-white hover:bg-rose-700 disabled:opacity-50"
                  >
                    {acting ? "Đang xử lý..." : "Xác nhận từ chối"}
                  </button>
                  <button
                    type="button"
                    disabled={acting}
                    onClick={() => setShowRejectInput(false)}
                    className="rounded-xl px-2.5 py-1 text-xs font-medium text-zinc-600 hover:bg-zinc-100 dark:text-zinc-300 dark:hover:bg-zinc-800"
                  >
                    Quay lại
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
