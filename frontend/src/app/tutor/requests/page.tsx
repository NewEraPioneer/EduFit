"use client";

import React, { Suspense } from "react";
import { TutorPendingRequests } from "@/features/connection";

export default function TutorRequestsPage() {
  return (
    <div className="p-6 md:p-8 max-w-7xl mx-auto">
      <Suspense fallback={<div className="p-8 text-center text-xs text-neutral-500">Đang tải danh sách yêu cầu...</div>}>
        <TutorPendingRequests />
      </Suspense>
    </div>
  );
}
