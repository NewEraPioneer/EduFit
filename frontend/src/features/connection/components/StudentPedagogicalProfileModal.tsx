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
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-stone-900/60 backdrop-blur-xs animate-in fade-in duration-200 overflow-y-auto"
    >
      <div className="relative w-full max-w-lg overflow-hidden rounded-3xl bg-white shadow-2xl border border-stone-200 dark:bg-zinc-900 dark:border-zinc-800 my-auto">
        <div className="flex items-center justify-between gap-3 border-b border-stone-100 dark:border-zinc-800 px-5 sm:px-6 py-4">
          <div className="flex items-center gap-2.5 min-w-0">
            <div className="grid size-10 shrink-0 place-items-center rounded-xl bg-indigo-100 text-indigo-700 dark:bg-indigo-950/60 dark:text-indigo-400">
              <GraduationCap className="size-5" />
            </div>
            <div className="min-w-0">
              <h2 id="profile-modal-title" className="text-base font-bold text-neutral-900 dark:text-zinc-100">
                Nhu cầu học của học sinh
              </h2>
              <p className="text-xs text-neutral-500 dark:text-zinc-400 truncate">
                {request.studentDisplayName || "Học sinh"} • {request.senderRole === "PARENT" ? "Phụ huynh gửi" : "Học sinh gửi"}
              </p>
            </div>
          </div>
          <button type="button" onClick={onClose} aria-label="Đóng"
            className="grid size-8 shrink-0 place-items-center rounded-full text-neutral-400 hover:bg-stone-100 hover:text-neutral-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200 transition-colors cursor-pointer">
            <X className="size-4" />
          </button>
        </div>

        <div className="p-5 sm:p-6 space-y-4">
          <div className="rounded-2xl bg-indigo-50/70 p-4 border border-indigo-100 dark:bg-indigo-950/30 dark:border-indigo-900/40 space-y-1.5">
            <div className="flex items-center gap-2 text-indigo-700 dark:text-indigo-300 font-semibold text-xs">
              <Target className="size-4" />
              <span>Nhu cầu học</span>
            </div>
            <p className="text-sm font-bold text-neutral-900 dark:text-zinc-100 break-words">{inboxNeedTitle(need)}</p>
            {details.length > 0 && <p className="text-xs text-neutral-600 dark:text-zinc-300 break-words">{details.join(" · ")}</p>}
          </div>

          {request.fitChecks.length > 0 && (
            <div className="rounded-2xl border border-stone-200 p-4 dark:border-zinc-800 space-y-2">
              <p className="text-xs font-semibold text-neutral-700 dark:text-zinc-300 break-words">
                So với gói dạy {request.offeringName ? <strong>{request.offeringName}</strong> : "của bạn"}
              </p>
              <FitChecks checks={request.fitChecks} />
            </div>
          )}

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="rounded-2xl border border-stone-200 bg-white p-3.5 dark:border-zinc-800 dark:bg-zinc-900">
              <div className="flex items-center gap-1.5 text-xs text-neutral-500 dark:text-zinc-400 font-medium mb-1">
                <MapPin className="size-3.5 text-neutral-400" />
                <span>Hình thức & khu vực</span>
              </div>
              <p className="text-xs font-bold text-neutral-800 dark:text-zinc-200 break-words">{inboxNeedPlace(need)}</p>
            </div>
            <div className="rounded-2xl border border-stone-200 bg-white p-3.5 dark:border-zinc-800 dark:bg-zinc-900">
              <div className="flex items-center gap-1.5 text-xs text-neutral-500 dark:text-zinc-400 font-medium mb-1">
                <DollarSign className="size-3.5 text-neutral-400" />
                <span>Ngân sách tối đa</span>
              </div>
              <p className="text-xs font-bold text-neutral-800 dark:text-zinc-200">
                {need.budgetMaxPerSession != null ? `${formatMoney(need.budgetMaxPerSession)}/buổi` : "Chưa nêu"}
              </p>
            </div>
          </div>

          <div className="rounded-2xl border border-stone-200 bg-white p-3.5 dark:border-zinc-800 dark:bg-zinc-900">
            <div className="flex items-center gap-1.5 text-xs text-neutral-500 dark:text-zinc-400 font-medium mb-1.5">
              <Clock className="size-3.5 text-neutral-400" />
              <span>Khung giờ mong muốn</span>
            </div>
            <div className="flex flex-wrap gap-1.5">
              {request.desiredSlots.length > 0 ? (
                request.desiredSlots.map((slot) => (
                  <span key={slot}
                    className="inline-flex items-center gap-1 rounded-lg bg-stone-100 dark:bg-zinc-800 px-2.5 py-1 text-[11px] text-neutral-700 dark:text-zinc-300">
                    <Calendar className="size-3 opacity-70" />
                    {slot}
                  </span>
                ))
              ) : (
                <span className="italic text-neutral-400 text-xs">Chưa chỉ định khung giờ</span>
              )}
            </div>
          </div>

          {request.message && (
            <div className="rounded-2xl bg-amber-50/60 p-4 border border-amber-200/60 dark:bg-amber-950/20 dark:border-amber-900/30">
              <div className="flex items-center gap-1.5 text-amber-800 dark:text-amber-400 font-medium text-xs mb-1">
                <MessageSquare className="size-3.5" />
                <span>Lời nhắn từ người gửi:</span>
              </div>
              <p className="text-xs text-amber-950 dark:text-amber-200 italic leading-relaxed break-words">&quot;{request.message}&quot;</p>
            </div>
          )}
        </div>

        <div className="flex items-center justify-end border-t border-stone-100 dark:border-zinc-800 px-6 py-3.5">
          <button type="button" onClick={onClose}
            className="h-9 px-4 rounded-xl bg-stone-100 hover:bg-stone-200 text-neutral-700 text-xs font-bold transition-colors dark:bg-zinc-800 dark:hover:bg-zinc-700 dark:text-zinc-200 cursor-pointer">
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
}
