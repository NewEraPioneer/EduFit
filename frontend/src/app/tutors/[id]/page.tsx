"use client";

import React, { useState, useEffect, useCallback, Suspense } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import {
  Star,
  ShieldCheck,
  MapPin,
  Briefcase,
  ChevronRight,
  ArrowLeft,
  MessageSquare,
  PenLine,
  AlertCircle,
  RefreshCw,
  Award,
  FileCheck,
} from "lucide-react";
import {
  discoveryApi,
  LoginPromptModal,
  type TutorDetail,
} from "@/features/discovery";
import { OfferingList } from "@/features/discovery/components/OfferingList";
import { TutorWeekSchedule } from "@/features/discovery/components/TutorWeekSchedule";
import {
  TEACHING_MODE_TEXT,
  formatRating,
  formatVnd,
  tutorAreaText,
} from "@/features/discovery/utils/tutorDisplay";
import { TUTOR_TYPE_LABELS } from "@/features/learning-needs/catalogRules";
import {
  RatingSummaryCard,
  ReviewList,
  ReviewModal,
  reviewApi,
  type ReviewItem,
  type TutorRatingSummary,
} from "@/features/review";
import { ConnectionRequestDialog } from "@/features/connection";
import { connectionService } from "@/services/connections";
import { progressApi } from "@/features/progress";
import { useAuth } from "@/context/AuthContext";
import { useToast } from "@/shared/ui/Toast";
import { PublicNavbar } from "@/shared/ui/PublicNavbar";

function TutorDetailContent() {
  const router = useRouter();
  const routeParams = useParams();
  const tutorId = String(routeParams?.id || "");

  const { user } = useAuth();
  const { showToast } = useToast();

  const [tutor, setTutor] = useState<TutorDetail | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Reviews & Rating Summary
  const [reviews, setReviews] = useState<ReviewItem[]>([]);
  const [ratingSummary, setRatingSummary] = useState<TutorRatingSummary | null>(null);
  const [isLoadingReviews, setIsLoadingReviews] = useState(false);

  // Modals state
  const [isPromptModalOpen, setIsPromptModalOpen] = useState(false);
  const [isReviewModalOpen, setIsReviewModalOpen] = useState(false);
  const [editingReview, setEditingReview] = useState<ReviewItem | null>(null);
  const [isConnectOpen, setIsConnectOpen] = useState(false);

  const [linkedChildren, setLinkedChildren] = useState<Array<{ studentId: string; name: string }>>([]);
  const [selectedChildId, setSelectedChildId] = useState<string>("");

  const loadTutor = useCallback(async () => {
    if (!tutorId) return;
    try {
      setIsLoading(true);
      setError(null);
      const res = await discoveryApi.getTutorDetail(tutorId);
      if (res.success && res.data) {
        setTutor(res.data);
      } else {
        setError(res.message || "Không thể tải thông tin chi tiết gia sư.");
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Lỗi kết nối khi tải hồ sơ gia sư.");
    } finally {
      setIsLoading(false);
    }
  }, [tutorId]);

  const loadReviews = useCallback(async () => {
    if (!tutorId) return;
    try {
      setIsLoadingReviews(true);
      const [sumRes, revRes] = await Promise.all([
        reviewApi.getTutorRatingSummary(tutorId),
        reviewApi.getTutorReviews(tutorId, 0, 20),
      ]);
      if (sumRes.success && sumRes.data) {
        setRatingSummary(sumRes.data);
      }
      if (revRes.success && revRes.data) {
        setReviews(revRes.data.content || []);
      }
    } catch {
      // Non-critical, ignore error to keep profile visible
    } finally {
      setIsLoadingReviews(false);
    }
  }, [tutorId]);

  useEffect(() => {
    loadTutor();
    loadReviews();
  }, [loadTutor, loadReviews]);

  // Load linked children for parent
  useEffect(() => {
    if (user && user.role === "PARENT") {
      let cancelled = false;
      Promise.all([
        connectionService.links().catch(() => []),
        progressApi.getParentDashboard().catch(() => null),
      ]).then(([links, dashboard]) => {
        if (cancelled) return;
        const studentNames = Object.fromEntries(
          (dashboard?.data?.students || [])
            .filter((s) => s.studentId && s.studentName)
            .map((s) => [s.studentId, s.studentName])
        );
        const children = links.map((link, idx) => ({
          studentId: link.studentId,
          name: studentNames[link.studentId] || `Học sinh ${idx + 1}`,
        }));
        setLinkedChildren(children);
        if (children.length > 0) {
          setSelectedChildId((prev) =>
            children.some((c) => c.studentId === prev) ? prev : children[0].studentId
          );
        }
      });
      return () => {
        cancelled = true;
      };
    }
  }, [user]);

  // Handle request connect click
  const handleRequestClick = () => {
    if (!user) {
      setIsPromptModalOpen(true);
      return;
    }

    if (user.role === "TUTOR" || user.role === "ADMIN") {
      showToast("Chức năng chỉ dành cho tài khoản Học sinh hoặc Phụ huynh.", "info");
      return;
    }

    if (user.role === "PARENT") {
      if (linkedChildren.length === 0) {
        showToast("Bạn chưa có học sinh liên kết. Vui lòng liên kết tài khoản con trước khi gửi yêu cầu.", "info");
        return;
      }
    }

    setIsConnectOpen(true);
  };

  // Handle review click
  const handleWriteReviewClick = () => {
    if (!user) {
      setIsPromptModalOpen(true);
      return;
    }

    if (user.role !== "STUDENT" && user.role !== "PARENT") {
      showToast("Chỉ học sinh hoặc phụ huynh đã học mới có thể đánh giá.", "info");
      return;
    }

    setEditingReview(null);
    setIsReviewModalOpen(true);
  };

  const prices = tutor?.offerings.map((offering) => offering.pricePerSession) ?? [];
  const minPrice = prices.length > 0 ? Math.min(...prices) : null;

  return (
    <div className="min-h-screen bg-stone-50 dark:bg-stone-950 text-neutral-900 dark:text-stone-100 flex flex-col font-sans transition-colors">
      <PublicNavbar />

      <main className="flex-1 max-w-5xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
        {/* Breadcrumb Navigation */}
        <nav aria-label="Đường dẫn trang" className="flex items-center gap-2 text-xs text-neutral-500 dark:text-stone-400">
          <Link href="/" className="hover:text-amber-600 transition-colors">
            Trang chủ
          </Link>
          <ChevronRight className="size-3.5 text-neutral-400" />
          <Link href="/tutors" className="hover:text-amber-600 transition-colors">
            Danh bạ gia sư
          </Link>
          <ChevronRight className="size-3.5 text-neutral-400" />
          <span className="text-neutral-800 dark:text-stone-200 font-semibold truncate max-w-xs">
            {tutor?.displayName || "Chi tiết hồ sơ"}
          </span>
        </nav>

        {/* 1. Error State */}
        {error && (
          <div
            role="alert"
            className="p-6 rounded-3xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/50 text-rose-800 dark:text-rose-300 text-xs font-medium space-y-3"
          >
            <div className="flex items-center gap-2.5">
              <AlertCircle className="size-5 text-rose-600 shrink-0" />
              <span className="text-sm font-bold">{error}</span>
            </div>
            <div className="flex items-center gap-3">
              <button
                type="button"
                onClick={loadTutor}
                className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-rose-600 text-white font-bold hover:bg-rose-700 transition-colors cursor-pointer"
              >
                <RefreshCw className="size-3.5" />
                <span>Thử lại</span>
              </button>
              <Link
                href="/tutors"
                className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-white dark:bg-stone-900 border border-stone-200 dark:border-stone-800 text-neutral-700 dark:text-stone-200 font-semibold hover:bg-stone-50 transition-colors"
              >
                <ArrowLeft className="size-3.5" />
                <span>Quay lại danh bạ</span>
              </Link>
            </div>
          </div>
        )}

        {/* 2. Loading State */}
        {isLoading && (
          <div className="space-y-6 animate-pulse" data-testid="tutor-detail-skeleton">
            <div className="h-56 rounded-3xl bg-white dark:bg-stone-900 border border-stone-200/80 dark:border-stone-800 p-8 flex items-center gap-6" />
            <div className="h-40 rounded-3xl bg-white dark:bg-stone-900 border border-stone-200/80 dark:border-stone-800 p-6" />
            <div className="h-40 rounded-3xl bg-white dark:bg-stone-900 border border-stone-200/80 dark:border-stone-800 p-6" />
          </div>
        )}

        {/* 3. Populated State */}
        {!isLoading && !error && tutor && (
          <div className="space-y-6">
            {/* Top Profile Card */}
            <div className="bg-white dark:bg-stone-900 rounded-3xl border border-stone-200/80 dark:border-stone-800 p-6 sm:p-8 shadow-sm flex flex-col md:flex-row items-start md:items-center justify-between gap-6">
              <div className="flex items-start sm:items-center gap-5 flex-1 min-w-0">
                <div className="size-20 sm:size-24 rounded-3xl bg-gradient-to-tr from-amber-500 to-orange-400 text-white font-heading font-black text-3xl sm:text-4xl flex items-center justify-center shadow-md shrink-0">
                  {tutor.displayName.charAt(0).toUpperCase()}
                </div>

                <div className="space-y-1.5 min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h1 className="font-heading font-extrabold text-xl sm:text-2xl text-neutral-900 dark:text-stone-100">
                      {tutor.displayName}
                    </h1>
                    {tutor.verified && (
                      <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs font-bold bg-emerald-100 dark:bg-emerald-950/60 text-emerald-800 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-900/50">
                        <ShieldCheck className="size-3.5 text-emerald-600" />
                        <span>Đã xác minh bằng cấp</span>
                      </span>
                    )}
                  </div>

                  {tutor.headline && (
                    <p className="text-xs sm:text-sm text-neutral-600 dark:text-stone-400 font-medium">
                      {tutor.headline}
                    </p>
                  )}

                  <div className="flex flex-wrap items-center gap-3 pt-1 text-xs text-neutral-500 dark:text-stone-400">
                    <span className="inline-flex items-center gap-1 font-bold text-amber-800 dark:text-amber-300 bg-amber-50 dark:bg-amber-950/60 border border-amber-200/70 dark:border-amber-900/40 px-2.5 py-0.5 rounded-lg">
                      <Star className="size-3.5 fill-amber-500 text-amber-500" />
                      {formatRating(tutor.ratingAvg, tutor.reviewCount)}
                    </span>
                    {tutor.tutorType && (
                      <span className="inline-flex items-center gap-1">
                        {TUTOR_TYPE_LABELS[tutor.tutorType]}
                        {tutor.tutorTypeVerified && " (đã xác minh)"}
                      </span>
                    )}
                    <span className="inline-flex items-center gap-1">
                      <Briefcase className="size-3.5 text-neutral-400" />
                      {tutor.experienceYears ? `${tutor.experienceYears} năm kinh nghiệm` : "Giáo viên tận tâm"}
                    </span>
                    <span className="inline-flex items-center gap-1 min-w-0">
                      <MapPin className="size-3.5 text-neutral-400 shrink-0" />
                      <span className="break-words">{TEACHING_MODE_TEXT[tutor.teachingMode]} · {tutorAreaText(tutor)}</span>
                    </span>
                  </div>
                </div>
              </div>

              {/* Price & CTA Action */}
              <div className="w-full md:w-auto pt-4 md:pt-0 border-t md:border-t-0 border-stone-100 dark:border-stone-800 flex sm:flex-col items-center sm:items-end justify-between gap-3 shrink-0">
                <div className="text-left sm:text-right">
                  <span className="text-[11px] text-neutral-400 dark:text-stone-500 font-medium block">
                    {prices.length > 1 ? "Học phí từ" : "Học phí"}
                  </span>
                  <span className="font-heading font-extrabold text-xl sm:text-2xl text-neutral-900 dark:text-stone-100">
                    {formatVnd(minPrice)}
                  </span>
                  {minPrice != null && <span className="text-xs text-neutral-400"> /buổi</span>}
                </div>

                {user?.role === "PARENT" && linkedChildren.length > 0 && (
                  <div className="text-left sm:text-right w-full sm:w-auto">
                    <label htmlFor="detail-child-select" className="text-[11px] font-semibold text-neutral-600 dark:text-stone-300 block mb-1">
                      Chọn con em kết nối:
                    </label>
                    <select
                      id="detail-child-select"
                      aria-label="Chọn con em liên kết"
                      value={selectedChildId}
                      onChange={(e) => setSelectedChildId(e.target.value)}
                      className="rounded-xl border border-stone-200 dark:border-stone-700 bg-stone-50 dark:bg-stone-800 px-3 py-1.5 text-xs font-semibold text-neutral-800 dark:text-stone-100 focus:outline-hidden focus:ring-2 focus:ring-amber-500"
                    >
                      {linkedChildren.map((c) => (
                        <option key={c.studentId} value={c.studentId}>
                          {c.name}
                        </option>
                      ))}
                    </select>
                  </div>
                )}

                <button
                  type="button"
                  onClick={handleRequestClick}
                  className="inline-flex items-center justify-center gap-2 px-6 py-3 rounded-2xl font-bold text-xs text-white bg-amber-600 hover:bg-amber-700 shadow-md shadow-amber-600/20 active:scale-95 transition-all cursor-pointer"
                >
                  <MessageSquare className="size-4" />
                  <span>Gửi yêu cầu học</span>
                </button>
              </div>
            </div>

            {/* Verified Credentials Highlight Box */}
            <div className="bg-emerald-50/70 dark:bg-emerald-950/20 rounded-3xl border border-emerald-200 dark:border-emerald-900/40 p-6 space-y-3">
              <div className="flex items-center gap-2 text-emerald-800 dark:text-emerald-300 font-bold text-sm">
                <Award className="size-5 text-emerald-600" />
                <h3>Bằng cấp và chứng chỉ sư phạm đã xác minh (Verified Credentials)</h3>
              </div>
              <p className="text-xs text-emerald-900/80 dark:text-emerald-200/80 leading-relaxed">
                Hồ sơ gia sư này đã vượt qua quy trình thẩm định nghiêm ngặt từ ban chuyên môn EduFit bao gồm:
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-1">
                <div className="flex items-center gap-2 text-xs text-emerald-800 dark:text-emerald-300 bg-white/80 dark:bg-stone-900/80 p-3 rounded-xl border border-emerald-200/60 dark:border-emerald-900/30">
                  <FileCheck className="size-4 text-emerald-600 shrink-0" />
                  <span className="font-medium">Xác minh danh tính CCCD</span>
                </div>
                <div className="flex items-center gap-2 text-xs text-emerald-800 dark:text-emerald-300 bg-white/80 dark:bg-stone-900/80 p-3 rounded-xl border border-emerald-200/60 dark:border-emerald-900/30">
                  <FileCheck className="size-4 text-emerald-600 shrink-0" />
                  <span className="font-medium">Bằng đại học / Sư phạm</span>
                </div>
                <div className="flex items-center gap-2 text-xs text-emerald-800 dark:text-emerald-300 bg-white/80 dark:bg-stone-900/80 p-3 rounded-xl border border-emerald-200/60 dark:border-emerald-900/30">
                  <FileCheck className="size-4 text-emerald-600 shrink-0" />
                  <span className="font-medium">Kiểm duyệt lý lịch & tác phong</span>
                </div>
              </div>
            </div>

            {/* Bio & Teaching Method */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {/* Bio */}
              <div className="bg-white dark:bg-stone-900 rounded-3xl border border-stone-200/80 dark:border-stone-800 p-6 space-y-3 shadow-xs">
                <h3 className="font-heading font-bold text-sm text-neutral-900 dark:text-stone-100 uppercase tracking-wider">
                  Giới thiệu bản thân
                </h3>
                <p className="text-xs sm:text-sm text-neutral-600 dark:text-stone-300 leading-relaxed whitespace-pre-line">
                  {tutor.bio || "Gia sư chưa cập nhật phần giới thiệu bản thân."}
                </p>
              </div>

              {/* Teaching Method */}
              <div className="bg-white dark:bg-stone-900 rounded-3xl border border-stone-200/80 dark:border-stone-800 p-6 space-y-3 shadow-xs">
                <h3 className="font-heading font-bold text-sm text-neutral-900 dark:text-stone-100 uppercase tracking-wider">
                  Phương pháp giảng dạy
                </h3>
                <p className="text-xs sm:text-sm text-neutral-600 dark:text-stone-300 leading-relaxed">
                  {tutor.teachingMethod || "Phương pháp dạy bám sát chương trình THCS, tập trung xây dựng nền tảng vững chắc và củng cố kỹ năng làm bài."}
                </p>
              </div>
            </div>

            {/* Gói dạy */}
            <div className="bg-white dark:bg-stone-900 rounded-3xl border border-stone-200/80 dark:border-stone-800 p-5 sm:p-6 space-y-4 shadow-xs">
              <h3 className="font-heading font-bold text-sm text-neutral-900 dark:text-stone-100 uppercase tracking-wider">
                Gói dạy
              </h3>
              <OfferingList offerings={tutor.offerings} />
            </div>

            {/* Weekly Availability Slots */}
            <div className="bg-white dark:bg-stone-900 rounded-3xl border border-stone-200/80 dark:border-stone-800 p-6 space-y-4 shadow-xs">
              <div className="flex items-center justify-between">
                <h3 className="font-heading font-bold text-sm text-neutral-900 dark:text-stone-100 uppercase tracking-wider">
                  Lịch của gia sư
                </h3>
              </div>

              <TutorWeekSchedule
                availabilitySlots={tutor.availabilitySlots ?? []}
                busySlots={tutor.busySlots}
              />
            </div>

            {/* Reviews & Ratings Section */}
            <div className="bg-white dark:bg-stone-900 rounded-3xl border border-stone-200/80 dark:border-stone-800 p-6 sm:p-8 space-y-6 shadow-xs">
              <div className="flex items-center justify-between flex-wrap gap-3">
                <div>
                  <h3 className="font-heading font-extrabold text-lg text-neutral-900 dark:text-stone-100">
                    Đánh giá từ Học sinh & Phụ huynh
                  </h3>
                  <p className="text-xs text-neutral-500 dark:text-stone-400 mt-0.5">
                    Phản hồi thực tế sau các buổi học 1-1 trên nền tảng EduFit
                  </p>
                </div>

                <button
                  type="button"
                  onClick={handleWriteReviewClick}
                  className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl text-xs font-bold text-amber-800 dark:text-amber-300 bg-amber-50 dark:bg-amber-950/60 hover:bg-amber-100 border border-amber-200 dark:border-amber-900/50 transition-colors cursor-pointer"
                >
                  <PenLine className="size-3.5" />
                  <span>Viết đánh giá</span>
                </button>
              </div>

              {/* Rating summary cards */}
              {ratingSummary && ratingSummary.totalReviews > 0 ? (
                <RatingSummaryCard summary={ratingSummary} />
              ) : (
                <div className="p-6 rounded-2xl bg-stone-50 dark:bg-stone-800/40 border border-stone-200/60 dark:border-stone-800 text-xs text-neutral-500 dark:text-stone-400 text-center">
                  Gia sư hiện chưa có đánh giá nào. Hãy là người đầu tiên trải nghiệm học cùng gia sư!
                </div>
              )}

              {/* Reviews List */}
              {isLoadingReviews ? (
                <div className="py-8 text-center text-xs text-neutral-400">
                  Đang tải danh sách nhận xét...
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
          </div>
        )}
      </main>

      {/* Guest Login Prompt Modal */}
      <LoginPromptModal
        isOpen={isPromptModalOpen}
        onClose={() => setIsPromptModalOpen(false)}
        tutorName={tutor?.displayName}
        returnUrl={`/tutors/${tutorId}`}
        actionText="gửi yêu cầu học"
      />

      {/* Write/Edit Review Modal */}
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
            showToast("Đã gửi đánh giá gia sư thành công!", "success");
            loadReviews();
          }}
        />
      )}

      {/* Authenticated Connection Request Dialog */}
      {isConnectOpen && tutor && (
        <ConnectionRequestDialog
          tutorId={tutor.tutorId}
          tutor={tutor}
          studentId={user?.role === "PARENT" ? selectedChildId || undefined : undefined}
          onClose={() => setIsConnectOpen(false)}
          onSent={() => {
            setIsConnectOpen(false);
            showToast("Đã gửi yêu cầu học thử thành công!", "success");
          }}
        />
      )}
    </div>
  );
}

export default function TutorDetailPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen bg-stone-50 dark:bg-stone-950 flex items-center justify-center font-sans">
          <div className="flex flex-col items-center gap-3">
            <div className="size-8 border-3 border-amber-500/30 border-t-amber-500 rounded-full animate-spin" />
            <span className="text-xs text-neutral-500 font-medium">Đang tải hồ sơ gia sư...</span>
          </div>
        </div>
      }
    >
      <TutorDetailContent />
    </Suspense>
  );
}
