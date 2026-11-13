"use client";

import React from "react";
import { X, GraduationCap, Target, Calendar, Clock, DollarSign, MapPin, MessageSquare } from "lucide-react";
import { MODE_LABELS, formatMoney } from "@/features/learning-needs/catalogRules";
import { FitChecks } from "@/features/discovery/components/FitChecks";
import type { InboxNeedSummary, TutorInboxItem } from "../types";

export interface StudentPedagogicalProfileModalProps {
  request: TutorInboxItem | null;
  isOpen: boolean;
  onClose: () => void;
}

/** "Toán – Nhánh · Lớp 9 · Luyện thi vào 10" cho nhu cầu trong hộp thư gia sư. */
export function inboxNeedTitle(need: InboxNeedSummary): string {
  const subject = need.trackName ? `${need.subjectName} – ${need.trackName}` : need.subjectName;
  return [subject, need.educationLevelName, need.goalTagName].filter(Boolean).join(" · ");
}

/** Bộ sách, tỉnh dự thi, trường hướng tới (chỉ những gì nhu cầu có). */
export function inboxNeedDetails(need: InboxNeedSummary): string[] {
  return [
    need.textbookSeriesName && `Bộ sách: ${need.textbookSeriesName}`,
    need.examProvinceName && `Thi vào 10 tại ${need.examProvinceName}`,
    need.targetSchoolName && `Hướng tới: ${need.targetSchoolName}`,
  ].filter(Boolean) as string[];
}

export function inboxNeedPlace(need: InboxNeedSummary): string {
  const mode = need.mode ? MODE_LABELS[need.mode] : "Chưa chọn hình thức";
  if (need.mode === "ONLINE" || !need.provinceName) return mode;
  return `${mode} · ${need.districtName ? `${need.districtName}, ${need.provinceName}` : need.provinceName}`;
}

export function StudentPedagogicalProfileModal({ request, isOpen, onClose }: StudentPedagogicalProfileModalProps) {
  if (!isOpen || !request) return null;
  const { need } = request;
  const details = inboxNeedDetails(need);

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="profile-modal-title"
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4 bg-night/60 backdrop-blur-xs animate-in fade-in duration-200 overflow-y-auto"
    >
      <div className="relative w-full max-w-dialog overflow-hidden rounded-3xl bg-surface shadow-2xl border border-line my-auto">
        <div className="flex items-center justify-between gap-3 border-b border-line px-5 sm:px-6 py-4">
          <div className="flex items-center gap-2.5 min-w-0">
            <div className="grid size-10 shrink-0 place-items-center rounded-xl bg-brand-soft text-brand-strong">
              <GraduationCap className="size-5" />
            </div>
            <div className="min-w-0">
              <h2 id="profile-modal-title" className="text-base font-medium text-ink">
                Nhu cầu học của học sinh
              </h2>
              <p className="text-xs text-muted truncate">
                {request.studentDisplayName || "Học sinh"} • {request.senderRole === "PARENT" ? "Phụ huynh gửi" : "Học sinh gửi"}
              </p>
            </div>
          </div>
          <button type="button" onClick={onClose} aria-label="Đóng"
            className="grid size-8 shrink-0 place-items-center rounded-full text-muted hover:bg-sunken hover:text-ink-2 transition-colors cursor-pointer">
            <X className="size-4" />
          </button>
        </div>

        <div className="p-5 sm:p-6 space-y-4">
          <div className="rounded-2xl bg-brand-soft/70 p-4 border border-brand-line space-y-1.5">
            <div className="flex items-center gap-2 text-brand-strong font-medium text-xs">
              <Target className="size-4" />
              <span>Nhu cầu học</span>
            </div>
            <p className="text-sm font-medium text-ink break-words">{inboxNeedTitle(need)}</p>
            {details.length > 0 && <p className="text-xs text-ink-2 break-words">{details.join(" · ")}</p>}
          </div>

          {request.fitChecks.length > 0 && (
            <div className="rounded-2xl border border-line p-4 space-y-2">
              <p className="text-xs font-medium text-ink-2 break-words">
                So với gói dạy {request.offeringName ? <strong>{request.offeringName}</strong> : "của bạn"}
              </p>
              <FitChecks checks={request.fitChecks} />
            </div>
          )}

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="rounded-2xl border border-line bg-surface p-3.5">
              <div className="flex items-center gap-1.5 text-xs text-muted font-medium mb-1">
                <MapPin className="size-3.5 text-muted" />
                <span>Hình thức & khu vực</span>
              </div>
              <p className="text-xs font-medium text-ink break-words">{inboxNeedPlace(need)}</p>
            </div>
            <div className="rounded-2xl border border-line bg-surface p-3.5">
              <div className="flex items-center gap-1.5 text-xs text-muted font-medium mb-1">
                <DollarSign className="size-3.5 text-muted" />
                <span>Ngân sách tối đa</span>
              </div>
              <p className="text-xs font-medium text-ink">
                {need.budgetMaxPerSession != null ? `${formatMoney(need.budgetMaxPerSession)}/buổi` : "Chưa nêu"}
              </p>
            </div>
          </div>

          <div className="rounded-2xl border border-line bg-surface p-3.5">
            <div className="flex items-center gap-1.5 text-xs text-muted font-medium mb-1.5">
              <Clock className="size-3.5 text-muted" />
              <span>Khung giờ mong muốn</span>
            </div>
            <div className="flex flex-wrap gap-1.5">
              {request.desiredSlots.length > 0 ? (
                request.desiredSlots.map((slot) => (
                  <span key={slot}
                    className="inline-flex items-center gap-1 rounded-lg bg-sunken px-2.5 py-1 text-xs text-ink-2">
                    <Calendar className="size-3 opacity-70" />
                    {slot}
                  </span>
                ))
              ) : (
                <span className="italic text-muted text-xs">Chưa chỉ định khung giờ</span>
              )}
            </div>
          </div>

          {request.message && (
            <div className="rounded-2xl bg-action-soft/60 p-4 border border-action-line/60">
              <div className="flex items-center gap-1.5 text-action-strong font-medium text-xs mb-1">
                <MessageSquare className="size-3.5" />
                <span>Lời nhắn từ người gửi:</span>
              </div>
              <p className="text-xs text-action-strong italic leading-relaxed break-words">&quot;{request.message}&quot;</p>
            </div>
          )}
        </div>

        <div className="flex items-center justify-end border-t border-line px-6 py-3.5">
          <button type="button" onClick={onClose}
            className="h-9 px-4 rounded-xl bg-sunken hover:bg-line text-ink-2 text-xs font-medium transition-colors cursor-pointer">
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
}
