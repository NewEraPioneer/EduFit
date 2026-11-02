"use client";

import React from "react";
import { TutorPendingRequests } from "@/features/connection";

export default function TutorRequestsPage() {
  return (
    <div className="p-6 md:p-8 max-w-7xl mx-auto">
      <TutorPendingRequests />
    </div>
  );
}
