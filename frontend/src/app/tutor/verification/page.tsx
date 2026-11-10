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
      <div className="bg-surface p-6 sm:p-8 rounded-3xl border border-line/90 shadow-xs space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-5 border-b border-line">
          <div>
            <div className="inline-flex items-center gap-1.5 px-3 py-1 bg-brand-soft text-brand-strong rounded-full text-xs font-medium mb-2">
              <ShieldCheck className="size-3.5 text-brand-strong" />
              <span>Hồ sơ chứng minh năng lực (KYC)</span>
            </div>
            <h3 className="font-heading text-xl font-bold text-ink">
              Bằng cấp và giấy tờ đã nộp
            </h3>
            <p className="text-xs text-muted mt-0.5">
              Các tài liệu được mã hóa và bảo mật, dùng để kiểm duyệt huy hiệu Gia Sư Uy Tín
            </p>
          </div>

          {(status === "DRAFT" || status === "REJECTED") && (
            <button
              type="button"
              onClick={() => setIsUploadOpen(true)}
              className="inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-brand hover:bg-brand-deep text-white rounded-xl text-xs font-medium shadow-xs transition-colors shrink-0"
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
              className="inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-success hover:bg-success-deep text-white rounded-xl text-xs font-medium shadow-xs transition-colors shrink-0"
            >
              <Upload className="size-3.5" />
              <span>Bổ sung bằng cấp</span>
            </button>
          )}

          {status === "PENDING" && (
            <span className="inline-flex items-center gap-1.5 px-3.5 py-2 bg-brand-soft text-brand-strong border border-brand-line rounded-xl text-xs font-medium shrink-0">
              <Clock className="size-3.5 text-brand-strong" />
              <span>{isProfileVerified ? "Đang xét duyệt bằng bổ sung" : "Đang chờ duyệt hồ sơ"}</span>
            </span>
          )}
        </div>

        {loading ? (
          <div className="p-8 flex flex-col items-center justify-center gap-2">
            <Loader2 className="size-5 text-brand-strong animate-spin" />
            <span className="text-xs text-muted">Đang kiểm tra trạng thái hồ sơ...</span>
          </div>
        ) : requestDetail && requestDetail.credentials && requestDetail.credentials.length > 0 ? (
          <div className="space-y-3">
            {requestDetail.credentials.map((cred) => (
              <div
                key={cred.credentialId}
                className="p-4 rounded-2xl border border-line/80 bg-paper/50 flex flex-col sm:flex-row sm:items-center justify-between gap-3"
              >
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-brand-soft text-brand-strong">
                      {cred.type}
                    </span>
                    <h4 className="text-xs font-medium text-ink">{cred.institution}</h4>
                  </div>
                  {cred.year && (
                    <p className="text-xs text-muted">Năm cấp bằng: {cred.year}</p>
                  )}
                  {cred.note && (
                    <p className="text-xs text-ink-2 italic font-sans">{`"${cred.note}"`}</p>
                  )}
                </div>

                <div className="flex flex-col sm:items-end gap-1.5 shrink-0">
                  <div className="text-xs text-muted flex items-center gap-1.5">
                    <FileText className="size-4 text-brand-strong" />
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
                          className="inline-flex items-center gap-1 px-2.5 py-1 bg-surface hover:bg-brand-soft text-brand-strong rounded-lg border border-line text-xs font-medium transition-colors shadow-2xs"
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
          <div className="py-8 text-center text-xs text-muted border border-dashed border-line rounded-2xl">
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
