import React from "react";
import { CheckCircle2, Ban } from "lucide-react";
import type { AccountStatus } from "../types";

interface UserStatusBadgeProps {
  status: AccountStatus;
}

export function UserStatusBadge({ status }: UserStatusBadgeProps) {
  if (status === "ACTIVE") {
    return (
      <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
        <CheckCircle2 className="size-3 text-emerald-600" />
        <span>Hoạt động</span>
      </span>
    );
  }

  return (
    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-rose-50 text-rose-700 border border-rose-200">
      <Ban className="size-3 text-rose-600" />
      <span>Đã khóa</span>
    </span>
  );
}
