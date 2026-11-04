"use client";

import React from "react";
import { ParentDashboardView } from "@/features/dashboard";
import { OnboardingGate } from "@/features/auth/components/OnboardingGate";

export default function ParentDashboardPage() {
  return <OnboardingGate>{(user) => <ParentDashboardView user={user} />}</OnboardingGate>;
}
