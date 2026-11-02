"use client";

import React, { useState, useEffect } from "react";
import { AlertTriangle, X, Loader2, StopCircle, CheckCircle2, Info } from "lucide-react";
import { connectionApi } from "../api/connectionApi";
import type { TutoringClass } from "../types";

export interface EndClassModalProps {
  classItem: TutoringClass | null;
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export function EndClassModal({
  classItem,
  isOpen,
  onClose,
  onSuccess,
}: EndClassModalProps) {
  const [reason, setReason] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen) {
      setReason("");
      setError(null);
      setLoading(false);
    }
  }, [isOpen]);

  if (!isOpen || !classItem) return null;

  const totalCount = classItem.totalCount ?? classItem.sessionCount ?? 0;
  const completedCount = classItem.completedCount ?? 0;
  const scheduledCount = classItem.scheduledCount;
  // Fail-closed: không biết số buổi đã lên lịch thì không cho kết thúc lớp
  const isScheduleUnknown = scheduledCount === undefined;
  const isOngoing = !isScheduleUnknown && scheduledCount > 0;
  const isBlocked = isOngoing || isScheduleUnknown;
  const isBatchCompleted =
    scheduledCount === 0 &&
    totalCount > 0 &&
    completedCount >= totalCount;
  const isUnscheduled = totalCount === 0 && scheduledCount === 0;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (isScheduleUnknown) return;

    if (isOngoing) {
      setError(
        `Không thể kết thúc lớp học khi vẫn còn ${scheduledCount} buổi học đã lên lịch. Vui lòng hoàn thành hoặc hủy từng buổi học trước.`
      );
      return;
    }

    if (!reason.trim()) {
      setError("Vui lòng nhập lý do kết thúc lớp học.");
      return;
    }

    if (reason.trim().length > 300) {
      setError("Lý do kết thúc lớp học không được vượt quá 300 ký tự.");
      return;
    }

    try {
      setLoading(true);
      await connectionApi.endClass(classItem.classId, reason.trim());
      onSuccess();
      onClose();
    } catch (err: unknown) {
      setError(
        err instanceof Error
          ? err.message
          : "Không thể kết thúc lớp học. Vui lòng thử lại sau."
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="end-class-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-stone-900/60 backdrop-blur-xs animate-in fade-in duration-200 overflow-y-auto"
    >
      <div className="relative w-full max-w-md overflow-hidden rounded-3xl bg-white shadow-2xl border border-stone-200 dark:bg-zinc-900 dark:border-zinc-800 my-auto">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-stone-100 dark:border-zinc-800 px-6 py-4">
          <div className="flex items-center gap-2.5">
            <div
              className={`grid size-10 place-items-center rounded-xl ${
                isBatchCompleted
                  ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-400"
                  : isOngoing
                  ? "bg-amber-100 text-amber-700 dark:bg-amber-950/60 dark:text-amber-400"
                  : "bg-rose-100 text-rose-700 dark:bg-rose-950/60 dark:text-rose-400"
              }`}
            >
              {isBatchCompleted ? (
                <CheckCircle2 className="size-5" />
              ) : isOngoing ? (
                <AlertTriangle className="size-5" />
              ) : (
                <StopCircle className="size-5" />
              )}
            </div>
            <div>
              <h2
                id="end-class-modal-title"
                className="text-base font-bold text-neutral-900 dark:text-zinc-100"
              >
                {isOngoing
                  ? "Không thể dừng lớp giữa chừng"
                  : isBatchCompleted
                  ? "Xác nhận hoàn tất khóa học"
                  : "Kết thúc lớp học"}
              </h2>
              <p className="text-xs text-neutral-500 dark:text-zinc-400">
                {classItem.subjectName
                  ? `${classItem.subjectName} - ${classItem.educationLevelName || "Cấp học"}`
                  : "Lớp học"}{" "}
                {classItem.studentDisplayName
                  ? `• Học sinh: ${classItem.studentDisplayName}`
                  : ""}{" "}
                {classItem.tutorDisplayName
                  ? `• Gia sư: ${classItem.tutorDisplayName}`
                  : ""}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={loading}
            className="grid size-8 place-items-center rounded-full text-neutral-400 hover:bg-stone-100 hover:text-neutral-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200 transition-colors cursor-pointer"
            aria-label="Đóng"
          >
            <X className="size-4" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {/* Ongoing Alert: Block early unilateral termination */}
          {isOngoing && (
            <div
              role="alert"
              className="flex items-start gap-3 rounded-2xl bg-amber-50 p-4 border border-amber-200 text-amber-900 text-xs dark:bg-amber-950/30 dark:border-amber-900/50 dark:text-amber-300"
            >
              <AlertTriangle className="size-4 shrink-0 mt-0.5 text-amber-600 dark:text-amber-400" />
              <div className="space-y-1.5">
                <p className="font-bold">Lớp học vẫn còn {scheduledCount} buổi học đã lên lịch:</p>
                <p className="leading-relaxed">
                  Để đảm bảo tính liên tục của việc học và bảo vệ quyền lợi tài chính của cả hai bên, hệ thống không cho phép đơn phương kết thúc lớp học khi còn các buổi học chưa hoàn thành.
                </p>
                <ul className="list-disc pl-4 space-y-1 text-[11px] text-amber-800 dark:text-amber-300">
                  <li>Vui lòng hoàn thành các buổi học đã lên lịch trước khi kết thúc lớp.</li>
                  <li>Nếu có sự cố đột xuất, bạn có thể <strong>Đề xuất đổi lịch</strong> hoặc hủy từng buổi học cụ thể kèm lý do.</li>
                </ul>
              </div>
            </div>
          )}

          {isScheduleUnknown && (
            <div
              role="alert"
              className="flex items-start gap-3 rounded-2xl bg-amber-50 p-4 border border-amber-200 text-amber-900 text-xs dark:bg-amber-950/30 dark:border-amber-900/50 dark:text-amber-300"
            >
              <AlertTriangle className="size-4 shrink-0 mt-0.5 text-amber-600 dark:text-amber-400" />
              <p className="leading-relaxed">
                Chưa xác định được lịch học của lớp này nên chưa thể kết thúc lớp. Vui lòng tải lại trang và thử lại.
              </p>
            </div>
          )}

          {/* Batch Completed Information */}
          {isBatchCompleted && (
            <div className="rounded-2xl border border-emerald-200 bg-emerald-50/70 p-4 text-xs text-emerald-900 dark:border-emerald-900/50 dark:bg-emerald-950/30 dark:text-emerald-300 space-y-1.5">
              <p className="font-bold flex items-center gap-1.5">
                <CheckCircle2 className="size-4 text-emerald-600 dark:text-emerald-400" />
                Hoàn tất chu kỳ học tập ({completedCount}/{totalCount} buổi):
              </p>
              <p className="leading-relaxed">
                Hai bên đã hoàn thành đầy đủ tất cả các buổi học trong đợt học này. Bạn có thể xác nhận kết thúc để đóng khóa học hoặc kích hoạt quyền đánh giá.
              </p>
              <ul className="list-disc pl-4 space-y-1 text-[11px] text-emerald-800 dark:text-emerald-300">
                <li>Lớp học sẽ chuyển sang trạng thái <strong>Đã hoàn thành</strong>.</li>
                <li>Học sinh và phụ huynh có thể viết đánh giá & nhận xét chất lượng giảng dạy của gia sư.</li>
                <li>Nếu muốn học tiếp, bạn có thể bấm <strong>Đề xuất tiếp đợt học mới</strong> thay vì kết thúc lớp.</li>
              </ul>
            </div>
          )}

          {/* Standard / Unscheduled Conditions & Cascade Warning */}
          {!isBlocked && !isBatchCompleted && (
            <>
              <div className="rounded-2xl border border-stone-200 bg-stone-50 p-4 text-xs text-neutral-700 dark:border-zinc-800 dark:bg-zinc-800/40 dark:text-zinc-300 space-y-1.5">
                <p className="font-bold text-neutral-900 dark:text-zinc-100 flex items-center gap-1.5">
                  <span className="size-2 rounded-full bg-rose-500" />
                  Điều kiện kết thúc lớp học:
                </p>
                <ul className="list-disc pl-4 space-y-1 text-[11px] text-neutral-600 dark:text-zinc-400">
                  <li>Chỉ <strong>Gia sư</strong> hoặc <strong>Học sinh / Phụ huynh</strong> của lớp học mới có quyền kết thúc.</li>
                  <li>Lớp học phải đang ở trạng thái Hoạt động (<strong>ACTIVE</strong>).</li>
                  <li>Bắt buộc nhập lý do kết thúc (tối đa 300 ký tự).</li>
                </ul>
              </div>

              <div
                role="alert"
                className="flex items-start gap-3 rounded-2xl bg-amber-50 p-4 border border-amber-200 text-amber-900 text-xs dark:bg-amber-950/30 dark:border-amber-900/50 dark:text-amber-300"
              >
                <AlertTriangle className="size-4 shrink-0 mt-0.5 text-amber-600 dark:text-amber-400" />
                <div>
                  <p className="font-bold">Lưu ý quan trọng khi kết thúc lớp:</p>
                  <p className="mt-1 leading-relaxed">
                    Tất cả các buổi học trong tương lai (ở trạng thái Đã xếp lịch hoặc Đang đề xuất) của lớp học này sẽ <strong>tự động bị hủy</strong> để giải phóng lịch dạy và học cho cả hai bên.
                  </p>
                </div>
              </div>
            </>
          )}

          {error && (
            <div className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-xs text-rose-800 dark:border-rose-900/50 dark:bg-rose-950/30 dark:text-rose-300">
              {error}
            </div>
          )}

          {!isBlocked && (
            <div>
              <label
                htmlFor="end-class-reason"
                className="block text-xs font-bold text-neutral-700 dark:text-zinc-300 mb-1"
              >
                Lý do kết thúc lớp học <span className="text-rose-600">*</span>
              </label>
              <textarea
                id="end-class-reason"
                rows={3}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder={
                  isBatchCompleted
                    ? "Ví dụ: Đã hoàn thành toàn bộ chương trình và đạt mục tiêu đề ra..."
                    : isUnscheduled
                    ? "Ví dụ: Không thống nhất được thời gian học phù hợp..."
                    : "Ví dụ: Cả hai bên thống nhất kết thúc khóa học..."
                }
                maxLength={300}
                disabled={loading}
                className="w-full rounded-xl border border-stone-200 bg-white p-3 text-xs text-neutral-800 focus:border-rose-500 focus:outline-hidden dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
              />
              <div className="flex justify-end mt-1 text-[11px] text-neutral-400 dark:text-zinc-500">
                <span>{reason.length}/300</span>
              </div>
            </div>
          )}

          {/* Action buttons */}
          <div className="flex items-center justify-end gap-2 pt-3 border-t border-stone-100 dark:border-zinc-800">
            <button
              type="button"
              onClick={onClose}
              disabled={loading}
              className="h-9 px-4 rounded-xl border border-stone-200 bg-white text-xs font-bold text-neutral-700 hover:bg-stone-50 disabled:opacity-50 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-300 cursor-pointer"
            >
              {isBlocked ? "Đã hiểu & Đóng" : "Hủy"}
            </button>
            {!isBlocked && (
              <button
                type="submit"
                disabled={loading}
                className={`h-9 px-4 rounded-xl text-white text-xs font-bold shadow-xs flex items-center gap-1.5 disabled:opacity-50 cursor-pointer ${
                  isBatchCompleted
                    ? "bg-emerald-600 hover:bg-emerald-700"
                    : "bg-rose-600 hover:bg-rose-700"
                }`}
              >
                {loading ? (
                  <>
                    <Loader2 className="size-3.5 animate-spin" />
                    Đang xử lý...
                  </>
                ) : (
                  "Xác nhận kết thúc lớp"
                )}
              </button>
            )}
          </div>
        </form>
      </div>
    </div>
  );
}
