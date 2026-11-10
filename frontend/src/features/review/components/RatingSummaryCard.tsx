"use client";

import React from "react";
import { Star } from "lucide-react";
import type { TutorRatingSummary } from "../types";

interface RatingSummaryCardProps {
  summary: TutorRatingSummary;
  className?: string;
}

export const RatingSummaryCard: React.FC<RatingSummaryCardProps> = ({
  summary,
  className = "",
}) => {
  const { averageRating, totalReviews, ratingDistribution } = summary;

  return (
    <div
      role="region"
      aria-label="Tóm tắt đánh giá"
      className={`p-6 rounded-2xl bg-surface border border-line shadow-sm flex flex-col sm:flex-row gap-6 items-center ${className}`}
    >
      {/* Cột trái: Điểm tổng quan */}
      <div className="flex flex-col items-center justify-center text-center sm:border-r sm:border-line sm:pr-8 min-w-[140px]">
        <span data-testid="rating-average" className="text-4xl font-bold text-ink tracking-tight">
          {averageRating > 0 ? averageRating.toFixed(1) : "0.0"}
        </span>
        <div className="flex items-center gap-1 mt-2 text-action-strong">
          {[1, 2, 3, 4, 5].map((star) => (
            <Star
              key={star}
              className={`size-4 ${
                star <= Math.round(averageRating)
                  ? "fill-action text-action-strong"
                  : "text-white/75"
              }`}
            />
          ))}
        </div>
        <span data-testid="rating-total" className="text-xs text-muted font-medium mt-1">
          {totalReviews} đánh giá
        </span>
      </div>

      {/* Cột phải: Thanh phân bổ sao */}
      <div className="flex-1 w-full space-y-2">
        {[5, 4, 3, 2, 1].map((star) => {
          const count = ratingDistribution[star] || 0;
          const percentage =
            totalReviews > 0 ? Math.round((count / totalReviews) * 100) : 0;

          return (
            <div key={star} data-testid="rating-distribution-row" data-rating={star} className="flex items-center gap-3 text-xs">
              <span className="w-8 font-medium text-ink-2 flex items-center gap-0.5 justify-end">
                {star}
                <Star className="size-3 fill-action text-action-strong inline" />
              </span>
              <div className="flex-1 h-2 bg-sunken rounded-full overflow-hidden">
                <div
                  className="h-full bg-action rounded-full transition-all duration-300"
                  style={{ width: `${percentage}%` }}
                />
              </div>
              <span data-testid="rating-distribution-count" className="w-10 text-muted text-right">{count}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
};

