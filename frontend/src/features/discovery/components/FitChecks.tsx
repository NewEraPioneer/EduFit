import React from "react";
import { AlertTriangle, CheckCircle2 } from "lucide-react";
import type { FitCheck } from "../types";

type FitCheckLike = Pick<FitCheck, "matched" | "label"> & { criterion: string };

/** Nhãn ✔/⚠ từng tiêu chí khớp giữa gia sư và nhu cầu học (khớp mềm: ⚠ không loại gia sư). */
export function FitChecks({ checks, className = "" }: { checks: FitCheckLike[]; className?: string }) {
  if (checks.length === 0) return null;
  return (
    <ul className={`flex flex-wrap gap-1.5 ${className}`} aria-label="Độ khớp với nhu cầu học">
      {checks.map((check) => (
        <li
          key={check.criterion}
          className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium ${
            check.matched ? "bg-emerald-50 text-emerald-800" : "bg-amber-50 text-amber-800"
          }`}
        >
          {check.matched
            ? <CheckCircle2 aria-label="Khớp" className="size-3 shrink-0" />
            : <AlertTriangle aria-label="Chưa khớp" className="size-3 shrink-0" />}
          <span className="break-words">{check.label}</span>
        </li>
      ))}
    </ul>
  );
}
