"use client";

import React, { Suspense } from "react";
import { StudentDiscoveryView } from "@/features/discovery/components/views/StudentDiscoveryView";

export default function StudentTutorsPage() {
  return (
    <Suspense fallback={<div className="p-8 text-center text-xs text-muted">Đang tải danh sách gia sư...</div>}>
      <StudentDiscoveryView />
    </Suspense>
  );
}
