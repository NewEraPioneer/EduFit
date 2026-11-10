"use client";

import React, { Suspense } from "react";
import { HeartHandshake } from "lucide-react";
import { StudentLinkInvitations } from "@/features/connection/components/StudentLinkInvitations";

export default function StudentFamilyPage() {
  return (
    <div className="space-y-6">
      <header className="space-y-1">
        <div className="flex items-center gap-2">
          <HeartHandshake className="size-6 text-action-strong" />
          <h1 className="font-heading text-2xl font-bold text-ink">Liên kết phụ huynh</h1>
        </div>
        <p className="text-sm text-muted">
          Kiểm tra lời mời đang chờ và các tài khoản phụ huynh đang được liên kết với bạn.
        </p>
      </header>
      <Suspense fallback={<div className="p-8 text-center text-xs text-muted">Đang tải lời mời...</div>}>
        <StudentLinkInvitations />
      </Suspense>
    </div>
  );
}
