"use client";

import React, { useState, Suspense, useEffect } from "react";
import { useRouter } from "next/navigation";
import {
  Compass,
  ChevronLeft,
  ChevronRight,
  Sparkles,
  RefreshCw,
  AlertCircle,
  Users,
} from "lucide-react";
import {
  TutorSearchFilters,
  TutorResultsGrid,
  type TutorViewMode,
  useTutorSearch,
  LoginPromptModal,
  type TutorDiscoveryCard,
} from "@/features/discovery";
import { ConnectionRequestDialog } from "@/features/connection";
import { connectionService } from "@/services/connections";
import { progressApi } from "@/features/progress";
import { useAuth } from "@/context/AuthContext";
import { useToast } from "@/shared/ui/Toast";
import { PublicNavbar } from "@/shared/ui/PublicNavbar";

function TutorsDirectoryContent() {
  const [viewMode, setViewMode] = useState<TutorViewMode>("list");
  const router = useRouter();
  const { user } = useAuth();
  const { showToast } = useToast();

  const {
    params,
    tutors,
    pagination,
    isLoading,
    error,
    updateFilters,
    setPage,
    resetFilters,
  } = useTutorSearch();

  // Guest login prompt modal state
  const [promptModal, setPromptModal] = useState<{
    isOpen: boolean;
    tutorName?: string;
    returnUrl?: string;
  }>({
    isOpen: false,
  });

  // Authenticated connect state
  const [connectTutor, setConnectTutor] = useState<TutorDiscoveryCard | null>(null);
  const [linkedChildren, setLinkedChildren] = useState<Array<{ studentId: string; name: string }>>([]);
  const [selectedChildId, setSelectedChildId] = useState<string>("");

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

  const handleViewDetail = (tutorId: string) => {
    router.push(`/tutors/${tutorId}`);
  };

  const handleRequestConnect = (tutorId: string) => {
    const targetTutor = tutors.find((t) => t.tutorId === tutorId);
    const tutorDisplayName = targetTutor?.displayName || "Gia sư";

    if (!user) {
      // 1. Guest user: show friendly LoginPromptModal
      setPromptModal({
        isOpen: true,
        tutorName: tutorDisplayName,
        returnUrl: `/tutors/${tutorId}`,
      });
      return;
    }

    // 2. Authenticated user
    if (user.role === "TUTOR" || user.role === "ADMIN") {
      showToast("Chức năng chỉ dành cho tài khoản Học sinh hoặc Phụ huynh.", "info");
      return;
    }

    if (user.role === "PARENT") {
      if (linkedChildren.length === 0) {
        showToast("Bạn chưa có học sinh liên kết. Liên kết tài khoản của con trước khi gửi yêu cầu.", "info");
        return;
      }
    }

    if (targetTutor) {
      setConnectTutor(targetTutor);
    }
  };

  return (
    <div className="min-h-screen bg-paper text-ink flex flex-col font-sans transition-colors">
      <PublicNavbar />

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8 sm:py-10 space-y-8">
        {/* Hero Banner Section */}
        <div className="relative rounded-3xl bg-action p-7 sm:p-10 text-on-action shadow-lg overflow-hidden">
          <div className="relative z-10 max-w-2xl space-y-3">
            <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-white/20 backdrop-blur-md text-xs font-medium text-white border border-white/30">
              <Sparkles className="size-3.5 text-action-tint" />
              <span>Chương trình THCS Chuẩn Lớp 6 - 9</span>
            </div>
            <h1 className="font-heading font-bold text-2xl sm:text-4xl tracking-tight leading-tight">
              Danh bạ Gia sư Chất lượng cao
            </h1>
            <p className="text-xs sm:text-sm text-action-tint leading-relaxed max-w-xl">
              100% hồ sơ gia sư được EduFit xác minh danh tính, bằng cấp sư phạm và kiểm duyệt năng lực. Tìm kiếm, xem lịch rảnh và kết nối học 1-1 minh bạch.
            </p>
          </div>

          <div className="absolute -bottom-10 -right-10 size-48 bg-white/10 rounded-full blur-2xl pointer-events-none" />
          <div className="absolute -top-10 right-20 size-36 bg-action-line/20 rounded-full blur-xl pointer-events-none" />
        </div>

        {/* Parent Child Switcher */}
        {user?.role === "PARENT" && linkedChildren.length > 0 && (
          <div className="flex flex-wrap items-center justify-between gap-3 p-4 bg-action-soft/80 border border-action-line/80 rounded-2xl text-xs">
            <div className="flex items-center gap-2 text-action-strong font-medium">
              <Users className="size-4 text-action-strong" />
              <span>Đang chọn gia sư cho con:</span>
            </div>
            <div className="flex items-center gap-2">
              <label htmlFor="select-child-tutors" className="text-ink-2 font-medium">
                Học sinh:
              </label>
              <select
                id="select-child-tutors"
                aria-label="Chọn con em liên kết"
                value={selectedChildId}
                onChange={(e) => setSelectedChildId(e.target.value)}
                className="rounded-xl border border-action-line bg-surface px-3 py-1.5 text-xs font-medium text-ink focus:outline-hidden focus:ring-2 focus:ring-action"
              >
                {linkedChildren.map((c) => (
                  <option key={c.studentId} value={c.studentId}>
                    {c.name}
                  </option>
                ))}
              </select>
            </div>
          </div>
        )}

        {/* Search & Filters */}
        <section aria-label="Bộ lọc tìm kiếm gia sư" className="space-y-4">
          <TutorSearchFilters
            params={params}
            onUpdateFilters={updateFilters}
            onResetFilters={resetFilters}
            totalResults={pagination?.totalElements ?? tutors.length}
            viewMode={viewMode}
            onViewModeChange={setViewMode}
          />
        </section>

        {/* 1. Error State */}
        {error && (
          <div
            role="alert"
            className="p-4 rounded-2xl bg-danger-soft border border-danger-line text-danger-strong text-xs font-medium flex items-center justify-between gap-3"
          >
            <div className="flex items-center gap-2">
              <AlertCircle className="size-4.5 text-danger-strong shrink-0" />
              <span>{error}</span>
            </div>
            <button
              type="button"
              onClick={() => resetFilters()}
              className="inline-flex items-center gap-1 px-3 py-1.5 rounded-xl bg-danger-soft text-danger-strong font-medium hover:bg-danger-line transition-colors cursor-pointer"
            >
              <RefreshCw className="size-3" />
              <span>Thử lại</span>
            </button>
          </div>
        )}

        {/* 2. Loading State (Skeleton) */}
        {isLoading && (
          <div className="space-y-3" data-testid="tutors-loading-skeleton">
            {[1, 2, 3, 4, 5].map((i) => (
              <div
                key={i}
                className="h-28 rounded-2xl bg-surface border border-line/80 p-5 animate-pulse flex items-center justify-between"
              >
                <div className="flex items-center gap-4">
                  <div className="size-12 rounded-full bg-sunken" />
                  <div className="space-y-2">
                    <div className="h-4 w-40 bg-sunken rounded-md" />
                    <div className="h-3 w-52 bg-sunken rounded-md" />
                  </div>
                </div>
                <div className="h-8 w-28 bg-sunken rounded-full" />
              </div>
            ))}
          </div>
        )}

        {/* 3. Empty State */}
        {!isLoading && !error && tutors.length === 0 && (
          <div className="p-12 sm:p-16 rounded-3xl bg-surface border border-dashed border-line-strong text-center space-y-4">
            <div className="size-14 rounded-2xl bg-sunken text-muted mx-auto flex items-center justify-center">
              <Compass className="size-7" />
            </div>
            <div className="space-y-1">
              <h3 className="font-heading font-bold text-base text-ink">
                Không tìm thấy gia sư phù hợp
              </h3>
              <p className="text-xs text-muted max-w-md mx-auto leading-relaxed">
                Thử nới lỏng bộ lọc (môn học, học phí hoặc khu vực) để xem thêm gia sư.
              </p>
            </div>
            <button
              type="button"
              onClick={resetFilters}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-full bg-action hover:bg-action-deep text-on-action text-xs font-medium shadow-xs transition-colors cursor-pointer"
            >
              <span>Đặt lại bộ lọc</span>
            </button>
          </div>
        )}

        {/* 4. Populated State (Tutor Cards) */}
        {!isLoading && !error && tutors.length > 0 && (
          <TutorResultsGrid
            tutors={tutors}
            viewMode={viewMode}
            onViewDetail={handleViewDetail}
            onRequestConnect={handleRequestConnect}
          />
        )}

        {/* Pagination Controls */}
        {pagination && pagination.totalPages > 1 && (
          <div className="flex items-center justify-between pt-6 border-t border-line text-xs">
            <span className="text-muted font-medium">
              Trang <strong className="text-ink">{pagination.page + 1}</strong> / {pagination.totalPages}
            </span>

            <div className="flex items-center gap-2">
              <button
                type="button"
                disabled={pagination.first || isLoading}
                onClick={() => setPage(pagination.page - 1)}
                className="inline-flex items-center gap-1 px-3.5 py-1.5 rounded-full bg-surface border border-line text-ink font-medium hover:bg-paper disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer shadow-2xs"
              >
                <ChevronLeft className="size-4" />
                <span>Trang trước</span>
              </button>

              <button
                type="button"
                disabled={pagination.last || isLoading}
                onClick={() => setPage(pagination.page + 1)}
                className="inline-flex items-center gap-1 px-3.5 py-1.5 rounded-full bg-surface border border-line text-ink font-medium hover:bg-paper disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer shadow-2xs"
              >
                <span>Trang sau</span>
                <ChevronRight className="size-4" />
              </button>
            </div>
          </div>
        )}
      </main>

      {/* Guest Login Prompt Modal */}
      <LoginPromptModal
        isOpen={promptModal.isOpen}
        onClose={() => setPromptModal({ isOpen: false })}
        tutorName={promptModal.tutorName}
        returnUrl={promptModal.returnUrl}
        actionText="gửi yêu cầu học"
      />

      {/* Authenticated Connection Request Dialog */}
      {connectTutor && (
        <ConnectionRequestDialog
          tutorId={connectTutor.tutorId}
          tutor={connectTutor}
          studentId={user?.role === "PARENT" ? selectedChildId || undefined : undefined}
          onClose={() => setConnectTutor(null)}
          onSent={() => {
            setConnectTutor(null);
            showToast("Đã gửi yêu cầu kết nối thành công!", "success");
          }}
        />
      )}
    </div>
  );
}

export default function TutorsPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen bg-paper flex items-center justify-center font-sans">
          <div className="flex flex-col items-center gap-3">
            <div className="size-8 border-3 border-action/30 border-t-action rounded-full animate-spin" />
            <span className="text-xs text-muted font-medium">Đang tải danh bạ gia sư EduFit...</span>
          </div>
        </div>
      }
    >
      <TutorsDirectoryContent />
    </Suspense>
  );
}
