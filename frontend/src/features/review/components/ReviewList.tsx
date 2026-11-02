"use client";

import React from "react";
import { Star, User, Calendar, Edit3 } from "lucide-react";
import type { ReviewItem } from "../types";

interface ReviewListProps {
  reviews: ReviewItem[];
  currentUserId?: string;
  onEditReview?: (review: ReviewItem) => void;
  className?: string;
}

export const ReviewList: React.FC<ReviewListProps> = ({
  reviews,
  currentUserId,
  onEditReview,
  className = "",
}) => {
  if (!reviews || reviews.length === 0) {
    return (
      <div className="py-12 text-center text-neutral-400 bg-stone-50 rounded-2xl border border-stone-100">
        <p className="text-sm font-medium">Chưa có đánh giá nào cho gia sư này.</p>
        <p className="text-xs text-neutral-400 mt-1">
          Các đánh giá sẽ xuất hiện tại đây sau khi học sinh hoặc phụ huynh hoàn thành buổi học.
        </p>
      </div>
    );
  }

  return (
    <div className={`space-y-4 ${className}`}>
      {reviews.map((review) => {
        const isAuthor = review.isAuthor ?? (Boolean(currentUserId) && review.reviewerUserId === currentUserId);
        const formattedDate = new Date(review.createdAt).toLocaleDateString("vi-VN", {
          day: "2-digit",
          month: "2-digit",
          year: "numeric",
        });

        return (
          <div
            key={review.reviewId}
            data-testid="review-item"
            className="p-5 rounded-2xl bg-white border border-stone-200/90 shadow-sm space-y-3"
          >
            <div className="flex items-start justify-between gap-4">
              <div className="flex items-center gap-3">
                <div className="size-10 rounded-full bg-amber-100/70 border border-amber-200 flex items-center justify-center text-amber-700 font-bold shrink-0">
                  <User className="size-5" />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <span data-testid="reviewer-name" className="text-sm font-bold text-neutral-900">
                      {review.reviewerName || "Người dùng"}
                    </span>
                    <span
                      className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
                        review.reviewerType === "STUDENT"
                          ? "bg-blue-50 text-blue-700 border border-blue-200"
                          : "bg-emerald-50 text-emerald-700 border border-emerald-200"
                      }`}
                    >
                      {review.reviewerType === "STUDENT" ? "Học sinh" : "Phụ huynh"}
                    </span>
                  </div>
                  <div className="flex items-center gap-2 mt-1">
                    <div role="img" aria-label={`${review.rating} trên 5 sao`} className="flex items-center gap-0.5 text-amber-500">
                      {[1, 2, 3, 4, 5].map((star) => (
                        <Star
                          key={star}
                          className={`size-3.5 ${
                            star <= review.rating
                              ? "fill-amber-400 text-amber-400"
                              : "text-stone-300"
                          }`}
                        />
                      ))}
                    </div>
                    <span className="text-xs text-neutral-400 flex items-center gap-1">
                      <Calendar className="size-3" />
                      {formattedDate}
                    </span>
                    {review.isEdited && (
                      <span className="text-[10px] text-neutral-400 italic">
                        (Đã chỉnh sửa)
                      </span>
                    )}
                  </div>
                </div>
              </div>

              {isAuthor && onEditReview && (
                <button
                  type="button"
                  onClick={() => onEditReview(review)}
                  className="text-xs text-amber-700 hover:text-amber-800 font-semibold flex items-center gap-1 hover:underline p-1"
                >
                  <Edit3 className="size-3.5" />
                  Sửa
                </button>
              )}
            </div>

            <p data-testid="review-comment" className="text-xs sm:text-sm text-neutral-700 leading-relaxed whitespace-pre-line pl-13">
              {review.comment}
            </p>
          </div>
        );
      })}
    </div>
  );
};

