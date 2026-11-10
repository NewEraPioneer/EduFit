import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

const TONES = {
  neutral: "bg-sunken text-ink-2",
  brand: "bg-brand-soft text-brand-strong",
  action: "bg-action-soft text-action-strong",
  success: "bg-success-soft text-success-strong",
  danger: "bg-danger-soft text-danger-strong",
};

export function StatusPill({ tone = "neutral", children, className }: { tone?: keyof typeof TONES; children: ReactNode; className?: string }) {
  return <span className={cn("inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium", TONES[tone], className)}>{children}</span>;
}
