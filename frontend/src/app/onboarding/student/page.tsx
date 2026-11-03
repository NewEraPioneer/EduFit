"use client";

import React, { useEffect, Suspense } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { StudentOnboardingWizard } from "@/features/onboarding/components/StudentOnboardingWizard";
import { Loader2 } from "lucide-react";

export default function StudentOnboardingPage() {
  const router = useRouter();
  const { user, isLoading } = useAuth();

  useEffect(() => {
    if (!isLoading && !user) {
      router.push("/login");
    }
  }, [user, isLoading, router]);

  if (isLoading || !user) {
    return (
      <div className="min-h-screen bg-stone-50 flex items-center justify-center font-sans">
        <div className="flex flex-col items-center gap-3">
          <Loader2 className="size-8 text-amber-500 animate-spin" />
          <span className="text-xs text-neutral-500 font-medium">Đang kiểm tra thông tin...</span>
        </div>
      </div>
    );
  }

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
      <StudentOnboardingWizard />
    </Suspense>
  );
}
