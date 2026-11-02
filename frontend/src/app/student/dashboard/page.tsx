"use client";

import React from "react";
import { useAuth } from "@/context/AuthContext";
import { StudentDashboardView } from "@/features/dashboard";
import { StudentLinkInvitations } from "@/features/connection/components/StudentLinkInvitations";

export default function StudentDashboardPage() {
  const { user } = useAuth();
  if (!user) return null;
  return (
    <div className="space-y-6">
      <StudentLinkInvitations variant="dashboard-banner" />
      <StudentDashboardView user={user} />
    </div>
  );
}
