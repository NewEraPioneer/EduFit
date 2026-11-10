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
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-line pb-5">
        <div>
          <h2 className="text-xl font-bold tracking-tight text-ink flex items-center gap-2.5">
            <BookOpen className="h-6 w-6 text-brand-strong" />
            Lớp học đang phụ trách
            <span className="inline-flex items-center rounded-full bg-sunken px-2.5 py-0.5 text-xs font-medium text-ink-2">
              {classes.length} lớp
            </span>
          </h2>
          <p className="text-sm text-muted mt-1">
            Quản lý các lớp gia sư 1-1, theo dõi trạng thái chốt lịch và số buổi học đã thực hiện.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <div className="inline-flex rounded-xl bg-sunken p-1">
            <button
              onClick={() => setFilter("ALL")}
              className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-all ${
                filter === "ALL"
                  ? "bg-surface text-ink shadow-xs"
                  : "text-ink-2 hover:text-ink"
              }`}
            >
              Tất cả ({classes.length})
            </button>
            <button
              onClick={() => setFilter("PENDING_SCHEDULE")}
              className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-all ${
                filter === "PENDING_SCHEDULE"
                  ? "bg-surface text-ink shadow-xs"
                  : "text-ink-2 hover:text-ink"
              }`}
            >
              Chờ thống nhất lịch ({pendingScheduleCount})
            </button>
          </div>

          <button
            onClick={() => void loadClasses()}
            disabled={loading}
            className="inline-flex items-center gap-1.5 rounded-xl border border-line bg-surface px-3 py-1.5 text-xs font-medium text-ink-2 shadow-xs hover:bg-paper active:scale-95 disabled:opacity-50"
            title="Làm mới danh sách"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`} />
            Làm mới
          </button>
        </div>
      </div>

      {/* Error State */}
      {error && (
        <div className="rounded-2xl border border-danger-line bg-danger-soft/70 p-4 text-sm text-danger-strong flex items-center justify-between">
          <div className="flex items-center gap-3">
            <AlertCircle className="h-5 w-5 text-danger-strong shrink-0" />
            <span>{error}</span>
          </div>
          <button
            onClick={() => void loadClasses()}
            className="rounded-lg bg-danger-soft px-3 py-1 text-xs font-medium text-danger-strong hover:bg-danger-line"
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
              className="rounded-2xl border border-line bg-surface p-5 shadow-xs animate-pulse space-y-4"
            >
              <div className="flex items-center justify-between">
                <div className="h-4 w-32 rounded-md bg-line" />
                <div className="h-6 w-24 rounded-full bg-line" />
              </div>
              <div className="space-y-2">
                <div className="h-3 w-40 rounded-md bg-line" />
                <div className="h-3 w-28 rounded-md bg-line" />
              </div>
              <div className="h-9 w-full rounded-xl bg-line" />
            </div>
          ))}
        </div>
      )}

      {/* Empty State */}
      {!loading && !error && filteredClasses.length === 0 && (
        <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-line-strong bg-paper/50 py-14 text-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-sunken text-muted mb-3">
            <BookOpen className="h-6 w-6" />
          </div>
          <h3 className="text-base font-medium text-ink">
            {filter === "PENDING_SCHEDULE"
              ? "Không có lớp học nào đang chờ thống nhất lịch"
              : "Bạn chưa có lớp học nào đang phụ trách"}
          </h3>
          <p className="mt-1 text-sm text-muted max-w-sm">
            Sau khi duyệt các yêu cầu học kèm từ học sinh trong Hộp thư yêu cầu, các lớp học mới
            sẽ được kích hoạt và xuất hiện tại đây.
          </p>
          <div className="mt-4">
            <Link
              href="/tutor/requests"
              className="inline-flex items-center gap-2 rounded-xl bg-brand px-4 py-2 text-xs font-medium text-white shadow-xs hover:bg-brand-deep active:scale-95 transition-all"
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
                className="flex flex-col justify-between rounded-2xl border border-line bg-surface p-5 shadow-xs card-lift"
              >
                <div>
                  {/* Top Bar: Subject & Badge */}
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <h4 className="font-medium text-ink text-base">
                        {item.subjectName || "Môn học kèm"}
                      </h4>
                      <p className="text-xs text-muted flex items-center gap-1 mt-0.5">
                        <GraduationCap className="h-3.5 w-3.5 text-muted" />
                        {item.educationLevelName || "Cấp học"}
                      </p>
                    </div>

                    {/* Badge */}
                    <div>
                      {isEnded ? (
                        <span
                          data-testid="badge-ended"
                          className="inline-flex items-center gap-1.5 rounded-full bg-sunken px-2.5 py-1 text-xs font-medium text-ink-2 border border-line"
                        >
                          <CheckCircle2 className="h-3 w-3 text-muted" />
                          Đã kết thúc
                        </span>
                      ) : isBatchCompleted ? (
                        <span
                          data-testid="badge-batch-completed"
                          className="inline-flex items-center gap-1.5 rounded-full bg-brand-soft px-2.5 py-1 text-xs font-medium text-brand-strong border border-brand-line"
                        >
                          <CheckCircle2 className="h-3 w-3 text-brand-strong" />
                          Đã hoàn thành đợt học
                        </span>
                      ) : isPendingSchedule ? (
                        <span
                          data-testid="badge-pending-schedule"
                          className="inline-flex items-center gap-1.5 rounded-full bg-action-soft px-2.5 py-1 text-xs font-medium text-action-strong border border-action-line"
                        >
                          <Clock className="h-3 w-3 text-action-strong" />
                          Chờ thống nhất lịch học
                        </span>
                      ) : (
                        <span
                          data-testid="badge-scheduled"
                          className="inline-flex items-center gap-1.5 rounded-full bg-success-soft px-2.5 py-1 text-xs font-medium text-success-strong border border-success-line"
                        >
                          <CheckCircle2 className="h-3 w-3 text-success-strong" />
                          Đã chốt lịch
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Student Info */}
                  <div className="mt-4 flex items-center gap-3 rounded-xl bg-paper p-3 border border-line">
                    <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-brand-soft text-brand-strong font-medium text-xs">
                      {item.studentDisplayName
                        ? item.studentDisplayName.charAt(0).toUpperCase()
                        : "HS"}
                    </div>
                    <div>
                      <span className="text-xs font-medium text-muted block">
                        Học sinh
                      </span>
                      <span className="text-sm font-medium text-ink">
                        {item.studentDisplayName || "Học sinh"}
                      </span>
                    </div>
                  </div>

                  {/* Session Stats */}
                  <div className="mt-4 grid grid-cols-2 gap-3 text-xs">
                    <div className="rounded-xl border border-line bg-paper/50 p-2.5">
                      <span className="text-muted block text-xs">
                        Số buổi đã học
                      </span>
                      <span className="text-sm font-medium text-ink mt-0.5 block">
                        {item.completedCount ?? 0} / {item.totalCount ?? item.sessionCount} buổi
                      </span>
                    </div>
                    <div className="rounded-xl border border-line bg-paper/50 p-2.5">
                      <span className="text-muted block text-xs">
                        Trạng thái lớp
                      </span>
                      <span
                        className={`text-sm font-medium mt-0.5 block ${
                          item.status === "ACTIVE"
                            ? "text-success-strong"
                            : "text-ink-2"
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
                    <div className="mt-3 rounded-xl bg-action-soft/80 p-2.5 text-xs text-action-strong border border-action-line/70">
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
                <div className="mt-5 flex flex-wrap items-center gap-2 pt-3 border-t border-line">
                  <button
                    type="button"
                    onClick={() => setPlanModalClass(item)}
                    className="flex-1 inline-flex items-center justify-center gap-1.5 rounded-xl border border-action-line bg-action-soft px-3 py-2 text-xs font-medium text-action-strong shadow-xs hover:bg-action-soft active:scale-95 transition-all text-center cursor-pointer"
                  >
                    <Award className="h-3.5 w-3.5 text-action-strong" />
                    Kế hoạch học tập
                  </button>
                  <Link
                    href={`/tutor/schedule`}
                    className="flex-1 inline-flex items-center justify-center gap-1.5 rounded-xl bg-brand px-3 py-2 text-xs font-medium text-white shadow-xs hover:bg-brand-deep active:scale-95 transition-all text-center"
                  >
                    <Calendar className="h-3.5 w-3.5" />
                    Lịch dạy và đổi lịch
                  </Link>
                  {item.status === "ACTIVE" && (
                    <>
                      <button
                        type="button"
                        onClick={() => setRecurringModalClass(item)}
                        className="inline-flex items-center justify-center gap-1 rounded-xl border border-brand-line bg-brand-soft px-3 py-2 text-xs font-medium text-brand-strong hover:bg-brand-soft active:scale-95 transition-all cursor-pointer"
                        title={
                          isOngoing
                            ? "Đề xuất lại lịch học định kỳ (Hủy lịch cũ khi được chấp thuận)"
                            : isBatchCompleted
                            ? "Đề xuất tiếp đợt học mới (4-8 tuần)"
                            : "Thống nhất lịch học định kỳ (4-8 tuần)"
                        }
                      >
                        <CalendarDays className="h-3.5 w-3.5 text-brand-strong" />
                        {isOngoing
                          ? "Đề xuất lại lịch học định kỳ"
                          : isBatchCompleted
                          ? "Đề xuất tiếp đợt học mới"
                          : "Thống nhất lịch học định kỳ"}
                      </button>
                      <button
                        type="button"
                        onClick={() => setEndClassTarget(item)}
                        className={`inline-flex items-center justify-center gap-1 rounded-xl px-3 py-2 text-xs font-medium active:scale-95 transition-all cursor-pointer ${
                          isBatchCompleted
                            ? "border border-success-line bg-success-soft text-success-strong hover:bg-success-soft"
                            : "border border-danger-line bg-danger-soft text-danger-strong hover:bg-danger-soft"
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
