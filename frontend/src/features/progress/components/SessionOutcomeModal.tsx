"use client";

import React, { useState, useEffect } from "react";
import {
  X,
  CheckCircle2,
  XCircle,
  FileText,
  Award,
  Loader2,
  AlertCircle,
  Clock,
  User,
  Star,
} from "lucide-react";
import { progressApi } from "../api/progressApi";
import { recordSessionOutcomeSchema } from "../schemas/progressSchemas";
import type { PlanResponse, MilestoneResponse } from "../types";
import { formatLocalDateToIso } from "@/shared/utils/dateTime";

export interface SessionOutcomeModalProps {
  isOpen: boolean;
  onClose: () => void;
  session: {
    sessionId: string;
    classId?: string;
    startAt: string;
    endAt: string;
    studentName?: string;
  };
  plan?: PlanResponse | null;
  onSuccess?: () => void;
}

export function SessionOutcomeModal({
  isOpen,
  onClose,
  session,
  plan,
  onSuccess,
}: SessionOutcomeModalProps) {
  const [outcome, setOutcome] = useState<"COMPLETED" | "ABSENT">("COMPLETED");
  const [noteContent, setNoteContent] = useState("");
  const [selectedMilestoneIds, setSelectedMilestoneIds] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen) {
      setOutcome("COMPLETED");
      setNoteContent("");
      setSelectedMilestoneIds([]);
      setErrorMessage(null);
    }
  }, [isOpen, session.sessionId]);

  if (!isOpen) return null;

  const formatDateTime = (iso: string) => {
    try {
      const d = new Date(iso);
      return new Intl.DateTimeFormat("vi-VN", {
        weekday: "short",
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      }).format(d);
    } catch {
      return iso;
    }
  };

  // Filter milestones that are not yet ACHIEVED
  const pendingMilestones =
    plan?.milestones?.filter(
      (m) => !m.isArchived && m.status !== "ACHIEVED" && m.status !== "COMPLETED"
    ) || [];

  const handleToggleMilestone = (milestoneId: string) => {
    setSelectedMilestoneIds((prev) =>
      prev.includes(milestoneId)
        ? prev.filter((id) => id !== milestoneId)
        : [...prev, milestoneId]
    );
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    const validation = recordSessionOutcomeSchema.safeParse({
      outcome,
      noteContent: noteContent.trim() || undefined,
      achievedMilestoneIds: selectedMilestoneIds,
    });

    if (!validation.success) {
      setErrorMessage(
        validation.error.issues[0]?.message || "Thông tin biên bản buổi học không hợp lệ."
      );
      return;
    }

    setLoading(true);
    try {
      // 1. Ghi nhận biên bản buổi học và điểm danh (UC4.6)
      const res = await progressApi.recordSessionOutcome(session.sessionId, {
        outcome,
        noteContent: noteContent.trim() || undefined,
      });

      if (!res.success) {
        setErrorMessage(res.message || "Không thể ghi nhận biên bản buổi học.");
        setLoading(false);
        return;
      }

      // 2. Nếu có cột mốc nào được gia sư tích chọn đạt được trong buổi học này
      if (selectedMilestoneIds.length > 0 && plan) {
        const todayStr = formatLocalDateToIso(new Date());
        let currentVersion = plan.version;

        for (const milestoneId of selectedMilestoneIds) {
          try {
            const milestoneRes = await progressApi.updateMilestoneStatus(milestoneId, {
              version: currentVersion,
              status: "ACHIEVED",
              achievedDate: todayStr,
            });
            if (milestoneRes.success && milestoneRes.data) {
              currentVersion = milestoneRes.data.version;
            }
          } catch {
            // Không làm ngắt quãng nếu 1 milestone bị lỗi conflict
          }
        }
      }

      onSuccess?.();
      onClose();
    } catch (err: unknown) {
      setErrorMessage(
        err instanceof Error
          ? err.message
          : "Đã xảy ra lỗi khi ghi nhận biên bản. Vui lòng thử lại."
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="outcome-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-stone-900/60 backdrop-blur-xs overflow-y-auto"
    >
      <div className="relative w-full max-w-2xl overflow-hidden rounded-3xl bg-white shadow-2xl border border-stone-200 animate-in fade-in zoom-in-95 duration-200 my-auto">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-stone-100 px-6 py-4 bg-stone-50/50">
          <div className="flex items-center gap-3">
            <div className="grid size-10 place-items-center rounded-2xl bg-emerald-100 text-emerald-800">
              <FileText className="size-5" />
            </div>
            <div>
              <h2 id="outcome-modal-title" className="text-base font-bold text-neutral-900">
                Ghi nhận biên bản buổi học & Điểm danh
              </h2>
              <p className="text-xs text-neutral-500 mt-0.5 flex items-center gap-2">
                <Clock className="size-3 text-neutral-400" />
                {formatDateTime(session.startAt)}
                {session.studentName && (
                  <>
                    <span>•</span>
                    <User className="size-3 text-neutral-400" />
                    <span>Học sinh: {session.studentName}</span>
                  </>
                )}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="grid size-8 place-items-center rounded-full text-neutral-400 hover:bg-stone-100 hover:text-neutral-700 transition-colors cursor-pointer"
            aria-label="Đóng"
          >
            <X className="size-4" />
          </button>
        </div>

        {/* Modal Form */}
        <form onSubmit={handleSubmit} className="flex flex-col max-h-[80vh]">
          <div className="p-6 space-y-6 overflow-y-auto">
            {/* Error Alert */}
            {errorMessage && (
              <div
                role="alert"
                className="flex items-start gap-3 rounded-2xl bg-rose-50 border border-rose-200 p-4 text-xs text-rose-800 font-medium"
              >
                <AlertCircle className="size-4.5 text-rose-600 shrink-0 mt-0.5" />
                <div>
                  <p className="font-bold">Có lỗi xảy ra</p>
                  <p className="mt-0.5">{errorMessage}</p>
                </div>
              </div>
            )}

            {/* Attendance Options */}
            <div className="space-y-2">
              <label className="block text-xs font-bold text-neutral-800 uppercase tracking-wider">
                Kết quả điểm danh học sinh <span className="text-rose-500">*</span>
              </label>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {/* ATTENDED / COMPLETED */}
                <button
                  type="button"
                  onClick={() => setOutcome("COMPLETED")}
                  className={`p-4 rounded-2xl border text-left flex items-start gap-3 transition-all cursor-pointer ${
                    outcome === "COMPLETED"
                      ? "border-emerald-500 bg-emerald-50/60 ring-2 ring-emerald-500/20 shadow-xs"
                      : "border-stone-200 bg-white hover:bg-stone-50"
                  }`}
                >
                  <div
                    className={`mt-0.5 size-5 rounded-full flex items-center justify-center shrink-0 ${
                      outcome === "COMPLETED"
                        ? "bg-emerald-600 text-white"
                        : "border border-stone-300 text-transparent"
                    }`}
                  >
                    <CheckCircle2 className="size-4" />
                  </div>
                  <div>
                    <span className="text-xs font-bold text-neutral-900 block">
                      Có mặt & Hoàn thành (Attended)
                    </span>
                    <span className="text-[11px] text-neutral-500 block mt-0.5 leading-relaxed">
                      Buổi học diễn ra đúng kế hoạch, học sinh tham gia học tập.
                    </span>
                  </div>
                </button>

                {/* ABSENT */}
                <button
                  type="button"
                  onClick={() => setOutcome("ABSENT")}
                  className={`p-4 rounded-2xl border text-left flex items-start gap-3 transition-all cursor-pointer ${
                    outcome === "ABSENT"
                      ? "border-rose-500 bg-rose-50/60 ring-2 ring-rose-500/20 shadow-xs"
                      : "border-stone-200 bg-white hover:bg-stone-50"
                  }`}
                >
                  <div
                    className={`mt-0.5 size-5 rounded-full flex items-center justify-center shrink-0 ${
                      outcome === "ABSENT"
                        ? "bg-rose-600 text-white"
                        : "border border-stone-300 text-transparent"
                    }`}
                  >
                    <XCircle className="size-4" />
                  </div>
                  <div>
                    <span className="text-xs font-bold text-neutral-900 block">
                      Vắng mặt (Absent / No-Show)
                    </span>
                    <span className="text-[11px] text-neutral-500 block mt-0.5 leading-relaxed">
                      Học sinh hoặc gia sư không tham gia buổi học theo lịch.
                    </span>
                  </div>
                </button>
              </div>
            </div>

            {/* Notes & Feedback Textarea */}
            <div className="space-y-1.5">
              <label
                htmlFor="session-note-content"
                className="block text-xs font-bold text-neutral-800 uppercase tracking-wider"
              >
                Nhận xét buổi học & Dặn dò bài tập
              </label>
              <textarea
                id="session-note-content"
                rows={4}
                value={noteContent}
                onChange={(e) => setNoteContent(e.target.value)}
                placeholder="Ví dụ: Học sinh nắm tốt phần lý thuyết hình học, cần luyện thêm kỹ năng vẽ hình phụ. Bài tập về nhà: Bài 3, 4 trang 52 SGK..."
                className="w-full rounded-2xl border border-stone-200 bg-stone-50/50 p-3.5 text-xs text-neutral-900 placeholder:text-neutral-400 focus:bg-white focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20 focus:outline-hidden transition-all"
                maxLength={5000}
              />
              <div className="flex justify-between text-[11px] text-neutral-400 px-1">
                <span>Nội dung sẽ được lưu vào nhật ký tiến độ để phụ huynh và học sinh theo dõi</span>
                <span>{noteContent.length}/5000</span>
              </div>
            </div>

            {/* Milestones Achieved Section (Only if outcome is COMPLETED) */}
            {outcome === "COMPLETED" && (
              <div className="space-y-3 pt-2 border-t border-stone-100">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Award className="size-4 text-amber-600" />
                    <label className="text-xs font-bold text-neutral-800 uppercase tracking-wider">
                      Đánh dấu cột mốc đạt được trong buổi học này
                    </label>
                  </div>
                  {selectedMilestoneIds.length > 0 && (
                    <span className="text-[11px] font-bold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full border border-emerald-200">
                      Đã chọn {selectedMilestoneIds.length} mốc
                    </span>
                  )}
                </div>

                {pendingMilestones.length === 0 ? (
                  <div className="rounded-xl bg-stone-50 p-3.5 text-center text-xs text-neutral-500 border border-stone-200/60">
                    {plan
                      ? "Tất cả các cột mốc trong kế hoạch học tập đã được hoàn thành! 🎉"
                      : "Lớp học này chưa thiết lập Kế hoạch học tập."}
                  </div>
                ) : (
                  <div className="space-y-2 max-h-48 overflow-y-auto pr-1">
                    {pendingMilestones.map((m) => {
                      const isChecked = selectedMilestoneIds.includes(m.milestoneId);
                      return (
                        <label
                          key={m.milestoneId}
                          className={`p-3 rounded-xl border flex items-start gap-3 transition-colors cursor-pointer ${
                            isChecked
                              ? "bg-amber-50/50 border-amber-300 ring-1 ring-amber-400/20"
                              : "bg-white border-stone-200 hover:bg-stone-50"
                          }`}
                        >
                          <input
                            type="checkbox"
                            checked={isChecked}
                            onChange={() => handleToggleMilestone(m.milestoneId)}
                            className="mt-0.5 size-4 rounded-sm border-stone-300 text-amber-600 focus:ring-amber-500 cursor-pointer"
                          />
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center justify-between gap-2">
                              <span className="text-xs font-bold text-neutral-900">
                                {m.title}
                              </span>
                              <span className="inline-flex items-center gap-1 rounded-md bg-stone-100 px-1.5 py-0.5 text-[10px] font-bold text-neutral-700 shrink-0">
                                <Star className="size-2.5 text-amber-500 fill-amber-500" />
                                Trọng số: {m.weight}/5
                              </span>
                            </div>
                            {m.description && (
                              <p className="text-[11px] text-neutral-500 line-clamp-1 mt-0.5">
                                {m.description}
                              </p>
                            )}
                          </div>
                        </label>
                      );
                    })}
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Footer */}
          <div className="flex items-center justify-between border-t border-stone-100 px-6 py-4 bg-stone-50/50">
            <span className="text-xs text-neutral-500">
              Trạng thái: {outcome === "COMPLETED" ? "Đã hoàn thành" : "Vắng mặt"}
            </span>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={onClose}
                className="h-9 px-4 rounded-xl border border-stone-200 bg-white text-xs font-bold text-neutral-700 hover:bg-stone-50 active:scale-95 transition-all cursor-pointer"
              >
                Hủy bỏ
              </button>
              <button
                type="submit"
                disabled={loading}
                className="h-9 px-5 rounded-xl bg-emerald-600 hover:bg-emerald-700 active:scale-95 text-white text-xs font-bold shadow-xs flex items-center gap-1.5 cursor-pointer disabled:opacity-50 transition-all"
              >
                {loading ? (
                  <Loader2 className="size-3.5 animate-spin" />
                ) : (
                  <CheckCircle2 className="size-3.5" />
                )}
                Lưu biên bản buổi học
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
}
