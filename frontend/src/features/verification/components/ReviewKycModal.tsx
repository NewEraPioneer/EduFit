"use client";

import React, { useState, useEffect } from "react";
import { verificationService, PendingQueueItem, VerificationRequestDetail } from "@/services/verification";
import { useToast } from "@/shared/ui/Toast";
import type { TutorType } from "@/services/profile";
import { TUTOR_TYPE_LABELS } from "@/features/learning-needs/catalogRules";
import {
  ShieldCheck,
  CheckCircle2,
  XCircle,
  X,
  FileText,
  Loader2,
  Calendar,
  ExternalLink,
} from "lucide-react";

/**
 * =========================================================================
 * SPRING BOOT API CONNECTION:
 * =========================================================================
 * Endpoint: POST /api/v1/admin/verifications/{id}/review
 * Controller: vn.edufit.verification.web.AdminVerificationController#review
 * Security: @PreAuthorize("hasRole('ADMIN')")
 * Request Body: ReviewVerificationRequest { action: 'APPROVE'|'REJECT', reason }
 * Business Rule: BR-06 - Từ chối bắt buộc phải có lý do giải thích
 * =========================================================================
 */

const CREDENTIAL_TYPE_LABELS: Record<string, string> = {
  DEGREE: "Bằng cấp",
  CERTIFICATE: "Chứng chỉ",
  TEACHING_LICENCE: "Chứng chỉ sư phạm",
  STUDENT_CARD: "Thẻ sinh viên",
  OTHER: "Giấy tờ khác / CCCD",
  IDENTITY_CARD: "Giấy tờ khác / CCCD",
};

interface ReviewKycModalProps {
  item: PendingQueueItem | null;
  isOpen: boolean;
  onClose: () => void;
  onReviewed: () => void;
}

export function ReviewKycModal({ item, isOpen, onClose, onReviewed }: ReviewKycModalProps) {
  const { showToast } = useToast();

  const [decision, setDecision] = useState<"APPROVED" | "REJECTED">("APPROVED");
  const [rejectionReason, setRejectionReason] = useState("");
  const [confirmedTutorType, setConfirmedTutorType] = useState<TutorType | "">("");
  const [submitting, setSubmitting] = useState(false);
  const [detail, setDetail] = useState<VerificationRequestDetail | null>(null);
  const [loadingDetail, setLoadingDetail] = useState(false);

  const currentRequestId = isOpen && item ? item.requestId : null;
  const [prevRequestId, setPrevRequestId] = useState<string | null>(null);

  if (currentRequestId !== prevRequestId) {
    setPrevRequestId(currentRequestId);
    setDetail(null);
    setLoadingDetail(Boolean(currentRequestId));
    setRejectionReason("");
    setDecision("APPROVED");
  }

  useEffect(() => {
    if (!isOpen || !item) {
      return;
    }
    let active = true;
    verificationService.getVerificationDetail(item.requestId)
      .then((data) => {
        if (active) setDetail(data);
      })
      .catch((err) => {
        console.error("Lỗi khi tải chi tiết hồ sơ KYC:", err);
      })
      .finally(() => {
        if (active) setLoadingDetail(false);
      });
    return () => {
      active = false;
    };
  }, [isOpen, item]);

  if (!isOpen || !item) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (decision === "REJECTED" && !rejectionReason.trim()) {
      showToast("Nhập lý do từ chối hồ sơ.", "error");
      return;
    }

    setSubmitting(true);
    try {
      await verificationService.reviewVerification(
        item.requestId,
        decision,
        decision === "REJECTED" ? rejectionReason.trim() : undefined,
        decision === "APPROVED" ? confirmedTutorType || null : null
      );

      showToast(
        decision === "APPROVED"
          ? "Phê duyệt hồ sơ gia sư thành công!"
          : "Đã từ chối hồ sơ kèm lý do phản hồi!",
        "success"
      );
      onReviewed();
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Thao tác thẩm định thất bại";
      showToast(msg, "error");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4 bg-night/60 backdrop-blur-xs animate-in fade-in duration-150 overflow-y-auto">
      <div className="w-full max-w-dialog bg-surface rounded-3xl border border-line shadow-2xl p-6 sm:p-7 space-y-5 animate-in zoom-in-95 duration-150 my-auto">
        {/* Modal Header */}
        <div className="flex items-start justify-between gap-3 pb-4 border-b border-line">
          <div className="flex items-center gap-3">
            <div className="size-10 rounded-2xl bg-brand-soft text-brand-strong flex items-center justify-center shrink-0">
              <ShieldCheck className="size-5" />
            </div>
            <div>
              <h3 className="font-heading text-lg font-bold text-ink">
                Thẩm định hồ sơ xác minh KYC
              </h3>
              <p className="text-xs text-muted">Mã yêu cầu: {item.requestId}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-xl hover:bg-sunken text-muted hover:text-ink-2 transition-colors"
          >
            <X className="size-4" />
          </button>
        </div>

        {/* Thông tin hồ sơ */}
        <div className="p-4 bg-paper rounded-2xl border border-line/80 space-y-2 text-xs">
          <div className="flex justify-between items-center">
            <span className="text-muted">Gia sư (Tutor ID):</span>
            <span className="font-mono font-medium text-ink">{item.tutorId}</span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-muted">Thời gian nộp:</span>
            <span className="font-medium text-ink-2 flex items-center gap-1">
              <Calendar className="size-3.5 text-muted" />
              {new Date(item.submittedAt).toLocaleString("vi-VN")}
            </span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-muted">Trạng thái hiện tại:</span>
            <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-action-soft text-action-strong">
              {item.status}
            </span>
          </div>
        </div>

        {/* Chi tiết bằng cấp & tài liệu tải lên */}
        {loadingDetail ? (
          <div className="flex items-center justify-center p-4 gap-2 text-xs text-muted bg-paper rounded-2xl border border-line/60">
            <Loader2 className="size-4 animate-spin text-brand-strong" />
            <span>Đang tải thông tin bằng cấp & tệp đính kèm...</span>
          </div>
        ) : detail && detail.credentials && detail.credentials.length > 0 ? (
          <div className="space-y-2">
            <h4 className="text-xs font-medium text-ink-2 flex items-center gap-1.5">
              <FileText className="size-3.5 text-brand-strong" />
              <span>Văn bằng và chứng chỉ đã nộp ({detail.credentials.length}):</span>
            </h4>
            <div className="space-y-2 max-h-48 overflow-y-auto pr-1">
              {detail.credentials.map((cred, idx) => (
                <div key={cred.credentialId || idx} className="p-3 bg-paper rounded-2xl border border-line/80 text-xs space-y-1.5">
                  <div className="flex items-center justify-between">
                    <span className="font-medium text-ink">{cred.institution}</span>
                    <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-brand-soft text-brand-strong">
                      {CREDENTIAL_TYPE_LABELS[cred.type] || cred.type} {cred.year ? `(${cred.year})` : ""}
                    </span>
                  </div>
                  {cred.note && <p className="text-xs text-muted italic">"{cred.note}"</p>}
                  {cred.offeringId && (
                    <p className="text-xs font-medium text-action-strong">
                      Minh chứng cho gói dạy có mục tiêu cần duyệt (HSG, chứng chỉ). Phê duyệt sẽ xác nhận mục tiêu đó.
                    </p>
                  )}
                  {cred.files && cred.files.length > 0 && (
                    <div className="flex flex-wrap gap-2 pt-1.5 border-t border-line/60">
                      {cred.files.map((f) => (
                        <a
                          key={f.fileId}
                          href={verificationService.getCredentialFileUrl(f.fileId)}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="inline-flex items-center gap-1.5 px-2.5 py-1 bg-surface hover:bg-sunken text-ink-2 rounded-xl border border-line text-xs font-medium transition-colors shadow-2xs"
                        >
                          <FileText className="size-3 text-brand-strong" />
                          <span className="truncate max-w-[140px]">{f.fileName}</span>
                          <span className="text-xs text-muted">({Math.round(f.sizeBytes / 1024)} KB)</span>
                          <ExternalLink className="size-2.5 text-muted" />
                        </a>
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        ) : null}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-ink-2 mb-2">
              Quyết định thẩm định (Decision):
            </label>
            <div className="grid grid-cols-2 gap-3">
              <button
                type="button"
                onClick={() => setDecision("APPROVED")}
                className={`p-3 rounded-2xl border flex items-center justify-center gap-2 text-xs font-medium transition-all ${
                  decision === "APPROVED"
                    ? "bg-success-soft text-success-strong border-success ring-2 ring-success/20"
                    : "bg-surface text-ink-2 border-line hover:border-line-strong"
                }`}
              >
                <CheckCircle2 className="size-4 text-success-strong" />
                <span>PHÊ DUYỆT (Approve)</span>
              </button>

              <button
                type="button"
                onClick={() => setDecision("REJECTED")}
                className={`p-3 rounded-2xl border flex items-center justify-center gap-2 text-xs font-medium transition-all ${
                  decision === "REJECTED"
                    ? "bg-danger-soft text-danger-strong border-danger ring-2 ring-danger/20"
                    : "bg-surface text-ink-2 border-line hover:border-line-strong"
                }`}
              >
                <XCircle className="size-4 text-danger-strong" />
                <span>TỪ CHỐI (Reject)</span>
              </button>
            </div>
          </div>

          {decision === "APPROVED" && (
            <div>
              <label htmlFor="confirmed-tutor-type" className="block text-xs font-medium text-ink-2 mb-1.5">
                Xác nhận loại gia sư theo giấy tờ (không bắt buộc)
              </label>
              <select
                id="confirmed-tutor-type"
                value={confirmedTutorType}
                onChange={(e) => setConfirmedTutorType(e.target.value as TutorType | "")}
                className="w-full px-3 py-2.5 text-xs bg-paper border border-line rounded-xl focus:bg-surface focus:outline-hidden focus:border-success"
              >
                <option value="">Chưa đủ giấy tờ để xác nhận</option>
                {(Object.keys(TUTOR_TYPE_LABELS) as TutorType[]).map((type) => (
                  <option key={type} value={type}>{TUTOR_TYPE_LABELS[type]}</option>
                ))}
              </select>
              <p className="mt-1 text-xs text-muted">
                Khi xác nhận, hồ sơ gia sư hiển thị loại này kèm dấu đã xác minh.
              </p>
            </div>
          )}

          {decision === "REJECTED" && (
            <div className="space-y-1.5 animate-in fade-in duration-150">
              <label className="block text-xs font-medium text-danger-strong">
                Lý do từ chối hồ sơ <span className="text-danger-strong">*</span>
              </label>
              <textarea
                rows={3}
                required
                value={rejectionReason}
                onChange={(e) => setRejectionReason(e.target.value)}
                placeholder="VD: Ảnh bằng tốt nghiệp bị mờ, không nhìn rõ số hiệu văn bằng..."
                className="w-full p-3 text-xs bg-paper border border-danger-line rounded-xl focus:bg-surface focus:outline-hidden focus:border-danger focus:ring-2 focus:ring-danger/20 text-ink"
              />
            </div>
          )}

          <div className="pt-2 flex items-center justify-end gap-3 border-t border-line">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2.5 rounded-xl border border-line hover:bg-paper text-xs font-medium text-ink-2 transition-colors"
            >
              Hủy bỏ
            </button>
            <button
              type="submit"
              disabled={submitting}
              className={`px-5 py-2.5 rounded-xl text-white text-xs font-medium shadow-xs transition-all flex items-center gap-2 disabled:opacity-50 cursor-pointer ${
                decision === "APPROVED"
                  ? "bg-success hover:bg-success-deep"
                  : "bg-danger hover:bg-danger-deep"
              }`}
            >
              {submitting && <Loader2 className="size-4 animate-spin" />}
              <span>{submitting ? "Đang xử lý..." : "Xác nhận quyết định"}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
