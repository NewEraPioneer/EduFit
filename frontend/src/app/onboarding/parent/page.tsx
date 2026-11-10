import { Metadata } from "next";
import { Suspense } from "react";
import { ParentOnboardingWizard } from "@/features/onboarding/components/ParentOnboardingWizard";
import { Loader2 } from "lucide-react";

export const metadata: Metadata = {
  title: "Liên kết hồ sơ con | EduFit",
  description: "Ghép nối tài khoản học sinh của con để theo dõi tiến độ học tập và thông báo lịch học trên EduFit.",
};

export default function ParentOnboardingPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen bg-paper flex items-center justify-center font-sans">
          <div className="flex flex-col items-center gap-3">
            <Loader2 className="size-8 text-action-strong animate-spin" />
            <span className="text-xs text-muted font-medium">Đang tải biểu mẫu...</span>
          </div>
        </div>
      }
    >
      <ParentOnboardingWizard />
    </Suspense>
  );
}
