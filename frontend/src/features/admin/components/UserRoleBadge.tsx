import React from "react";
import { GraduationCap, BookOpen, Users, ShieldAlert } from "lucide-react";
import type { AccountRole } from "../types";

interface UserRoleBadgeProps {
  role: AccountRole;
}

export function UserRoleBadge({ role }: UserRoleBadgeProps) {
  switch (role) {
    case "STUDENT":
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-brand-soft text-brand-strong border border-brand-line">
          <BookOpen className="size-3 text-brand-strong" />
          <span>Học sinh</span>
        </span>
      );
    case "PARENT":
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-brand-soft text-brand-strong border border-brand-line">
          <Users className="size-3 text-brand-strong" />
          <span>Phụ huynh</span>
        </span>
      );
    case "TUTOR":
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-brand-soft text-brand-strong border border-brand-line">
          <GraduationCap className="size-3 text-brand-strong" />
          <span>Gia sư</span>
        </span>
      );
    case "ADMIN":
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-action-soft text-action-strong border border-action-line">
          <ShieldAlert className="size-3 text-action-strong" />
          <span>Quản trị viên</span>
        </span>
      );
    default:
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-sunken text-ink-2 border border-line">
          <span>{role}</span>
        </span>
      );
  }
}
