"use client";

import React from "react";
import { TutorDashboardView } from "@/features/dashboard";
import { OnboardingGate } from "@/features/auth/components/OnboardingGate";

export default function TutorDashboardPage() {
  return <OnboardingGate>{(user) => <TutorDashboardView user={user} />}</OnboardingGate>;
}
