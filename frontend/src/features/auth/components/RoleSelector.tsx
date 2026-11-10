"use client";

import React from "react";
import { GraduationCap, Users, BookOpen } from "lucide-react";

export type RoleType = "STUDENT" | "TUTOR" | "PARENT";

interface RoleSelectorProps {
  selectedRole: RoleType;
  onChange: (role: RoleType) => void;
}

export function RoleSelector({ selectedRole, onChange }: RoleSelectorProps) {
  const roles: { id: RoleType; label: string; desc: string; icon: React.ComponentType<{ className?: string }> }[] = [
    { id: "STUDENT", label: "Học sinh", desc: "Tìm gia sư & học tập", icon: GraduationCap },
    { id: "TUTOR", label: "Gia sư", desc: "Giảng dạy & nhận lớp", icon: BookOpen },
    { id: "PARENT", label: "Phụ huynh", desc: "Theo dõi tiến độ con", icon: Users },
  ];

  return (
    <div className="space-y-1.5">
      <label className="block text-xs font-medium text-ink-2">Tôi muốn đăng ký vai trò:</label>
      <div className="grid grid-cols-3 gap-2">
        {roles.map((r) => {
          const Icon = r.icon;
          const isSelected = selectedRole === r.id;
          return (
            <button
              key={r.id}
              type="button"
              onClick={() => onChange(r.id)}
              className={`p-2.5 rounded-2xl border text-center flex flex-col items-center justify-center gap-1 transition-all ${
                isSelected
                  ? "border-action bg-action-soft/50 text-action-strong font-medium shadow-xs ring-2 ring-action/20"
                  : "border-line hover:border-line-strong text-ink-2 bg-surface"
              }`}
            >
              <Icon className={`size-4.5 ${isSelected ? "text-action-strong" : "text-muted"}`} />
              <span className="text-xs font-medium leading-none">{r.label}</span>
              <span className="text-xs text-muted leading-none">{r.desc}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
