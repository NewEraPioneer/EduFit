"use client";

import React, { useState } from "react";
import {
  Sparkles,
  Target,
  CheckCircle2,
  X,
} from "lucide-react";
import type { TutorMatchResult, MatchingNeed } from "../types";
import { MatchingNeedModal } from "./MatchingNeedModal";

export interface AiMatchViewProps {
  matches: TutorMatchResult[];
  isMatching: boolean;
  error: string | null;
  need: MatchingNeed;
  onUpdateNeed?: (newNeed: MatchingNeed) => void | Promise<void>;
  onViewDetail?: (tutorId: string) => void;
  onRequestConnect?: (tutorId: string) => void;
}

export function AiMatchView({
  matches,
  isMatching,
  error,
  need,
  onUpdateNeed,
  onViewDetail,
  onRequestConnect,
}: AiMatchViewProps) {
  const [isEditNeedOpen, setIsEditNeedOpen] = useState(false);
  const [selectedExplanation, setSelectedExplanation] = useState<TutorMatchResult | null>(null);

  // Loading Skeleton State
  if (isMatching) {
    return (
      <div className="space-y-4">
        {/* Banner Skeleton */}
        <div className="p-4 sm:p-5 rounded-2xl bg-stone-100/80 animate-pulse h-20" />
        <div className="h-6 w-36 bg-stone-100 rounded-md animate-pulse" />
        {[1, 2, 3].map((i) => (
          <div
            key={i}
            className="h-28 rounded-2xl bg-white border border-stone-200/70 p-5 animate-pulse flex items-center justify-between"
          >
            <div className="flex items-center gap-4">
              <div className="size-8 rounded-full bg-stone-100" />
              <div className="size-12 rounded-full bg-stone-100" />
              <div className="space-y-2">
                <div className="h-4 w-32 bg-stone-100 rounded-md" />
                <div className="h-3 w-48 bg-stone-100 rounded-md" />
              </div>
            </div>
            <div className="h-9 w-28 bg-stone-100 rounded-full" />
          </div>
        ))}
      </div>
    );
  }

  const modeText =
    need.teachingMode === "ONLINE"
      ? "Chỉ học Online"
      : need.teachingMode === "OFFLINE"
      ? `Học tại ${need.area}`
      : `Online hoặc ${need.area}`;

  return (
    <div className="space-y-6">
      {/* 1. MATCHING NEED SUMMARY BANNER (Figma 36:3764) */}
      <div className="p-4 sm:p-5 rounded-2xl bg-[#F7F6F3] border border-stone-200/80 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="flex items-center gap-3.5">
          <div className="size-11 rounded-full bg-[#006DCC] text-white flex items-center justify-center shrink-0 shadow-2xs">
            <Target className="size-5.5 text-white" />
          </div>
          <div>
            <h4 className="font-heading font-bold text-sm text-neutral-900">
              Nhu cầu đang dùng để Matching
            </h4>
            <p className="text-xs text-neutral-600 mt-0.5">
              {need.goalId ? `${need.subjectName} ${need.gradeName} · ${need.targetDescription} · ${modeText} · ${need.timeSlotsDescription}` : "Chưa chọn mục tiêu học tập từ hệ thống"}
            </p>
          </div>
        </div>

        {onUpdateNeed && <button
          type="button"
          onClick={() => setIsEditNeedOpen(true)}
          className="px-4 py-2 rounded-full bg-white hover:bg-stone-50 border border-stone-200 text-neutral-800 text-xs font-semibold shadow-2xs transition-all cursor-pointer self-start sm:self-auto shrink-0"
        >
          Thay đổi nhu cầu
        </button>}
      </div>

      {/* 2. SUBHEADER ROW */}
      {error && (
        <div role="alert" className="px-4 py-3 rounded-2xl bg-rose-50 border border-rose-200 text-xs text-rose-700">
          {error}
        </div>
      )}

      <div className="flex items-center justify-between text-xs pt-1">
        <div className="font-heading font-bold text-sm text-neutral-900">
          {matches.length} gợi ý phù hợp
        </div>

        <div className="flex items-center gap-1.5 text-purple-700 font-semibold text-[11px] bg-purple-50 px-2.5 py-1 rounded-full">
          <Sparkles className="size-3 text-purple-600" />
          <span>{matches.some(match => match.aiGenerated) ? "Có giải thích từ AI" : "Gợi ý theo tiêu chí phù hợp"}</span>
        </div>
      </div>

      {/* 3. RANKED MATCHES LIST (Figma 36:3764 Cards) */}
      {matches.length === 0 ? (
        <div className="bg-white p-12 rounded-3xl border border-stone-200 text-center space-y-3">
          <Target className="size-8 text-neutral-400 mx-auto" />
          <h3 className="font-heading font-bold text-base text-neutral-900">
            Chưa có gia sư nào phù hợp với yêu cầu này
          </h3>
          <p className="text-xs text-neutral-500 max-w-md mx-auto">
            Hãy bấm &quot;Thay đổi nhu cầu&quot; để thử tìm với môn học khác hoặc mở rộng khung giờ học.
          </p>
          <button
            type="button"
            onClick={() => setIsEditNeedOpen(true)}
            className="px-4 py-2 rounded-full bg-[#006DCC] text-white text-xs font-semibold transition-colors cursor-pointer"
          >
            Tùy chỉnh nhu cầu học
          </button>
        </div>
      ) : (
        <div className="space-y-3">
          {matches.map((item, idx) => {
            const tutor = item.tutorInfo;
            const rankStr = String(idx + 1).padStart(2, "0");

            return (
              <React.Fragment key={item.tutorId}>
              {idx === 0 && <h3 className="font-heading font-bold text-base text-neutral-900">Gợi ý phù hợp nhất</h3>}
              {idx === 1 && <h3 className="font-heading font-bold text-sm text-neutral-700 pt-5">Các gia sư khác</h3>}
              <div
                key={item.tutorId}
                className={`bg-white rounded-2xl border p-4 sm:p-5 hover:border-sky-300 hover:shadow-xs transition-all flex flex-col lg:flex-row lg:items-center justify-between gap-4 ${idx === 0 ? "border-sky-400 ring-1 ring-sky-100" : "border-stone-200/80"}`}
              >
                {/* Left: Rank + Avatar + Basic Info */}
                <div className="flex items-start sm:items-center gap-3.5 flex-1 min-w-0">
                  {/* Rank Badge */}
                  <div className="size-7 sm:size-8 rounded-full bg-neutral-900 text-white font-bold text-xs flex items-center justify-center shrink-0">
                    {rankStr}
                  </div>

                  {/* Avatar */}
                  <div className="size-12 rounded-full bg-sky-100 text-[#006DCC] flex items-center justify-center font-bold text-base shrink-0 shadow-2xs">
                    {tutor.displayName.charAt(0).toUpperCase()}
                  </div>

                  {/* Info */}
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-1.5">
                      <h4 className="font-heading font-bold text-[15px] text-neutral-900 truncate">
                        {tutor.displayName}
                      </h4>
                      {tutor.verified && (
                        <CheckCircle2 className="size-4 text-emerald-500 fill-emerald-50 shrink-0" />
                      )}
                    </div>
                    <p className="text-xs text-neutral-600 font-medium truncate mt-0.5">
                      {tutor.subjects?.[0]?.subjectName || need.subjectName} ·{" "}
                      {tutor.subjects?.[0]?.educationLevelName || need.gradeName}
                      {tutor.experienceYears !== undefined && ` · ${tutor.experienceYears} năm kinh nghiệm`}
                    </p>
                    <p className="text-[11px] text-neutral-400 mt-0.5 flex items-center gap-1">
                      <span className="text-amber-500 font-semibold">★</span>
                      <span>{tutor.ratingAvg != null ? tutor.ratingAvg.toFixed(1) : "Chưa có"} ({tutor.reviewCount ?? 0} đánh giá)</span>
                    </p>
                  </div>
                </div>

                {/* Middle: Bullet Points Reasons */}
                <div className="flex-1 lg:max-w-xs space-y-1 text-xs text-neutral-600 border-t lg:border-t-0 pt-2 lg:pt-0 border-stone-100">
                  <p className="text-[11px] font-semibold text-purple-700">{item.aiGenerated ? "Nhận xét từ AI" : "Nhận xét theo dữ liệu"}</p>
                  <p className="text-xs leading-relaxed text-neutral-700">{item.explanation}</p>
                </div>

                {/* Right: Match Score & Action Buttons */}
                <div className="flex items-center justify-between lg:justify-end gap-3.5 pt-2 lg:pt-0 border-t lg:border-t-0 border-stone-100 shrink-0">
                  <div className="font-heading font-bold text-sm sm:text-base text-emerald-600">
                    {Math.round(item.matchScore)}% phù hợp
                  </div>

                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => setSelectedExplanation(item)}
                      className="px-3.5 py-1.5 rounded-full text-xs font-semibold text-neutral-700 bg-stone-100 hover:bg-stone-200 transition-colors cursor-pointer"
                    >
                      Xem lý do
                    </button>
                    <button
                      type="button"
                      onClick={() => onRequestConnect?.(item.tutorId)}
                      className="px-4 py-1.5 rounded-full text-xs font-semibold text-white bg-[#006DCC] hover:bg-[#005bb5] transition-colors cursor-pointer"
                    >
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

      {/* 4. MODAL THAY ĐỔI NHU CẦU */}
      {isEditNeedOpen && (
        <MatchingNeedModal
          isOpen
          currentNeed={need}
          onClose={() => setIsEditNeedOpen(false)}
          onSave={(newNeed) => onUpdateNeed?.(newNeed)}
        />
      )}

      {/* 5. MODAL XEM LÝ DO GIẢI THÍCH CHI TIẾT (EXPLANATION MODAL) */}
      {selectedExplanation && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-neutral-950/50 backdrop-blur-xs animate-in fade-in duration-150 overflow-y-auto">
          <div className="bg-white rounded-3xl max-w-md w-full border border-stone-200 shadow-2xl p-6 space-y-5 animate-in zoom-in-95 duration-150 my-auto">
            <div className="flex items-center justify-between border-b border-stone-100 pb-3">
              <div className="flex items-center gap-2">
                <Sparkles className="size-4.5 text-purple-600" />
                <h4 className="font-heading font-bold text-base text-neutral-900">
                  Tại sao phù hợp {Math.round(selectedExplanation.matchScore)}%?
                </h4>
              </div>
              <button
                type="button"
                onClick={() => setSelectedExplanation(null)}
                className="p-1.5 text-neutral-400 hover:text-neutral-700 rounded-full"
              >
                <X className="size-4" />
              </button>
            </div>

            {/* Tutor Header */}
            <div className="flex items-center gap-3 p-3 bg-stone-50 rounded-2xl">
              <div className="size-11 rounded-full bg-sky-100 text-[#006DCC] font-bold flex items-center justify-center text-sm">
                {selectedExplanation.tutorInfo.displayName.charAt(0)}
              </div>
              <div>
                <p className="font-heading font-bold text-sm text-neutral-900">
                  {selectedExplanation.tutorInfo.displayName}
                </p>
                <p className="text-xs text-neutral-500">
                  {selectedExplanation.tutorInfo.headline || "Gia sư chuyên môn đã qua kiểm duyệt"}
                </p>
              </div>
            </div>

            {/* Explanation paragraph */}
            <p className="text-xs text-neutral-700 leading-relaxed bg-purple-50/60 p-3.5 rounded-2xl border border-purple-100">
              {selectedExplanation.explanation}
            </p>

            {/* Four criteria; subject and level jointly form learning need. */}
            <div className="space-y-2.5 pt-1">
              <span className="text-[11px] font-bold text-neutral-400 uppercase tracking-wider block">
                4 tiêu chí đánh giá
              </span>

              <div className="space-y-2 text-xs">
                <div>
                  <div className="flex justify-between text-neutral-700 mb-1">
                    <span>Nhu cầu học · 40%</span>
                    <span className="font-bold text-neutral-900">
                      {Math.round((selectedExplanation.scoreBreakdown.subjectFit + selectedExplanation.scoreBreakdown.levelFit) / 2)}%
                    </span>
                  </div>
                  <div className="h-1.5 bg-stone-100 rounded-full overflow-hidden">
                    <div
                      className="h-full bg-emerald-500 rounded-full"
                      style={{ width: `${(selectedExplanation.scoreBreakdown.subjectFit + selectedExplanation.scoreBreakdown.levelFit) / 2}%` }}
                    />
                  </div>
                </div>

                <div>
                  <div className="flex justify-between text-neutral-700 mb-1">
                    <span>Lịch & hình thức · 25% ({selectedExplanation.scoreBreakdown.overlappingSlots} khung giờ)</span>
                    <span className="font-bold text-neutral-900">
                      {Math.round(selectedExplanation.scoreBreakdown.scheduleFit)}%
                    </span>
                  </div>
                  <div className="h-1.5 bg-stone-100 rounded-full overflow-hidden">
                    <div
                      className="h-full bg-sky-500 rounded-full"
                      style={{ width: `${selectedExplanation.scoreBreakdown.scheduleFit}%` }}
                    />
                  </div>
                </div>

                <div>
                  <div className="flex justify-between text-neutral-700 mb-1">
                    <span>Ngân sách · 10%</span>
                    <span className="font-bold text-neutral-900">
                      {Math.round(selectedExplanation.scoreBreakdown.budgetFit)}%
                    </span>
                  </div>
                  <div className="h-1.5 bg-stone-100 rounded-full overflow-hidden">
                    <div
                      className="h-full bg-amber-500 rounded-full"
                      style={{ width: `${selectedExplanation.scoreBreakdown.budgetFit}%` }}
                    />
                  </div>
                </div>

                <div>
                  <div className="flex justify-between text-neutral-700 mb-1">
                    <span>Năng lực & độ tin cậy · 25%</span>
                    <span className="font-bold text-neutral-900">
                      {Math.round(selectedExplanation.scoreBreakdown.ratingFit)}%
                    </span>
                  </div>
                  <div className="h-1.5 bg-stone-100 rounded-full overflow-hidden">
                    <div
                      className="h-full bg-purple-500 rounded-full"
                      style={{ width: `${selectedExplanation.scoreBreakdown.ratingFit}%` }}
                    />
                  </div>
                </div>
              </div>
            </div>

            <div className="pt-2 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => {
                  setSelectedExplanation(null);
                  onViewDetail?.(selectedExplanation.tutorId);
                }}
                className="px-4 py-2 rounded-full text-xs font-semibold text-neutral-700 bg-stone-100 hover:bg-stone-200 transition-colors"
              >
                Xem toàn bộ hồ sơ
              </button>
              <button
                type="button"
                onClick={() => {
                  const id = selectedExplanation.tutorId;
                  setSelectedExplanation(null);
                  onRequestConnect?.(id);
                }}
                className="px-4 py-2 rounded-full text-xs font-semibold text-white bg-[#006DCC] hover:bg-[#005bb5] transition-colors"
              >
                Gửi yêu cầu ngay
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
