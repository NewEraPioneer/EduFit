"use client";

import { HeartHandshake } from "lucide-react";
import { StudentLinkInvitations } from "@/features/connection/components/StudentLinkInvitations";

export default function StudentFamilyPage() {
  return (
    <div className="space-y-6">
      <header className="space-y-1">
        <div className="flex items-center gap-2">
          <HeartHandshake className="size-6 text-amber-600" />
          <h1 className="font-heading text-2xl font-bold text-neutral-900">Liên kết phụ huynh</h1>
        </div>
        <p className="text-sm text-neutral-500">
          Kiểm tra lời mời đang chờ và các tài khoản phụ huynh đang được liên kết với bạn.
        </p>
      </header>
      <StudentLinkInvitations />
    </div>
  );
}
