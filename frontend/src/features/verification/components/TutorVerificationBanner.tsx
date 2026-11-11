"use client";

import React from "react";
import { ShieldCheck, Clock, AlertTriangle, AlertCircle, UploadCloud } from "lucide-react";
import { VerificationStatus } from "@/services/verification";

interface TutorVerificationBannerProps {
  status: VerificationStatus;
  rejectionReason?: string;
  onOpenUploadModal: () => void;
  isProfileVerified?: boolean;
}

export function TutorVerificationBanner({
  status,
  rejectionReason,
  onOpenUploadModal,
  isProfileVerified,
}: TutorVerificationBannerProps) {
  switch (status) {
    case "APPROVED":
    case "VERIFIED":
      return (
        <div className="p-4 rounded-2xl bg-success-soft border border-success-line flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-success-strong">
          <div className="flex items-center gap-3">
            <div className="size-9 rounded-xl bg-success-soft text-success-strong flex items-center justify-center shrink-0">
              <ShieldCheck className="size-5" />
            </div>
            <div>
              <span className="text-xs sm:text-sm font-medium block">
                Hồ sơ gia sư đã được xác thực
              </span>
              <span className="text-xs text-success-strong">
                Hồ sơ của bạn đã có huy hiệu uy tín và ưu tiên hiển thị trên kết quả tìm kiếm học sinh.
              </span>
            </div>
          </div>
          <button
            type="button"
            onClick={onOpenUploadModal}
            className="px-4 py-2 bg-success hover:bg-success-deep text-white rounded-xl text-xs font-medium transition-colors shrink-0 flex items-center gap-1.5 shadow-xs"
          >
            <UploadCloud className="size-4" />
            <span>Bổ sung bằng cấp</span>
          </button>
        </div>
      );

    case "PENDING":
      if (isProfileVerified) {
        return (
          <div className="p-4 rounded-2xl bg-success-soft/80 border border-success-line flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-success-strong">
            <div className="flex items-center gap-3">
              <div className="size-9 rounded-xl bg-success-soft text-success-strong flex items-center justify-center shrink-0">
                <ShieldCheck className="size-5" />
              </div>
              <div>
                <span className="text-xs sm:text-sm font-medium block">
                  Hồ sơ gia sư đã được xác thực
                </span>
                <span className="text-xs text-success-strong">
                  Bằng cấp bổ sung mới đang được Quản trị viên xét duyệt ⏳ (Huy hiệu uy tín của bạn vẫn được giữ nguyên).
                </span>
              </div>
            </div>
            <span className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-surface border border-success-line text-success-strong rounded-xl text-xs font-medium shrink-0 shadow-2xs">
              <Clock className="size-3.5 text-success-strong" />
              <span>Đang duyệt bằng bổ sung</span>
            </span>
          </div>
        );
      }
      return (
        <div className="p-4 rounded-2xl bg-brand-soft border border-brand-line flex items-center justify-between gap-3 text-brand-strong">
          <div className="flex items-center gap-3">
            <div className="size-9 rounded-xl bg-brand-soft text-brand-strong flex items-center justify-center shrink-0">
              <Clock className="size-5" />
            </div>
            <div>
              <span className="text-xs sm:text-sm font-medium block">
                Hồ sơ đang chờ Quản trị viên xét duyệt ⏳
              </span>
              <span className="text-xs text-brand-strong">
                Đội ngũ kiểm duyệt EduFit đang xử lý hồ sơ bằng cấp của bạn (thường trong vòng 24 giờ).
              </span>
            </div>
          </div>
        </div>
      );

    case "REJECTED":
      return (
        <div className="p-4 rounded-2xl bg-danger-soft border border-danger-line flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-danger-strong">
          <div className="flex items-start gap-3">
            <div className="size-9 rounded-xl bg-danger-soft text-danger-strong flex items-center justify-center shrink-0 mt-0.5">
              <AlertTriangle className="size-5" />
            </div>
            <div>
              <span className="text-xs sm:text-sm font-medium block">
                {isProfileVerified
                  ? "Hồ sơ bổ sung bằng cấp chưa được duyệt"
                  : "Hồ sơ xác minh chưa được duyệt"}
              </span>
              <span className="text-xs text-danger-strong">
                {rejectionReason || "Hình ảnh bằng cấp mờ hoặc chưa đủ thông tin công chứng. Vui lòng nộp lại giấy tờ."}
              </span>
            </div>
          </div>
          <button
            type="button"
            onClick={onOpenUploadModal}
            className="px-4 py-2 bg-danger hover:bg-danger-deep text-white rounded-xl text-xs font-medium transition-colors shrink-0 shadow-xs"
          >
            {isProfileVerified ? "Nộp lại bằng bổ sung" : "Nộp lại bằng cấp"}
          </button>
        </div>
      );

    case "DRAFT":
    default:
      return (
        <div className="p-4 rounded-2xl bg-action-soft border border-action-line flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-action-strong">
          <div className="flex items-start gap-3">
            <div className="size-9 rounded-xl bg-action-soft text-action-strong flex items-center justify-center shrink-0 mt-0.5">
              <AlertCircle className="size-5 text-action-strong" />
            </div>
            <div>
              <span className="text-xs sm:text-sm font-medium block">
                Tài khoản gia sư chưa được xác minh bằng cấp
              </span>
              <span className="text-xs text-action-strong">
                Nộp bằng đại học, chứng chỉ hoặc giấy tờ định danh để mở khóa nhận lớp và kết nối học sinh.
              </span>
            </div>
          </div>
          <button
            type="button"
            onClick={onOpenUploadModal}
            className="px-4 py-2 bg-action hover:bg-action-deep text-on-action rounded-xl text-xs font-medium transition-colors shrink-0 flex items-center gap-1.5 shadow-xs"
          >
            <UploadCloud className="size-4" />
            <span>Nộp hồ sơ ngay</span>
          </button>
        </div>
      );
  }
}
