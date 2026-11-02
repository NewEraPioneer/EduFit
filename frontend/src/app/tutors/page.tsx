"use client";

import React, { useState, Suspense, useEffect } from "react";
import { useRouter } from "next/navigation";
import {
  Compass,
  ChevronLeft,
  ChevronRight,
  ShieldCheck,
  Search,
  Sparkles,
  RefreshCw,
  AlertCircle,
  Users,
} from "lucide-react";
import {
  TutorSearchFilters,
  TutorCard,
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
          name: studentNames[link.studentId] || `Học sinh ${idx + 1} (${link.studentId.slice(-8)})`,
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
        showToast("Bạn chưa có học sinh liên kết. Vui lòng liên kết tài khoản con trước khi gửi yêu cầu kết nối gia sư.", "info");
        return;
      }
    }

    if (targetTutor) {
      setConnectTutor(targetTutor);
    }
  };

  return (
    <div className="min-h-screen bg-stone-50 dark:bg-stone-950 text-neutral-900 dark:text-stone-100 flex flex-col font-sans transition-colors">
      <PublicNavbar />

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8 sm:py-10 space-y-8">
        {/* Hero Banner Section */}
        <div className="relative rounded-3xl bg-gradient-to-r from-amber-500 via-orange-500 to-amber-600 p-7 sm:p-10 text-white shadow-lg overflow-hidden">
          <div className="relative z-10 max-w-2xl space-y-3">
            <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-white/20 backdrop-blur-md text-xs font-bold text-white border border-white/30">
              <Sparkles className="size-3.5 text-amber-200" />
              <span>Chương trình THCS Chuẩn Lớp 6 - 9</span>
            </div>
            <h1 className="font-heading font-extrabold text-2xl sm:text-4xl tracking-tight leading-tight">
              Danh bạ Gia sư Chất lượng cao
            </h1>
            <p className="text-xs sm:text-sm text-amber-100 leading-relaxed max-w-xl">
              100% hồ sơ gia sư được EduFit xác minh danh tính, bằng cấp sư phạm và kiểm duyệt năng lực. Tìm kiếm, xem lịch rảnh và kết nối học 1-1 minh bạch.
            </p>
          </div>

          <div className="absolute -bottom-10 -right-10 size-48 bg-white/10 rounded-full blur-2xl pointer-events-none" />
          <div className="absolute -top-10 right-20 size-36 bg-amber-300/20 rounded-full blur-xl pointer-events-none" />
        </div>

        {/* Parent Child Switcher */}
        {user?.role === "PARENT" && linkedChildren.length > 0 && (
          <div className="flex flex-wrap items-center justify-between gap-3 p-4 bg-amber-50/80 dark:bg-amber-950/40 border border-amber-200/80 dark:border-amber-900/60 rounded-2xl text-xs">
            <div className="flex items-center gap-2 text-amber-900 dark:text-amber-200 font-bold">
              <Users className="size-4 text-amber-600 dark:text-amber-400" />
              <span>Đang chọn gia sư cho con:</span>
            </div>
            <div className="flex items-center gap-2">
              <label htmlFor="select-child-tutors" className="text-neutral-600 dark:text-stone-300 font-medium">
                Học sinh:
              </label>
              <select
                id="select-child-tutors"
                aria-label="Chọn con em liên kết"
                value={selectedChildId}
                onChange={(e) => setSelectedChildId(e.target.value)}
                className="rounded-xl border border-amber-300 dark:border-amber-800 bg-white dark:bg-stone-900 px-3 py-1.5 text-xs font-bold text-neutral-900 dark:text-stone-100 focus:outline-hidden focus:ring-2 focus:ring-amber-500"
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
            totalResults={pagination?.totalElements}
          />

          <div className="flex flex-wrap items-center justify-between gap-3 text-xs pt-2">
            <span className="font-heading font-bold text-sm text-neutral-900 dark:text-stone-100">
              {pagination?.totalElements ?? tutors.length} gia sư được tìm thấy
            </span>
            <div className="flex items-center gap-3 text-neutral-500 dark:text-stone-400">
              <span className="inline-flex items-center gap-1 font-medium text-emerald-600 dark:text-emerald-400">
                <ShieldCheck className="size-4" />
                <span>Đã xác minh bằng cấp</span>
              </span>
            </div>
          </div>
        </section>

        {/* 1. Error State */}
        {error && (
          <div
            role="alert"
            className="p-4 rounded-2xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/50 text-rose-800 dark:text-rose-300 text-xs font-medium flex items-center justify-between gap-3"
          >
            <div className="flex items-center gap-2">
              <AlertCircle className="size-4.5 text-rose-600 shrink-0" />
              <span>{error}</span>
            </div>
            <button
              type="button"
              onClick={() => resetFilters()}
              className="inline-flex items-center gap-1 px-3 py-1.5 rounded-xl bg-rose-100 dark:bg-rose-900/60 text-rose-800 dark:text-rose-200 font-bold hover:bg-rose-200 transition-colors cursor-pointer"
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
                className="h-28 rounded-2xl bg-white dark:bg-stone-900 border border-stone-200/80 dark:border-stone-800 p-5 animate-pulse flex items-center justify-between"
              >
                <div className="flex items-center gap-4">
                  <div className="size-12 rounded-full bg-stone-100 dark:bg-stone-800" />
                  <div className="space-y-2">
                    <div className="h-4 w-40 bg-stone-100 dark:bg-stone-800 rounded-md" />
                    <div className="h-3 w-52 bg-stone-100 dark:bg-stone-800 rounded-md" />
                  </div>
                </div>
                <div className="h-8 w-28 bg-stone-100 dark:bg-stone-800 rounded-full" />
              </div>
            ))}
          </div>
        )}

        {/* 3. Empty State */}
        {!isLoading && !error && tutors.length === 0 && (
          <div className="p-12 sm:p-16 rounded-3xl bg-white dark:bg-stone-900 border border-dashed border-stone-300 dark:border-stone-800 text-center space-y-4">
            <div className="size-14 rounded-2xl bg-stone-100 dark:bg-stone-800 text-neutral-400 mx-auto flex items-center justify-center">
              <Compass className="size-7" />
            </div>
            <div className="space-y-1">
              <h3 className="font-heading font-bold text-base text-neutral-900 dark:text-stone-100">
                Không tìm thấy gia sư phù hợp
              </h3>
              <p className="text-xs text-neutral-500 dark:text-stone-400 max-w-md mx-auto leading-relaxed">
                Hãy thử nới lỏng các tiêu chí bộ lọc (môn học, mức học phí hoặc khu vực) để khám phá thêm nhiều lựa chọn gia sư khác.
              </p>
            </div>
            <button
              type="button"
              onClick={resetFilters}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-full bg-amber-600 hover:bg-amber-700 text-white text-xs font-bold shadow-xs transition-colors cursor-pointer"
            >
              <span>Đặt lại bộ lọc</span>
            </button>
          </div>
        )}

        {/* 4. Populated State (Tutor Cards) */}
        {!isLoading && !error && tutors.length > 0 && (
          <div className="space-y-3.5">
            {tutors.map((tutor) => (
              <TutorCard
                key={tutor.tutorId}
                tutor={tutor}
                onViewDetail={handleViewDetail}
                onRequestConnect={handleRequestConnect}
              />
            ))}
          </div>
        )}

        {/* Pagination Controls */}
        {pagination && pagination.totalPages > 1 && (
          <div className="flex items-center justify-between pt-6 border-t border-stone-200 dark:border-stone-800 text-xs">
            <span className="text-neutral-500 dark:text-stone-400 font-medium">
              Trang <strong className="text-neutral-900 dark:text-stone-100">{pagination.page + 1}</strong> / {pagination.totalPages}
            </span>

            <div className="flex items-center gap-2">
              <button
                type="button"
                disabled={pagination.first || isLoading}
                onClick={() => setPage(pagination.page - 1)}
                className="inline-flex items-center gap-1 px-3.5 py-1.5 rounded-full bg-white dark:bg-stone-900 border border-stone-200 dark:border-stone-800 text-neutral-800 dark:text-stone-200 font-semibold hover:bg-stone-50 dark:hover:bg-stone-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer shadow-2xs"
              >
                <ChevronLeft className="size-4" />
                <span>Trang trước</span>
              </button>

              <button
                type="button"
                disabled={pagination.last || isLoading}
                onClick={() => setPage(pagination.page + 1)}
                className="inline-flex items-center gap-1 px-3.5 py-1.5 rounded-full bg-white dark:bg-stone-900 border border-stone-200 dark:border-stone-800 text-neutral-800 dark:text-stone-200 font-semibold hover:bg-stone-50 dark:hover:bg-stone-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer shadow-2xs"
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
        <div className="min-h-screen bg-stone-50 dark:bg-stone-950 flex items-center justify-center font-sans">
          <div className="flex flex-col items-center gap-3">
            <div className="size-8 border-3 border-amber-500/30 border-t-amber-500 rounded-full animate-spin" />
            <span className="text-xs text-neutral-500 font-medium">Đang tải danh bạ gia sư EduFit...</span>
          </div>
        </div>
      }
    >
      <TutorsDirectoryContent />
    </Suspense>
  );
}
