import React from "react";
import { LucideIcon } from "lucide-react";
import { StatusPill } from "./StatusPill";

interface StatCardProps {
  label: string;
  value: string | number;
  suffix?: string;
  subtext?: string;
  subtextColor?: string;
  icon: LucideIcon;
  iconColor?: string;
  iconBg?: string;
  badge?: string;
}

export function StatCard({ label, value, suffix, subtext, subtextColor = "text-muted", icon: Icon, iconColor = "text-ink-2", iconBg = "bg-sunken", badge }: StatCardProps) {
  return (
    <div className="card-lift flex flex-col justify-between rounded-2xl border border-line bg-surface p-5">
      <div className="flex items-center justify-between gap-2">
        <span className="text-sm text-muted">{label}</span>
        <div className={`flex size-9 shrink-0 items-center justify-center rounded-xl ${iconBg} ${iconColor}`}>
          <Icon className="size-4.5" />
        </div>
      </div>
      <div className="mt-3">
        <div className="font-heading text-3xl font-bold leading-tight text-ink">
          {value} {suffix && <span className="text-sm font-normal text-muted">{suffix}</span>}
        </div>
        {(subtext || badge) && (
          <div className="mt-1 flex items-center gap-1.5">
            {badge && <StatusPill tone="success">{badge}</StatusPill>}
            {subtext && <span className={`text-xs ${subtextColor}`}>{subtext}</span>}
          </div>
        )}
      </div>
    </div>
  );
}
