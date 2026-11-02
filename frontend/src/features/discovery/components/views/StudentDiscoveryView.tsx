"use client";

import React, { useState } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import { Compass, ChevronLeft, ChevronRight, ShieldCheck } from "lucide-react";
import {
  TutorSearchFilters,
  TutorCard,
  TutorDetailModal,
  AiMatchView,
  useTutorSearch,
  useTutorDetail,
  useTutorMatching,
} from "@/features/discovery";
import { useToast } from "@/shared/ui/Toast";
import { ConnectionRequestDialog, SentRequests, type ConnectionTutorInfo } from "@/features/connection";

export function StudentDiscoveryView() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const { showToast } = useToast();

  const tabParam = searchParams.get("tab");
  const activeTab =
    tabParam === "matches"
      ? "matches"
      : tabParam === "requests"
      ? "requests"
      : "browse";

  const handleTabChange = (tab: "browse" | "matches" | "requests") => {
    if (tab === "matches") {
      router.push("/student/tutors?tab=matches");
    } else if (tab === "requests") {
      router.push("/student/tutors?tab=requests");
    } else {
      router.push("/student/tutors");
    }
  };

  // 1. Search Hook (Tab 1: Khám phá)
  const {
    params,
    tutors,
    pagination,
    isLoading: isSearchLoading,
    error: searchError,
    updateFilters,
    setPage,
    resetFilters,
  } = useTutorSearch();

  // 2. Detail Modal Hook
  const {
    isOpen: isDetailOpen,
    tutor: selectedTutor,
    isLoading: isDetailLoading,
    error: detailError,
    openTutor,
    closeTutor,
  } = useTutorDetail();

  // 3. AI Matching Hook (Tab 2: Gợi ý thông minh)
  const matching = useTutorMatching({ enabled: activeTab === "matches" });
  const { matches } = matching;

  // 4. Request connection state
  const [connectTutor, setConnectTutor] = useState<ConnectionTutorInfo | null>(null);

  const handleRequestConnect = (tutorId: string) => {
    const match = matching.matches.find((m) => m.tutorId === tutorId);
    const found =
      tutors.find((t) => t.tutorId === tutorId) ||
      (selectedTutor?.tutorId === tutorId ? selectedTutor : null) ||
      match?.tutorInfo || { tutorId, displayName: "Gia sư" };
    setConnectTutor(found);
  };

  return (
    <div className="space-y-6 pb-12 max-w-7xl mx-auto">
      {/* 1. HEADER (Figma 36:3538) */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-2 border-b border-stone-200 dark:border-stone-800">
        <div>
          <h1 className="font-heading font-extrabold text-2xl text-neutral-900 dark:text-neutral-100 tracking-tight">
            Tìm kiếm gia sư
          </h1>
          <p className="text-xs text-neutral-500 dark:text-neutral-400 mt-1">
            Khám phá đội ngũ gia sư THCS được xác minh danh tính và bằng cấp sư phạm
          </p>
        </div>
      </div>

      {/* 2. TABS NAVIGATION */}
      <div className="flex items-center gap-6 border-b border-stone-200 dark:border-stone-800 text-xs font-semibold">
        <button
          type="button"
          onClick={() => handleTabChange("browse")}
          className={`pb-3 border-b-2 transition-all cursor-pointer flex items-center gap-1.5 ${
            activeTab === "browse"
              ? "border-[#006DCC] text-[#006DCC] font-bold"
              : "border-transparent text-neutral-500 hover:text-neutral-900 dark:hover:text-neutral-100"
          }`}
        >
          <span>Khám phá gia sư</span>
        </button>

        <button
          type="button"
          onClick={() => handleTabChange("matches")}
          className={`pb-3 border-b-2 transition-all cursor-pointer flex items-center gap-1.5 ${
            activeTab === "matches"
              ? "border-[#006DCC] text-[#006DCC] font-bold"
              : "border-transparent text-neutral-500 hover:text-neutral-900 dark:hover:text-neutral-100"
          }`}
        >
          <span>Gợi ý phù hợp (AI)</span>
          <span
            className={`px-1.5 py-0.5 rounded-full text-[10px] font-bold ${
              activeTab === "matches"
                ? "bg-[#006DCC] text-white"
                : "bg-stone-200 dark:bg-stone-700 text-neutral-600 dark:text-neutral-300"
            }`}
          >
            {matches.length}
          </span>
        </button>

        <button
          type="button"
          onClick={() => handleTabChange("requests")}
          className={`pb-3 border-b-2 transition-all cursor-pointer flex items-center gap-1.5 ${
            activeTab === "requests"
              ? "border-[#006DCC] text-[#006DCC] font-bold"
              : "border-transparent text-neutral-500 hover:text-neutral-900 dark:hover:text-neutral-100"
          }`}
        >
          <span>Yêu cầu đã gửi</span>
        </button>
      </div>

      {/* 3. TAB 1: KHÁM PHÁ GIA SƯ */}
      {activeTab === "browse" && (
        <div className="space-y-4">
          <TutorSearchFilters
            params={params}
            onUpdateFilters={updateFilters}
            onResetFilters={resetFilters}
            totalResults={pagination?.totalElements}
          />

          <div className="flex items-center justify-between text-xs pt-2">
            <span className="font-heading font-bold text-sm text-neutral-900 dark:text-neutral-100">
              {pagination?.totalElements ?? tutors.length} gia sư phù hợp
            </span>
            <span className="text-[11px] text-neutral-400 flex items-center gap-1 font-medium">
              <ShieldCheck className="size-3.5 text-emerald-500" />
              <span>Chỉ hiển thị gia sư đã xác minh</span>
            </span>
          </div>

          {searchError && (
            <div className="p-4 rounded-2xl bg-rose-50 border border-rose-200 text-rose-800 text-xs font-medium">
              {searchError}
            </div>
          )}

          {isSearchLoading ? (
            <div className="space-y-3">
              {[1, 2, 3, 4].map((i) => (
                <div
                  key={i}
                  className="h-24 rounded-2xl bg-card border border-stone-200/60 dark:border-stone-800 p-5 animate-pulse flex items-center justify-between"
                >
                  <div className="flex items-center gap-4">
                    <div className="size-12 rounded-full bg-stone-100 dark:bg-stone-800" />
                    <div className="space-y-2">
                      <div className="h-4 w-36 bg-stone-100 dark:bg-stone-800 rounded-md" />
                      <div className="h-3 w-48 bg-stone-100 dark:bg-stone-800 rounded-md" />
                    </div>
                  </div>
                  <div className="h-8 w-28 bg-stone-100 dark:bg-stone-800 rounded-full" />
                </div>
              ))}
            </div>
          ) : tutors.length === 0 ? (
            <div className="bg-card p-12 rounded-3xl border border-stone-200 dark:border-stone-800 text-center space-y-3">
              <Compass className="size-8 text-neutral-400 mx-auto" />
              <h3 className="font-heading font-bold text-base text-neutral-900 dark:text-neutral-100">
                Không tìm thấy gia sư phù hợp
              </h3>
              <p className="text-xs text-neutral-500 dark:text-neutral-400 max-w-md mx-auto">
                Hãy thử nới lỏng các tiêu chí bộ lọc (môn học, mức học phí hoặc khu vực) để xem thêm nhiều lựa chọn gia sư khác.
              </p>
              <button
                type="button"
                onClick={resetFilters}
                className="px-4 py-2 rounded-full bg-stone-100 dark:bg-stone-800 hover:bg-stone-200 dark:hover:bg-stone-700 text-neutral-800 dark:text-neutral-200 text-xs font-bold transition-colors cursor-pointer"
              >
                Đặt lại bộ lọc
              </button>
            </div>
          ) : (
            <div className="space-y-3">
              {tutors.map((tutor) => (
                <TutorCard
                  key={tutor.tutorId}
                  tutor={tutor}
                  onViewDetail={openTutor}
                  onRequestConnect={handleRequestConnect}
                />
              ))}
            </div>
          )}

          {pagination && pagination.totalPages > 1 && (
            <div className="flex items-center justify-between pt-4 border-t border-stone-200 dark:border-stone-800 text-xs">
              <span className="text-neutral-500 font-medium">
                Trang <strong className="text-neutral-900 dark:text-neutral-100">{pagination.page + 1}</strong> / {pagination.totalPages}
              </span>

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  disabled={pagination.first || isSearchLoading}
                  onClick={() => setPage(pagination.page - 1)}
                  className="inline-flex items-center gap-1 px-3 py-1.5 rounded-full bg-card border border-stone-200 dark:border-stone-800 text-card-foreground font-semibold hover:bg-stone-50 dark:hover:bg-stone-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer"
                >
                  <ChevronLeft className="size-4" />
                  <span>Trang trước</span>
                </button>

                <button
                  type="button"
                  disabled={pagination.last || isSearchLoading}
                  onClick={() => setPage(pagination.page + 1)}
                  className="inline-flex items-center gap-1 px-3 py-1.5 rounded-full bg-card border border-stone-200 dark:border-stone-800 text-card-foreground font-semibold hover:bg-stone-50 dark:hover:bg-stone-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer"
                >
                  <span>Trang sau</span>
                  <ChevronRight className="size-4" />
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* 4. TAB 2: GỢI Ý PHÙ HỢP (AI MATCH VIEW) */}
      {activeTab === "matches" && (
        <div className="space-y-4">
          <AiMatchView
            matching={matching}
            onViewDetail={openTutor}
            onRequestConnect={handleRequestConnect}
          />
        </div>
      )}

      {/* 5. TAB 3: YÊU CẦU ĐÃ GỬI */}
      {activeTab === "requests" && <SentRequests />}

      {/* 6. MODALS */}
      {connectTutor && (
        <ConnectionRequestDialog
          tutorId={connectTutor.tutorId}
          tutor={connectTutor}
          initialNeedId={matching.needId || undefined}
          onNeedSaved={(saved) => matching.refreshNeeds(saved.needId)}
          onClose={() => setConnectTutor(null)}
          onSent={() => {
            setConnectTutor(null);
            showToast("Đã gửi yêu cầu kết nối thành công.", "success");
            handleTabChange("requests");
          }}
        />
      )}

      <TutorDetailModal
        isOpen={isDetailOpen}
        tutor={selectedTutor}
        isLoading={isDetailLoading}
        onClose={closeTutor}
        onRequestConnect={handleRequestConnect}
      />
    </div>
  );
}
