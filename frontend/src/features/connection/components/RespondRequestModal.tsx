"use client";

import React, { useState, useEffect } from "react";
import { AlertCircle, AlertTriangle, CheckCircle2, X, Loader2 } from "lucide-react";
import { connectionApi } from "../api/connectionApi";
import type { TutorInboxItem } from "../types";
import { respondRequestSchema } from "../schemas/connectionSchemas";
import { ApiError } from "@/lib/api";
import { inboxNeedDetails, inboxNeedPlace, inboxNeedTitle } from "./StudentPedagogicalProfileModal";
import { FitChecks } from "@/features/discovery/components/FitChecks";
import { formatMoney } from "@/features/learning-needs/catalogRules";

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
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/50 p-4 backdrop-blur-xs animate-in fade-in duration-200 overflow-y-auto"
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
    >
      <div className="relative w-full max-w-dialog rounded-2xl bg-surface p-6 shadow-2xl border border-line my-auto">
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-line">
          <div className="flex items-center gap-3">
            <div
              className={`flex h-10 w-10 items-center justify-center rounded-full ${
                isApprove
                  ? "bg-success-soft text-success-strong"
                  : "bg-danger-soft text-danger-strong"
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
                className="text-lg font-bold text-ink"
              >
                {isApprove ? "Chấp thuận yêu cầu kết nối" : "Từ chối yêu cầu kết nối"}
              </h3>
              <p className="text-xs text-muted">
                Học sinh:{" "}
                <span className="font-medium text-ink-2">
                  {request.studentDisplayName || "Học sinh"}
                </span>{" "}
                • Môn: {request.need.subjectName || "Chưa xác định"}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            disabled={submitting}
            className="rounded-lg p-1.5 text-muted hover:bg-sunken hover:text-ink-2 transition-colors"
            aria-label="Đóng modal"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Body Form */}
        <form onSubmit={handleSubmit} className="mt-4 space-y-4">
          {/* Tóm tắt nhu cầu học */}
          <div className="rounded-xl border border-line bg-paper/70 p-3.5 text-xs space-y-2">
            <p className="font-medium text-ink break-words">{inboxNeedTitle(request.need)}</p>
            {inboxNeedDetails(request.need).length > 0 && (
              <p className="text-ink-2 break-words">{inboxNeedDetails(request.need).join(" · ")}</p>
            )}
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="text-muted">Hình thức:</span>
              <span className="font-medium text-ink break-words">{inboxNeedPlace(request.need)}</span>
            </div>
            {request.need.budgetMaxPerSession != null && (
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="text-muted">Ngân sách tối đa:</span>
                <span className="font-medium text-ink">
                  {formatMoney(request.need.budgetMaxPerSession)}/buổi
                </span>
              </div>
            )}
            {request.fitChecks.length > 0 && <FitChecks checks={request.fitChecks} />}
          </div>

          {/* Slot conflict warning banner if approving */}
          {isApprove && request.hasSlotConflict && (
            <div className="rounded-xl border border-action-line bg-action-soft/70 p-3.5 text-sm text-action-strong flex items-start gap-3">
              <AlertTriangle className="h-5 w-5 text-action-strong shrink-0 mt-0.5" />
              <div>
                <p className="font-medium">Phát hiện xung đột khung giờ!</p>
                <p className="text-xs mt-0.5 text-action-strong">
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
            <div className="rounded-xl border border-brand-line bg-brand-soft/70 p-3.5 text-xs text-brand-strong">
              <p className="font-medium text-brand-strong">
                Quy trình 2 bước tạo lớp học:
              </p>
              <p className="mt-1">
                Lớp học sẽ được tạo ở trạng thái{" "}
                <span className="font-medium text-action-strong">
                  &quot;Chờ thống nhất lịch học&quot;
                </span>
                . Buổi học chỉ chính thức lên lịch (SCHEDULED) sau khi hai bên đề xuất và xác nhận
                buổi học cụ thể.
              </p>
            </div>
          )}

          {/* Error alerts */}
          {apiError && (
            <div className="rounded-xl border border-danger-line bg-danger-soft p-3.5 text-sm text-danger-strong flex items-start gap-2.5">
              <AlertCircle className="h-5 w-5 text-danger-strong shrink-0 mt-0.5" />
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
              className="block text-sm font-medium text-ink-2"
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
                  ? "border-danger-line bg-danger-soft/30 text-danger-strong focus:border-danger"
                  : "border-line-strong bg-surface text-ink focus:border-brand focus:ring-1 focus:ring-brand"
              }`}
            />
            <div className="flex items-center justify-between mt-1 text-xs text-muted">
              {validationError ? (
                <span className="text-danger-strong font-medium">
                  {validationError}
                </span>
              ) : (
                <span>{isApprove ? "Tối đa 300 ký tự" : "Tối thiểu 5 ký tự"}</span>
              )}
              <span>{reason.length}/300</span>
            </div>
          </div>

          {/* Actions */}
          <div className="flex items-center justify-end gap-3 pt-3 border-t border-line">
            <button
              type="button"
              onClick={onClose}
              disabled={submitting}
              className="rounded-xl px-4 py-2.5 text-sm font-medium text-ink-2 hover:bg-sunken transition-colors"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={submitting}
              className={`inline-flex items-center gap-2 rounded-xl px-5 py-2.5 text-sm font-medium text-white shadow-xs transition-all ${
                isApprove
                  ? "bg-success hover:bg-success-deep active:scale-95 disabled:bg-success"
                  : "bg-danger hover:bg-danger-deep active:scale-95 disabled:bg-danger"
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
