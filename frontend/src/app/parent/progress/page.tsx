"use client";

import React, { Suspense } from "react";
import { ProgressDashboardView } from "@/features/progress";

export default function ParentProgressPage() {
  return (
    <div className="space-y-6 max-w-7xl mx-auto pb-12">
      <div>
        <h1 className="font-heading font-bold text-2xl text-ink tracking-tight">
          Tiến độ học tập của con
        </h1>
        <p className="text-xs text-muted mt-1">
          Theo dõi mức độ hoàn thành cột mốc, số buổi học và nhật ký giảng dạy của gia sư
        </p>
      </div>

      <Suspense fallback={<div className="py-12 text-center text-xs text-muted">Đang tải tiến độ...</div>}>
        <ProgressDashboardView role="PARENT" />
      </Suspense>
    </div>
  );
}
