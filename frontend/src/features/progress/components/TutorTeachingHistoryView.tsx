"use client";

import React, { useState, useEffect, useCallback, useMemo } from "react";
import Link from "next/link";
import {
  GraduationCap,
  Search,
  BookOpen,
  Calendar,
  CheckCircle2,
  XCircle,
  Clock,
  ArrowLeft,
  RefreshCw,
  FileText,
  Target,
  AlertCircle,
  Sparkles,
} from "lucide-react";
import { progressApi } from "../api/progressApi";
import type { TeachingHistoryItem, SessionNoteTimelineResponse, PlanResponse } from "../types";
import { formatSessionTime, formatLocalDateToIso } from "@/shared/utils/dateTime";
import { EmptyState } from "@/shared/ui/EmptyState";

export function TutorTeachingHistoryView() {
  // Master state
  const [items, setItems] = useState<TeachingHistoryItem[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Filters & Pagination
  const [studentName, setStudentName] = useState<string>("");
  const [selectedStatus, setSelectedStatus] = useState<string>("");
  const [currentPage, setCurrentPage] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(1);
  const [totalElements, setTotalElements] = useState<number>(0);

  // Detail state (Selected Class)
  const [selectedClass, setSelectedClass] = useState<TeachingHistoryItem | null>(null);
  const [detailTab, setDetailTab] = useState<"notes" | "plan">("notes");
  const [notes, setNotes] = useState<SessionNoteTimelineResponse[]>([]);
  const [notesLoading, setNotesLoading] = useState<boolean>(false);
  const [notesError, setNotesError] = useState<string | null>(null);
  const [plan, setPlan] = useState<PlanResponse | null>(null);
  const [planLoading, setPlanLoading] = useState<boolean>(false);
  const [planError, setPlanError] = useState<string | null>(null);

  // Lọc chỉ giữ các buổi học thực tế (COMPLETED, ABSENT) hoặc các buổi có ghi chú, loại bỏ các lịch bị hủy thuần túy
  const visibleNotes = useMemo(() => {
    return notes.filter((n) => n.status?.toUpperCase() !== "CANCELLED" || Boolean(n.noteText?.trim()));
  }, [notes]);

  // Fetch Teaching History Classes
  const fetchHistory = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await progressApi.getTeachingHistory({
        page: currentPage,
        size: 10,
        studentName: studentName.trim() || undefined,
        status: selectedStatus || undefined,
      });

      if (res.success && res.data) {
        setItems(res.data.content || []);
        setTotalPages(res.data.totalPages || 1);
        setTotalElements(res.data.totalElements || 0);
      } else {
        setError(res.message || "Không thể tải lịch sử giảng dạy.");
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Đã xảy ra lỗi khi kết nối tới máy chủ.";
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, [currentPage, studentName, selectedStatus]);

  useEffect(() => {
    fetchHistory();
  }, [fetchHistory]);

  // Fetch Class Notes when detail opens
  const fetchClassNotes = useCallback(async (classId: string) => {
    setNotesLoading(true);
    setNotesError(null);
    try {
      const res = await progressApi.getClassNotes(classId, { page: 0, size: 50 });
      if (res.success && res.data) {
        setNotes(res.data.content || []);
      } else {
        setNotesError(res.message || "Không thể tải danh sách ghi chú buổi học.");
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Đã xảy ra lỗi khi tải ghi chú buổi học.";
      setNotesError(msg);
    } finally {
      setNotesLoading(false);
    }
  }, []);

  // Fetch Learning Plan when detail opens
  const fetchLearningPlan = useCallback(async (classId: string) => {
    setPlanLoading(true);
    setPlanError(null);
    try {
      const res = await progressApi.getPlanByClassId(classId);
      if (res.success && res.data) {
        setPlan(res.data);
      } else {
        setPlan(null);
      }
    } catch {
      // 404 means no plan created yet
      setPlan(null);
    } finally {
      setPlanLoading(false);
    }
  }, []);

  const handleSelectClass = (item: TeachingHistoryItem) => {
    setSelectedClass(item);
    setDetailTab("notes");
    fetchClassNotes(item.classId);
    fetchLearningPlan(item.classId);
  };

  const handleCloseDetail = () => {
    setSelectedClass(null);
    setNotes([]);
    setPlan(null);
  };

  const renderStatusBadge = (status: string) => {
    const s = status.toUpperCase();
    if (s === "COMPLETED" || s === "ENDED") {
      return (
        <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800">
          Đã kết thúc
        </span>
      );
    }
    if (s === "ACTIVE" || s === "IN_PROGRESS") {
      return (
        <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-sky-50 text-sky-700 dark:bg-sky-950/40 dark:text-sky-300 border border-sky-200 dark:border-sky-800">
          Đang diễn ra
        </span>
      );
    }
    return (
      <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-stone-100 text-stone-700 dark:bg-stone-800 dark:text-stone-300 border border-stone-200 dark:border-stone-700">
        {status}
      </span>
    );
  };

  return (
    <div className="max-w-7xl mx-auto space-y-6 sm:space-y-8 animate-in fade-in duration-200 p-4 sm:p-6">
      {/* 1. Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-stone-200/80 dark:border-stone-800">
        <div>
          <div className="flex items-center gap-2 mb-1.5">
            <span className="px-2 py-0.5 text-[10px] font-extrabold uppercase tracking-wider rounded-md bg-stone-100 dark:bg-stone-800 text-stone-600 dark:text-stone-300">
              GIẢNG DẠY
            </span>
            <span className="px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider rounded-md bg-sky-500/10 text-sky-700 dark:text-sky-400 border border-sky-300/40 dark:border-sky-700/40">
              UC5.5
            </span>
          </div>
          <h1 className="font-heading text-2xl sm:text-3xl font-extrabold text-neutral-900 dark:text-stone-100 tracking-tight">
            Lịch sử giảng dạy
          </h1>
          <p className="text-xs sm:text-sm text-neutral-500 dark:text-stone-400 mt-1 font-medium">
            Theo dõi danh sách các lớp kèm cặp 1-1, số buổi đã dạy, điểm danh và ghi chú học tập chi tiết
          </p>
        </div>

        <div className="flex items-center gap-2.5 shrink-0">
          <Link
            href="/tutor/schedule"
            className="inline-flex items-center gap-1.5 px-3.5 py-2 bg-white dark:bg-stone-900 hover:bg-stone-50 dark:hover:bg-stone-800 border border-stone-200 dark:border-stone-700 text-neutral-700 dark:text-stone-200 rounded-xl text-xs font-bold shadow-2xs transition-colors"
          >
            <Calendar className="size-3.5" />
            <span>Lịch dạy tuần</span>
          </Link>
          <Link
            href="/tutor/requests"
            className="inline-flex items-center gap-1.5 px-3.5 py-2 bg-sky-600 hover:bg-sky-700 text-white rounded-xl text-xs font-bold shadow-xs transition-colors"
          >
            <BookOpen className="size-3.5" />
            <span>Hàng đợi yêu cầu</span>
          </Link>
        </div>
      </div>

      {/* 2. Detail View (Selected Class) */}
      {selectedClass ? (
        <div className="space-y-6">
          <div className="flex items-center justify-between gap-4 bg-white dark:bg-stone-900 p-5 rounded-2xl border border-stone-200/80 dark:border-stone-800 shadow-xs">
            <div className="flex items-center gap-3">
              <button
                type="button"
                onClick={handleCloseDetail}
                className="p-2 rounded-xl hover:bg-stone-100 dark:hover:bg-stone-800 text-neutral-600 dark:text-stone-300 transition-colors cursor-pointer"
                title="Quay lại danh sách lớp"
              >
                <ArrowLeft className="size-5" />
              </button>
              <div>
                <div className="flex items-center gap-2">
                  <h2 className="text-lg font-bold text-neutral-900 dark:text-stone-100">
                    {selectedClass.goalTitle || "Lớp học kèm cặp 1-1"}
                  </h2>
                  {renderStatusBadge(selectedClass.status)}
                </div>
                <p className="text-xs text-neutral-500 dark:text-stone-400 mt-0.5">
                  Học sinh: <span className="font-semibold text-neutral-800 dark:text-stone-200">{selectedClass.studentName}</span>
                </p>
              </div>
            </div>

            {/* Tabs switcher */}
            <div className="flex items-center p-1 rounded-xl bg-stone-100 dark:bg-stone-800 text-xs font-semibold">
              <button
                type="button"
                onClick={() => setDetailTab("notes")}
                className={`px-3 py-1.5 rounded-lg transition-colors cursor-pointer flex items-center gap-1.5 ${
                  detailTab === "notes"
                    ? "bg-white dark:bg-stone-900 text-neutral-900 dark:text-stone-100 shadow-xs"
                    : "text-neutral-500 dark:text-stone-400 hover:text-neutral-900"
                }`}
              >
                <FileText className="size-3.5" />
                <span>Buổi học & Ghi chú ({visibleNotes.length})</span>
              </button>
              <button
                type="button"
                onClick={() => setDetailTab("plan")}
                className={`px-3 py-1.5 rounded-lg transition-colors cursor-pointer flex items-center gap-1.5 ${
                  detailTab === "plan"
                    ? "bg-white dark:bg-stone-900 text-neutral-900 dark:text-stone-100 shadow-xs"
                    : "text-neutral-500 dark:text-stone-400 hover:text-neutral-900"
                }`}
              >
                <Target className="size-3.5" />
                <span>Kế hoạch học tập</span>
              </button>
            </div>
          </div>

          {/* Tab 1: Notes Timeline */}
          {detailTab === "notes" && (
            <div className="bg-white dark:bg-stone-900 rounded-3xl border border-stone-200/80 dark:border-stone-800 p-6 shadow-xs space-y-4">
              <div className="flex items-center justify-between pb-3 border-b border-stone-100 dark:border-stone-800">
                <h3 className="font-heading text-base font-bold text-neutral-900 dark:text-stone-100">
                  Nhật ký buổi dạy & Ghi chú sư phạm
                </h3>
                <span className="text-xs text-neutral-400">
                  Dữ liệu ghi nhận từ hệ thống điểm danh
                </span>
              </div>

              {notesLoading ? (
                <div className="py-12 flex flex-col items-center justify-center gap-3">
                  <div className="size-8 border-3 border-sky-500/30 border-t-sky-500 rounded-full animate-spin" />
                  <span className="text-xs text-neutral-500">Đang tải lịch sử các buổi học...</span>
                </div>
              ) : notesError ? (
                <div className="p-4 rounded-2xl bg-rose-50 text-rose-800 text-xs flex items-center justify-between">
                  <span>{notesError}</span>
                  <button
                    type="button"
                    onClick={() => fetchClassNotes(selectedClass.classId)}
                    className="font-bold underline cursor-pointer"
                  >
                    Thử lại
                  </button>
                </div>
              ) : visibleNotes.length === 0 ? (
                <EmptyState
                  icon={BookOpen}
                  title="Chưa có buổi học nào được ghi nhận"
                  description="Lịch sử các buổi học và ghi chú của bạn sẽ xuất hiện tại đây sau khi bạn hoàn thành buổi dạy và điểm danh."
                  compact
                />
              ) : (
                <div className="space-y-3">
                  {visibleNotes.map((note, idx) => {
                    const isCompleted = note.status.toUpperCase() === "COMPLETED";
                    const isAbsent = note.status.toUpperCase() === "ABSENT";
                    const isCancelled = note.status.toUpperCase() === "CANCELLED";

                    return (
                      <div
                        key={note.sessionId || idx}
                        className="p-4 rounded-2xl border border-stone-200/80 dark:border-stone-800 hover:border-sky-300 transition-colors flex flex-col gap-2"
                      >
                        <div className="flex items-center justify-between gap-2">
                          <div className="flex items-center gap-2">
                            <span className="font-mono text-xs font-bold text-neutral-900 dark:text-stone-100">
                              {formatLocalDateToIso(note.startAt)}
                            </span>
                            <span className="text-xs text-neutral-400">·</span>
                            <span className="font-mono text-xs text-neutral-600 dark:text-stone-400">
                              {formatSessionTime(note.startAt, note.endAt)}
                            </span>
                          </div>

                          <div className="flex items-center gap-1.5">
                            {isCompleted && (
                              <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300">
                                <CheckCircle2 className="size-3" />
                                Hoàn thành
                              </span>
                            )}
                            {isAbsent && (
                              <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-rose-50 text-rose-700 dark:bg-rose-950/40 dark:text-rose-300">
                                <XCircle className="size-3" />
                                Vắng mặt
                              </span>
                            )}
                            {isCancelled && (
                              <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-stone-100 text-stone-700 dark:bg-stone-800 dark:text-stone-300">
                                <Clock className="size-3" />
                                Đã hủy
                              </span>
                            )}
                            {!isCompleted && !isAbsent && !isCancelled && (
                              <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-stone-100 text-stone-700 dark:bg-stone-800 dark:text-stone-300">
                                <Clock className="size-3" />
                                {note.status}
                              </span>
                            )}
                          </div>
                        </div>

                        {/* Note text safely rendered as text */}
                        <div className="bg-stone-50 dark:bg-stone-800/50 p-3 rounded-xl border border-stone-100 dark:border-stone-800 text-xs">
                          <div className="font-semibold text-neutral-600 dark:text-stone-300 mb-1 flex items-center gap-1">
                            <FileText className="size-3.5 text-neutral-400" />
                            <span>Ghi chú buổi dạy:</span>
                          </div>
                          {note.noteText ? (
                            <p className="text-neutral-800 dark:text-stone-200 whitespace-pre-wrap font-sans">
                              {note.noteText}
                            </p>
                          ) : (
                            <span className="italic text-neutral-400">Không có ghi chú cho buổi dạy này.</span>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}

          {/* Tab 2: Learning Plan Read-Only */}
          {detailTab === "plan" && (
            <div className="bg-white dark:bg-stone-900 rounded-3xl border border-stone-200/80 dark:border-stone-800 p-6 shadow-xs space-y-4">
              <div className="flex items-center justify-between pb-3 border-b border-stone-100 dark:border-stone-800">
                <h3 className="font-heading text-base font-bold text-neutral-900 dark:text-stone-100">
                  Kế hoạch học tập & Cột mốc
                </h3>
                {plan && (
                  <span className="text-xs font-bold text-sky-600">
                    Tiến độ: {plan.progressPercentage || 0}%
                  </span>
                )}
              </div>

              {planLoading ? (
                <div className="py-12 flex flex-col items-center justify-center gap-3">
                  <div className="size-8 border-3 border-sky-500/30 border-t-sky-500 rounded-full animate-spin" />
                  <span className="text-xs text-neutral-500">Đang tải kế hoạch học tập...</span>
                </div>
              ) : planError ? (
                <div className="p-4 rounded-2xl bg-rose-50 text-rose-800 text-xs flex items-center justify-between">
                  <span>{planError}</span>
                </div>
              ) : !plan || plan.milestones.length === 0 ? (
                <EmptyState
                  icon={Target}
                  title="Chưa thiết lập kế hoạch học tập"
                  description="Lớp học này chưa khởi tạo các cột mốc mục tiêu. Bạn có thể tạo kế hoạch học tập tại trang Quản lý lớp học."
                  compact
                />
              ) : (
                <div className="space-y-3">
                  {plan.summary && (
                    <div className="p-4 rounded-2xl bg-sky-50/60 dark:bg-sky-950/30 border border-sky-100 dark:border-sky-900/40 text-xs text-sky-900 dark:text-sky-200">
                      <span className="font-bold">Tóm tắt mục tiêu: </span>
                      {plan.summary}
                    </div>
                  )}

                  <div className="space-y-2">
                    {plan.milestones.map((m, idx) => {
                      const isAchieved = m.status === "ACHIEVED" || m.status === "COMPLETED";

                      return (
                        <div
                          key={m.milestoneId || idx}
                          className="p-4 rounded-2xl border border-stone-200/80 dark:border-stone-800 flex items-start justify-between gap-3"
                        >
                          <div className="space-y-1">
                            <div className="flex items-center gap-2">
                              <span className="size-5 rounded-full bg-stone-100 dark:bg-stone-800 text-stone-600 dark:text-stone-300 font-bold text-[10px] flex items-center justify-center">
                                {idx + 1}
                              </span>
                              <h4 className="text-xs sm:text-sm font-bold text-neutral-900 dark:text-stone-100">
                                {m.title}
                              </h4>
                            </div>
                            {m.description && (
                              <p className="text-xs text-neutral-500 dark:text-stone-400 pl-7">
                                {m.description}
                              </p>
                            )}
                            {m.targetDate && (
                              <p className="text-[11px] text-neutral-400 pl-7 font-mono">
                                Dự kiến: {m.targetDate}
                              </p>
                            )}
                          </div>

                          <div className="flex items-center gap-2 shrink-0">
                            <span className="px-2 py-0.5 text-[10px] font-bold rounded bg-stone-100 text-stone-600 dark:bg-stone-800 dark:text-stone-300">
                              Trọng số: {m.weight}
                            </span>
                            {isAchieved ? (
                              <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300">
                                Đã đạt
                              </span>
                            ) : (
                              <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300">
                                Đang học
                              </span>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      ) : (
        /* 3. Master List View (Classes Summary) */
        <div className="space-y-6">
          {/* Filters Bar */}
          <div className="bg-white dark:bg-stone-900 p-4 sm:p-5 rounded-2xl border border-stone-200/80 dark:border-stone-800 shadow-xs flex flex-col sm:flex-row items-center justify-between gap-3">
            <div className="flex flex-col sm:flex-row items-center gap-3 w-full sm:w-auto flex-1">
              {/* Search input */}
              <div className="relative w-full sm:w-72">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-neutral-400" />
                <input
                  type="text"
                  value={studentName}
                  onChange={(e) => {
                    setStudentName(e.target.value);
                    setCurrentPage(0);
                  }}
                  placeholder="Tìm theo tên học sinh..."
                  className="w-full pl-9 pr-3 py-2 text-xs rounded-xl border border-stone-200 dark:border-stone-700 bg-stone-50 dark:bg-stone-800 text-neutral-900 dark:text-stone-100 focus:outline-hidden focus:ring-2 focus:ring-sky-500"
                />
              </div>

              {/* Status filter */}
              <select
                value={selectedStatus}
                onChange={(e) => {
                  setSelectedStatus(e.target.value);
                  setCurrentPage(0);
                }}
                className="w-full sm:w-48 px-3 py-2 text-xs rounded-xl border border-stone-200 dark:border-stone-700 bg-stone-50 dark:bg-stone-800 text-neutral-900 dark:text-stone-100 focus:outline-hidden focus:ring-2 focus:ring-sky-500 cursor-pointer"
              >
                <option value="">Tất cả trạng thái lớp</option>
                <option value="ACTIVE">Đang diễn ra (ACTIVE)</option>
                <option value="ENDED">Đã kết thúc / Hoàn thành (ENDED)</option>
              </select>
            </div>

            <div className="flex items-center gap-2 self-end sm:self-center shrink-0">
              <span className="text-xs text-neutral-500 dark:text-stone-400 font-medium">
                Tổng cộng: <strong className="text-neutral-900 dark:text-stone-100">{totalElements}</strong> lớp
              </span>
              <button
                type="button"
                onClick={() => fetchHistory()}
                className="p-2 rounded-xl hover:bg-stone-100 dark:hover:bg-stone-800 text-neutral-500 hover:text-neutral-800 transition-colors cursor-pointer"
                title="Làm mới dữ liệu"
              >
                <RefreshCw className="size-4" />
              </button>
            </div>
          </div>

          {/* Classes Table / Cards */}
          {loading ? (
            <div className="bg-white dark:bg-stone-900 rounded-3xl border border-stone-200/80 dark:border-stone-800 p-8 shadow-xs">
              <div className="py-12 flex flex-col items-center justify-center gap-3">
                <div className="size-8 border-3 border-sky-500/30 border-t-sky-500 rounded-full animate-spin" />
                <span className="text-xs text-neutral-500">Đang tải lịch sử giảng dạy...</span>
              </div>
            </div>
          ) : error ? (
            <div className="p-6 rounded-3xl bg-rose-50 text-rose-800 border border-rose-200 text-xs flex items-center justify-between">
              <div className="flex items-center gap-2 font-medium">
                <AlertCircle className="size-4" />
                <span>{error}</span>
              </div>
              <button
                type="button"
                onClick={() => fetchHistory()}
                className="px-3 py-1.5 rounded-xl bg-rose-600 text-white font-bold cursor-pointer"
              >
                Thử lại
              </button>
            </div>
          ) : items.length === 0 ? (
            <EmptyState
              icon={GraduationCap}
              theme="sky"
              title="Chưa có dữ liệu lịch sử giảng dạy"
              description="Khi bạn nhận học viên và hoàn thành các buổi dạy kèm cặp 1-1, thông tin chi tiết về từng lớp và số buổi dạy sẽ hiển thị tại đây."
              action={{
                label: "Xem hàng đợi yêu cầu",
                href: "/tutor/requests",
                icon: BookOpen,
              }}
            />
          ) : (
            <div className="space-y-3">
              {items.map((item) => {
                const stats = item.sessionStats;
                const completedCount = stats?.completedCount ?? stats?.completedSessions ?? 0;
                const absentCount = stats?.absentCount ?? stats?.absentSessions ?? 0;
                const cancelledCount = stats?.cancelledCount ?? stats?.cancelledSessions ?? 0;

                return (
                  <div
                    key={item.classId}
                    className="bg-white dark:bg-stone-900 p-5 rounded-2xl border border-stone-200/80 dark:border-stone-800 shadow-xs hover:border-sky-300 dark:hover:border-sky-700 transition-all flex flex-col lg:flex-row lg:items-center justify-between gap-4"
                  >
                    <div className="space-y-1.5 flex-1">
                      <div className="flex items-center gap-2.5">
                        <h3 className="font-heading text-sm sm:text-base font-bold text-neutral-900 dark:text-stone-100">
                          {item.goalTitle || "Lớp kèm cặp 1-1"}
                        </h3>
                        {renderStatusBadge(item.status)}
                      </div>

                      <p className="text-xs text-neutral-500 dark:text-stone-400">
                        Học sinh: <span className="font-semibold text-neutral-800 dark:text-stone-200">{item.studentName}</span>
                      </p>

                      {/* Progress bar */}
                      {item.progressPercentage !== null && item.progressPercentage !== undefined && (
                        <div className="flex items-center gap-2 pt-1 max-w-xs">
                          <div className="flex-1 h-2 rounded-full bg-stone-100 dark:bg-stone-800 overflow-hidden">
                            <div
                              className="h-full bg-sky-500 rounded-full"
                              style={{ width: `${Math.min(100, item.progressPercentage)}%` }}
                            />
                          </div>
                          <span className="text-[11px] font-bold text-neutral-600 dark:text-stone-400 font-mono">
                            {item.progressPercentage}%
                          </span>
                        </div>
                      )}
                    </div>

                    {/* Session Stats pill */}
                    <div className="flex flex-wrap items-center gap-3 text-xs">
                      <div className="px-3 py-1.5 rounded-xl bg-emerald-50 dark:bg-emerald-950/40 text-emerald-800 dark:text-emerald-300 font-semibold border border-emerald-200/60 flex items-center gap-1.5">
                        <CheckCircle2 className="size-3.5" />
                        <span>Đã dạy: {completedCount} buổi</span>
                      </div>
                      <div className="px-3 py-1.5 rounded-xl bg-rose-50 dark:bg-rose-950/40 text-rose-800 dark:text-rose-300 font-semibold border border-rose-200/60 flex items-center gap-1.5">
                        <XCircle className="size-3.5" />
                        <span>Vắng: {absentCount}</span>
                      </div>
                      {cancelledCount > 0 && (
                        <div className="px-3 py-1.5 rounded-xl bg-stone-100 dark:bg-stone-800 text-stone-700 dark:text-stone-300 font-semibold flex items-center gap-1.5">
                          <span>Hủy: {cancelledCount}</span>
                        </div>
                      )}
                    </div>

                    {/* Action button */}
                    <div className="shrink-0">
                      <button
                        type="button"
                        onClick={() => handleSelectClass(item)}
                        className="w-full sm:w-auto px-4 py-2 bg-sky-600 hover:bg-sky-700 text-white rounded-xl text-xs font-bold shadow-2xs transition-colors cursor-pointer flex items-center justify-center gap-1.5"
                      >
                        <FileText className="size-3.5" />
                        <span>Chi tiết buổi & ghi chú</span>
                      </button>
                    </div>
                  </div>
                );
              })}

              {/* Pagination controls */}
              {totalPages > 1 && (
                <div className="flex items-center justify-center gap-2 pt-4">
                  <button
                    type="button"
                    disabled={currentPage === 0}
                    onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
                    className="px-3 py-1.5 rounded-xl border border-stone-200 text-xs font-semibold disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
                  >
                    Trang trước
                  </button>
                  <span className="text-xs font-mono text-neutral-600">
                    Trang {currentPage + 1} / {totalPages}
                  </span>
                  <button
                    type="button"
                    disabled={currentPage >= totalPages - 1}
                    onClick={() => setCurrentPage((p) => p + 1)}
                    className="px-3 py-1.5 rounded-xl border border-stone-200 text-xs font-semibold disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
                  >
                    Trang sau
                  </button>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
