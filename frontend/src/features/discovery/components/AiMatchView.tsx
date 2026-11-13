"use client";

import React, { useEffect, useState } from "react";
import { AlertTriangle, CheckCircle2, Plus, Sparkles, Target, X } from "lucide-react";
import { LearningNeedModal } from "@/features/learning-needs/components/LearningNeedModal";
import { LearningNeedSummary } from "@/features/learning-needs/components/LearningNeedSummary";
import { describeMissing } from "@/features/learning-needs/catalogRules";
import type { LearningNeed } from "@/services/learningNeeds";
import type { useTutorMatching } from "../hooks/useTutorMatching";
import type { TutorMatchResult } from "../types";
import { FitChecks } from "./FitChecks";
import { NeedSelector } from "./NeedSelector";
import { formatRating, formatVnd } from "../utils/tutorDisplay";

export interface AiMatchViewProps {
  matching: ReturnType<typeof useTutorMatching>;
  parent?: boolean;
  onViewDetail?: (tutorId: string) => void;
  onRequestConnect?: (tutorId: string) => void;
}

type NeedDialog = { kind: "edit"; need: LearningNeed } | { kind: "complete"; need: LearningNeed } | { kind: "create" };

/**
 * Gợi ý gia sư cho nhu cầu học đang chọn. Nhu cầu tạo lúc onboarding chỉ có Lớp → Môn → Mục tiêu,
 * nên lần đầu ghép sẽ mở hộp thoại hỏi thêm hình thức, khu vực, ngân sách và lịch học.
 */
export function AiMatchView({ matching, parent = false, onViewDetail, onRequestConnect }: AiMatchViewProps) {
  const { need, needs, matches, isMatching, error } = matching;
  const [dialog, setDialog] = useState<NeedDialog | null>(null);
  const [promptedNeedId, setPromptedNeedId] = useState<string | null>(null);
  const [selectedExplanation, setSelectedExplanation] = useState<TutorMatchResult | null>(null);
  const studentId = parent ? matching.studentId : undefined;

  useEffect(() => {
    if (need?.status === "INCOMPLETE" && promptedNeedId !== need.needId) {
      setPromptedNeedId(need.needId);
      setDialog({ kind: "complete", need });
    }
  }, [need, promptedNeedId]);

  const latest = needs.at(-1);
  const dialogProps = dialog?.kind === "complete"
    ? {
        need: dialog.need,
        title: "Bổ sung thông tin để tìm gia sư",
        description: "Chọn hình thức học, khu vực, ngân sách và khung giờ để hệ thống so khớp với gia sư.",
        submitLabel: "Lưu và tìm gia sư",
      }
    : dialog?.kind === "edit"
      ? { need: dialog.need }
      : {
          need: null,
          defaults: latest
            ? {
                educationLevelId: latest.educationLevelId ?? undefined,
                mode: latest.mode,
                provinceId: latest.provinceId,
                districtId: latest.districtId,
                budgetMaxPerSession: latest.budgetMaxPerSession,
                availabilitySlots: latest.availabilitySlots,
              }
            : undefined,
        };

  return (
    <div className="space-y-6">
      <NeedSelector matching={matching} parent={parent} />

      <div className="p-4 sm:p-5 rounded-2xl bg-paper border border-line/80 flex flex-col sm:flex-row sm:items-start justify-between gap-4">
        <div className="flex items-start gap-3.5 min-w-0">
          <div className="size-11 rounded-full bg-brand text-white flex items-center justify-center shrink-0 shadow-2xs">
            <Target className="size-5.5 text-white" />
          </div>
          <div className="min-w-0">
            <h4 className="font-heading font-bold text-sm text-ink">Nhu cầu đang dùng để ghép gia sư</h4>
            {need ? <LearningNeedSummary need={need} compact /> : (
              <p className="text-xs text-ink-2 mt-0.5">Chưa có nhu cầu học. Thêm nhu cầu để nhận gợi ý gia sư.</p>
            )}
          </div>
        </div>
        <div className="flex flex-wrap gap-2 shrink-0">
          {need && (
            <button type="button" onClick={() => setDialog({ kind: "edit", need })}
              className="px-4 py-2 rounded-full bg-surface hover:bg-paper border border-line text-ink text-xs font-medium shadow-2xs">
              Sửa nhu cầu
            </button>
          )}
          {(!parent || matching.studentId) && (
            <button type="button" onClick={() => setDialog({ kind: "create" })}
              className="inline-flex items-center gap-1 px-4 py-2 rounded-full bg-surface hover:bg-paper border border-line text-ink text-xs font-medium shadow-2xs">
              <Plus className="size-3.5" />Thêm nhu cầu
            </button>
          )}
        </div>
      </div>

      {error && (
        <div role="alert" className="px-4 py-3 rounded-2xl bg-danger-soft border border-danger-line text-xs text-danger-strong">{error}</div>
      )}

      {need?.status === "INCOMPLETE" ? (
        <div className="bg-surface p-6 sm:p-8 rounded-3xl border border-action-line text-center space-y-3">
          <AlertTriangle className="size-8 text-action-strong mx-auto" />
          <h3 className="font-heading font-bold text-base text-ink">Nhu cầu này chưa đủ thông tin để ghép gia sư</h3>
          <p className="text-xs text-ink-2 max-w-md mx-auto">Cần bổ sung: {describeMissing(need.missingFields)}.</p>
          <button type="button" onClick={() => setDialog({ kind: "complete", need })}
            className="px-4 py-2 rounded-full bg-brand text-white text-xs font-medium">
            Bổ sung thông tin
          </button>
        </div>
      ) : isMatching ? (
        <div className="space-y-3" aria-busy="true">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-28 rounded-2xl bg-surface border border-line/70 animate-pulse" />
          ))}
        </div>
      ) : need ? (
        <>
          <div className="flex flex-wrap items-center justify-between gap-2 text-xs pt-1">
            <div className="font-heading font-bold text-sm text-ink">{matches.length} gợi ý phù hợp</div>
            <div className="flex items-center gap-1.5 text-brand-strong font-medium text-xs bg-brand-soft px-2.5 py-1 rounded-full">
              <Sparkles className="size-3 text-brand-strong" />
              <span>{matches.some((match) => match.aiGenerated) ? "Có giải thích từ AI" : "Gợi ý theo tiêu chí phù hợp"}</span>
            </div>
          </div>

          {matches.length === 0 ? (
            <div className="bg-surface p-8 sm:p-12 rounded-3xl border border-line text-center space-y-3">
              <Target className="size-8 text-muted mx-auto" />
              <h3 className="font-heading font-bold text-base text-ink">Chưa có gia sư nào dạy môn này</h3>
              <p className="text-xs text-muted max-w-md mx-auto">
                Thử chọn nhu cầu khác, hoặc sửa nhu cầu để mở rộng hình thức học và khung giờ.
              </p>
            </div>
          ) : (
            <div className="space-y-3">
              {matches.map((item, idx) => {
                const tutor = item.tutorInfo;
                return (
                  <React.Fragment key={item.tutorId}>
                    {idx === 0 && <h3 className="font-heading font-bold text-base text-ink">Gợi ý phù hợp nhất</h3>}
                    {idx === 1 && <h3 className="font-heading font-bold text-sm text-ink-2 pt-5">Các gia sư khác</h3>}
                    <div className={`bg-surface rounded-2xl border p-4 sm:p-5 flex flex-col lg:flex-row lg:items-start justify-between gap-4 ${idx === 0 ? "border-brand ring-1 ring-brand-line" : "border-line/80"}`}>
                      <div className="flex items-start gap-3.5 flex-1 min-w-0">
                        <div className="size-7 sm:size-8 rounded-full bg-night text-white font-medium text-xs flex items-center justify-center shrink-0 border border-transparent">
                          {String(idx + 1).padStart(2, "0")}
                        </div>
                        <div className="min-w-0 flex-1 space-y-1">
                          <div className="flex items-center gap-1.5 min-w-0">
                            <h4 className="font-heading font-bold text-[15px] text-ink truncate">{tutor.displayName}</h4>
                            {tutor.verified && <CheckCircle2 aria-label="Đã xác minh" className="size-4 text-success-strong fill-success shrink-0" />}
                          </div>
                          <p className="text-xs text-ink-2 font-medium break-words">
                            {tutor.fit?.offeringName}
                            {tutor.fit && ` · ${formatVnd(tutor.fit.pricePerSession)}/buổi`}
                            {tutor.experienceYears ? ` · ${tutor.experienceYears} năm kinh nghiệm` : ""}
                          </p>
                          <p className="text-xs text-muted">★ {formatRating(tutor.ratingAvg, tutor.reviewCount)}</p>
                          {tutor.fit && <FitChecks checks={tutor.fit.checks} className="pt-1" />}
                          <p className="text-xs leading-relaxed text-ink-2 pt-1 break-words">{item.explanation}</p>
                        </div>
                      </div>
                      <div className="flex flex-wrap items-center justify-between lg:justify-end gap-3 pt-2 lg:pt-0 border-t lg:border-t-0 border-line shrink-0">
                        <div className="font-heading font-bold text-sm sm:text-base text-success-strong">{Math.round(item.matchScore)}% phù hợp</div>
                        <div className="flex items-center gap-2">
                          <button type="button" onClick={() => setSelectedExplanation(item)}
                            className="px-3.5 py-1.5 rounded-full text-xs font-medium text-ink-2 bg-sunken hover:bg-line border border-transparent">
                            Xem lý do
                          </button>
                          <button type="button" onClick={() => onRequestConnect?.(item.tutorId)}
                            className="px-4 py-1.5 rounded-full text-xs font-medium text-white bg-brand hover:bg-brand-deep">
                            Gửi yêu cầu
                          </button>
                        </div>
                      </div>
                    </div>
                  </React.Fragment>
                );
              })}
            </div>
          )}
        </>
      ) : null}

      {dialog && (
        <LearningNeedModal
          isOpen
          studentId={studentId}
          {...dialogProps}
          onClose={() => setDialog(null)}
          onSaved={async (saved) => {
            setDialog(null);
            await matching.refreshNeeds(saved.needId);
          }}
        />
      )}

      {selectedExplanation && (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4 bg-night/50 backdrop-blur-xs overflow-y-auto">
          <div role="dialog" aria-modal="true" aria-labelledby="match-explanation-title"
            className="bg-surface rounded-3xl max-w-dialog-confirm w-full border border-line shadow-2xl p-6 space-y-5 my-auto">
            <div className="flex items-center justify-between gap-3 border-b border-line pb-3">
              <div className="flex items-center gap-2 min-w-0">
                <Sparkles className="size-4.5 text-brand-strong shrink-0" />
                <h4 id="match-explanation-title" className="font-heading font-bold text-base text-ink">
                  Tại sao phù hợp {Math.round(selectedExplanation.matchScore)}%?
                </h4>
              </div>
              <button type="button" aria-label="Đóng" onClick={() => setSelectedExplanation(null)}
                className="p-1.5 text-muted hover:text-ink-2 rounded-full">
                <X className="size-4" />
              </button>
            </div>

            <div className="p-3 bg-paper rounded-2xl min-w-0">
              <p className="font-heading font-bold text-sm text-ink break-words">{selectedExplanation.tutorInfo.displayName}</p>
              <p className="text-xs text-muted break-words">
                {selectedExplanation.tutorInfo.fit?.offeringName ?? selectedExplanation.tutorInfo.headline}
              </p>
            </div>

            <p className="text-xs text-ink-2 leading-relaxed bg-brand-soft/60 p-3.5 rounded-2xl border border-brand-line break-words">
              {selectedExplanation.explanation}
            </p>

            {selectedExplanation.tutorInfo.fit && (
              <div className="space-y-2">
                <span className="text-xs font-medium text-muted block">Từng tiêu chí</span>
                <FitChecks checks={selectedExplanation.tutorInfo.fit.checks} />
              </div>
            )}

            <div className="pt-2 flex flex-wrap justify-end gap-2">
              <button type="button"
                onClick={() => { const id = selectedExplanation.tutorId; setSelectedExplanation(null); onViewDetail?.(id); }}
                className="px-4 py-2 rounded-full text-xs font-medium text-ink-2 bg-sunken hover:bg-line border border-transparent">
                Xem toàn bộ hồ sơ
              </button>
              <button type="button"
                onClick={() => { const id = selectedExplanation.tutorId; setSelectedExplanation(null); onRequestConnect?.(id); }}
                className="px-4 py-2 rounded-full text-xs font-medium text-white bg-brand hover:bg-brand-deep">
                Gửi yêu cầu ngay
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
