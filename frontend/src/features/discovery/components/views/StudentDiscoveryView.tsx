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
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-2 border-b border-line">
        <div>
          <h1 className="font-heading font-bold text-2xl text-ink tracking-tight">
            Tìm kiếm gia sư
          </h1>
          <p className="text-xs text-muted mt-1">
            Gia sư THCS đã được xác minh danh tính và bằng cấp sư phạm
          </p>
        </div>
      </div>

      {/* 2. TABS NAVIGATION */}
      <div className="flex items-center gap-6 border-b border-line text-xs font-medium">
        <button
          type="button"
          onClick={() => handleTabChange("browse")}
          className={`pb-3 border-b-2 transition-all cursor-pointer flex items-center gap-1.5 ${
            activeTab === "browse"
              ? "border-brand text-brand-strong font-medium"
              : "border-transparent text-muted hover:text-ink"
          }`}
        >
          <span>Tìm gia sư</span>
        </button>

        <button
          type="button"
          onClick={() => handleTabChange("matches")}
          className={`pb-3 border-b-2 transition-all cursor-pointer flex items-center gap-1.5 ${
            activeTab === "matches"
              ? "border-brand text-brand-strong font-medium"
              : "border-transparent text-muted hover:text-ink"
          }`}
        >
          <span>Gợi ý phù hợp (AI)</span>
          <span
            className={`px-1.5 py-0.5 rounded-full text-xs font-medium ${
              activeTab === "matches"
                ? "bg-brand text-white"
                : "bg-line text-ink-2"
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
              ? "border-brand text-brand-strong font-medium"
              : "border-transparent text-muted hover:text-ink"
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
            <span className="font-heading font-bold text-sm text-ink">
              {pagination?.totalElements ?? tutors.length} gia sư phù hợp
            </span>
            <span className="text-xs text-muted flex items-center gap-1 font-medium">
              <ShieldCheck className="size-3.5 text-success-strong" />
              <span>Chỉ hiển thị gia sư đã xác minh</span>
            </span>
          </div>

          {searchError && (
            <div className="p-4 rounded-2xl bg-danger-soft border border-danger-line text-danger-strong text-xs font-medium">
              {searchError}
            </div>
          )}

          {isSearchLoading ? (
            <div className="space-y-3">
              {[1, 2, 3, 4].map((i) => (
                <div
                  key={i}
                  className="h-24 rounded-2xl bg-card border border-line/60 p-5 animate-pulse flex items-center justify-between"
                >
                  <div className="flex items-center gap-4">
                    <div className="size-12 rounded-full bg-sunken" />
                    <div className="space-y-2">
                      <div className="h-4 w-36 bg-sunken rounded-md" />
                      <div className="h-3 w-48 bg-sunken rounded-md" />
                    </div>
                  </div>
                  <div className="h-8 w-28 bg-sunken rounded-full" />
                </div>
              ))}
            </div>
          ) : tutors.length === 0 ? (
            <div className="bg-card p-12 rounded-3xl border border-line text-center space-y-3">
              <Compass className="size-8 text-muted mx-auto" />
              <h3 className="font-heading font-bold text-base text-ink">
                Không tìm thấy gia sư phù hợp
              </h3>
              <p className="text-xs text-muted max-w-md mx-auto">
                Thử nới lỏng bộ lọc (môn học, học phí hoặc khu vực) để xem thêm gia sư.
              </p>
              <button
                type="button"
                onClick={resetFilters}
                className="px-4 py-2 rounded-full bg-sunken hover:bg-line text-ink text-xs font-medium transition-colors cursor-pointer"
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
            <div className="flex items-center justify-between pt-4 border-t border-line text-xs">
              <span className="text-muted font-medium">
                Trang <strong className="text-ink">{pagination.page + 1}</strong> / {pagination.totalPages}
              </span>

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  disabled={pagination.first || isSearchLoading}
                  onClick={() => setPage(pagination.page - 1)}
                  className="inline-flex items-center gap-1 px-3 py-1.5 rounded-full bg-card border border-line text-card-foreground font-medium hover:bg-paper disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer"
                >
                  <ChevronLeft className="size-4" />
                  <span>Trang trước</span>
                </button>

                <button
                  type="button"
                  disabled={pagination.last || isSearchLoading}
                  onClick={() => setPage(pagination.page + 1)}
                  className="inline-flex items-center gap-1 px-3 py-1.5 rounded-full bg-card border border-line text-card-foreground font-medium hover:bg-paper disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer"
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
