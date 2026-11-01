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
      showToast("Vui lòng nhập lý do từ chối hồ sơ (Quy tắc BR-06)!", "error");
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
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-neutral-950/60 backdrop-blur-xs animate-in fade-in duration-150 overflow-y-auto">
      <div className="w-full max-w-lg bg-white rounded-3xl border border-stone-200 shadow-2xl p-6 sm:p-7 space-y-5 animate-in zoom-in-95 duration-150 my-auto">
        {/* Modal Header */}
        <div className="flex items-start justify-between gap-3 pb-4 border-b border-stone-100">
          <div className="flex items-center gap-3">
            <div className="size-10 rounded-2xl bg-purple-100 text-purple-700 flex items-center justify-center shrink-0">
              <ShieldCheck className="size-5" />
            </div>
            <div>
              <h3 className="font-heading text-lg font-bold text-neutral-900">
                Thẩm Định Hồ Sơ Xác Minh KYC
              </h3>
              <p className="text-xs text-neutral-500">Mã yêu cầu: {item.requestId}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-xl hover:bg-stone-100 text-neutral-400 hover:text-neutral-700 transition-colors"
          >
            <X className="size-4" />
          </button>
        </div>

        {/* Thông tin hồ sơ */}
        <div className="p-4 bg-stone-50 rounded-2xl border border-stone-200/80 space-y-2 text-xs">
          <div className="flex justify-between items-center">
            <span className="text-neutral-500">Gia sư (Tutor ID):</span>
            <span className="font-mono font-bold text-neutral-800">{item.tutorId}</span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-neutral-500">Thời gian nộp:</span>
            <span className="font-medium text-neutral-700 flex items-center gap-1">
              <Calendar className="size-3.5 text-neutral-400" />
              {new Date(item.submittedAt).toLocaleString("vi-VN")}
            </span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-neutral-500">Trạng thái hiện tại:</span>
            <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-100 text-amber-900">
              {item.status}
            </span>
          </div>
        </div>

        {/* Chi tiết bằng cấp & tài liệu tải lên */}
        {loadingDetail ? (
          <div className="flex items-center justify-center p-4 gap-2 text-xs text-neutral-500 bg-stone-50 rounded-2xl border border-stone-200/60">
            <Loader2 className="size-4 animate-spin text-purple-600" />
            <span>Đang tải thông tin bằng cấp & tệp đính kèm...</span>
          </div>
        ) : detail && detail.credentials && detail.credentials.length > 0 ? (
          <div className="space-y-2">
            <h4 className="text-xs font-bold text-neutral-700 flex items-center gap-1.5">
              <FileText className="size-3.5 text-purple-600" />
              <span>Văn bằng & Chứng chỉ đã nộp ({detail.credentials.length}):</span>
            </h4>
            <div className="space-y-2 max-h-48 overflow-y-auto pr-1">
              {detail.credentials.map((cred, idx) => (
                <div key={cred.credentialId || idx} className="p-3 bg-stone-50 rounded-2xl border border-stone-200/80 text-xs space-y-1.5">
                  <div className="flex items-center justify-between">
                    <span className="font-bold text-neutral-900">{cred.institution}</span>
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-purple-100 text-purple-800">
                      {CREDENTIAL_TYPE_LABELS[cred.type] || cred.type} {cred.year ? `(${cred.year})` : ""}
                    </span>
                  </div>
                  {cred.note && <p className="text-[11px] text-neutral-500 italic">"{cred.note}"</p>}
                  {cred.offeringId && (
                    <p className="text-[11px] font-semibold text-amber-700">
                      Minh chứng cho gói dạy có mục tiêu cần duyệt (HSG, chứng chỉ). Phê duyệt sẽ xác nhận mục tiêu đó.
                    </p>
                  )}
                  {cred.files && cred.files.length > 0 && (
                    <div className="flex flex-wrap gap-2 pt-1.5 border-t border-stone-200/60">
                      {cred.files.map((f) => (
                        <a
                          key={f.fileId}
                          href={verificationService.getCredentialFileUrl(f.fileId)}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="inline-flex items-center gap-1.5 px-2.5 py-1 bg-white hover:bg-stone-100 text-neutral-700 rounded-xl border border-stone-200 text-[11px] font-medium transition-colors shadow-2xs"
                        >
                          <FileText className="size-3 text-purple-600" />
                          <span className="truncate max-w-[140px]">{f.fileName}</span>
                          <span className="text-[10px] text-neutral-400">({Math.round(f.sizeBytes / 1024)} KB)</span>
                          <ExternalLink className="size-2.5 text-neutral-400" />
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
            <label className="block text-xs font-bold text-neutral-700 mb-2">
              Quyết định thẩm định (Decision):
            </label>
            <div className="grid grid-cols-2 gap-3">
              <button
                type="button"
                onClick={() => setDecision("APPROVED")}
                className={`p-3 rounded-2xl border flex items-center justify-center gap-2 text-xs font-bold transition-all ${
                  decision === "APPROVED"
                    ? "bg-emerald-50 text-emerald-800 border-emerald-500 ring-2 ring-emerald-500/20"
                    : "bg-white text-neutral-600 border-stone-200 hover:border-stone-300"
                }`}
              >
                <CheckCircle2 className="size-4 text-emerald-600" />
                <span>PHÊ DUYỆT (Approve)</span>
              </button>

              <button
                type="button"
                onClick={() => setDecision("REJECTED")}
                className={`p-3 rounded-2xl border flex items-center justify-center gap-2 text-xs font-bold transition-all ${
                  decision === "REJECTED"
                    ? "bg-rose-50 text-rose-800 border-rose-500 ring-2 ring-rose-500/20"
                    : "bg-white text-neutral-600 border-stone-200 hover:border-stone-300"
                }`}
              >
                <XCircle className="size-4 text-rose-600" />
                <span>TỪ CHỐI (Reject)</span>
              </button>
            </div>
          </div>

          {decision === "APPROVED" && (
            <div>
              <label htmlFor="confirmed-tutor-type" className="block text-xs font-bold text-neutral-700 mb-1.5">
                Xác nhận loại gia sư theo giấy tờ (không bắt buộc)
              </label>
              <select
                id="confirmed-tutor-type"
                value={confirmedTutorType}
                onChange={(e) => setConfirmedTutorType(e.target.value as TutorType | "")}
                className="w-full px-3 py-2.5 text-xs bg-stone-50 border border-stone-200 rounded-xl focus:bg-white focus:outline-hidden focus:border-emerald-500"
              >
                <option value="">Chưa đủ giấy tờ để xác nhận</option>
                {(Object.keys(TUTOR_TYPE_LABELS) as TutorType[]).map((type) => (
                  <option key={type} value={type}>{TUTOR_TYPE_LABELS[type]}</option>
                ))}
              </select>
              <p className="mt-1 text-[11px] text-neutral-500">
                Khi xác nhận, hồ sơ gia sư hiển thị loại này kèm dấu đã xác minh.
              </p>
            </div>
          )}

          {decision === "REJECTED" && (
            <div className="space-y-1.5 animate-in fade-in duration-150">
              <label className="block text-xs font-bold text-rose-700">
                Lý do từ chối hồ sơ (Bắt buộc theo BR-06) <span className="text-rose-500">*</span>
              </label>
              <textarea
                rows={3}
                required
                value={rejectionReason}
                onChange={(e) => setRejectionReason(e.target.value)}
                placeholder="VD: Ảnh bằng tốt nghiệp bị mờ, không nhìn rõ số hiệu văn bằng..."
                className="w-full p-3 text-xs bg-stone-50 border border-rose-200 rounded-xl focus:bg-white focus:outline-hidden focus:border-rose-500 focus:ring-2 focus:ring-rose-500/20 text-neutral-900"
              />
            </div>
          )}

          <div className="pt-2 flex items-center justify-end gap-3 border-t border-stone-100">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2.5 rounded-xl border border-stone-200 hover:bg-stone-50 text-xs font-semibold text-neutral-700 transition-colors"
            >
              Hủy bỏ
            </button>
            <button
              type="submit"
              disabled={submitting}
              className={`px-5 py-2.5 rounded-xl text-white text-xs font-bold shadow-xs transition-all flex items-center gap-2 disabled:opacity-50 cursor-pointer ${
                decision === "APPROVED"
                  ? "bg-emerald-600 hover:bg-emerald-700"
                  : "bg-rose-600 hover:bg-rose-700"
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
