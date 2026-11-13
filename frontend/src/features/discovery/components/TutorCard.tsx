"use client";

import React from "react";
import { Star, CheckCircle2 } from "lucide-react";
import { TUTOR_TYPE_LABELS } from "@/features/learning-needs/catalogRules";
import type { TutorDiscoveryCard } from "../types";
import { FitChecks } from "./FitChecks";
import {
  TEACHING_MODE_TEXT,
  formatRating,
  formatVnd,
  offeringsSummary,
  tutorAreaText,
} from "../utils/tutorDisplay";

export interface TutorCardProps {
  tutor: TutorDiscoveryCard;
  onViewDetail?: (tutorId: string) => void;
  onRequestConnect?: (tutorId: string) => void;
  layout?: "list" | "grid";
}

export function TutorCard({ tutor, onViewDetail, onRequestConnect, layout = "list" }: TutorCardProps) {
  const isGrid = layout === "grid";
  // Khi tìm kèm nhu cầu học, học phí là của gói dạy khớp với nhu cầu đó
  const price = tutor.fit ? tutor.fit.pricePerSession : tutor.minPricePerSession;
  const priceLabel = tutor.fit || tutor.offerings.length <= 1 ? "/ buổi" : "/ buổi (từ)";
  const tutorType = tutor.tutorType ? TUTOR_TYPE_LABELS[tutor.tutorType] : null;

  return (
    <div className={`bg-surface rounded-2xl border border-line/80 p-4 sm:p-5 card-lift flex flex-col justify-between gap-4 ${isGrid ? "" : "md:flex-row md:items-center"}`}>
      <div className="flex items-start gap-3.5 flex-1 min-w-0">
        <div className="size-12 rounded-full bg-brand-soft text-brand-strong flex items-center justify-center font-medium text-base shrink-0 shadow-2xs">
          {tutor.displayName.charAt(0).toUpperCase()}
        </div>

        <div className="min-w-0 flex-1 space-y-1">
          <div className="flex items-center gap-1.5 min-w-0">
            <h4 className="font-heading font-bold text-[15px] text-ink truncate">{tutor.displayName}</h4>
            {tutor.verified && <CheckCircle2 aria-label="Đã xác minh" className="size-4 text-success-strong fill-success shrink-0" />}
            {tutor.fit && (
              <span className="ml-auto md:ml-2 shrink-0 text-xs font-medium text-success-strong">
                {Math.round(tutor.fit.matchScore)}% phù hợp
              </span>
            )}
          </div>
          <p className="text-xs text-ink-2 font-medium break-words">
            {tutor.fit ? tutor.fit.offeringName : offeringsSummary(tutor.offerings)}
          </p>
          <p className="text-xs text-muted break-words">
            {[
              tutorType && (tutor.tutorTypeVerified ? `${tutorType} (đã xác minh)` : tutorType),
              tutor.experienceYears ? `${tutor.experienceYears} năm kinh nghiệm` : null,
              `${TEACHING_MODE_TEXT[tutor.teachingMode]} · ${tutorAreaText(tutor)}`,
            ].filter(Boolean).join(" · ")}
          </p>
          <p className="text-xs text-muted inline-flex items-center gap-1">
            <Star className="size-3.5 fill-action text-action-strong" />
            {formatRating(tutor.ratingAvg, tutor.reviewCount)}
          </p>
          {tutor.fit && <FitChecks checks={tutor.fit.checks} className="pt-1" />}
        </div>
      </div>

      <div className={`flex flex-wrap items-center justify-between gap-3 w-full pt-3 border-t border-line shrink-0 ${isGrid ? "" : "md:justify-end md:w-auto md:pt-0 md:border-t-0"}`}>
        <div className={isGrid ? "text-left" : "text-left md:text-right"}>
          <span className="font-heading font-bold text-sm sm:text-base text-ink">{formatVnd(price)}</span>
          {price != null && <span className="text-xs text-muted"> {priceLabel}</span>}
        </div>

        <div className="flex items-center gap-2">
          {onViewDetail && (
            <button type="button" onClick={() => onViewDetail(tutor.tutorId)}
              className="px-4 py-2 rounded-full text-xs font-medium text-ink-2 bg-sunken hover:bg-line transition-colors cursor-pointer">
              Xem hồ sơ
            </button>
          )}
          {onRequestConnect && (
            <button type="button" onClick={() => onRequestConnect(tutor.tutorId)}
              className="px-4 py-2 rounded-full text-xs font-medium text-white bg-brand hover:bg-brand-deep transition-colors cursor-pointer">
              Gửi yêu cầu
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
