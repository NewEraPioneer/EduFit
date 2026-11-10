"use client";

import { useEffect, useState } from "react";
import { profileService } from "@/services/profile";
import { reviewApi } from "@/features/review/api/reviewApi";
import { RatingSummaryCard } from "@/features/review/components/RatingSummaryCard";
import { ReviewList } from "@/features/review/components/ReviewList";
import type { ReviewItem, TutorRatingSummary } from "@/features/review/types";

const PAGE_SIZE = 10;

export default function TutorReviewsPage() {
  const [page, setPage] = useState(0);
  const [summary, setSummary] = useState<TutorRatingSummary | null>(null);
  const [reviews, setReviews] = useState<ReviewItem[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      setError(null);
      try {
        const profile = await profileService.getMyTutorProfile();
        if (!profile?.tutorId) throw new Error("Chưa có hồ sơ gia sư để xem đánh giá.");
        const [summaryResponse, reviewsResponse] = await Promise.all([
          reviewApi.getTutorRatingSummary(profile.tutorId),
          reviewApi.getTutorReviews(profile.tutorId, page, PAGE_SIZE),
        ]);
        if (!summaryResponse.data || !reviewsResponse.data) throw new Error("Dữ liệu đánh giá chưa sẵn sàng.");
        if (!active) return;
        setSummary(summaryResponse.data);
        setReviews(reviewsResponse.data.content);
        setTotalPages(reviewsResponse.data.totalPages);
      } catch (cause) {
        if (active) setError(cause instanceof Error ? cause.message : "Không thể tải đánh giá.");
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => { active = false; };
  }, [page, retry]);

  return <main className="max-w-5xl mx-auto space-y-6">
    <header>
      <h1 className="font-heading text-2xl sm:text-3xl font-bold text-ink">Đánh giá từ học viên</h1>
      <p className="text-sm text-muted mt-1">Theo dõi điểm sao và phản hồi về các buổi học của bạn.</p>
    </header>
    {loading ? <p role="status" className="rounded-2xl border border-line bg-surface p-8 text-sm text-muted">Đang tải đánh giá...</p> :
      error ? <div role="alert" className="rounded-2xl border border-danger-line bg-danger-soft p-5 text-sm text-danger-strong">{error} <button type="button" onClick={() => setRetry((value) => value + 1)} className="font-medium underline">Thử lại</button></div> :
      <>
        {summary && <RatingSummaryCard summary={summary} />}
        <section aria-labelledby="reviews-title" className="space-y-4">
          <h2 id="reviews-title" className="font-heading text-lg font-bold text-ink">Nhận xét ({summary?.totalReviews ?? 0})</h2>
          <ReviewList reviews={reviews} />
          {totalPages > 1 && <nav aria-label="Phân trang đánh giá" className="flex items-center justify-center gap-4 text-sm">
            <button type="button" disabled={page === 0} onClick={() => setPage((value) => value - 1)} className="rounded-lg border border-line px-3 py-2 disabled:opacity-40">Trang trước</button>
            <span>Trang {page + 1}/{totalPages}</span>
            <button type="button" disabled={page + 1 >= totalPages} onClick={() => setPage((value) => value + 1)} className="rounded-lg border border-line px-3 py-2 disabled:opacity-40">Trang sau</button>
          </nav>}
        </section>
      </>}
  </main>;
}
