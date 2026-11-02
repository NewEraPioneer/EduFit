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
}

export function TutorCard({ tutor, onViewDetail, onRequestConnect }: TutorCardProps) {
  // Khi tìm kèm nhu cầu học, học phí là của gói dạy khớp với nhu cầu đó
  const price = tutor.fit ? tutor.fit.pricePerSession : tutor.minPricePerSession;
  const priceLabel = tutor.fit || tutor.offerings.length <= 1 ? "/ buổi" : "/ buổi (từ)";
  const tutorType = tutor.tutorType ? TUTOR_TYPE_LABELS[tutor.tutorType] : null;

  return (
    <div className="bg-white rounded-2xl border border-stone-200/80 p-4 sm:p-5 hover:border-sky-300 hover:shadow-xs transition-all flex flex-col md:flex-row md:items-center justify-between gap-4">
      <div className="flex items-start gap-3.5 flex-1 min-w-0">
        <div className="size-12 rounded-full bg-sky-100 text-[#006DCC] flex items-center justify-center font-bold text-base shrink-0 shadow-2xs">
          {tutor.displayName.charAt(0).toUpperCase()}
        </div>

        <div className="min-w-0 flex-1 space-y-1">
          <div className="flex items-center gap-1.5 min-w-0">
            <h4 className="font-heading font-bold text-[15px] text-neutral-900 truncate">{tutor.displayName}</h4>
            {tutor.verified && <CheckCircle2 aria-label="Đã xác minh" className="size-4 text-emerald-500 fill-emerald-50 shrink-0" />}
            {tutor.fit && (
              <span className="ml-auto md:ml-2 shrink-0 text-xs font-bold text-emerald-700">
                {Math.round(tutor.fit.matchScore)}% phù hợp
              </span>
            )}
          </div>
          <p className="text-xs text-neutral-600 font-medium break-words">
            {tutor.fit ? tutor.fit.offeringName : offeringsSummary(tutor.offerings)}
          </p>
          <p className="text-[11px] text-neutral-500 break-words">
            {[
              tutorType && (tutor.tutorTypeVerified ? `${tutorType} (đã xác minh)` : tutorType),
              tutor.experienceYears ? `${tutor.experienceYears} năm kinh nghiệm` : null,
              `${TEACHING_MODE_TEXT[tutor.teachingMode]} · ${tutorAreaText(tutor)}`,
            ].filter(Boolean).join(" · ")}
          </p>
          <p className="text-[11px] text-neutral-500 inline-flex items-center gap-1">
            <Star className="size-3.5 fill-amber-400 text-amber-400" />
            {formatRating(tutor.ratingAvg, tutor.reviewCount)}
          </p>
          {tutor.fit && <FitChecks checks={tutor.fit.checks} className="pt-1" />}
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-between md:justify-end gap-3 w-full md:w-auto pt-3 md:pt-0 border-t md:border-t-0 border-stone-100 shrink-0">
        <div className="text-left md:text-right">
          <span className="font-heading font-bold text-sm sm:text-base text-neutral-900">{formatVnd(price)}</span>
          {price != null && <span className="text-[11px] text-neutral-400"> {priceLabel}</span>}
        </div>

        <div className="flex items-center gap-2">
          {onViewDetail && (
            <button type="button" onClick={() => onViewDetail(tutor.tutorId)}
              className="px-4 py-2 rounded-full text-xs font-semibold text-neutral-700 bg-stone-100 hover:bg-stone-200 transition-colors cursor-pointer">
              Xem hồ sơ
            </button>
          )}
          {onRequestConnect && (
            <button type="button" onClick={() => onRequestConnect(tutor.tutorId)}
              className="px-4 py-2 rounded-full text-xs font-semibold text-white bg-[#006DCC] hover:bg-[#005bb5] transition-colors cursor-pointer">
              Gửi yêu cầu
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
