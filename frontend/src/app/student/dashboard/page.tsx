"use client";

import React from "react";
import { StudentDashboardView } from "@/features/dashboard";
import { StudentLinkInvitations } from "@/features/connection/components/StudentLinkInvitations";
import { OnboardingGate } from "@/features/auth/components/OnboardingGate";

export default function StudentDashboardPage() {
  return (
    <OnboardingGate>
      {(user) => (
        <div className="space-y-6">
          <React.Suspense fallback={null}>
            <StudentLinkInvitations variant="dashboard-banner" />
          </React.Suspense>
          <StudentDashboardView user={user} />
        </div>
      )}
    </OnboardingGate>
  );
}
