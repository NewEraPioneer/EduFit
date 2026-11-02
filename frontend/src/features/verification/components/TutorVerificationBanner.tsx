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
        <div className="p-4 rounded-2xl bg-emerald-50 border border-emerald-200 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-emerald-950">
          <div className="flex items-center gap-3">
            <div className="size-9 rounded-xl bg-emerald-100 text-emerald-700 flex items-center justify-center shrink-0">
              <ShieldCheck className="size-5" />
            </div>
            <div>
              <span className="text-xs sm:text-sm font-bold block">
                Hồ sơ gia sư đã được xác thực chính thức ✅
              </span>
              <span className="text-xs text-emerald-800">
                Hồ sơ của bạn đã có huy hiệu uy tín và ưu tiên hiển thị trên kết quả tìm kiếm học sinh.
              </span>
            </div>
          </div>
          <button
            type="button"
            onClick={onOpenUploadModal}
            className="px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-bold transition-colors shrink-0 flex items-center gap-1.5 shadow-xs"
          >
            <UploadCloud className="size-4" />
            <span>Bổ sung bằng cấp</span>
          </button>
        </div>
      );

    case "PENDING":
      if (isProfileVerified) {
        return (
          <div className="p-4 rounded-2xl bg-emerald-50/80 border border-emerald-200 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-emerald-950">
            <div className="flex items-center gap-3">
              <div className="size-9 rounded-xl bg-emerald-100 text-emerald-700 flex items-center justify-center shrink-0">
                <ShieldCheck className="size-5" />
              </div>
              <div>
                <span className="text-xs sm:text-sm font-bold block">
                  Hồ sơ gia sư đã được xác thực chính thức ✅
                </span>
                <span className="text-xs text-emerald-800">
                  Bằng cấp bổ sung mới đang được Quản trị viên xét duyệt ⏳ (Huy hiệu uy tín của bạn vẫn được giữ nguyên).
                </span>
              </div>
            </div>
            <span className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-white border border-emerald-300 text-emerald-800 rounded-xl text-xs font-semibold shrink-0 shadow-2xs">
              <Clock className="size-3.5 text-emerald-600" />
              <span>Đang duyệt bằng bổ sung</span>
            </span>
          </div>
        );
      }
      return (
        <div className="p-4 rounded-2xl bg-sky-50 border border-sky-200 flex items-center justify-between gap-3 text-sky-950">
          <div className="flex items-center gap-3">
            <div className="size-9 rounded-xl bg-sky-100 text-sky-700 flex items-center justify-center shrink-0">
              <Clock className="size-5" />
            </div>
            <div>
              <span className="text-xs sm:text-sm font-bold block">
                Hồ sơ đang chờ Quản trị viên xét duyệt ⏳
              </span>
              <span className="text-xs text-sky-800">
                Đội ngũ kiểm duyệt EduFit đang xử lý hồ sơ bằng cấp của bạn (thường trong vòng 24 giờ).
              </span>
            </div>
          </div>
        </div>
      );

    case "REJECTED":
      return (
        <div className="p-4 rounded-2xl bg-red-50 border border-red-200 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-red-950">
          <div className="flex items-start gap-3">
            <div className="size-9 rounded-xl bg-red-100 text-red-700 flex items-center justify-center shrink-0 mt-0.5">
              <AlertTriangle className="size-5" />
            </div>
            <div>
              <span className="text-xs sm:text-sm font-bold block">
                {isProfileVerified
                  ? "Hồ sơ bổ sung bằng cấp chưa được duyệt ❌"
                  : "Hồ sơ xác minh chưa được duyệt ❌"}
              </span>
              <span className="text-xs text-red-800">
                {rejectionReason || "Hình ảnh bằng cấp mờ hoặc chưa đủ thông tin công chứng. Vui lòng nộp lại giấy tờ."}
              </span>
            </div>
          </div>
          <button
            type="button"
            onClick={onOpenUploadModal}
            className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-xl text-xs font-bold transition-colors shrink-0 shadow-xs"
          >
            {isProfileVerified ? "Nộp lại bằng bổ sung" : "Nộp lại bằng cấp"}
          </button>
        </div>
      );

    case "DRAFT":
    default:
      return (
        <div className="p-4 rounded-2xl bg-amber-50 border border-amber-200 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-amber-950">
          <div className="flex items-start gap-3">
            <div className="size-9 rounded-xl bg-amber-100 text-amber-800 flex items-center justify-center shrink-0 mt-0.5">
              <AlertCircle className="size-5 text-amber-600" />
            </div>
            <div>
              <span className="text-xs sm:text-sm font-bold block">
                Tài khoản gia sư chưa được xác minh bằng cấp ⚠️
              </span>
              <span className="text-xs text-amber-800">
                Nộp bằng đại học, chứng chỉ hoặc giấy tờ định danh để mở khóa nhận lớp và kết nối học sinh.
              </span>
            </div>
          </div>
          <button
            type="button"
            onClick={onOpenUploadModal}
            className="px-4 py-2 bg-amber-500 hover:bg-amber-600 text-neutral-900 rounded-xl text-xs font-bold transition-colors shrink-0 flex items-center gap-1.5 shadow-xs"
          >
            <UploadCloud className="size-4" />
            <span>Nộp hồ sơ ngay</span>
          </button>
        </div>
      );
  }
}
