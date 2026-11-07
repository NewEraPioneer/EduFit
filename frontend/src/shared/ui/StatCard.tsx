import React from "react";
import { LucideIcon } from "lucide-react";

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

export function StatCard({
  label,
  value,
  suffix,
  subtext,
  subtextColor = "text-neutral-500",
  icon: Icon,
  iconColor = "text-neutral-700",
  iconBg = "bg-stone-100",
  badge,
}: StatCardProps) {
  return (
    <div className="edufit-surface bg-white p-5 rounded-2xl border border-stone-200/80 flex flex-col justify-between card-interactive">
      <div className="flex items-center justify-between text-neutral-400">
        <span className="text-xs font-bold uppercase tracking-wider">{label}</span>
        <div className={`size-8 rounded-xl ${iconBg} ${iconColor} flex items-center justify-center shrink-0`}>
          <Icon className="size-4" />
        </div>
      </div>
      <div className="mt-3">
        <div className="text-2xl font-extrabold font-heading text-neutral-900 leading-tight">
          {value} {suffix && <span className="text-xs font-normal text-neutral-400">{suffix}</span>}
        </div>
        {(subtext || badge) && (
          <div className="flex items-center gap-1.5 mt-1">
            {badge && (
              <span className="text-[10px] font-bold px-1.5 py-0.5 rounded-full bg-emerald-100 text-emerald-800">
                {badge}
              </span>
            )}
            {subtext && <span className={`text-[11px] font-medium ${subtextColor}`}>{subtext}</span>}
          </div>
        )}
      </div>
    </div>
  );
}
