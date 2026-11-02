"use client";

import React, { useCallback, useEffect, useState } from "react";
import {
  verificationService,
  VerificationRequestDetail,
  VerificationStatus,
} from "@/services/verification";
import { profileService, TutorProfileData } from "@/services/profile";
import {
  TutorVerificationBanner,
  UploadCredentialModal,
} from "@/features/verification";
import { ShieldCheck, FileText, Upload, Loader2, Clock } from "lucide-react";

export default function TutorVerificationPage() {
  const [requestDetail, setRequestDetail] = useState<VerificationRequestDetail | null>(null);
  const [profile, setProfile] = useState<TutorProfileData | null>(null);
  const [loading, setLoading] = useState(true);
  const [isUploadOpen, setIsUploadOpen] = useState(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [reqData, profileData] = await Promise.allSettled([
        verificationService.getMyRequest(),
        profileService.getMyTutorProfile(),
      ]);
      if (reqData.status === "fulfilled") {
        setRequestDetail(reqData.value);
      }
      if (profileData.status === "fulfilled") {
        setProfile(profileData.value);
      }
    } catch (err) {
      console.error("Lỗi khi tải hồ sơ KYC gia sư:", err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const status: VerificationStatus = requestDetail?.status || "DRAFT";
  const isProfileVerified = profile?.status === "VERIFIED" || Boolean(profile?.verifiedAt);

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Banner trạng thái */}
      <TutorVerificationBanner
        status={status}
        rejectionReason={requestDetail?.rejectionReason}
        onOpenUploadModal={() => setIsUploadOpen(true)}
        isProfileVerified={isProfileVerified}
      />

      {/* Khung chi tiết hồ sơ xác minh */}
      <div className="bg-white p-6 sm:p-8 rounded-3xl border border-stone-200/90 shadow-xs space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-5 border-b border-stone-100">
          <div>
            <div className="inline-flex items-center gap-1.5 px-3 py-1 bg-sky-50 text-sky-900 rounded-full text-xs font-bold mb-2">
              <ShieldCheck className="size-3.5 text-sky-600" />
              <span>Hồ Sơ Chứng Minh Năng Lực (KYC)</span>
            </div>
            <h3 className="font-heading text-xl font-bold text-neutral-900">
              Bằng cấp & Giấy tờ đã nộp
            </h3>
            <p className="text-xs text-neutral-500 mt-0.5">
              Các tài liệu được mã hóa và bảo mật, dùng để kiểm duyệt huy hiệu Gia Sư Uy Tín
            </p>
          </div>

          {(status === "DRAFT" || status === "REJECTED") && (
            <button
              type="button"
              onClick={() => setIsUploadOpen(true)}
              className="inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-sky-600 hover:bg-sky-700 text-white rounded-xl text-xs font-bold shadow-xs transition-colors shrink-0"
            >
              <Upload className="size-3.5" />
              <span>
                {status === "REJECTED"
                  ? isProfileVerified
                    ? "Nộp lại bằng bổ sung"
                    : "Nộp lại hồ sơ"
                  : "Nộp hồ sơ"}
              </span>
            </button>
          )}

          {(status === "APPROVED" || status === "VERIFIED") && (
            <button
              type="button"
              onClick={() => setIsUploadOpen(true)}
              className="inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-bold shadow-xs transition-colors shrink-0"
            >
              <Upload className="size-3.5" />
              <span>Bổ sung bằng cấp</span>
            </button>
          )}

          {status === "PENDING" && (
            <span className="inline-flex items-center gap-1.5 px-3.5 py-2 bg-sky-50 text-sky-800 border border-sky-200 rounded-xl text-xs font-medium shrink-0">
              <Clock className="size-3.5 text-sky-600" />
              <span>{isProfileVerified ? "Đang xét duyệt bằng bổ sung" : "Đang chờ duyệt hồ sơ"}</span>
            </span>
          )}
        </div>

        {loading ? (
          <div className="p-8 flex flex-col items-center justify-center gap-2">
            <Loader2 className="size-5 text-sky-600 animate-spin" />
            <span className="text-xs text-neutral-400">Đang kiểm tra trạng thái hồ sơ...</span>
          </div>
        ) : requestDetail && requestDetail.credentials && requestDetail.credentials.length > 0 ? (
          <div className="space-y-3">
            {requestDetail.credentials.map((cred) => (
              <div
                key={cred.credentialId}
                className="p-4 rounded-2xl border border-stone-200/80 bg-stone-50/50 flex flex-col sm:flex-row sm:items-center justify-between gap-3"
              >
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-sky-100 text-sky-900">
                      {cred.type}
                    </span>
                    <h4 className="text-xs font-bold text-neutral-900">{cred.institution}</h4>
                  </div>
                  {cred.year && (
                    <p className="text-[11px] text-neutral-500">Năm cấp bằng: {cred.year}</p>
                  )}
                  {cred.note && (
                    <p className="text-xs text-neutral-600 italic font-sans">{`"${cred.note}"`}</p>
                  )}
                </div>

                <div className="flex flex-col sm:items-end gap-1.5 shrink-0">
                  <div className="text-xs text-neutral-400 flex items-center gap-1.5">
                    <FileText className="size-4 text-sky-600" />
                    <span>{cred.files?.length || 0} tệp đính kèm</span>
                  </div>
                  {cred.files && cred.files.length > 0 && (
                    <div className="flex flex-wrap gap-1.5">
                      {cred.files.map((file) => (
                        <a
                          key={file.fileId}
                          href={verificationService.getCredentialFileUrl(file.fileId)}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="inline-flex items-center gap-1 px-2.5 py-1 bg-white hover:bg-sky-50 text-sky-800 rounded-lg border border-stone-200 text-[11px] font-medium transition-colors shadow-2xs"
                        >
                          <span className="truncate max-w-[120px]">{file.fileName}</span>
                        </a>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="py-8 text-center text-xs text-neutral-400 border border-dashed border-stone-200 rounded-2xl">
            Bạn chưa nộp hồ sơ bằng cấp nào. Bấm nút phía trên để bắt đầu xác minh.
          </div>
        )}
      </div>

      <UploadCredentialModal
        isOpen={isUploadOpen}
        onClose={() => setIsUploadOpen(false)}
        onSuccess={() => {
          void loadData();
        }}
      />
    </div>
  );
}
