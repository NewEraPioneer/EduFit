"use client";

import React, { useState, useEffect, useMemo } from "react";
import {
  TrendingUp,
  CheckCircle2,
  Clock,
  CircleDashed,
  BookOpen,
  Calendar,
  User,
  GraduationCap,
  Award,
  ChevronRight,
  FileText,
  AlertCircle,
  Sparkles,
  Star,
  RefreshCw,
  XCircle,
  Plus,
} from "lucide-react";
import { progressApi } from "../api/progressApi";
import type {
  StudentDashboardResponse,
  ParentDashboardResponse,
  ParentStudentSummary,
  MilestoneResponse,
  SessionNoteTimelineResponse,
  PlanResponse,
} from "../types";
import { calculateWeightedProgress } from "../utils/progressCalculator";

interface ProgressDashboardViewProps {
  role?: "STUDENT" | "PARENT" | "TUTOR";
  studentId?: string;
  classId?: string;
  onOpenCreatePlan?: (classId: string) => void;
}

export function ProgressDashboardView({
  role = "PARENT",
  studentId: initialStudentId,
  classId: initialClassId,
  onOpenCreatePlan,
}: ProgressDashboardViewProps) {
  const [parentData, setParentData] = useState<ParentDashboardResponse | null>(null);
  const [studentData, setStudentData] = useState<StudentDashboardResponse | null>(null);
  const [selectedStudentId, setSelectedStudentId] = useState<string | undefined>(initialStudentId);
  const [selectedClassId, setSelectedClassId] = useState<string | undefined>(initialClassId);
  const [milestoneFilter, setMilestoneFilter] = useState<"ALL" | "ACHIEVED" | "IN_PROGRESS" | "NOT_STARTED">("ALL");
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Load parent or student data
  const loadData = async () => {
    try {
      setIsLoading(true);
      setError(null);

      if (role === "PARENT") {
        const res = await progressApi.getParentDashboard();
        if (res.success && res.data) {
          setParentData(res.data);
          const firstStudent = res.data.students?.[0];
          if (firstStudent) {
            const activeStudentId = selectedStudentId || firstStudent.studentId;
            setSelectedStudentId(activeStudentId);
            const target = res.data.students.find((s) => s.studentId === activeStudentId) || firstStudent;
            setStudentData(target.activeClassDashboard || null);
          }
        }
      } else if (selectedStudentId || initialStudentId) {
        const targetStudentId = selectedStudentId || initialStudentId!;
        const res = await progressApi.getStudentDashboard(targetStudentId, selectedClassId);
        if (res.success && res.data) {
          setStudentData(res.data);
        }
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Không thể tải dữ liệu tiến độ";
      setError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    void loadData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [role, selectedStudentId, selectedClassId, initialStudentId]);

  // Handler for parent switching child
  const handleSelectStudent = (student: ParentStudentSummary) => {
    setSelectedStudentId(student.studentId);
    setSelectedClassId(undefined);
    setStudentData(student.activeClassDashboard || null);
  };

  // Handler for class switching
  const handleSelectClass = async (classId: string) => {
    if (!selectedStudentId && !initialStudentId) return;
    const targetStudentId = selectedStudentId || initialStudentId!;
    try {
      setIsLoading(true);
      setSelectedClassId(classId);
      const res = await progressApi.getStudentDashboard(targetStudentId, classId);
      if (res.success && res.data) {
        setStudentData(res.data);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Lỗi khi chuyển lớp học";
      setError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  const plan = studentData?.plan;
  const milestones = useMemo(() => plan?.milestones || [], [plan]);
  const stats = studentData?.sessionStats;
  const completedSessions = stats?.completedCount ?? stats?.completedSessions ?? 0;
  const absentSessions = stats?.absentCount ?? stats?.absentSessions ?? 0;
  const upcomingSessions = stats?.upcomingCount ?? stats?.upcomingSessions ?? 0;
  const totalSessions = stats?.totalSessions ?? (completedSessions + absentSessions + upcomingSessions);

  // Weighted Progress calculation
  const weightedProgress = useMemo(() => {
    if (studentData?.progressPercentage !== null && studentData?.progressPercentage !== undefined) {
      return studentData.progressPercentage;
    }
    return calculateWeightedProgress(milestones) ?? 0;
  }, [studentData?.progressPercentage, milestones]);

  // Milestone weight stats
  const { totalWeight, achievedWeight, achievedCount } = useMemo(() => {
    let totalW = 0;
    let achievedW = 0;
    let count = 0;
    milestones.forEach((m) => {
      if (!m.isArchived) {
        const w = m.weight || 1;
        totalW += w;
        if (m.status === "ACHIEVED" || m.status === "COMPLETED") {
          achievedW += w;
          count++;
        }
      }
    });
    return { totalWeight: totalW, achievedWeight: achievedW, achievedCount: count };
  }, [milestones]);

  // Filtered milestones
  const filteredMilestones = useMemo(() => {
    if (milestoneFilter === "ALL") return milestones;
    if (milestoneFilter === "ACHIEVED") {
      return milestones.filter((m) => m.status === "ACHIEVED" || m.status === "COMPLETED");
    }
    if (milestoneFilter === "IN_PROGRESS") {
      return milestones.filter((m) => m.status === "IN_PROGRESS");
    }
    if (milestoneFilter === "NOT_STARTED") {
      return milestones.filter(
        (m) => m.status === "NOT_STARTED" || (!m.status && m.status !== "ACHIEVED" && m.status !== "IN_PROGRESS")
      );
    }
    return milestones;
  }, [milestones, milestoneFilter]);

  // Format date helper
  const formatDate = (iso?: string | null) => {
    if (!iso) return "";
    try {
      const d = new Date(iso);
      return new Intl.DateTimeFormat("vi-VN", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
      }).format(d);
    } catch {
      return iso;
    }
  };

  const formatDateTime = (iso?: string | null) => {
    if (!iso) return "";
    try {
      const d = new Date(iso);
      return new Intl.DateTimeFormat("vi-VN", {
        weekday: "short",
        day: "2-digit",
        month: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
      }).format(d);
    } catch {
      return iso;
    }
  };

  // 1. Loading State
  if (isLoading && !studentData) {
    return (
      <div className="py-20 flex flex-col items-center justify-center gap-4">
        <div className="size-10 border-4 border-amber-500/20 border-t-amber-500 rounded-full animate-spin" />
        <span className="text-xs text-neutral-500 font-medium">
          Đang tải dữ liệu tiến độ và kế hoạch học tập...
        </span>
      </div>
    );
  }

  // 2. Error State
  if (error && !studentData) {
    return (
      <div className="p-8 text-center bg-rose-50 border border-rose-200 rounded-3xl text-xs text-rose-700 space-y-3">
        <AlertCircle className="size-8 text-rose-500 mx-auto" />
        <p className="font-bold text-sm">Đã có lỗi xảy ra</p>
        <p className="text-neutral-600 max-w-md mx-auto">{error}</p>
        <button
          type="button"
          onClick={() => void loadData()}
          className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-rose-600 text-white font-bold text-xs shadow-xs hover:bg-rose-700 cursor-pointer"
        >
          <RefreshCw className="size-3.5" />
          Thử lại
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* 1. Header & Parent Child Switcher */}
      {role === "PARENT" && parentData && parentData.students.length > 0 && (
        <div className="p-4 rounded-3xl bg-white border border-stone-200 shadow-xs flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <GraduationCap className="size-5 text-amber-600" />
            <span className="text-xs font-bold text-neutral-700 uppercase tracking-wider">
              Chọn học sinh theo dõi:
            </span>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {parentData.students.map((student) => {
              const isSelected = student.studentId === selectedStudentId;
              return (
                <button
                  key={student.studentId}
                  type="button"
                  onClick={() => handleSelectStudent(student)}
                  className={`px-4 py-2 rounded-xl text-xs font-bold transition-all cursor-pointer ${
                    isSelected
                      ? "bg-amber-500 text-white shadow-xs"
                      : "bg-stone-100 text-neutral-700 hover:bg-stone-200"
                  }`}
                >
                  {student.studentName}
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* 2. Class Selector Filter if multiple classes */}
      {studentData?.classes && studentData.classes.length > 1 && (
        <div className="flex items-center gap-2 overflow-x-auto pb-1">
          <span className="text-xs text-neutral-400 font-medium shrink-0">Lớp học:</span>
          {studentData.classes.map((cls) => {
            const isSelected = cls.classId === (selectedClassId || studentData.selectedClassId);
            return (
              <button
                key={cls.classId}
                type="button"
                onClick={() => handleSelectClass(cls.classId)}
                className={`px-3 py-1.5 rounded-xl text-xs font-medium whitespace-nowrap transition-colors cursor-pointer ${
                  isSelected
                    ? "bg-amber-100 text-amber-900 border border-amber-300 font-bold"
                    : "bg-white text-neutral-600 hover:bg-stone-100 border border-stone-200"
                }`}
              >
                {cls.title} ({cls.progressPercentage ?? 0}%)
              </button>
            );
          })}
        </div>
      )}

      {/* 3. Top Overview Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Card 1: Weighted Progress percentage */}
        <div className="p-5 rounded-3xl bg-white border border-stone-200 shadow-xs space-y-2">
          <div className="flex items-center justify-between text-neutral-500">
            <span className="text-xs font-bold uppercase tracking-wider">Tiến độ mục tiêu</span>
            <TrendingUp className="size-4 text-amber-600" />
          </div>
          <div className="flex items-baseline gap-2">
            <span className="text-3xl font-black text-neutral-900">{weightedProgress}%</span>
            <span className="text-xs text-neutral-400 font-medium">bình quân gia quyền</span>
          </div>
          <div className="w-full h-2 bg-stone-100 rounded-full overflow-hidden">
            <div
              className="h-full bg-amber-500 rounded-full transition-all duration-500"
              style={{ width: `${weightedProgress}%` }}
            />
          </div>
          <p className="text-[11px] text-neutral-500">
            Trọng số đạt được: {achievedWeight}/{totalWeight || 0} điểm
          </p>
        </div>

        {/* Card 2: Completed Sessions */}
        <div className="p-5 rounded-3xl bg-white border border-stone-200 shadow-xs space-y-2">
          <div className="flex items-center justify-between text-neutral-500">
            <span className="text-xs font-bold uppercase tracking-wider">Buổi học hoàn tất</span>
            <CheckCircle2 className="size-4 text-emerald-600" />
          </div>
          <div className="flex items-baseline gap-2">
            <span className="text-3xl font-black text-neutral-900">
              {completedSessions}
            </span>
            <span className="text-xs text-neutral-400 font-medium">
              / {totalSessions} buổi
            </span>
          </div>
          <p className="text-[11px] text-neutral-500">
            Vắng: {absentSessions} • Sắp tới: {upcomingSessions} buổi
          </p>
        </div>

        {/* Card 3: Milestones reached */}
        <div className="p-5 rounded-3xl bg-white border border-stone-200 shadow-xs space-y-2">
          <div className="flex items-center justify-between text-neutral-500">
            <span className="text-xs font-bold uppercase tracking-wider">Cột mốc kiến thức</span>
            <Award className="size-4 text-blue-600" />
          </div>
          <div className="flex items-baseline gap-2">
            <span className="text-3xl font-black text-neutral-900">
              {achievedCount}
            </span>
            <span className="text-xs text-neutral-400 font-medium">
              / {milestones.length} mốc
            </span>
          </div>
          <p className="text-[11px] text-neutral-500 truncate">
            {plan?.summary || "Kế hoạch học tập 1:1"}
          </p>
        </div>

        {/* Card 4: Tutor In Charge */}
        <div className="p-5 rounded-3xl bg-white border border-stone-200 shadow-xs space-y-2">
          <div className="flex items-center justify-between text-neutral-500">
            <span className="text-xs font-bold uppercase tracking-wider">Gia sư phụ trách</span>
            <User className="size-4 text-purple-600" />
          </div>
          <div className="truncate">
            <span className="text-base font-bold text-neutral-900 block truncate">
              {studentData?.tutorName || "Gia sư EduFit"}
            </span>
            <span className="text-xs text-neutral-400 font-medium block truncate">
              {studentData?.goalTitle || "Khóa học kèm 1:1"}
            </span>
          </div>
          <p className="text-[11px] text-neutral-500 truncate">
            Học sinh: {studentData?.studentName || "Học sinh"}
          </p>
        </div>
      </div>

      {/* 4. Milestones & Learning Plan Detail */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column: Milestones Timeline */}
        <div className="lg:col-span-2 p-6 rounded-3xl bg-white border border-stone-200 shadow-xs space-y-5">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-stone-100 pb-4">
            <div>
              <div className="flex items-center gap-2">
                <Award className="size-5 text-amber-600" />
                <h3 className="font-heading font-bold text-base text-neutral-900">
                  Lộ trình các cột mốc học tập
                </h3>
              </div>
              <p className="text-xs text-neutral-500 mt-0.5">
                Tiến độ tính theo công thức bình quân gia quyền của từng cột mốc kiến thức
              </p>
            </div>

            <div className="flex items-center gap-2">
              {onOpenCreatePlan && studentData?.selectedClassId && (
                <button
                  type="button"
                  onClick={() => onOpenCreatePlan(studentData.selectedClassId!)}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-amber-500 hover:bg-amber-600 text-white text-xs font-bold shadow-xs cursor-pointer"
                >
                  <Plus className="size-3.5" />
                  {plan ? "Chỉnh sửa kế hoạch" : "Thiết lập kế hoạch"}
                </button>
              )}
            </div>
          </div>

          {/* Filter Pills */}
          {milestones.length > 0 && (
            <div className="flex flex-wrap items-center gap-1.5">
              <button
                type="button"
                onClick={() => setMilestoneFilter("ALL")}
                className={`px-3 py-1 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                  milestoneFilter === "ALL"
                    ? "bg-neutral-900 text-white"
                    : "bg-stone-100 text-neutral-600 hover:bg-stone-200"
                }`}
              >
                Tất cả ({milestones.length})
              </button>
              <button
                type="button"
                onClick={() => setMilestoneFilter("ACHIEVED")}
                className={`px-3 py-1 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                  milestoneFilter === "ACHIEVED"
                    ? "bg-emerald-600 text-white"
                    : "bg-stone-100 text-neutral-600 hover:bg-stone-200"
                }`}
              >
                Đã đạt ({achievedCount})
              </button>
              <button
                type="button"
                onClick={() => setMilestoneFilter("IN_PROGRESS")}
                className={`px-3 py-1 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                  milestoneFilter === "IN_PROGRESS"
                    ? "bg-amber-500 text-white"
                    : "bg-stone-100 text-neutral-600 hover:bg-stone-200"
                }`}
              >
                Đang học ({milestones.filter((m) => m.status === "IN_PROGRESS").length})
              </button>
              <button
                type="button"
                onClick={() => setMilestoneFilter("NOT_STARTED")}
                className={`px-3 py-1 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                  milestoneFilter === "NOT_STARTED"
                    ? "bg-stone-700 text-white"
                    : "bg-stone-100 text-neutral-600 hover:bg-stone-200"
                }`}
              >
                Chưa bắt đầu
              </button>
            </div>
          )}

          {/* Milestones Content */}
          {milestones.length === 0 ? (
            <div className="py-14 text-center rounded-2xl border border-dashed border-stone-200 bg-stone-50/50 p-6 space-y-2">
              <Award className="size-8 text-neutral-300 mx-auto" />
              <p className="text-xs font-bold text-neutral-700">Chưa có kế hoạch học tập</p>
              <p className="text-xs text-neutral-500 max-w-sm mx-auto">
                Gia sư phụ trách sẽ thiết lập lộ trình các cột mốc kiến thức có trọng số cho lớp học này.
              </p>
            </div>
          ) : (
            <div className="space-y-3">
              {filteredMilestones.map((m, idx) => {
                const isCompleted = m.status === "ACHIEVED" || m.status === "COMPLETED";
                const isInProgress = m.status === "IN_PROGRESS";

                return (
                  <div
                    key={m.milestoneId || idx}
                    className={`p-4 rounded-2xl border flex items-start gap-3.5 transition-all ${
                      isCompleted
                        ? "bg-emerald-50/40 border-emerald-200/80"
                        : isInProgress
                        ? "bg-amber-50/40 border-amber-200 ring-1 ring-amber-400/30"
                        : "bg-stone-50/40 border-stone-200/70"
                    }`}
                  >
                    <div className="mt-0.5 shrink-0">
                      {isCompleted ? (
                        <CheckCircle2 className="size-5 text-emerald-600" />
                      ) : isInProgress ? (
                        <CircleDashed className="size-5 text-amber-600 animate-spin" />
                      ) : (
                        <Clock className="size-5 text-stone-400" />
                      )}
                    </div>

                    <div className="flex-1 min-w-0 space-y-1.5">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className="text-xs font-bold text-neutral-900">
                          {m.title}
                        </span>

                        <div className="flex items-center gap-2">
                          <span className="inline-flex items-center gap-1 rounded-md bg-stone-100 px-2 py-0.5 text-[10px] font-bold text-neutral-700 border border-stone-200">
                            <Star className="size-2.5 text-amber-500 fill-amber-500" />
                            Trọng số: {m.weight || 1}/5
                          </span>

                          <span
                            className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
                              isCompleted
                                ? "bg-emerald-100 text-emerald-800"
                                : isInProgress
                                ? "bg-amber-100 text-amber-800"
                                : "bg-stone-200 text-neutral-600"
                            }`}
                          >
                            {isCompleted
                              ? "Đã hoàn thành"
                              : isInProgress
                              ? "Đang thực hiện"
                              : "Chưa bắt đầu"}
                          </span>
                        </div>
                      </div>

                      {m.description && (
                        <p className="text-[11px] text-neutral-600 leading-relaxed">
                          {m.description}
                        </p>
                      )}

                      <div className="flex flex-wrap items-center gap-3 text-[10px] text-neutral-400 pt-0.5">
                        {m.targetDate && (
                          <span className="flex items-center gap-1">
                            <Calendar className="size-3" />
                            Hạn dự kiến: {formatDate(m.targetDate)}
                          </span>
                        )}
                        {m.achievedDate && (
                          <span className="flex items-center gap-1 text-emerald-600 font-semibold">
                            <CheckCircle2 className="size-3" />
                            Hoàn thành ngày: {formatDate(m.achievedDate)}
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Right Column: Recent Session Notes / Logs */}
        <div className="p-6 rounded-3xl bg-white border border-stone-200 shadow-xs space-y-4">
          <div className="flex items-center justify-between border-b border-stone-100 pb-3">
            <div className="flex items-center gap-2">
              <FileText className="size-5 text-amber-600" />
              <h3 className="font-heading font-bold text-base text-neutral-900">
                Biên bản buổi học & Lời phê
              </h3>
            </div>
            {studentData?.recentNotes && studentData.recentNotes.length > 0 && (
              <span className="text-xs text-neutral-400">
                {studentData.recentNotes.length} buổi gần nhất
              </span>
            )}
          </div>

          {!studentData?.recentNotes || studentData.recentNotes.length === 0 ? (
            <div className="py-14 text-center rounded-2xl border border-dashed border-stone-200 bg-stone-50/50 p-6 space-y-2">
              <FileText className="size-8 text-neutral-300 mx-auto" />
              <p className="text-xs font-bold text-neutral-700">Chưa có biên bản buổi học</p>
              <p className="text-xs text-neutral-500 max-w-xs mx-auto">
                Sau khi mỗi buổi học kết thúc, gia sư sẽ điểm danh và ghi nhận nhận xét đánh giá tại đây.
              </p>
            </div>
          ) : (
            <div className="space-y-3.5 max-h-[500px] overflow-y-auto pr-1">
              {studentData.recentNotes.map((note) => {
                const isAttended = note.status === "COMPLETED";
                const isAbsent = note.status === "ABSENT";

                return (
                  <div
                    key={note.sessionId}
                    className="p-4 rounded-2xl bg-stone-50/70 border border-stone-200/80 text-xs space-y-2.5 transition-all hover:bg-stone-50"
                  >
                    <div className="flex items-center justify-between text-[11px]">
                      <span className="font-bold text-neutral-800">
                        Buổi học: {formatDateTime(note.startAt)}
                      </span>

                      {/* Attendance Badge */}
                      <span
                        className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold ${
                          isAttended
                            ? "bg-emerald-100 text-emerald-800"
                            : isAbsent
                            ? "bg-rose-100 text-rose-800"
                            : "bg-stone-200 text-neutral-700"
                        }`}
                      >
                        {isAttended ? (
                          <>
                            <CheckCircle2 className="size-3" /> Có mặt
                          </>
                        ) : isAbsent ? (
                          <>
                            <XCircle className="size-3" /> Vắng mặt
                          </>
                        ) : (
                          note.status
                        )}
                      </span>
                    </div>

                    {/* Note Content / Feedback */}
                    {note.noteText ? (
                      <div className="rounded-xl bg-white p-3 border border-stone-200/60">
                        <span className="text-[10px] font-bold uppercase tracking-wider text-neutral-400 block mb-1">
                          Lời phê của gia sư:
                        </span>
                        <p className="text-neutral-700 text-xs leading-relaxed whitespace-pre-line">
                          {note.noteText}
                        </p>
                      </div>
                    ) : (
                      <p className="text-[11px] text-neutral-400 italic">
                        Không có ghi chú thêm từ gia sư.
                      </p>
                    )}

                    {note.recordedAt && (
                      <div className="text-[10px] text-neutral-400 text-right">
                        Ghi nhận lúc: {formatDateTime(note.recordedAt)}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
