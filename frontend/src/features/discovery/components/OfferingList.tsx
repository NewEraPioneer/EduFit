import React from "react";
import { AlertTriangle, BookOpen, ShieldCheck } from "lucide-react";
import type { TutorOffering } from "@/services/profile";
import { formatVnd, offeringName } from "../utils/tutorDisplay";

/** Gói dạy công khai: môn (+ nhánh), khối lớp, mục tiêu, bộ sách, tỉnh luyện đề và học phí riêng. */
export function OfferingList({ offerings }: { offerings: TutorOffering[] }) {
  if (offerings.length === 0) {
    return <p className="text-xs text-muted italic p-3 bg-paper rounded-xl">Gia sư chưa công bố gói dạy nào.</p>;
  }
  return (
    <ul className="grid grid-cols-1 sm:grid-cols-2 gap-2">
      {offerings.map((offering) => (
        <li key={offering.offeringId} className="p-3 rounded-xl bg-paper border border-line/80 text-xs space-y-1.5 min-w-0">
          <div className="flex items-start justify-between gap-2">
            <span className="inline-flex items-center gap-1.5 font-medium text-ink min-w-0">
              <BookOpen className="size-4 text-action-strong shrink-0" />
              <span className="break-words">{offeringName(offering)}</span>
            </span>
            <span className="shrink-0 font-medium text-ink">{formatVnd(offering.pricePerSession)}</span>
          </div>
          <p className="text-ink-2 break-words">{offering.levels.map((level) => level.name).join(", ")}</p>
          {offering.goals.length > 0 && (
            <ul className="flex flex-wrap gap-1">
              {offering.goals.map((goal) => (
                <li key={goal.goalTagId}
                  title={goal.premium && !goal.evidenceVerified ? "Chưa có minh chứng chuyên môn được duyệt" : undefined}
                  className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs ${
                    goal.premium && !goal.evidenceVerified ? "bg-action-soft text-action-strong" : "bg-surface border border-line text-ink-2"
                  }`}>
                  {goal.premium && (goal.evidenceVerified
                    ? <ShieldCheck aria-label="Đã duyệt minh chứng" className="size-3 text-success-strong" />
                    : <AlertTriangle aria-label="Chưa duyệt minh chứng" className="size-3" />)}
                  {goal.name}
                </li>
              ))}
            </ul>
          )}
          {offering.textbooks.length > 0 && (
            <p className="text-xs text-muted break-words">Bộ sách: {offering.textbooks.map((t) => t.name).join(", ")}</p>
          )}
          {offering.examProvinces.length > 0 && (
            <p className="text-xs text-muted break-words">Luyện đề vào 10: {offering.examProvinces.map((p) => p.name).join(", ")}</p>
          )}
        </li>
      ))}
    </ul>
  );
}
