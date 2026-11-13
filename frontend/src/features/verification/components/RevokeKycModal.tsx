"use client";

import React, { useState } from "react";
import { verificationService, PendingQueueItem } from "@/services/verification";
import { useToast } from "@/shared/ui/Toast";
import {
  AlertTriangle,
  X,
  Loader2,
  ShieldAlert,
  User,
  Mail,
  Award,
  Calendar,
} from "lucide-react";

/**
 * =========================================================================
 * SPRING BOOT API CONNECTION:
 * =========================================================================
 * Endpoint: POST /api/v1/admin/verifications/{id}/revoke
 * Controller: vn.edufit.verification.web.AdminVerificationController#revoke
 * Security: @PreAuthorize("hasRole('ADMIN')")
 * Request Body: RevokeVerificationRequest { reason }
 * Business Rule:
 *  - Thu hồi bằng cấp của gia sư (tái sử dụng status REJECTED với prefix [REVOKED])
 *  - Giáng trạng thái Profile gia sư về UNVERIFIED (ẩn khỏi tìm kiếm công khai)
 *  - Giữ nguyên các lớp học đang hoạt động (active classes)
 *  - Ghi nhận AuditLog với action TUTOR_VERIFICATION_REVOKED
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

interface RevokeKycModalProps {
  item: PendingQueueItem | null;
  isOpen: boolean;
  onClose: () => void;
  onRevoked: () => void;
}

export function RevokeKycModal({
  item,
  isOpen,
  onClose,
  onRevoked,
}: RevokeKycModalProps) {
  const { showToast } = useToast();
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen || !item) return null;

  const targetId = item.requestId || item.id || "";
  const tutorDisplayName = item.tutorName || "Gia sư";
  const tutorDisplayEmail = item.tutorEmail || item.email || "Chưa cập nhật";
  const credentialLabel = item.credentialType
    ? CREDENTIAL_TYPE_LABELS[item.credentialType] || item.credentialType
    : "Bằng cấp / Chứng chỉ";

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmedReason = reason.trim();

    if (trimmedReason.length < 5) {
      setError("Nhập lý do thu hồi (tối thiểu 5 ký tự).");
      return;
    }

    setError(null);
    setSubmitting(true);
    try {
      await verificationService.revokeVerification(targetId, trimmedReason);
      showToast("Đã thu hồi phê duyệt bằng cấp thành công!", "success");
      setReason("");
      onRevoked();
      onClose();
    } catch (err: unknown) {
      const msg =
        err instanceof Error
          ? err.message
          : "Có lỗi xảy ra khi thu hồi bằng cấp";
      showToast(msg, "error");
    } finally {
      setSubmitting(false);
    }
  };

  const handleClose = () => {
    setError(null);
    setReason("");
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4 bg-night/60 backdrop-blur-xs animate-in fade-in duration-150 overflow-y-auto">
      <div className="w-full max-w-dialog-confirm bg-surface rounded-3xl border border-line shadow-2xl p-6 sm:p-7 space-y-5 animate-in zoom-in-95 duration-150 my-auto">
        {/* Header */}
        <div className="flex items-start justify-between gap-3 pb-4 border-b border-line">
          <div className="flex items-center gap-3">
            <div className="size-10 rounded-2xl bg-danger-soft text-danger-strong flex items-center justify-center shrink-0">
              <ShieldAlert className="size-5" />
            </div>
            <div>
              <h3 className="font-heading text-lg font-bold text-ink">
                Thu Hồi Phê Duyệt Bằng Cấp
              </h3>
              <p className="text-xs text-muted font-mono">
                Mã yêu cầu: {targetId}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={handleClose}
            className="p-1.5 rounded-xl hover:bg-sunken text-muted hover:text-ink-2 transition-colors"
          >
            <X className="size-4" />
          </button>
        </div>

        {/* Warning Banner */}
        <div className="p-4 bg-action-soft rounded-2xl border border-action-line text-action-strong text-xs space-y-2">
          <div className="flex items-center gap-2 font-medium text-action-strong">
            <AlertTriangle className="size-4 shrink-0 text-action-strong" />
            <span>Cảnh báo hành động có tác động hệ thống:</span>
          </div>
          <p className="text-action-strong leading-relaxed">
            Hành động này sẽ hủy phê duyệt bằng cấp, chuyển hồ sơ sang trạng thái{" "}
            <strong>Đã từ chối / Thu hồi</strong>, và giáng hồ sơ gia sư về{" "}
            <strong>Chưa xác thực (UNVERIFIED)</strong> — gia sư sẽ bị ẩn khỏi
            danh bạ tìm kiếm công khai.
          </p>
          <p className="text-action-strong text-xs font-medium border-t border-action-line/80 pt-1.5">
            Lưu ý: Các lớp học đang hoạt động (active classes) vẫn được bảo lưu
            để đảm bảo quyền lợi của học sinh.
          </p>
        </div>

        {/* Tutor & Credential Info */}
        <div className="p-4 bg-paper rounded-2xl border border-line/80 space-y-2 text-xs">
          <div className="flex items-center justify-between">
            <span className="text-muted flex items-center gap-1.5">
              <User className="size-3.5 text-muted" />
              <span>Gia sư:</span>
            </span>
            <span className="font-medium text-ink">{tutorDisplayName}</span>
          </div>

          <div className="flex items-center justify-between">
            <span className="text-muted flex items-center gap-1.5">
              <Mail className="size-3.5 text-muted" />
              <span>Email:</span>
            </span>
            <span className="font-medium text-ink-2">{tutorDisplayEmail}</span>
          </div>

          {item.institution && (
            <div className="flex items-center justify-between">
              <span className="text-muted flex items-center gap-1.5">
                <Award className="size-3.5 text-muted" />
                <span>Bằng cấp / Cơ sở:</span>
              </span>
              <span className="font-medium text-ink">
                {credentialLabel} - {item.institution}{" "}
                {item.year ? `(${item.year})` : ""}
              </span>
            </div>
          )}

          {item.submittedAt && (
            <div className="flex items-center justify-between">
              <span className="text-muted flex items-center gap-1.5">
                <Calendar className="size-3.5 text-muted" />
                <span>Thời gian nộp:</span>
              </span>
              <span className="text-ink-2">
                {new Date(item.submittedAt).toLocaleString("vi-VN")}
              </span>
            </div>
          )}
        </div>

        {/* Revoke Form */}
        <form onSubmit={handleSubmit} noValidate className="space-y-4">
          <div className="space-y-1.5">
            <label
              htmlFor="revoke-reason"
              className="block text-xs font-medium text-ink"
            >
              Lý do thu hồi bằng cấp <span className="text-danger-strong">*</span>
            </label>
            <textarea
              id="revoke-reason"
              rows={3}
              required
              value={reason}
              onChange={(e) => {
                setReason(e.target.value);
                if (error) setError(null);
              }}
              placeholder="VD: Phát hiện bằng cấp không hợp lệ qua xác minh thực tế, hoặc tài liệu có dấu hiệu chỉnh sửa..."
              className="w-full p-3 text-xs bg-paper border border-line rounded-xl focus:bg-surface focus:outline-hidden focus:border-danger focus:ring-2 focus:ring-danger/20 text-ink"
            />
            {error && (
              <p className="text-xs text-danger-strong font-medium">{error}</p>
            )}
            <p className="text-xs text-muted">
              Lý do này sẽ được lưu vào nhật ký kiểm toán (Audit Log) và hiển thị
              trong hồ sơ của gia sư với tiền tố [REVOKED].
            </p>
          </div>

          {/* Actions */}
          <div className="pt-2 flex items-center justify-end gap-3 border-t border-line">
            <button
              type="button"
              onClick={handleClose}
              disabled={submitting}
              className="px-4 py-2.5 rounded-xl border border-line hover:bg-paper text-xs font-medium text-ink-2 transition-colors"
            >
              Hủy bỏ
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="px-5 py-2.5 rounded-xl text-white text-xs font-medium bg-danger hover:bg-danger-deep shadow-xs transition-all flex items-center gap-2 disabled:opacity-50 cursor-pointer"
            >
              {submitting && <Loader2 className="size-4 animate-spin" />}
              <span>{submitting ? "Đang xử lý..." : "Xác nhận thu hồi"}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
