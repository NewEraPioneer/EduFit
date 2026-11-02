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
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-sky-50 text-sky-700 border border-sky-200">
          <BookOpen className="size-3 text-sky-600" />
          <span>Học sinh</span>
        </span>
      );
    case "PARENT":
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-indigo-50 text-indigo-700 border border-indigo-200">
          <Users className="size-3 text-indigo-600" />
          <span>Phụ huynh</span>
        </span>
      );
    case "TUTOR":
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-purple-50 text-purple-700 border border-purple-200">
          <GraduationCap className="size-3 text-purple-600" />
          <span>Gia sư</span>
        </span>
      );
    case "ADMIN":
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-amber-50 text-amber-700 border border-amber-200">
          <ShieldAlert className="size-3 text-amber-600" />
          <span>Quản trị viên</span>
        </span>
      );
    default:
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-stone-100 text-neutral-700 border border-stone-200">
          <span>{role}</span>
        </span>
      );
  }
}
