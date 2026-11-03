"use client";

import React, { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { TutorDashboardView } from "@/features/dashboard";
import { profileService } from "@/services/profile";
import { shouldRedirectTutorToOnboarding } from "@/features/auth/utils/authRedirect";
import { Loader2 } from "lucide-react";

export default function TutorDashboardPage() {
  const router = useRouter();
  const { user, isLoading } = useAuth();
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    let ignore = false;

    async function checkOnboarding() {
      if (isLoading || !user) return;
      const userKey = `edufit_tutor_onboarding_completed_${user.id}`;
      const hasOnboarded =
        typeof window !== "undefined" &&
        (Boolean(localStorage.getItem(userKey)) ||
          Boolean(localStorage.getItem("edufit_tutor_onboarding_completed")));

      try {
        const profile = await profileService.getMyTutorProfile();
        if (!ignore && shouldRedirectTutorToOnboarding(profile, hasOnboarded)) {
          router.replace("/onboarding/tutor");
          return;
        }
      } catch {
        // Nếu backend chưa có hồ sơ gia sư, bắt buộc chuyển về Onboarding
        if (!ignore) {
          router.replace("/onboarding/tutor");
          return;
        }
      }
      if (!ignore) setChecking(false);
    }

    void checkOnboarding();
    return () => {
      ignore = true;
    };
  }, [user, isLoading, router]);

  if (isLoading || !user || checking) {
    return (
      <div className="min-h-[50vh] flex items-center justify-center">
        <Loader2 className="size-8 text-amber-500 animate-spin" />
      </div>
    );
  }

  return <TutorDashboardView user={user} />;
}
