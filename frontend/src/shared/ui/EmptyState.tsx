"use client";

import React from "react";
import Link from "next/link";
import { LucideIcon, ArrowRight } from "lucide-react";

export interface EmptyStateAction {
  label: string;
  href?: string;
  onClick?: () => void;
  icon?: LucideIcon;
  variant?: "primary" | "secondary" | "outline";
}

export interface OnboardingStep {
  step: number;
  title: string;
  description: string;
  actionText?: string;
  href?: string;
}

export interface EmptyStateProps {
  icon: LucideIcon;
  title: string;
  description: string;
  badge?: string;
  theme?: "amber" | "sky" | "purple" | "emerald" | "neutral";
  action?: EmptyStateAction;
  secondaryAction?: EmptyStateAction;
  steps?: OnboardingStep[];
  compact?: boolean;
  className?: string;
}

const THEME_STYLES = {
  amber: {
    iconBg: "bg-amber-100/80 dark:bg-amber-950/70 text-amber-900 dark:text-amber-300 border-amber-200/60 dark:border-amber-800/60",
    glow: "bg-amber-400/10 dark:bg-amber-400/5",
    badge: "bg-amber-50 dark:bg-amber-950/60 text-amber-800 dark:text-amber-300 border-amber-200 dark:border-amber-800/60",
    btnPrimary: "bg-amber-500 hover:bg-amber-600 text-neutral-950 shadow-amber-500/20",
    stepBadge: "bg-amber-500 text-neutral-950",
  },
  sky: {
    iconBg: "bg-sky-100/80 dark:bg-sky-950/70 text-sky-900 dark:text-sky-300 border-sky-200/60 dark:border-sky-800/60",
    glow: "bg-sky-400/10 dark:bg-sky-400/5",
    badge: "bg-sky-50 dark:bg-sky-950/60 text-sky-800 dark:text-sky-300 border-sky-200 dark:border-sky-800/60",
    btnPrimary: "bg-sky-600 hover:bg-sky-700 text-white shadow-sky-600/20",
    stepBadge: "bg-sky-600 text-white",
  },
  purple: {
    iconBg: "bg-purple-100/80 dark:bg-purple-950/70 text-purple-900 dark:text-purple-300 border-purple-200/60 dark:border-purple-800/60",
    glow: "bg-purple-400/10 dark:bg-purple-400/5",
    badge: "bg-purple-50 dark:bg-purple-950/60 text-purple-800 dark:text-purple-300 border-purple-200 dark:border-purple-800/60",
    btnPrimary: "bg-purple-600 hover:bg-purple-700 text-white shadow-purple-600/20",
    stepBadge: "bg-purple-600 text-white",
  },
  emerald: {
    iconBg: "bg-emerald-100/80 dark:bg-emerald-950/70 text-emerald-900 dark:text-emerald-300 border-emerald-200/60 dark:border-emerald-800/60",
    glow: "bg-emerald-400/10 dark:bg-emerald-400/5",
    badge: "bg-emerald-50 dark:bg-emerald-950/60 text-emerald-800 dark:text-emerald-300 border-emerald-200 dark:border-emerald-800/60",
    btnPrimary: "bg-emerald-600 hover:bg-emerald-700 text-white shadow-emerald-600/20",
    stepBadge: "bg-emerald-600 text-white",
  },
  neutral: {
    iconBg: "bg-stone-100 dark:bg-stone-800 text-stone-700 dark:text-stone-300 border-stone-200 dark:border-stone-700",
    glow: "bg-stone-400/10",
    badge: "bg-stone-100 dark:bg-stone-800 text-stone-700 dark:text-stone-300 border-stone-200 dark:border-stone-700",
    btnPrimary: "bg-stone-900 hover:bg-neutral-800 text-white shadow-stone-900/10",
    stepBadge: "bg-stone-800 text-white",
  },
};

export function EmptyState({
  icon: Icon,
  title,
  description,
  badge,
  theme = "amber",
  action,
  secondaryAction,
  steps,
  compact = false,
  className = "",
}: EmptyStateProps) {
  const currentTheme = THEME_STYLES[theme];

  const renderActionBtn = (act: EmptyStateAction, isSecondary = false) => {
    const ActionIcon = act.icon || ArrowRight;
    const btnClasses = isSecondary
      ? "px-4 py-2 bg-stone-100 dark:bg-stone-800 hover:bg-stone-200/80 dark:hover:bg-stone-700 text-neutral-700 dark:text-stone-200 rounded-xl text-xs font-bold transition-all border border-stone-200/80 dark:border-stone-700 flex items-center justify-center gap-1.5 cursor-pointer"
      : `px-4 py-2.5 ${currentTheme.btnPrimary} rounded-xl text-xs font-bold transition-all shadow-sm flex items-center justify-center gap-2 cursor-pointer active:scale-[0.98]`;

    if (act.href) {
      return (
        <Link href={act.href} className={btnClasses}>
          <span>{act.label}</span>
          <ActionIcon className="size-3.5" />
        </Link>
      );
    }

    return (
      <button type="button" onClick={act.onClick} className={btnClasses}>
        <span>{act.label}</span>
        <ActionIcon className="size-3.5" />
      </button>
    );
  };

  if (compact) {
    return (
      <div
        className={`p-6 bg-stone-50/70 dark:bg-stone-900/70 border border-dashed border-stone-200/90 dark:border-stone-700/80 rounded-2xl flex flex-col items-center text-center space-y-3 ${className}`}
      >
        <div
          className={`size-11 rounded-2xl border flex items-center justify-center shadow-xs ${currentTheme.iconBg}`}
        >
          <Icon className="size-5.5" />
        </div>
        <div className="space-y-1.5 max-w-sm">
          <h4 className="text-xs sm:text-sm font-bold text-neutral-900 dark:text-stone-100">{title}</h4>
          <p className="text-[11px] sm:text-xs text-neutral-600 dark:text-stone-300 leading-relaxed font-sans font-medium">
            {description}
          </p>
        </div>
        {action && <div className="pt-1">{renderActionBtn(action)}</div>}
      </div>
    );
  }

  return (
    <div
      className={`edufit-surface relative overflow-hidden bg-white dark:bg-stone-900 rounded-3xl border border-stone-200/90 dark:border-stone-800 p-6 sm:p-8 flex flex-col items-center text-center space-y-5 ${className}`}
    >
      {/* Background Soft Glow */}
      <div
        className={`absolute -top-12 size-48 rounded-full blur-3xl pointer-events-none ${currentTheme.glow}`}
      />

      {/* Badge (Optional) */}
      {badge && (
        <span
          className={`px-3 py-1 rounded-full text-[11px] font-bold border uppercase tracking-wider ${currentTheme.badge}`}
        >
          {badge}
        </span>
      )}

      {/* Icon */}
      <div
        className={`size-14 sm:size-16 rounded-2xl border flex items-center justify-center shadow-xs z-10 ${currentTheme.iconBg}`}
      >
        <Icon className="size-7 sm:size-8" />
      </div>

      {/* Typography */}
      <div className="space-y-1.5 max-w-md z-10">
        <h3 className="font-heading text-base sm:text-lg font-bold text-neutral-900 dark:text-stone-100">
          {title}
        </h3>
        <p className="text-xs text-neutral-600 dark:text-stone-300 leading-relaxed font-sans font-medium">
          {description}
        </p>
      </div>

      {/* Actions */}
      {(action || secondaryAction) && (
        <div className="flex flex-wrap items-center justify-center gap-3 pt-1 z-10">
          {action && renderActionBtn(action)}
          {secondaryAction && renderActionBtn(secondaryAction, true)}
        </div>
      )}

      {/* Onboarding Steps for Newcomers (Optional) */}
      {steps && steps.length > 0 && (
        <div className="w-full max-w-xl pt-5 border-t border-stone-100 z-10 text-left">
          <span className="text-[11px] font-bold text-neutral-400 uppercase tracking-wider block text-center mb-3">
            Hướng dẫn thao tác cho thành viên mới
          </span>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            {steps.map((st) => (
              <div
                key={st.step}
                className="p-3 bg-stone-50 rounded-2xl border border-stone-200/80 flex flex-col justify-between space-y-2"
              >
                <div className="space-y-1">
                  <div className="flex items-center gap-1.5">
                    <span
                      className={`size-4.5 rounded-full text-[10px] font-extrabold flex items-center justify-center shrink-0 ${currentTheme.stepBadge}`}
                    >
                      {st.step}
                    </span>
                    <h5 className="text-[11px] font-bold text-neutral-800 line-clamp-1">
                      {st.title}
                    </h5>
                  </div>
                  <p className="text-[10px] text-neutral-500 leading-snug">
                    {st.description}
                  </p>
                </div>
                {st.href && (
                  <Link
                    href={st.href}
                    className="text-[10px] text-amber-600 hover:text-amber-700 font-bold flex items-center gap-1 hover:underline pt-1 border-t border-stone-200/60"
                  >
                    <span>{st.actionText || "Bắt đầu"}</span>
                    <ArrowRight className="size-2.5" />
                  </Link>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
