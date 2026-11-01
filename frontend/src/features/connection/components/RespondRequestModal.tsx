"use client";

import React, { useState, useEffect } from "react";
import { AlertCircle, AlertTriangle, CheckCircle2, X, Loader2 } from "lucide-react";
import { connectionApi } from "../api/connectionApi";
import type { TutorInboxItem } from "../types";
import { respondRequestSchema } from "../schemas/connectionSchemas";
import { ApiError } from "@/lib/api";
import { formatGoalType, formatDeadline } from "./StudentPedagogicalProfileModal";

export interface RespondRequestModalProps {
  request: TutorInboxItem | null;
  mode: "APPROVE" | "REJECT";
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (classId?: string) => void;
}

export function RespondRequestModal({
  request,
  mode,
  isOpen,
  onClose,
  onSuccess,
}: RespondRequestModalProps) {
  const [reason, setReason] = useState("");
  const [validationError, setValidationError] = useState<string | null>(null);
  const [apiError, setApiError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setReason("");
      setValidationError(null);
      setApiError(null);
    }
  }, [isOpen, mode]);

  if (!isOpen || !request) return null;

  const isApprove = mode === "APPROVE";

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setValidationError(null);
    setApiError(null);

    // Validate with Zod
    const validationResult = respondRequestSchema.safeParse({
      accept: isApprove,
      reason: reason.trim(),
    });

    if (!validationResult.success) {
      const issue = validationResult.error.issues[0];
      setValidationError(issue?.message || "Dữ liệu không hợp lệ");
      return;
    }

    try {
      setSubmitting(true);
      const res = await connectionApi.respondRequest(request.requestId, {
        accept: isApprove,
        reason: reason.trim() || undefined,
      });

      onSuccess(res?.classId);
      onClose();
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        if (err.code === "SLOT_CONFLICT_WITH_ACTIVE_SCHEDULE") {
          setApiError(
            err.message ||
              "Khung giờ đề xuất bị xung đột với lịch dạy đang hoạt động của bạn. Vui lòng kiểm tra lại!"
          );
        } else {
          setApiError(err.message || "Đã xảy ra lỗi khi xử lý yêu cầu.");
        }
      } else if (err instanceof Error) {
        setApiError(err.message);
      } else {
        setApiError("Không thể kết nối đến máy chủ. Vui lòng thử lại sau.");
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-xs animate-in fade-in duration-200 overflow-y-auto"
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
    >
      <div className="relative w-full max-w-lg rounded-2xl bg-white p-6 shadow-2xl dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 my-auto">
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-zinc-100 dark:border-zinc-800">
          <div className="flex items-center gap-3">
            <div
              className={`flex h-10 w-10 items-center justify-center rounded-full ${
                isApprove
                  ? "bg-emerald-100 text-emerald-600 dark:bg-emerald-950/50 dark:text-emerald-400"
                  : "bg-rose-100 text-rose-600 dark:bg-rose-950/50 dark:text-rose-400"
              }`}
            >
              {isApprove ? (
                <CheckCircle2 className="h-5 w-5" />
              ) : (
                <AlertCircle className="h-5 w-5" />
              )}
            </div>
            <div>
              <h3
                id="modal-title"
                className="text-lg font-semibold text-zinc-900 dark:text-zinc-100"
              >
                {isApprove ? "Chấp thuận yêu cầu kết nối" : "Từ chối yêu cầu kết nối"}
              </h3>
              <p className="text-xs text-zinc-500 dark:text-zinc-400">
                Học sinh:{" "}
                <span className="font-medium text-zinc-700 dark:text-zinc-300">
                  {request.studentDisplayName || "Học sinh"}
                </span>{" "}
                • Môn: {request.subjectName || "Chưa xác định"}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            disabled={submitting}
            className="rounded-lg p-1.5 text-zinc-400 hover:bg-zinc-100 hover:text-zinc-600 dark:hover:bg-zinc-800 dark:hover:text-zinc-300 transition-colors"
            aria-label="Đóng modal"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Body Form */}
        <form onSubmit={handleSubmit} className="mt-4 space-y-4">
          {/* Pedagogical Summary Card */}
          <div className="rounded-xl border border-stone-200 bg-stone-50/70 p-3.5 dark:border-zinc-800 dark:bg-zinc-800/40 text-xs space-y-2">
            <div className="flex items-center justify-between">
              <span className="font-semibold text-neutral-700 dark:text-zinc-200">
                Mục tiêu học tập:
              </span>
              <span className="inline-flex items-center rounded-md bg-indigo-50 px-2 py-0.5 text-xs font-semibold text-indigo-700 border border-indigo-200 dark:bg-indigo-950 dark:border-indigo-800 dark:text-indigo-300">
                {formatGoalType(request.goalType)}
              </span>
            </div>
            {(request.currentLevel != null || request.desiredLevel != null || request.currentGrade || request.targetScore) && (
              <div className="flex items-center justify-between">
                <span className="text-neutral-500 dark:text-zinc-400">Lộ trình năng lực:</span>
                <span className="font-semibold text-neutral-800 dark:text-zinc-100">
                  {request.currentLevel != null ? `Cấp độ ${request.currentLevel}` : request.currentGrade || "Chưa rõ"}
                  {" → "}
                  {request.desiredLevel != null ? `Cấp độ ${request.desiredLevel}` : request.targetScore || "Chưa rõ"}
                </span>
              </div>
            )}
            {request.deadline && (
              <div className="flex items-center justify-between">
                <span className="text-neutral-500 dark:text-zinc-400">Hạn chót mục tiêu:</span>
                <span className="font-semibold text-neutral-800 dark:text-zinc-100">
                  {formatDeadline(request.deadline)}
                </span>
              </div>
            )}
            {request.budgetMax && (
              <div className="flex items-center justify-between">
                <span className="text-neutral-500 dark:text-zinc-400">Ngân sách:</span>
                <span className="font-semibold text-neutral-800 dark:text-zinc-100">
                  {request.budgetMin ? `${request.budgetMin.toLocaleString("vi-VN")} - ` : ""}
                  {request.budgetMax.toLocaleString("vi-VN")} đ/buổi
                </span>
              </div>
            )}
          </div>

          {/* Slot conflict warning banner if approving */}
          {isApprove && request.hasSlotConflict && (
            <div className="rounded-xl border border-amber-200 bg-amber-50/70 p-3.5 text-sm text-amber-900 dark:border-amber-900/50 dark:bg-amber-950/30 dark:text-amber-300 flex items-start gap-3">
              <AlertTriangle className="h-5 w-5 text-amber-600 dark:text-amber-400 shrink-0 mt-0.5" />
              <div>
                <p className="font-medium">Phát hiện xung đột khung giờ!</p>
                <p className="text-xs mt-0.5 text-amber-800 dark:text-amber-400">
                  Một hoặc nhiều khung giờ học sinh mong muốn (
                  {request.conflictingSlots?.join(", ") || "Slot bận"}) đang trùng với lịch
                  dạy khác của bạn. Khi duyệt, lớp học vẫn được tạo nhưng bạn cần chủ động
                  thống nhất đổi lịch với học sinh.
                </p>
              </div>
            </div>
          )}

          {/* 2-Step notice banner if approving */}
          {isApprove && (
            <div className="rounded-xl border border-blue-200 bg-blue-50/70 p-3.5 text-xs text-blue-900 dark:border-blue-900/50 dark:bg-blue-950/30 dark:text-blue-300">
              <p className="font-semibold text-blue-950 dark:text-blue-200">
                Quy trình 2 bước tạo lớp học:
              </p>
              <p className="mt-1">
                Lớp học sẽ được tạo ở trạng thái{" "}
                <span className="font-semibold text-amber-700 dark:text-amber-400">
                  &quot;Chờ thống nhất lịch học&quot;
                </span>
                . Buổi học chỉ chính thức lên lịch (SCHEDULED) sau khi hai bên đề xuất và xác nhận
                buổi học cụ thể.
              </p>
            </div>
          )}

          {/* Error alerts */}
          {apiError && (
            <div className="rounded-xl border border-rose-200 bg-rose-50 p-3.5 text-sm text-rose-800 dark:border-rose-900/50 dark:bg-rose-950/30 dark:text-rose-300 flex items-start gap-2.5">
              <AlertCircle className="h-5 w-5 text-rose-600 dark:text-rose-400 shrink-0 mt-0.5" />
              <div>
                <p className="font-medium">Không thể hoàn tất thao tác</p>
                <p className="text-xs mt-0.5">{apiError}</p>
              </div>
            </div>
          )}

          {/* Reason / Note Input */}
          <div>
            <label
              htmlFor="respond-reason"
              className="block text-sm font-medium text-zinc-700 dark:text-zinc-300"
            >
              {isApprove ? "Lời nhắn cho học sinh (tùy chọn):" : "Lý do từ chối (bắt buộc):"}
            </label>
            <textarea
              id="respond-reason"
              rows={3}
              value={reason}
              onChange={(e) => {
                setReason(e.target.value);
                if (validationError) setValidationError(null);
              }}
              placeholder={
                isApprove
                  ? "Ví dụ: Thầy chào em, thầy đã nhận lớp và sẽ liên hệ để thống nhất lịch học..."
                  : "Ví dụ: Hiện tại lịch của thầy đã kín vào các khung giờ này, rất tiếc chưa thể nhận lớp..."
              }
              maxLength={300}
              disabled={submitting}
              className={`mt-1.5 w-full rounded-xl border p-3 text-sm transition-colors focus:outline-hidden ${
                validationError
                  ? "border-rose-300 bg-rose-50/30 text-rose-900 focus:border-rose-500 dark:border-rose-800 dark:bg-rose-950/20 dark:text-rose-200"
                  : "border-zinc-300 bg-white text-zinc-900 focus:border-blue-600 focus:ring-1 focus:ring-blue-600 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
              }`}
            />
            <div className="flex items-center justify-between mt-1 text-xs text-zinc-500 dark:text-zinc-400">
              {validationError ? (
                <span className="text-rose-600 dark:text-rose-400 font-medium">
                  {validationError}
                </span>
              ) : (
                <span>{isApprove ? "Tối đa 300 ký tự" : "Tối thiểu 5 ký tự"}</span>
              )}
              <span>{reason.length}/300</span>
            </div>
          </div>

          {/* Actions */}
          <div className="flex items-center justify-end gap-3 pt-3 border-t border-zinc-100 dark:border-zinc-800">
            <button
              type="button"
              onClick={onClose}
              disabled={submitting}
              className="rounded-xl px-4 py-2.5 text-sm font-medium text-zinc-700 hover:bg-zinc-100 dark:text-zinc-300 dark:hover:bg-zinc-800 transition-colors"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={submitting}
              className={`inline-flex items-center gap-2 rounded-xl px-5 py-2.5 text-sm font-medium text-white shadow-xs transition-all ${
                isApprove
                  ? "bg-emerald-600 hover:bg-emerald-700 active:scale-95 disabled:bg-emerald-400"
                  : "bg-rose-600 hover:bg-rose-700 active:scale-95 disabled:bg-rose-400"
              }`}
            >
              {submitting ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Đang xử lý...
                </>
              ) : isApprove ? (
                "Xác nhận tạo lớp"
              ) : (
                "Xác nhận từ chối"
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
