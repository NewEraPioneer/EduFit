"use client";

import React from "react";
import Link from "next/link";
import { ArrowRight, LucideIcon } from "lucide-react";
import { LottiePlayer } from "@/shared/motion/LottiePlayer";
import { cn } from "@/lib/utils";
import { Button } from "./Button";
import { StatusPill } from "./StatusPill";

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
  action?: EmptyStateAction;
  secondaryAction?: EmptyStateAction;
  steps?: OnboardingStep[];
  compact?: boolean;
  className?: string;
}

function ActionButton({ action, secondary }: { action: EmptyStateAction; secondary?: boolean }) {
  const ActionIcon = action.icon || ArrowRight;
  return (
    <Button size="sm" variant={secondary || action.variant === "secondary" || action.variant === "outline" ? "secondary" : "primary"} href={action.href} onClick={action.onClick}>
      <span>{action.label}</span>
      <ActionIcon className="size-4" />
    </Button>
  );
}

export function EmptyState({ icon: Icon, title, description, badge, action, secondaryAction, steps, compact = false, className }: EmptyStateProps) {
  if (compact) {
    return (
      <div className={cn("flex flex-col items-center space-y-3 rounded-2xl border border-dashed border-line-strong bg-paper p-6 text-center", className)}>
        <div className="flex size-11 items-center justify-center rounded-2xl bg-sunken text-ink-2">
          <Icon className="size-5" />
        </div>
        <div className="max-w-sm space-y-1">
          <h4 className="text-sm font-medium text-ink">{title}</h4>
          <p className="text-xs text-muted">{description}</p>
        </div>
        {action && <ActionButton action={action} />}
      </div>
    );
  }

  return (
    <div className={cn("flex flex-col items-center space-y-4 rounded-3xl border border-line bg-surface p-6 text-center sm:p-8", className)}>
      <LottiePlayer src="/lottie/empty-sprout.json" className="size-24" />
      {badge && <StatusPill>{badge}</StatusPill>}
      <div className="max-w-md space-y-1.5">
        <h3 className="font-heading text-xl font-bold text-ink">{title}</h3>
        <p className="text-sm text-muted">{description}</p>
      </div>
      {(action || secondaryAction) && (
        <div className="flex flex-wrap items-center justify-center gap-3">
          {action && <ActionButton action={action} />}
          {secondaryAction && <ActionButton action={secondaryAction} secondary />}
        </div>
      )}
      {steps && steps.length > 0 && (
        <ol className="grid w-full max-w-2xl grid-cols-1 gap-3 border-t border-line pt-5 text-left sm:grid-cols-3">
          {steps.map((step) => (
            <li key={step.step} className="flex flex-col gap-1.5 rounded-2xl bg-paper p-4">
              <span className="text-xs text-muted">Bước {step.step}</span>
              <h5 className="text-sm font-medium text-ink">{step.title}</h5>
              <p className="text-xs text-muted">{step.description}</p>
              {step.href && (
                <Link href={step.href} className="mt-auto inline-flex items-center gap-1 pt-1 text-xs font-medium text-brand-strong hover:underline">
                  {step.actionText || "Bắt đầu"}
                  <ArrowRight className="size-3.5" />
                </Link>
              )}
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
