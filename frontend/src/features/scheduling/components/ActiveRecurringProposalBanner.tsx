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
    <div className="mt-3 overflow-hidden rounded-2xl border border-brand-line bg-brand-soft/90 p-3.5 shadow-xs">
      {/* Header */}
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-brand text-white shadow-xs">
            <CalendarDays className="h-3.5 w-3.5" />
          </span>
          <span className="text-xs font-medium text-brand-strong">
            Đề xuất lịch định kỳ mới (UC4.8)
          </span>
        </div>
        <span className="rounded-full bg-brand-soft px-2 py-0.5 text-xs font-medium text-brand-strong">
          Chờ phản hồi
        </span>
      </div>

      {/* Info details */}
      <div className="mt-2.5 space-y-1.5 text-xs text-ink-2">
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
          <div className="flex items-center gap-1 font-medium">
            <Clock className="h-3.5 w-3.5 text-brand-strong shrink-0" />
            <span>
              Chu kỳ: <b>{formatDate(proposal.effectiveFrom)}</b> → <b>{formatDate(proposal.effectiveTo)}</b>
            </span>
          </div>
          {proposal.defaultMode && (
            <div className="flex items-center gap-1 text-ink-2">
              {proposal.defaultMode === "ONLINE" ? (
                <>
                  <Video className="h-3.5 w-3.5 text-brand-strong shrink-0" />
                  <span>Trực tuyến</span>
                </>
              ) : (
                <>
                  <MapPin className="h-3.5 w-3.5 text-success-strong shrink-0" />
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
              className="inline-flex items-center gap-1 rounded-lg border border-brand-line/80 bg-surface/80 px-2 py-0.5 text-xs font-medium text-brand-strong shadow-2xs"
            >
              {formatSlotDisplay(slot)}
            </span>
          ))}
        </div>

        {proposal.placeOrLink && (
          <div className="text-xs text-ink-2 truncate">
            {proposal.defaultMode === "ONLINE" ? "Link: " : "Địa chỉ: "}
            <span className="font-medium text-ink">{proposal.placeOrLink}</span>
          </div>
        )}

        {proposal.message && (
          <p className="italic text-xs text-muted bg-surface/50 rounded-lg p-1.5">
            &ldquo;{proposal.message}&rdquo;
          </p>
        )}
      </div>

      {/* Proposer perspective vs Recipient perspective */}
      <div className="mt-3 pt-2.5 border-t border-brand-line/60">
        {isProposer ? (
          <div className="flex items-center gap-1.5 text-xs text-brand-strong">
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
              className="rounded-xl border border-action-line bg-action-soft p-2.5 text-xs text-action-strong flex items-start gap-2"
            >
              <AlertTriangle className="size-4 shrink-0 text-action-strong mt-0.5" />
              <div>
                <span className="font-medium">Lưu ý quan trọng:</span> Khi bạn bấm đồng ý,{" "}
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
                  className="inline-flex items-center justify-center gap-1.5 rounded-xl bg-success px-3 py-1.5 text-xs font-medium text-white shadow-xs hover:bg-success-deep active:scale-95 disabled:opacity-50 transition-all cursor-pointer"
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
                  className="inline-flex items-center justify-center gap-1 rounded-xl border border-danger-line bg-surface px-3 py-1.5 text-xs font-medium text-danger-strong shadow-xs hover:bg-danger-soft active:scale-95 disabled:opacity-50 transition-all cursor-pointer"
                >
                  <XCircle className="h-3.5 w-3.5" />
                  Từ chối
                </button>
              </div>
            ) : (
              <div className="space-y-2 pt-1">
                <div className="flex items-center gap-1.5 text-xs font-medium text-danger-strong">
                  <AlertTriangle className="h-3.5 w-3.5" />
                  <span>Lý do từ chối (tùy chọn):</span>
                </div>
                <input
                  type="text"
                  value={rejectReason}
                  onChange={(e) => setRejectReason(e.target.value)}
                  placeholder="Nhập lý do hoặc khung giờ mong muốn khác..."
                  className="w-full rounded-xl border border-danger-line bg-surface px-3 py-1.5 text-xs text-ink focus:border-danger focus:outline-none"
                />
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    disabled={acting}
                    onClick={() => void handleRespond(false)}
                    className="rounded-xl bg-danger px-3 py-1 text-xs font-medium text-white hover:bg-danger-deep disabled:opacity-50"
                  >
                    {acting ? "Đang xử lý..." : "Xác nhận từ chối"}
                  </button>
                  <button
                    type="button"
                    disabled={acting}
                    onClick={() => setShowRejectInput(false)}
                    className="rounded-xl px-2.5 py-1 text-xs font-medium text-ink-2 hover:bg-sunken"
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
