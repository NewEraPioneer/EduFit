"use client";

import React, { useEffect, useState } from "react";
import { Star, X, Loader2, AlertCircle, Tag, Check } from "lucide-react";
import { reviewApi } from "../api/reviewApi";
import { reviewFormSchema } from "../schemas/reviewSchema";
import type { ReviewItem } from "../types";
import { profileService } from "@/services/profile";
import { connectionApi } from "@/features/connection/api/connectionApi";
import type { UserRole } from "@/features/auth/types";
import { ApiError } from "@/lib/api";

export const QUICK_REVIEW_TAGS = [
  "Nhiệt tình",
  "Đúng giờ",
  "Dễ hiểu",
  "Kiên nhẫn",
  "Phương pháp hay",
  "Tận tâm",
  "Truyền cảm hứng",
  "Bài tập thực tế",
];

export const TAG_PHRASES: Record<string, string> = {
  "Nhiệt tình": "Gia sư giảng dạy rất nhiệt tình và tận tâm.",
  "Đúng giờ": "Gia sư luôn vào lớp đúng giờ và chuẩn bị bài giảng chu đáo.",
  "Dễ hiểu": "Gia sư giảng bài rất dễ hiểu và chi tiết.",
  "Kiên nhẫn": "Gia sư luôn kiên nhẫn hướng dẫn và giải đáp từng thắc mắc.",
  "Phương pháp hay": "Phương pháp giảng dạy rất hay, trực quan và dễ tiếp thu.",
  "Tận tâm": "Gia sư rất tận tâm, theo sát từng bước tiến bộ của học sinh.",
  "Truyền cảm hứng": "Buổi học rất sinh động và truyền nhiều cảm hứng học tập.",
  "Bài tập thực tế": "Bài tập thực tế đa dạng, bám sát kiến thức thực tế.",
};

interface ReviewModalProps {
  isOpen: boolean;
  onClose: () => void;
  tutorId: string;
  studentId?: string;
  reviewerRole?: UserRole;
  tutorName?: string;
  existingReview?: ReviewItem | null;
  onSuccess?: (review: ReviewItem) => void;
}

export const ReviewModal: React.FC<ReviewModalProps> = ({
  isOpen,
  onClose,
  tutorId,
  studentId,
  reviewerRole,
  tutorName,
  existingReview: existingReviewProp,
  onSuccess,
}) => {
  // Đánh giá cũ: ưu tiên bản truyền vào, nếu không có thì tự tra theo gia sư + học sinh.
  const [fetchedReview, setFetchedReview] = useState<ReviewItem | null>(null);
  const existingReview = existingReviewProp ?? fetchedReview;
  const isEditing = Boolean(existingReview);
  const [rating, setRating] = useState<number>(existingReviewProp?.rating || 5);
  const [hoverRating, setHoverRating] = useState<number>(0);
  const [comment, setComment] = useState<string>(existingReviewProp?.comment || "");
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [studentChoices, setStudentChoices] = useState<Array<{ id: string; label: string }>>([]);
  const [selectedStudentId, setSelectedStudentId] = useState(studentId || "");
  const [isLoadingStudent, setIsLoadingStudent] = useState(false);

  const lookupStudentId = studentId || selectedStudentId;
  useEffect(() => {
    if (!isOpen || existingReviewProp || !lookupStudentId) {
      return;
    }
    let cancelled = false;
    reviewApi
      .getMyReview(tutorId, lookupStudentId)
      .then((res) => {
        if (cancelled) return;
        const found = res?.data ?? null;
        setFetchedReview(found);
        if (found) {
          setRating(found.rating);
          setComment(found.comment ?? "");
          setSelectedTags([]);
        }
      })
      .catch(() => {
        if (!cancelled) setFetchedReview(null);
      });
    return () => {
      cancelled = true;
    };
  }, [isOpen, existingReviewProp, tutorId, lookupStudentId]);

  useEffect(() => {
    if (!isOpen || existingReviewProp || studentId) {
      return;
    }

    let cancelled = false;
    const loadReviewStudent = async () => {
      setIsLoadingStudent(true);
      setError(null);
      try {
        if (reviewerRole === "STUDENT") {
          const profile = await profileService.getMyStudentProfile();
          if (!cancelled) {
            setStudentChoices([{ id: profile.studentId, label: "Hồ sơ học sinh của bạn" }]);
            setSelectedStudentId(profile.studentId);
          }
          return;
        }

        if (reviewerRole === "PARENT") {
          const links = await connectionApi.getLinks();
          const choices = links.map((link, index) => ({
            id: link.studentId,
            label: `Học sinh ${index + 1}`,
          }));
          if (!cancelled) {
            setStudentChoices(choices);
            setSelectedStudentId(choices.length === 1 ? choices[0].id : "");
          }
          return;
        }

        if (!cancelled) {
          setError("Không xác định được hồ sơ học sinh dùng để đánh giá.");
        }
      } catch (err: unknown) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Không thể tải hồ sơ học sinh.");
        }
      } finally {
        if (!cancelled) setIsLoadingStudent(false);
      }
    };

    void loadReviewStudent();
    return () => {
      cancelled = true;
    };
  }, [existingReviewProp, isOpen, reviewerRole, studentId]);

  if (!isOpen) return null;

  const toggleTag = (tag: string) => {
    const isCurrentlySelected = selectedTags.includes(tag);
    const phrase = TAG_PHRASES[tag] || `Gia sư rất ${tag.toLowerCase()} và giảng dạy tốt.`;

    if (!isCurrentlySelected) {
      // Khi chọn nhãn nhanh: tự động điền hoặc ghép thêm câu nhận xét hoàn chỉnh (>= 10 ký tự)
      setSelectedTags((prev) => [...prev, tag]);
      setComment((prev) => {
        const trimmed = prev.trim();
        if (!trimmed) {
          return phrase;
        }
        if (trimmed.includes(phrase)) {
          return prev;
        }
        return `${trimmed} ${phrase}`;
      });
    } else {
      // Khi bỏ chọn nhãn nhanh: loại bỏ câu tương ứng nếu chưa bị người dùng chỉnh sửa
      setSelectedTags((prev) => prev.filter((t) => t !== tag));
      setComment((prev) => {
        const cleaned = prev
          .replace(phrase, "")
          .replace(/\s{2,}/g, " ")
          .trim();
        return cleaned;
      });
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const effectiveStudentId = studentId || selectedStudentId;
    if (!isEditing && !effectiveStudentId) {
      setError(
        reviewerRole === "PARENT"
          ? "Vui lòng chọn học sinh được liên kết để gửi đánh giá."
          : "Không tìm thấy hồ sơ học sinh. Vui lòng hoàn tất hồ sơ trước khi đánh giá."
      );
      return;
    }

    const trimmedComment = comment.trim();
    // Build combined comment with quick tags if selected and not yet present in comment text
    let finalComment = trimmedComment;
    if (selectedTags.length > 0) {
      const tagPrefix = `[${selectedTags.join(", ")}]`;
      if (!trimmedComment.includes(tagPrefix)) {
        finalComment = trimmedComment ? `${tagPrefix} ${trimmedComment}` : tagPrefix;
      }
    }

    // Bảo đảm nội dung nhận xét đạt tối thiểu 10 ký tự khi chọn nhãn nhanh, tránh lỗi validation ngầm
    if (selectedTags.length > 0 && finalComment.length < 10) {
      const fallbackPhrase =
        TAG_PHRASES[selectedTags[0]] || `Gia sư rất ${selectedTags[0].toLowerCase()} và giảng dạy tốt.`;
      finalComment = `${finalComment} ${fallbackPhrase}`.trim();
    }

    // Zod Schema Validation
    const validationResult = reviewFormSchema.safeParse({
      rating,
      comment: finalComment,
      tags: selectedTags,
    });

    if (!validationResult.success) {
      const firstIssue = validationResult.error.issues[0];
      setError(firstIssue?.message || "Dữ liệu đánh giá không hợp lệ. Vui lòng kiểm tra lại.");
      return;
    }

    try {
      setLoading(true);
      let response;
      if (isEditing && existingReview) {
        response = await reviewApi.updateReview(existingReview.reviewId, {
          rating,
          comment: finalComment,
        });
      } else {
        response = await reviewApi.createReview({
          tutorId,
          studentId: effectiveStudentId,
          rating,
          comment: finalComment,
        });
      }

      if (response && response.data) {
        if (onSuccess) onSuccess(response.data);
        onClose();
      } else {
        setError(response?.message || "Có lỗi xảy ra khi gửi đánh giá.");
      }
    } catch (err: unknown) {
      const fieldDetails = err instanceof ApiError
        ? err.details?.map((detail) => detail.message).filter(Boolean)
        : undefined;
      const errorMessage = fieldDetails?.length
        ? fieldDetails.join(" ")
        : err instanceof Error
          ? err.message
          : "Đã xảy ra lỗi không xác định. Vui lòng thử lại.";
      setError(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="review-modal-title"
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4 bg-black/50 backdrop-blur-xs overflow-y-auto"
    >
      <div className="w-full max-w-dialog bg-surface rounded-3xl shadow-xl border border-line overflow-hidden animate-in fade-in zoom-in-95 duration-200 my-auto">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-line bg-paper/70">
          <div>
            <h3 id="review-modal-title" className="text-base font-medium text-ink">
              {isEditing ? "Chỉnh sửa đánh giá gia sư" : "Đánh giá gia sư"}
            </h3>
            {tutorName && (
              <p className="text-xs text-muted mt-0.5">Dành cho: {tutorName}</p>
            )}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Đóng"
            className="p-1.5 rounded-full text-muted hover:text-ink-2 hover:bg-line/60 transition-colors"
          >
            <X className="size-5" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-6 space-y-5">
          {error && (
            <div
              role="alert"
              className="p-3.5 rounded-xl bg-danger-soft border border-danger-line text-xs text-danger-strong flex items-start gap-2.5"
            >
              <AlertCircle className="size-4 shrink-0 text-danger-strong mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          {!isEditing && reviewerRole === "PARENT" && studentChoices.length > 1 && (
            <div className="space-y-1.5">
              <label htmlFor="review-student" className="text-xs font-medium text-ink">
                Đánh giá cho học sinh <span className="text-danger-strong">*</span>
              </label>
              <select
                id="review-student"
                value={selectedStudentId}
                onChange={(event) => setSelectedStudentId(event.target.value)}
                className="w-full rounded-xl border border-line bg-surface px-3 py-2.5 text-sm text-ink focus:border-action focus:outline-hidden focus:ring-2 focus:ring-action/20"
              >
                <option value="">Chọn học sinh</option>
                {studentChoices.map((choice) => (
                  <option key={choice.id} value={choice.id}>{choice.label}</option>
                ))}
              </select>
            </div>
          )}

          {/* 1. Chấm điểm sao (1-5 sao) */}
          <div className="space-y-2 text-center">
            <label className="text-xs font-medium text-ink-2 block">
              Mức độ hài lòng của bạn
            </label>
            <div className="flex items-center justify-center gap-2">
              {[1, 2, 3, 4, 5].map((star) => {
                const isFilled = (hoverRating || rating) >= star;
                return (
                  <button
                    key={star}
                    type="button"
                    aria-label={`${star} sao`}
                    onClick={() => setRating(star)}
                    onMouseEnter={() => setHoverRating(star)}
                    onMouseLeave={() => setHoverRating(0)}
                    className="p-1 transition-transform focus:outline-hidden cursor-pointer"
                  >
                    <Star
                      className={`size-7 transition-colors ${
                        isFilled
                          ? "fill-action text-action-strong"
                          : "text-white/75 hover:text-action-tint"
                      }`}
                    />
                  </button>
                );
              })}
            </div>
            <p className="text-xs text-action-strong font-medium">
              {rating === 5 && "⭐ Tuyệt vời! Rất hài lòng"}
              {rating === 4 && "⭐ Tốt, hài lòng"}
              {rating === 3 && "⭐ Bình thường, tạm ổn"}
              {rating === 2 && "⭐ Chưa hài lòng"}
              {rating === 1 && "⭐ Kém, cần cải thiện"}
            </p>
          </div>

          {/* 2. Chọn các nhãn nhận xét nhanh (quick tags) */}
          <div className="space-y-2">
            <div className="flex items-center gap-1.5 text-xs font-medium text-ink-2">
              <Tag className="size-3.5 text-action-strong" />
              <span>Nhãn nhận xét nhanh (chọn nhiều)</span>
            </div>
            <div className="flex flex-wrap gap-1.5">
              {QUICK_REVIEW_TAGS.map((tag) => {
                const isSelected = selectedTags.includes(tag);
                return (
                  <button
                    key={tag}
                    type="button"
                    onClick={() => toggleTag(tag)}
                    className={`inline-flex items-center gap-1 px-3 py-1.5 rounded-full text-xs font-medium transition-all cursor-pointer ${
                      isSelected
                        ? "bg-action-soft text-action-strong border border-action-line shadow-2xs font-medium"
                        : "bg-paper text-ink-2 border border-line hover:bg-sunken hover:text-ink"
                    }`}
                  >
                    {isSelected && <Check className="size-3 text-action-strong" />}
                    <span>{tag}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* 3. Ô nhập nhận xét chi tiết */}
          <div className="space-y-1.5">
            <div className="flex items-center justify-between text-xs">
              <label htmlFor="review-comment" className="font-medium text-ink">
                Nhận xét chi tiết <span className="text-danger-strong">*</span>
              </label>
              <span
                className={`text-xs ${
                  comment.trim().length > 1000
                    ? "text-danger-strong font-medium"
                    : "text-muted"
                }`}
              >
                {comment.trim().length}/1000 ký tự (tối thiểu 10)
              </span>
            </div>
            <textarea
              id="review-comment"
              rows={4}
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              placeholder="Chia sẻ về buổi học, thái độ giảng dạy và phương pháp của gia sư..."
              className="w-full text-xs sm:text-sm p-3.5 rounded-2xl border border-line focus:outline-hidden focus:border-action focus:ring-2 focus:ring-action/20 transition-all resize-none"
            />
          </div>

          {/* 4. Nút hành động */}
          <div className="flex items-center justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              disabled={loading || isLoadingStudent}
              className="px-4 py-2.5 rounded-xl text-xs font-medium text-ink-2 hover:bg-sunken transition-colors cursor-pointer"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={loading || isLoadingStudent}
              className="px-5 py-2.5 rounded-xl text-xs font-medium text-on-action bg-action hover:bg-action-deep active:bg-action disabled:opacity-50 transition-colors flex items-center gap-2 shadow-sm cursor-pointer"
            >
              {(loading || isLoadingStudent) && <Loader2 className="size-3.5 animate-spin" />}
              {isEditing ? "Lưu thay đổi" : "Gửi đánh giá"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
