"use client";

import React, { useState, useEffect } from "react";
import {
  X,
  Star,
  ShieldCheck,
  MapPin,
  Clock,
  Briefcase,
  GraduationCap,
  MessageSquare,
  PenLine,
} from "lucide-react";
import { TUTOR_TYPE_LABELS } from "@/features/learning-needs/catalogRules";
import type { TutorDetail } from "../types";
import { OfferingList } from "./OfferingList";
import { TEACHING_MODE_TEXT, formatRating, formatVnd, tutorAreaText } from "../utils/tutorDisplay";
import {
  RatingSummaryCard,
  ReviewList,
  ReviewModal,
  reviewApi,
  type ReviewItem,
  type TutorRatingSummary,
} from "@/features/review";
import { useAuth } from "@/context/AuthContext";

export interface TutorDetailModalProps {
  isOpen: boolean;
  tutor: TutorDetail | null;
  isLoading: boolean;
  onClose: () => void;
  onRequestConnect?: (tutorId: string) => void;
}

const DAY_LABELS: Record<number, string> = {
  1: "Thứ 2",
  2: "Thứ 3",
  3: "Thứ 4",
  4: "Thứ 5",
  5: "Thứ 6",
  6: "Thứ 7",
  7: "Chủ Nhật",
};

export function TutorDetailModal({
  isOpen,
  tutor,
  isLoading,
  onClose,
  onRequestConnect,
}: TutorDetailModalProps) {
  const { user } = useAuth();
  const [reviews, setReviews] = useState<ReviewItem[]>([]);
  const [ratingSummary, setRatingSummary] = useState<TutorRatingSummary | null>(null);
  const [isLoadingReviews, setIsLoadingReviews] = useState(false);
  const [isReviewModalOpen, setIsReviewModalOpen] = useState(false);
  const [editingReview, setEditingReview] = useState<ReviewItem | null>(null);

  const loadReviewData = async (tutorId: string) => {
    try {
      setIsLoadingReviews(true);
      const [sumRes, revRes] = await Promise.all([
        reviewApi.getTutorRatingSummary(tutorId),
        reviewApi.getTutorReviews(tutorId, 0, 10),
      ]);
      if (sumRes.success && sumRes.data) {
        setRatingSummary(sumRes.data);
      }
      if (revRes.success && revRes.data) {
        setReviews(revRes.data.content || []);
      }
    } catch {
      // Ignored for resilience
    } finally {
      setIsLoadingReviews(false);
    }
  };

  useEffect(() => {
    if (isOpen && tutor?.tutorId) {
      loadReviewData(tutor.tutorId);
    } else {
      setReviews([]);
      setRatingSummary(null);
      setEditingReview(null);
    }
  }, [isOpen, tutor?.tutorId]);

  if (!isOpen) return null;

  const prices = tutor?.offerings.map((offering) => offering.pricePerSession) ?? [];
  const minPrice = prices.length > 0 ? Math.min(...prices) : null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6 overflow-y-auto bg-black/50 backdrop-blur-xs animate-in fade-in duration-200">
      <div
        className="relative w-full max-w-3xl bg-white rounded-3xl shadow-2xl border border-stone-200 overflow-hidden my-8 max-h-[90vh] flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header bar */}
        <div className="flex items-center justify-between p-5 sm:p-6 border-b border-stone-100 bg-stone-50/50">
          <div className="flex items-center gap-2">
            <GraduationCap className="size-5 text-amber-600" />
            <h2 className="font-heading font-bold text-base sm:text-lg text-neutral-900">
              Hồ sơ chi tiết gia sư
            </h2>
          </div>
          <button
            type="button"
            aria-label="Đóng"
            onClick={onClose}
            className="p-1.5 rounded-full text-neutral-400 hover:text-neutral-700 hover:bg-stone-200/60 transition-colors cursor-pointer"
          >
            <X className="size-5" />
          </button>
        </div>

        {/* Content body */}
        <div className="p-6 sm:p-8 space-y-6 overflow-y-auto flex-1">
          {isLoading || !tutor ? (
            <div className="py-16 flex flex-col items-center justify-center gap-3">
              <div className="size-8 border-3 border-amber-500/30 border-t-amber-500 rounded-full animate-spin" />
              <span className="text-xs text-neutral-500 font-medium">Đang tải hồ sơ gia sư...</span>
            </div>
          ) : (
            <>
              {/* Profile Top Banner */}
              <div className="flex flex-col sm:flex-row items-start sm:items-center gap-5 p-5 rounded-2xl bg-amber-50/40 border border-amber-100">
                <div className="size-18 rounded-2xl bg-gradient-to-tr from-amber-500 to-orange-400 text-white font-heading font-black text-2xl flex items-center justify-center shadow-xs shrink-0">
                  {tutor.displayName.charAt(0).toUpperCase()}
                </div>

                <div className="flex-1 space-y-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="font-heading font-bold text-xl text-neutral-900">{tutor.displayName}</h3>
                    {tutor.verified && (
                      <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-800">
                        <ShieldCheck className="size-3 text-emerald-600" />
                        Đã xác minh KYC
                      </span>
                    )}
                  </div>
                  {tutor.headline && <p className="text-xs text-neutral-600 font-medium">{tutor.headline}</p>}

                  <div className="flex flex-wrap items-center gap-3 pt-1 text-xs text-neutral-600">
                    <span className="inline-flex items-center gap-1 font-bold text-amber-800 bg-amber-100 px-2 py-0.5 rounded-lg">
                      <Star className="size-3.5 fill-amber-500 text-amber-500" />
                      {formatRating(tutor.ratingAvg, tutor.reviewCount)}
                    </span>
                    {tutor.tutorType && (
                      <span className="inline-flex items-center gap-1">
                        <GraduationCap className="size-3.5 text-neutral-400" />
                        {TUTOR_TYPE_LABELS[tutor.tutorType]}
                        {tutor.tutorTypeVerified && <ShieldCheck aria-label="Đã xác minh" className="size-3.5 text-emerald-600" />}
                      </span>
                    )}
                    {tutor.experienceYears != null && (
                      <span className="inline-flex items-center gap-1">
                        <Briefcase className="size-3.5 text-neutral-400" />
                        {tutor.experienceYears} năm kinh nghiệm
                      </span>
                    )}
                    <span className="inline-flex items-center gap-1 min-w-0">
                      <MapPin className="size-3.5 text-neutral-400 shrink-0" />
                      <span className="break-words">{TEACHING_MODE_TEXT[tutor.teachingMode]} · {tutorAreaText(tutor)}</span>
                    </span>
                  </div>
                </div>
              </div>

              {/* Tiểu sử (Bio) */}
              <div className="space-y-2">
                <h4 className="text-xs font-bold text-neutral-800 uppercase tracking-wider">Giới thiệu bản thân</h4>
                <p className="text-xs text-neutral-600 leading-relaxed whitespace-pre-line bg-stone-50 p-4 rounded-2xl border border-stone-200/80">
                  {tutor.bio || "Gia sư chưa cập nhật phần giới thiệu bản thân."}
                </p>
              </div>

              {/* Phương pháp giảng dạy */}
              {tutor.teachingMethod && (
                <div className="space-y-2">
                  <h4 className="text-xs font-bold text-neutral-800 uppercase tracking-wider">Phương pháp giảng dạy</h4>
                  <p className="text-xs text-neutral-600 leading-relaxed bg-stone-50 p-4 rounded-2xl border border-stone-200/80">
                    {tutor.teachingMethod}
                  </p>
                </div>
              )}

              {/* Gói dạy */}
              <div className="space-y-2">
                <h4 className="text-xs font-bold text-neutral-800 uppercase tracking-wider">Gói dạy</h4>
                <OfferingList offerings={tutor.offerings} />
              </div>

              {/* Ma trận khung giờ rảnh trong tuần */}
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <h4 className="text-xs font-bold text-neutral-800 uppercase tracking-wider">Khung giờ rảnh trong tuần</h4>
                  <span className="text-[11px] text-neutral-400">Giờ địa phương</span>
                </div>

                {tutor.availabilitySlots && tutor.availabilitySlots.length > 0 ? (
                  <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                    {tutor.availabilitySlots.map((slot, idx) => (
                      <div
                        key={idx}
                        className="p-2.5 rounded-xl bg-amber-50/50 border border-amber-200/60 text-xs flex items-center gap-2"
                      >
                        <Clock className="size-3.5 text-amber-600 shrink-0" />
                        <div>
                          <span className="font-bold text-neutral-900 block">{DAY_LABELS[slot.dayOfWeek] || `Thứ ${slot.dayOfWeek}`}</span>
                          <span className="text-[11px] text-neutral-600 font-medium">
                            {slot.startTime} - {slot.endTime}
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-neutral-400 italic p-3 bg-stone-50 rounded-xl">
                    Gia sư chưa thiết lập khung giờ rảnh cụ thể.
                  </p>
                )}
              </div>

              {/* Đánh giá & Nhận xét từ học sinh / phụ huynh */}
              <div className="space-y-4 pt-4 border-t border-stone-200">
                <div className="flex items-center justify-between">
                  <h4 className="text-xs font-bold text-neutral-800 uppercase tracking-wider">
                    Đánh giá & Nhận xét
                  </h4>
                  {user && (user.role === "STUDENT" || user.role === "PARENT") && (
                    <button
                      type="button"
                      onClick={() => {
                        setEditingReview(null);
                        setIsReviewModalOpen(true);
                      }}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-bold text-amber-700 bg-amber-50 hover:bg-amber-100 border border-amber-200 transition-colors cursor-pointer"
                    >
                      <PenLine className="size-3.5" />
                      <span>Viết đánh giá</span>
                    </button>
                  )}
                </div>

                {ratingSummary && ratingSummary.totalReviews > 0 ? (
                  <RatingSummaryCard summary={ratingSummary} />
                ) : (
                  <div className="p-4 rounded-xl bg-stone-50 border border-stone-200/60 text-xs text-neutral-500 text-center">
                    Gia sư này hiện chưa có đánh giá nào.
                  </div>
                )}

                {isLoadingReviews ? (
                  <div className="py-8 flex justify-center text-xs text-neutral-400">
                    Đang tải đánh giá...
                  </div>
                ) : (
                  <ReviewList
                    reviews={reviews}
                    currentUserId={user?.id}
                    onEditReview={(rev) => {
                      setEditingReview(rev);
                      setIsReviewModalOpen(true);
                    }}
                  />
                )}
              </div>
            </>
          )}
        </div>

        {/* Footer */}
        {tutor && !isLoading && (
          <div className="p-5 sm:p-6 bg-stone-50 border-t border-stone-200 flex flex-wrap items-center justify-between gap-4">
            <div>
              <span className="text-[10px] text-neutral-400 block font-medium">
                {prices.length > 1 ? "Học phí từ" : "Học phí"}
              </span>
              <span className="text-base sm:text-lg font-black text-neutral-900">{formatVnd(minPrice)}</span>
              {minPrice != null && <span className="text-xs text-neutral-500"> /buổi</span>}
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2.5 rounded-xl text-xs font-bold text-neutral-700 bg-white hover:bg-stone-100 border border-stone-200 transition-colors cursor-pointer"
              >
                Đóng
              </button>
              {onRequestConnect && (
                <button
                  type="button"
                  onClick={() => onRequestConnect(tutor.tutorId)}
                  className="inline-flex items-center gap-1.5 px-5 py-2.5 rounded-xl text-xs font-bold text-white bg-amber-500 hover:bg-amber-600 shadow-xs transition-colors cursor-pointer"
                >
                  <MessageSquare className="size-4" />
                  <span>Gửi lời mời học thử</span>
                </button>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Review Modal */}
      {isReviewModalOpen && tutor && (
        <ReviewModal
          isOpen={isReviewModalOpen}
          onClose={() => {
            setIsReviewModalOpen(false);
            setEditingReview(null);
          }}
          tutorId={tutor.tutorId}
          reviewerRole={user?.role}
          tutorName={tutor.displayName}
          existingReview={editingReview}
          onSuccess={() => {
            setIsReviewModalOpen(false);
            setEditingReview(null);
            loadReviewData(tutor.tutorId);
          }}
        />
      )}
    </div>
  );
}
