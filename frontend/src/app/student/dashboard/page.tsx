"use client";

import React, { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { StudentDashboardView } from "@/features/dashboard";
import { StudentLinkInvitations } from "@/features/connection/components/StudentLinkInvitations";
import { profileService } from "@/services/profile";
import { Loader2 } from "lucide-react";

export default function StudentDashboardPage() {
  const router = useRouter();
  const { user, isLoading } = useAuth();
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    let ignore = false;

    async function checkProfile() {
      if (isLoading || !user) return;
      try {
        const profile = await profileService.getMyStudentProfile();
        if (!ignore && !profile.profileComplete) {
          router.replace("/onboarding/student");
          return;
        }
      } catch {
        if (!ignore) {
          router.replace("/onboarding/student");
          return;
        }
      }
      if (!ignore) setChecking(false);
    }

    void checkProfile();
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

  return (
    <div className="space-y-6">
      <StudentLinkInvitations variant="dashboard-banner" />
      <StudentDashboardView user={user} />
    </div>
  );
}
