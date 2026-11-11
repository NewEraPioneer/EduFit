import React from "react";
import { CheckCircle2, Ban } from "lucide-react";
import type { AccountStatus } from "../types";

interface UserStatusBadgeProps {
  status: AccountStatus;
}

export function UserStatusBadge({ status }: UserStatusBadgeProps) {
  if (status === "ACTIVE") {
    return (
      <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-success-soft text-success-strong border border-success-line">
        <CheckCircle2 className="size-3 text-success-strong" />
        <span>Hoạt động</span>
      </span>
    );
  }

  return (
    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-danger-soft text-danger-strong border border-danger-line">
      <Ban className="size-3 text-danger-strong" />
      <span>Đã khóa</span>
    </span>
  );
}
