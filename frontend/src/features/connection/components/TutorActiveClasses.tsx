"use client";

import React, { useState, useEffect, useCallback } from "react";
import Link from "next/link";
import {
  BookOpen,
  Calendar,
  Clock,
  CheckCircle2,
  AlertCircle,
  RefreshCw,
  GraduationCap,
  ArrowRight,
  TrendingUp,
  Award,
  CalendarDays,
} from "lucide-react";
import { connectionApi } from "../api/connectionApi";
import type { TutoringClass } from "../types";
import { CreateLearningPlanModal } from "@/features/progress";
import { EndClassModal } from "./EndClassModal";
import { useOptionalAuth } from "@/context/AuthContext";
import {
  ProposeRecurringScheduleModal,
  ActiveRecurringProposalBanner,
} from "@/features/scheduling";

export function TutorActiveClasses() {
  const auth = useOptionalAuth();
  const user = auth?.user;
  const [classes, setClasses] = useState<TutoringClass[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<"ALL" | "PENDING_SCHEDULE" | "SCHEDULED">("ALL");
  const [planModalClass, setPlanModalClass] = useState<TutoringClass | null>(null);
  const [endClassTarget, setEndClassTarget] = useState<TutoringClass | null>(null);
  const [recurringModalClass, setRecurringModalClass] = useState<TutoringClass | null>(null);

  const loadClasses = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await connectionApi.getTutorClasses();
      setClasses(data);
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Không thể tải danh sách lớp học. Vui lòng thử lại."
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadClasses();
  }, [loadClasses]);

  // Listen to Global Event Bus (edufit:schedule-updated) and StorageEvent to auto-reload
  useEffect(() => {
    const handleScheduleUpdated = () => {
      void loadClasses();
    };
    window.addEventListener("edufit:schedule-updated", handleScheduleUpdated);
    const handleStorage = (e: StorageEvent) => {
      if (e.key === "edufit:schedule-updated") {
        void loadClasses();
      }
    };
    window.addEventListener("storage", handleStorage);
    return () => {
      window.removeEventListener("edufit:schedule-updated", handleScheduleUpdated);
      window.removeEventListener("storage", handleStorage);
    };
  }, [loadClasses]);

  const isClassScheduled = (c: TutoringClass) =>
    (c.scheduledCount ?? 0) > 0 ||
    (c.sessionCount ?? 0) > 0 ||
    c.scheduleStatus === "SCHEDULED" ||
    c.scheduleStatus === "COMPLETED";

  const filteredClasses = classes.filter((c) => {
    const isPendingSchedule = !isClassScheduled(c);
    if (filter === "PENDING_SCHEDULE") return isPendingSchedule;
    if (filter === "SCHEDULED") return !isPendingSchedule;
    return true;
  });

  const pendingScheduleCount = classes.filter(
    (c) => !isClassScheduled(c)
  ).length;

  return (
    <div className="space-y-6">
      {/* Header & Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-zinc-200 dark:border-zinc-800 pb-5">
        <div>
          <h2 className="text-xl font-bold tracking-tight text-zinc-900 dark:text-zinc-100 flex items-center gap-2.5">
            <BookOpen className="h-6 w-6 text-blue-600 dark:text-blue-400" />
            Lớp học đang phụ trách
            <span className="inline-flex items-center rounded-full bg-zinc-100 px-2.5 py-0.5 text-xs font-semibold text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300">
              {classes.length} lớp
            </span>
          </h2>
          <p className="text-sm text-zinc-500 dark:text-zinc-400 mt-1">
            Quản lý các lớp gia sư 1-1, theo dõi trạng thái chốt lịch và số buổi học đã thực hiện.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <div className="inline-flex rounded-xl bg-zinc-100 p-1 dark:bg-zinc-800/80">
            <button
              onClick={() => setFilter("ALL")}
              className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-all ${
                filter === "ALL"
                  ? "bg-white text-zinc-900 shadow-xs dark:bg-zinc-700 dark:text-zinc-100"
                  : "text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-200"
              }`}
            >
              Tất cả ({classes.length})
            </button>
            <button
              onClick={() => setFilter("PENDING_SCHEDULE")}
              className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-all ${
                filter === "PENDING_SCHEDULE"
                  ? "bg-white text-zinc-900 shadow-xs dark:bg-zinc-700 dark:text-zinc-100"
                  : "text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-200"
              }`}
            >
              Chờ thống nhất lịch ({pendingScheduleCount})
            </button>
          </div>

          <button
            onClick={() => void loadClasses()}
            disabled={loading}
            className="inline-flex items-center gap-1.5 rounded-xl border border-zinc-200 bg-white px-3 py-1.5 text-xs font-medium text-zinc-700 shadow-xs hover:bg-zinc-50 active:scale-95 disabled:opacity-50 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-200 dark:hover:bg-zinc-700"
            title="Làm mới danh sách"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`} />
            Làm mới
          </button>
        </div>
      </div>

      {/* Error State */}
      {error && (
        <div className="rounded-2xl border border-rose-200 bg-rose-50/70 p-4 text-sm text-rose-800 dark:border-rose-900/50 dark:bg-rose-950/20 dark:text-rose-300 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <AlertCircle className="h-5 w-5 text-rose-600 shrink-0" />
            <span>{error}</span>
          </div>
          <button
            onClick={() => void loadClasses()}
            className="rounded-lg bg-rose-100 px-3 py-1 text-xs font-semibold text-rose-700 hover:bg-rose-200 dark:bg-rose-900/40 dark:text-rose-200"
          >
            Thử lại
          </button>
        </div>
      )}

      {/* Loading State */}
      {loading && (
        <div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
          {[1, 2, 3].map((i) => (
            <div
              key={i}
              className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-xs animate-pulse dark:border-zinc-800 dark:bg-zinc-900 space-y-4"
            >
              <div className="flex items-center justify-between">
                <div className="h-4 w-32 rounded-md bg-zinc-200 dark:bg-zinc-800" />
                <div className="h-6 w-24 rounded-full bg-zinc-200 dark:bg-zinc-800" />
              </div>
              <div className="space-y-2">
                <div className="h-3 w-40 rounded-md bg-zinc-200 dark:bg-zinc-800" />
                <div className="h-3 w-28 rounded-md bg-zinc-200 dark:bg-zinc-800" />
              </div>
              <div className="h-9 w-full rounded-xl bg-zinc-200 dark:bg-zinc-800" />
            </div>
          ))}
        </div>
      )}

      {/* Empty State */}
      {!loading && !error && filteredClasses.length === 0 && (
        <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-zinc-300 bg-zinc-50/50 py-14 text-center dark:border-zinc-800 dark:bg-zinc-900/50">
          <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-zinc-100 text-zinc-400 dark:bg-zinc-800 dark:text-zinc-500 mb-3">
            <BookOpen className="h-6 w-6" />
          </div>
          <h3 className="text-base font-semibold text-zinc-900 dark:text-zinc-100">
            {filter === "PENDING_SCHEDULE"
              ? "Không có lớp học nào đang chờ thống nhất lịch"
              : "Bạn chưa có lớp học nào đang phụ trách"}
          </h3>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400 max-w-sm">
            Sau khi duyệt các yêu cầu học kèm từ học sinh trong Hộp thư yêu cầu, các lớp học mới
            sẽ được kích hoạt và xuất hiện tại đây.
          </p>
          <div className="mt-4">
            <Link
              href="/tutor/requests"
              className="inline-flex items-center gap-2 rounded-xl bg-blue-600 px-4 py-2 text-xs font-semibold text-white shadow-xs hover:bg-blue-700 active:scale-95 transition-all"
            >
              Xem Hộp thư yêu cầu
              <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          </div>
        </div>
      )}

      {/* Populated State */}
      {!loading && !error && filteredClasses.length > 0 && (
        <div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
          {filteredClasses.map((item) => {
            const isScheduled = isClassScheduled(item);
            const totalCount = item.totalCount ?? item.sessionCount ?? 0;
            const completedCount = item.completedCount ?? 0;
            const scheduledCount = item.scheduledCount ?? (isScheduled ? Math.max(0, totalCount - completedCount) : 0);
            const isEnded = item.status === "ENDED";
            const isBatchCompleted = !isEnded && totalCount > 0 && scheduledCount === 0 && completedCount >= totalCount;
            const isOngoing = !isEnded && isScheduled && !isBatchCompleted;
            const isUnscheduled = !isEnded && !isScheduled;
            const isPendingSchedule = isUnscheduled;

            return (
              <div
                key={item.classId}
                className="flex flex-col justify-between rounded-2xl border border-zinc-200 bg-white p-5 shadow-xs hover:shadow-md transition-shadow dark:border-zinc-800 dark:bg-zinc-900"
              >
                <div>
                  {/* Top Bar: Subject & Badge */}
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <h4 className="font-bold text-zinc-900 dark:text-zinc-100 text-base">
                        {item.subjectName || "Môn học kèm"}
                      </h4>
                      <p className="text-xs text-zinc-500 dark:text-zinc-400 flex items-center gap-1 mt-0.5">
                        <GraduationCap className="h-3.5 w-3.5 text-zinc-400" />
                        {item.educationLevelName || "Cấp học"}
                      </p>
                    </div>

                    {/* Badge */}
                    <div>
                      {isEnded ? (
                        <span
                          data-testid="badge-ended"
                          className="inline-flex items-center gap-1.5 rounded-full bg-zinc-100 px-2.5 py-1 text-xs font-semibold text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300 border border-zinc-200 dark:border-zinc-700"
                        >
                          <CheckCircle2 className="h-3 w-3 text-zinc-500" />
                          Đã kết thúc
                        </span>
                      ) : isBatchCompleted ? (
                        <span
                          data-testid="badge-batch-completed"
                          className="inline-flex items-center gap-1.5 rounded-full bg-purple-100 px-2.5 py-1 text-xs font-semibold text-purple-800 dark:bg-purple-950/60 dark:text-purple-300 border border-purple-200 dark:border-purple-900/50"
                        >
                          <CheckCircle2 className="h-3 w-3 text-purple-600 dark:text-purple-400" />
                          Đã hoàn thành đợt học
                        </span>
                      ) : isPendingSchedule ? (
                        <span
                          data-testid="badge-pending-schedule"
                          className="inline-flex items-center gap-1.5 rounded-full bg-amber-100 px-2.5 py-1 text-xs font-semibold text-amber-800 dark:bg-amber-950/60 dark:text-amber-300 border border-amber-200 dark:border-amber-900/50"
                        >
                          <Clock className="h-3 w-3 text-amber-600 dark:text-amber-400" />
                          Chờ thống nhất lịch học
                        </span>
                      ) : (
                        <span
                          data-testid="badge-scheduled"
                          className="inline-flex items-center gap-1.5 rounded-full bg-emerald-100 px-2.5 py-1 text-xs font-semibold text-emerald-800 dark:bg-emerald-950/60 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-900/50"
                        >
                          <CheckCircle2 className="h-3 w-3 text-emerald-600 dark:text-emerald-400" />
                          Đã chốt lịch
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Student Info */}
                  <div className="mt-4 flex items-center gap-3 rounded-xl bg-zinc-50 p-3 dark:bg-zinc-800/60 border border-zinc-100 dark:border-zinc-800">
                    <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-blue-100 text-blue-700 dark:bg-blue-950/80 dark:text-blue-300 font-bold text-xs">
                      {item.studentDisplayName
                        ? item.studentDisplayName.charAt(0).toUpperCase()
                        : "HS"}
                    </div>
                    <div>
                      <span className="text-[11px] font-medium text-zinc-400 uppercase tracking-wider block">
                        Học sinh
                      </span>
                      <span className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
                        {item.studentDisplayName || "Học sinh"}
                      </span>
                    </div>
                  </div>

                  {/* Session Stats */}
                  <div className="mt-4 grid grid-cols-2 gap-3 text-xs">
                    <div className="rounded-xl border border-zinc-100 bg-zinc-50/50 p-2.5 dark:border-zinc-800 dark:bg-zinc-800/40">
                      <span className="text-zinc-500 dark:text-zinc-400 block text-[11px]">
                        Số buổi đã học
                      </span>
                      <span className="text-sm font-bold text-zinc-900 dark:text-zinc-100 mt-0.5 block">
                        {item.completedCount ?? 0} / {item.totalCount ?? item.sessionCount} buổi
                      </span>
                    </div>
                    <div className="rounded-xl border border-zinc-100 bg-zinc-50/50 p-2.5 dark:border-zinc-800 dark:bg-zinc-800/40">
                      <span className="text-zinc-500 dark:text-zinc-400 block text-[11px]">
                        Trạng thái lớp
                      </span>
                      <span
                        className={`text-sm font-bold mt-0.5 block ${
                          item.status === "ACTIVE"
                            ? "text-emerald-600 dark:text-emerald-400"
                            : "text-zinc-600 dark:text-zinc-400"
                        }`}
                      >
                        {item.status === "ACTIVE"
                          ? "Đang diễn ra"
                          : item.status === "ENDED"
                          ? "Đã kết thúc"
                          : item.status}
                      </span>
                    </div>
                  </div>

                  {/* 2-Step Note if schedule pending */}
                  {isPendingSchedule && (
                    <div className="mt-3 rounded-xl bg-amber-50/80 p-2.5 text-xs text-amber-900 dark:bg-amber-950/30 dark:text-amber-300 border border-amber-200/70 dark:border-amber-900/40">
                      <p className="leading-relaxed">
                        Lớp mới tạo. Vui lòng vào Lịch dạy để đề xuất buổi học đầu tiên hoặc xác
                        nhận lịch học đề xuất từ học sinh.
                      </p>
                    </div>
                  )}

                  {/* Active Recurring Proposal Banner (UC4.8) */}
                  <ActiveRecurringProposalBanner
                    classId={item.classId}
                    currentUserId={user?.id}
                    userRole="TUTOR"
                    onUpdated={loadClasses}
                  />
                </div>

                {/* Actions */}
                <div className="mt-5 flex flex-wrap items-center gap-2 pt-3 border-t border-zinc-100 dark:border-zinc-800">
                  <button
                    type="button"
                    onClick={() => setPlanModalClass(item)}
                    className="flex-1 inline-flex items-center justify-center gap-1.5 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-xs font-semibold text-amber-800 shadow-xs hover:bg-amber-100 active:scale-95 transition-all text-center cursor-pointer dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-900/50"
                  >
                    <Award className="h-3.5 w-3.5 text-amber-600 dark:text-amber-400" />
                    Kế hoạch học tập
                  </button>
                  <Link
                    href={`/tutor/schedule`}
                    className="flex-1 inline-flex items-center justify-center gap-1.5 rounded-xl bg-blue-600 px-3 py-2 text-xs font-semibold text-white shadow-xs hover:bg-blue-700 active:scale-95 transition-all text-center"
                  >
                    <Calendar className="h-3.5 w-3.5" />
                    Lịch dạy & Đổi lịch
                  </Link>
                  {item.status === "ACTIVE" && (
                    <>
                      <button
                        type="button"
                        onClick={() => setRecurringModalClass(item)}
                        className="inline-flex items-center justify-center gap-1 rounded-xl border border-blue-200 bg-blue-50 px-3 py-2 text-xs font-semibold text-blue-700 hover:bg-blue-100 active:scale-95 transition-all cursor-pointer dark:border-blue-900/50 dark:bg-blue-950/40 dark:text-blue-300"
                        title={
                          isOngoing
                            ? "Đề xuất lại lịch học định kỳ (Hủy lịch cũ khi được chấp thuận)"
                            : isBatchCompleted
                            ? "Đề xuất tiếp đợt học mới (4-8 tuần)"
                            : "Thống nhất lịch học định kỳ (4-8 tuần)"
                        }
                      >
                        <CalendarDays className="h-3.5 w-3.5 text-blue-600 dark:text-blue-400" />
                        {isOngoing
                          ? "Đề xuất lại lịch học định kỳ"
                          : isBatchCompleted
                          ? "Đề xuất tiếp đợt học mới"
                          : "Thống nhất lịch học định kỳ"}
                      </button>
                      <button
                        type="button"
                        onClick={() => setEndClassTarget(item)}
                        className={`inline-flex items-center justify-center gap-1 rounded-xl px-3 py-2 text-xs font-semibold active:scale-95 transition-all cursor-pointer ${
                          isBatchCompleted
                            ? "border border-emerald-200 bg-emerald-50 text-emerald-700 hover:bg-emerald-100 dark:border-emerald-900/50 dark:bg-emerald-950/40 dark:text-emerald-300"
                            : "border border-rose-200 bg-rose-50 text-rose-700 hover:bg-rose-100 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-300"
                        }`}
                        title="Kết thúc lớp học"
                      >
                        {isBatchCompleted ? (
                          <>
                            <CheckCircle2 className="h-3.5 w-3.5" />
                            Xác nhận hoàn tất
                          </>
                        ) : (
                          "Kết thúc lớp"
                        )}
                      </button>
                    </>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Modal thiết lập Kế hoạch học tập */}
      {planModalClass && (
        <CreateLearningPlanModal
          isOpen={Boolean(planModalClass)}
          onClose={() => setPlanModalClass(null)}
          classId={planModalClass.classId}
          className={planModalClass.subjectName ?? undefined}
          studentName={planModalClass.studentDisplayName ?? undefined}
          onSuccess={() => {
            setPlanModalClass(null);
            void loadClasses();
          }}
        />
      )}

      {/* Modal kết thúc lớp học */}
      <EndClassModal
        classItem={endClassTarget}
        isOpen={Boolean(endClassTarget)}
        onClose={() => setEndClassTarget(null)}
        onSuccess={() => void loadClasses()}
      />

      {/* Modal lập lịch học định kỳ UC4.8 */}
      {recurringModalClass && (
        <ProposeRecurringScheduleModal
          isOpen={Boolean(recurringModalClass)}
          onClose={() => setRecurringModalClass(null)}
          classId={recurringModalClass.classId}
          className={
            recurringModalClass.subjectName
              ? `${recurringModalClass.subjectName}${recurringModalClass.educationLevelName ? ` - ${recurringModalClass.educationLevelName}` : ""}`
              : undefined
          }
          tutorName={recurringModalClass.tutorDisplayName ?? undefined}
          studentName={recurringModalClass.studentDisplayName ?? undefined}
          isReschedule={Boolean(
            recurringModalClass && isClassScheduled(recurringModalClass)
          )}
          onSuccess={() => {
            setRecurringModalClass(null);
            void loadClasses();
          }}
        />
      )}
    </div>
  );
}
