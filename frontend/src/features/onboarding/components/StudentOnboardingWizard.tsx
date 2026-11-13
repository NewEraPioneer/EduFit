"use client";

import React, { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { CheckCircle2, Loader2, Plus } from "lucide-react";
import { EduFitLogo } from "@/shared/ui/EduFitLogo";
import { useAuth } from "@/context/AuthContext";
import { markOnboardingDone, sanitizeReturnUrl } from "@/features/auth/utils/authRedirect";
import { useToast } from "@/shared/ui/Toast";
import { profileService } from "@/services/profile";
import type { LearningNeed } from "@/services/learningNeeds";
import { LearningNeedForm } from "@/features/learning-needs/components/LearningNeedForm";
import { needTitle } from "@/features/learning-needs/components/LearningNeedSummary";
import { useLearningCatalog } from "@/features/learning-needs/hooks/useLearningCatalog";

/**
 * Onboarding học sinh chỉ hỏi Lớp → Môn → Mục tiêu (kèm bộ sách hoặc tỉnh dự thi khi luật yêu cầu).
 * Nhu cầu tạo ra ở trạng thái chưa đủ thông tin; hình thức, khu vực, ngân sách và lịch được hỏi
 * khi người dùng bấm AI Match lần đầu.
 */
export function StudentOnboardingWizard() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { user, refreshUser } = useAuth();
  const { showToast } = useToast();
  const { catalog, error } = useLearningCatalog();
  const [created, setCreated] = useState<LearningNeed[]>([]);
  const [formKey, setFormKey] = useState(0);
  const [finishing, setFinishing] = useState(false);

  const safeReturnUrl = sanitizeReturnUrl(searchParams.get("returnUrl"));

  const handleSaved = async (need: LearningNeed) => {
    if (created.length === 0 && need.educationLevelId) {
      // Hồ sơ chỉ lưu khối lớp để điền sẵn cho các nhu cầu sau
      await profileService.updateStudentProfile({ educationLevelId: need.educationLevelId }).catch(() => undefined);
    }
    setCreated((prev) => [...prev, need]);
    setFormKey((key) => key + 1);
    showToast(`Đã thêm nhu cầu: ${needTitle(need)}`, "success");
  };

  const finish = async () => {
    setFinishing(true);
    // Cả "Hoàn tất" lẫn "Bỏ qua" đều là quyết định rời wizard: dashboard không được đẩy về đây nữa,
    // kể cả khi lưu khối lớp (profileComplete) thất bại.
    markOnboardingDone("student", user?.id);
    try {
      await refreshUser?.();
      router.push(safeReturnUrl || "/student/dashboard");
    } finally {
      setFinishing(false);
    }
  };

  return (
    <div className="min-h-screen bg-paper/80 flex flex-col font-sans text-ink">
      <header className="w-full bg-surface border-b border-line/80 px-4 sm:px-6 py-4 flex items-center justify-between gap-3">
        <EduFitLogo href="/" />
        <span className="text-xs text-muted truncate">
          Xin chào, <strong className="text-ink">{user?.fullName || "Học sinh"}</strong>
        </span>
      </header>

      <main className="flex-1 w-full max-w-2xl mx-auto px-4 py-8 space-y-6">
        <div className="space-y-1">
          <h1 className="font-heading text-2xl font-bold">Con cần học gì?</h1>
          <p className="text-sm text-ink-2">
            Chọn khối lớp, môn và mục tiêu. Có thể thêm nhiều môn; lịch học, khu vực và ngân sách sẽ hỏi khi bạn tìm gia sư.
          </p>
        </div>

        {created.length > 0 && (
          <ul className="space-y-2" aria-label="Nhu cầu đã thêm">
            {created.map((need) => (
              <li key={need.needId} className="flex items-center gap-2 px-4 py-3 rounded-2xl bg-surface border border-success-line text-sm">
                <CheckCircle2 className="size-4 text-success-strong shrink-0" />
                <span className="min-w-0 break-words">{needTitle(need)}</span>
              </li>
            ))}
          </ul>
        )}

        <div className="bg-surface rounded-3xl border border-line p-5 sm:p-6">
          {error && <p role="alert" className="text-sm text-danger-strong">{error}</p>}
          {!catalog && !error && (
            <p className="text-sm text-muted inline-flex items-center gap-2"><Loader2 className="size-4 animate-spin" />Đang tải danh mục…</p>
          )}
          {catalog && (
            <LearningNeedForm
              key={formKey}
              catalog={catalog}
              variant="classification"
              defaults={created.at(-1)?.educationLevelId ? { educationLevelId: created.at(-1)!.educationLevelId! } : undefined}
              submitLabel={created.length === 0 ? "Lưu nhu cầu" : "Thêm môn này"}
              onSaved={handleSaved}
            />
          )}
        </div>

        <div className="flex flex-col-reverse sm:flex-row sm:justify-between gap-3">
          <button type="button" onClick={finish} disabled={finishing}
            className="px-4 py-2.5 rounded-xl text-sm font-medium text-ink-2 hover:bg-sunken">
            {created.length === 0 ? "Bỏ qua, thiết lập sau" : "Để sau"}
          </button>
          {created.length > 0 && (
            <div className="flex flex-col sm:flex-row gap-2">
              <span className="inline-flex items-center gap-1 text-xs text-muted sm:self-center">
                <Plus className="size-3.5" />Có thể thêm môn khác ở form phía trên
              </span>
              <button type="button" onClick={finish} disabled={finishing}
                className="px-5 py-2.5 rounded-xl bg-brand text-white text-sm font-medium hover:bg-brand-deep disabled:opacity-60 inline-flex items-center justify-center gap-2">
                {finishing && <Loader2 className="size-4 animate-spin" />}Hoàn tất
              </button>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
