"use client";

import React from "react";
import {
  X,
  GraduationCap,
  Target,
  TrendingUp,
  Calendar,
  Clock,
  DollarSign,
  MapPin,
  MessageSquare,
  Award,
} from "lucide-react";
import type { TutorInboxItem } from "../types";

export interface StudentPedagogicalProfileModalProps {
  request: TutorInboxItem | null;
  isOpen: boolean;
  onClose: () => void;
}

export function formatGoalType(goalType?: string): string {
  if (!goalType) return "Mục tiêu chung";
  switch (goalType) {
    case "EXAM_PREPARATION":
      return "Luyện thi chuyển cấp";
    case "GRADE_IMPROVEMENT":
      return "Cải thiện điểm số";
    case "FOUNDATION":
      return "Củng cố căn bản";
    case "ADVANCED":
      return "Bồi dưỡng học sinh giỏi";
    default:
      return goalType;
  }
}

export function formatDeadline(deadline?: string | null): string {
  if (!deadline) return "";
  try {
    const d = new Date(deadline);
    if (Number.isNaN(d.getTime())) return deadline;
    const day = String(d.getDate()).padStart(2, "0");
    const month = String(d.getMonth() + 1).padStart(2, "0");
    const year = d.getFullYear();
    return `${day}/${month}/${year}`;
  } catch {
    return deadline;
  }
}

export function StudentPedagogicalProfileModal({
  request,
  isOpen,
  onClose,
}: StudentPedagogicalProfileModalProps) {
  if (!isOpen || !request) return null;

  const currentLevelDisplay =
    request.currentLevel !== undefined && request.currentLevel !== null
      ? `Cấp độ ${request.currentLevel}`
      : request.currentGrade || "Chưa xác định";

  const desiredLevelDisplay =
    request.desiredLevel !== undefined && request.desiredLevel !== null
      ? `Cấp độ ${request.desiredLevel}`
      : request.targetScore || "Chưa xác định";

  const formattedDeadline = formatDeadline(request.deadline);

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="profile-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-stone-900/60 backdrop-blur-xs animate-in fade-in duration-200 overflow-y-auto"
    >
      <div className="relative w-full max-w-lg overflow-hidden rounded-3xl bg-white shadow-2xl border border-stone-200 dark:bg-zinc-900 dark:border-zinc-800 my-auto">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-stone-100 dark:border-zinc-800 px-6 py-4">
          <div className="flex items-center gap-2.5">
            <div className="grid size-10 place-items-center rounded-xl bg-indigo-100 text-indigo-700 dark:bg-indigo-950/60 dark:text-indigo-400">
              <GraduationCap className="size-5" />
            </div>
            <div>
              <h2 id="profile-modal-title" className="text-base font-bold text-neutral-900 dark:text-zinc-100">
                Hồ sơ học tập chi tiết
              </h2>
              <p className="text-xs text-neutral-500 dark:text-zinc-400">
                {request.studentDisplayName || "Học sinh"} • {request.senderRole === "PARENT" ? "Phụ huynh gửi" : "Học sinh gửi"}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="grid size-8 place-items-center rounded-full text-neutral-400 hover:bg-stone-100 hover:text-neutral-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200 transition-colors cursor-pointer"
            aria-label="Đóng"
          >
            <X className="size-4" />
          </button>
        </div>

        {/* Content Body */}
        <div className="p-6 space-y-4 max-h-[75vh] overflow-y-auto">
          {/* Mục tiêu học tập Banner */}
          <div className="rounded-2xl bg-indigo-50/70 p-4 border border-indigo-100 dark:bg-indigo-950/30 dark:border-indigo-900/40">
            <div className="flex items-center gap-2 text-indigo-700 dark:text-indigo-300 font-semibold text-xs mb-1">
              <Target className="size-4" />
              <span>Mục tiêu sư phạm</span>
            </div>
            <div className="flex flex-wrap items-center gap-2 mt-1">
              <span className="inline-flex items-center rounded-lg bg-indigo-600 px-2.5 py-1 text-xs font-bold text-white shadow-xs">
                {formatGoalType(request.goalType)}
              </span>
              <span className="text-xs text-neutral-600 dark:text-zinc-300">
                Môn: <strong>{request.subjectName || "Chưa xác định"}</strong>
              </span>
              {request.educationLevelName && (
                <span className="text-xs text-neutral-600 dark:text-zinc-300">
                  • <strong>{request.educationLevelName}</strong>
                </span>
              )}
            </div>
          </div>

          {/* Thang năng lực: Hiện tại -> Mong muốn */}
          <div className="rounded-2xl border border-stone-200 bg-stone-50/50 p-4 dark:border-zinc-800 dark:bg-zinc-800/40">
            <div className="flex items-center gap-2 text-neutral-700 dark:text-zinc-300 font-semibold text-xs mb-3">
              <TrendingUp className="size-4 text-emerald-600 dark:text-emerald-400" />
              <span>Lộ trình năng lực</span>
            </div>
            <div className="grid grid-cols-2 gap-3 text-center">
              <div className="rounded-xl bg-white p-3 border border-stone-200 dark:bg-zinc-900 dark:border-zinc-700">
                <span className="text-[11px] text-neutral-400 dark:text-zinc-400 block font-medium">Hiện tại</span>
                <span className="text-sm font-bold text-neutral-800 dark:text-zinc-100 mt-0.5 block">
                  {currentLevelDisplay}
                </span>
              </div>
              <div className="rounded-xl bg-emerald-50 p-3 border border-emerald-200 dark:bg-emerald-950/40 dark:border-emerald-800">
                <span className="text-[11px] text-emerald-600 dark:text-emerald-400 block font-medium">Mục tiêu kỳ vọng</span>
                <span className="text-sm font-bold text-emerald-700 dark:text-emerald-300 mt-0.5 block">
                  {desiredLevelDisplay}
                </span>
              </div>
            </div>
          </div>

          {/* Hạn chót & Ngân sách */}
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="rounded-2xl border border-stone-200 bg-white p-3.5 dark:border-zinc-800 dark:bg-zinc-900">
              <div className="flex items-center gap-1.5 text-xs text-neutral-500 dark:text-zinc-400 font-medium mb-1">
                <Calendar className="size-3.5 text-neutral-400" />
                <span>Hạn chót mục tiêu</span>
              </div>
              <p className="text-xs font-bold text-neutral-800 dark:text-zinc-200">
                {formattedDeadline || "Không thời hạn"}
              </p>
            </div>

            <div className="rounded-2xl border border-stone-200 bg-white p-3.5 dark:border-zinc-800 dark:bg-zinc-900">
              <div className="flex items-center gap-1.5 text-xs text-neutral-500 dark:text-zinc-400 font-medium mb-1">
                <DollarSign className="size-3.5 text-neutral-400" />
                <span>Ngân sách đề xuất</span>
              </div>
              <p className="text-xs font-bold text-neutral-800 dark:text-zinc-200">
                {request.budgetMax
                  ? `${request.budgetMin ? `${request.budgetMin.toLocaleString("vi-VN")} - ` : ""}${request.budgetMax.toLocaleString("vi-VN")} đ/buổi`
                  : "Thỏa thuận"}
              </p>
            </div>
          </div>

          {/* Địa điểm học tập */}
          {(request.area || request.district || request.city) && (
            <div className="rounded-2xl border border-stone-200 bg-white p-3.5 dark:border-zinc-800 dark:bg-zinc-900">
              <div className="flex items-center gap-1.5 text-xs text-neutral-500 dark:text-zinc-400 font-medium mb-1">
                <MapPin className="size-3.5 text-neutral-400" />
                <span>Khu vực học</span>
              </div>
              <p className="text-xs font-semibold text-neutral-800 dark:text-zinc-200">
                {[request.area, request.district, request.city].filter(Boolean).join(", ")}
              </p>
            </div>
          )}

          {/* Khung giờ mong muốn */}
          <div className="rounded-2xl border border-stone-200 bg-white p-3.5 dark:border-zinc-800 dark:bg-zinc-900">
            <div className="flex items-center gap-1.5 text-xs text-neutral-500 dark:text-zinc-400 font-medium mb-1.5">
              <Clock className="size-3.5 text-neutral-400" />
              <span>Khung giờ mong muốn</span>
            </div>
            <div className="flex flex-wrap gap-1.5">
              {request.desiredSlots && request.desiredSlots.length > 0 ? (
                request.desiredSlots.map((slot, idx) => (
                  <span
                    key={idx}
                    className="inline-flex items-center gap-1 rounded-lg bg-stone-100 dark:bg-zinc-800 px-2.5 py-1 font-mono text-[11px] text-neutral-700 dark:text-zinc-300"
                  >
                    <Calendar className="size-3 opacity-70" />
                    {slot}
                  </span>
                ))
              ) : (
                <span className="italic text-neutral-400 text-xs">Chưa chỉ định khung giờ</span>
              )}
            </div>
          </div>

          {/* Lời nhắn */}
          {request.message && (
            <div className="rounded-2xl bg-amber-50/60 p-4 border border-amber-200/60 dark:bg-amber-950/20 dark:border-amber-900/30">
              <div className="flex items-center gap-1.5 text-amber-800 dark:text-amber-400 font-medium text-xs mb-1">
                <MessageSquare className="size-3.5" />
                <span>Lời nhắn từ người gửi:</span>
              </div>
              <p className="text-xs text-amber-950 dark:text-amber-200 italic leading-relaxed">
                &quot;{request.message}&quot;
              </p>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end border-t border-stone-100 dark:border-zinc-800 px-6 py-3.5">
          <button
            type="button"
            onClick={onClose}
            className="h-9 px-4 rounded-xl bg-stone-100 hover:bg-stone-200 text-neutral-700 text-xs font-bold transition-colors dark:bg-zinc-800 dark:hover:bg-zinc-700 dark:text-zinc-200 cursor-pointer"
          >
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
}
