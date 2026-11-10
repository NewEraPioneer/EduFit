import React from "react";
import { AlertTriangle, CheckCircle2 } from "lucide-react";
import type { LearningNeed } from "@/services/learningNeeds";
import { MODE_LABELS, describeMissing, formatMoney, formatSlot } from "../catalogRules";

/** Tiêu đề ngắn của nhu cầu: "Toán (nhánh) · Lớp 9 · Luyện thi vào 10". */
export function needTitle(need: LearningNeed): string {
  const subject = need.trackName ? `${need.subjectName} – ${need.trackName}` : need.subjectName;
  return [subject, need.educationLevelName, need.goalTagName].filter(Boolean).join(" · ");
}

export function LearningNeedSummary({ need, compact = false }: { need: LearningNeed; compact?: boolean }) {
  const details = [
    need.textbookSeriesName && `Bộ sách: ${need.textbookSeriesName}`,
    need.examProvinceName && `Thi vào 10 tại ${need.examProvinceName}`,
    need.targetSchoolName && `Hướng tới: ${need.targetSchoolName}`,
    need.mode && MODE_LABELS[need.mode],
    need.mode !== "ONLINE" && (need.districtName ? `${need.districtName}, ${need.provinceName}` : need.provinceName),
    need.budgetMaxPerSession != null && `Tối đa ${formatMoney(need.budgetMaxPerSession)}/buổi`,
  ].filter(Boolean) as string[];

  return (
    <div className="min-w-0 space-y-1.5">
      <p className="font-medium text-sm text-ink break-words">{needTitle(need)}</p>
      {details.length > 0 && <p className="text-xs text-ink-2 break-words">{details.join(" · ")}</p>}
      {!compact && need.availabilitySlots.length > 0 && (
        <p className="text-xs text-muted break-words">{need.availabilitySlots.map(formatSlot).join(", ")}</p>
      )}
      {need.status === "ACTIVE" && (
        <p className="inline-flex items-center gap-1 text-xs font-medium text-success-strong">
          <CheckCircle2 className="size-3.5" />Đủ thông tin để ghép gia sư
        </p>
      )}
      {need.status === "INCOMPLETE" && (
        <p className="inline-flex items-start gap-1 text-xs font-medium text-action-strong">
          <AlertTriangle className="size-3.5 mt-0.5 shrink-0" />Cần bổ sung: {describeMissing(need.missingFields)}
        </p>
      )}
    </div>
  );
}
