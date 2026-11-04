import { Metadata } from "next";
import { Suspense } from "react";
import { TutorOnboardingWizard } from "@/features/onboarding/components/TutorOnboardingWizard";
import { Loader2 } from "lucide-react";

export const metadata: Metadata = {
  title: "Thiết lập hồ sơ gia sư | EduFit",
  description: "Hoàn thiện thông tin chuyên môn, học phí, CV và giới thiệu bản thân trước khi vào Bảng điều khiển.",
};

export default function TutorOnboardingPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen bg-stone-50 flex items-center justify-center font-sans">
          <div className="flex flex-col items-center gap-3">
            <Loader2 className="size-8 text-amber-500 animate-spin" />
            <span className="text-xs text-neutral-500 font-medium">Đang tải biểu mẫu...</span>
          </div>
        </div>
      }
    >
      <TutorOnboardingWizard />
    </Suspense>
  );
}
