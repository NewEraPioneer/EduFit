"use client";

import React, { useEffect, useRef, useState } from "react";
import {
  AlertCircle,
  BookOpen,
  CalendarClock,
  CheckCircle2,
  Circle,
  CircleDot,
  Compass,
  FileText,
  GraduationCap,
  Plus,
  RefreshCw,
  Target,
} from "lucide-react";
import { progressApi } from "../api/progressApi";
import type {
  MilestoneResponse,
  ParentDashboardResponse,
  ParentStudentSummary,
  ProgressOverview,
  SelfRatingValue,
  SessionNoteTimelineResponse,
  StudentDashboardResponse,
} from "../types";
import { formatGoalTitle } from "../utils/goalTitle";
import { MAX_INDEPENDENCE_LEVEL } from "../constants/roadmap";
import { ProgressInsights } from "./ProgressInsights";
import { PeriodReportsSection } from "./PeriodReports";
import {
  MISSED_SESSION_LABELS,
  ON_TRACK_LABELS,
  ON_TRACK_TONE,
  SELF_RATINGS,
  formatDelta,
  attitudeLabel,
  homeworkLabel,
  independenceLabel,
  selfRatingOption,
} from "../constants/feedbackLabels";

const SESSION_STATUS: Record<string, { label: string; tone: string }> = {
  COMPLETED: { label: "Có mặt", tone: "bg-emerald-100 text-emerald-800" },
  ABSENT: { label: "Vắng mặt", tone: "bg-rose-100 text-rose-800" },
  SCHEDULED: { label: "Sắp diễn ra", tone: "bg-sky-100 text-sky-800" },
  RESCHEDULED: { label: "Đã dời lịch", tone: "bg-amber-100 text-amber-900" },
  CANCELLED: { label: "Đã hủy", tone: "bg-stone-200 text-neutral-700" },
};

const CLASS_PANEL_ID = "progress-class-panel";

interface ProgressDashboardViewProps {
  role?: "STUDENT" | "PARENT" | "TUTOR";
  studentId?: string;
  classId?: string;
  onOpenCreatePlan?: (classId: string) => void;
}

const formatDate = (iso?: string | null) => {
  if (!iso) return "";
  try {
    return new Intl.DateTimeFormat("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" }).format(new Date(iso));
  } catch {
    return iso;
  }
};

const formatDateTime = (iso?: string | null) => {
  if (!iso) return "";
  try {
    return new Intl.DateTimeFormat("vi-VN", {
      weekday: "short",
      day: "2-digit",
      month: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    }).format(new Date(iso));
  } catch {
    return iso;
  }
};

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
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  /** Mỗi lần tải có một số thứ tự; phản hồi của lần tải đã bị thay thế sẽ bị bỏ qua. */
  const requestRef = useRef(0);
  const parentLoadedRef = useRef(false);

  const loadData = async () => {
    const requestId = ++requestRef.current;
    const isCurrent = () => requestId === requestRef.current;
    try {
      setIsLoading(true);
      setError(null);
      if (role === "PARENT") {
        if (selectedStudentId && selectedClassId) {
          const res = await progressApi.getStudentDashboard(selectedStudentId, selectedClassId);
          if (!isCurrent()) return;
          if (res.success && res.data) setStudentData(res.data);
          else setError(res.message || "Không thể tải tiến độ của lớp này");
          return;
        }
        // Đổi con: dữ liệu đã có sẵn trong parentData, không gọi lại API.
        if (parentLoadedRef.current && selectedStudentId) return;
        const res = await progressApi.getParentDashboard();
        if (!isCurrent()) return;
        if (res.success && res.data) {
          parentLoadedRef.current = true;
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
        const res = await progressApi.getStudentDashboard(selectedStudentId || initialStudentId!, selectedClassId);
        if (!isCurrent()) return;
        if (res.success && res.data) setStudentData(res.data);
        else setError(res.message || "Không thể tải tiến độ của lớp này");
      }
    } catch (err: unknown) {
      if (isCurrent()) setError(err instanceof Error ? err.message : "Không thể tải dữ liệu tiến độ");
    } finally {
      if (isCurrent()) setIsLoading(false);
    }
  };

  useEffect(() => {
    void loadData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [role, selectedStudentId, selectedClassId, initialStudentId]);

  const handleSelectStudent = (student: ParentStudentSummary) => {
    setSelectedStudentId(student.studentId);
    setSelectedClassId(undefined);
    setStudentData(student.activeClassDashboard || null);
  };

  /** Cập nhật tại chỗ cảm nhận 1 chạm vừa lưu, không tải lại cả dashboard. */
  const updateNoteRating = (sessionId: string, rating: SelfRatingValue) =>
    setStudentData((prev) =>
      prev
        ? {
            ...prev,
            recentNotes: prev.recentNotes?.map((n) => (n.sessionId === sessionId ? { ...n, selfRating: rating } : n)),
          }
        : prev
    );

  const activeClassId = selectedClassId || studentData?.selectedClassId;

  const handleSelectClass = (classId: string) => {
    if (classId !== activeClassId) setSelectedClassId(classId);
    else if (error) void loadData(); // bấm lại đúng lớp đang lỗi = thử lại
  };

  if (isLoading && !studentData) {
    return (
      <div className="py-20 flex flex-col items-center justify-center gap-4">
        <div className="size-10 border-4 border-amber-500/20 border-t-amber-500 rounded-full animate-spin" />
        <span className="text-xs text-neutral-500 font-medium">Đang tải dữ liệu tiến độ...</span>
      </div>
    );
  }

  if (error && !studentData) {
    return (
      <div className="p-8 text-center bg-rose-50 border border-rose-200 rounded-3xl text-xs text-rose-700 space-y-3">
        <AlertCircle className="size-8 text-rose-500 mx-auto" />
        <p className="font-bold text-sm">Đã có lỗi xảy ra</p>
        <p className="text-neutral-600 max-w-md mx-auto">{error}</p>
        <button
          type="button"
          onClick={() => void loadData()}
          className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-rose-600 text-white font-bold text-xs hover:bg-rose-700 cursor-pointer"
        >
          <RefreshCw className="size-3.5" />
          Thử lại
        </button>
      </div>
    );
  }

  const overview = studentData?.overview ?? null;
  const plan = studentData?.plan ?? null;
  const milestones = (plan?.milestones ?? []).filter((m) => !m.isArchived).sort((a, b) => a.sortOrder - b.sortOrder);

  return (
    <div className="space-y-6">
      {role === "PARENT" && parentData && parentData.students.length > 0 && (
        <div className="p-4 rounded-3xl bg-white border border-stone-200 flex flex-wrap items-center gap-3">
          <GraduationCap className="size-5 text-amber-600" />
          <span className="text-xs font-bold text-neutral-700">Con:</span>
          {parentData.students.map((student) => (
            <button
              key={student.studentId}
              type="button"
              aria-pressed={student.studentId === selectedStudentId}
              onClick={() => handleSelectStudent(student)}
              className={`min-h-9 px-4 rounded-xl text-xs font-bold cursor-pointer ${
                student.studentId === selectedStudentId
                  ? "bg-amber-500 text-white"
                  : "bg-stone-100 text-neutral-700 hover:bg-stone-200"
              }`}
            >
              {student.studentName}
            </button>
          ))}
        </div>
      )}

      {studentData?.classes && studentData.classes.length > 1 && (
        <div className="flex items-center gap-2 overflow-x-auto pb-1" role="tablist" aria-label="Lớp học">
          {studentData.classes.map((cls) => {
            const isSelected = cls.classId === activeClassId;
            return (
              <button
                key={cls.classId}
                type="button"
                role="tab"
                aria-selected={isSelected}
                aria-controls={CLASS_PANEL_ID}
                id={`class-tab-${cls.classId}`}
                onClick={() => handleSelectClass(cls.classId)}
                className={`min-h-9 px-3 rounded-xl text-xs whitespace-nowrap cursor-pointer border ${
                  isSelected
                    ? "bg-amber-100 text-amber-900 border-amber-300 font-bold"
                    : "bg-white text-neutral-600 border-stone-200 hover:bg-stone-100"
                }`}
              >
                {formatGoalTitle(cls.title)}
              </button>
            );
          })}
        </div>
      )}

      {error && studentData && (
        <div role="alert" className="flex flex-wrap items-center justify-between gap-2 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-xs text-rose-700">
          <span>{error}</span>
          <button
            type="button"
            onClick={() => void loadData()}
            className="inline-flex items-center gap-1.5 rounded-lg bg-rose-600 px-3 py-1.5 font-bold text-white hover:bg-rose-700 cursor-pointer"
          >
            <RefreshCw className="size-3.5" />
            Thử lại
          </button>
        </div>
      )}

      <div
        id={CLASS_PANEL_ID}
        role={studentData?.classes && studentData.classes.length > 1 ? "tabpanel" : undefined}
        aria-labelledby={activeClassId && studentData?.classes && studentData.classes.length > 1 ? `class-tab-${activeClassId}` : undefined}
        aria-busy={isLoading}
        className={`space-y-6 transition-opacity ${isLoading && studentData ? "opacity-60" : ""}`}
      >
      {isLoading && studentData && (
        <p role="status" className="text-xs font-medium text-neutral-500">
          Đang tải tiến độ của lớp...
        </p>
      )}
      {overview ? (
        <>
          <OverviewCards overview={overview} />
          <ProgressInsights forecast={overview.forecast} radar={overview.radar} />
          {role === "PARENT" && <SelfRatingTrend points={overview.selfRatings ?? []} />}
          <PeriodReportsSection classId={studentData?.selectedClassId} />
        </>
      ) : (
        <div className="rounded-3xl border border-dashed border-stone-200 bg-stone-50/50 p-8 text-center text-xs text-neutral-500">
          Chưa có lớp học nào để theo dõi tiến độ.
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <section className="lg:col-span-2 p-5 rounded-3xl bg-white border border-stone-200 space-y-4" aria-labelledby="milestones-heading">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-stone-100 pb-3">
            <div>
              <h3 id="milestones-heading" className="font-heading font-bold text-base text-neutral-900">
                Lộ trình cột mốc
              </h3>
              <p className="text-xs text-neutral-500 mt-0.5">Mức tự lập từ lần đánh giá đầu đến hiện tại</p>
            </div>
            {onOpenCreatePlan && studentData?.selectedClassId && (
              <button
                type="button"
                onClick={() => onOpenCreatePlan(studentData.selectedClassId!)}
                className="inline-flex items-center gap-1.5 min-h-9 px-3 rounded-xl bg-amber-500 hover:bg-amber-600 text-white text-xs font-bold cursor-pointer"
              >
                <Plus className="size-3.5" />
                {plan ? "Chỉnh sửa lộ trình" : "Lập lộ trình"}
              </button>
            )}
          </div>

          {milestones.length === 0 ? (
            <div className="py-12 text-center rounded-2xl border border-dashed border-stone-200 bg-stone-50/50 p-6 space-y-1">
              <p className="text-xs font-bold text-neutral-700">Gia sư chưa lập lộ trình</p>
              <p className="text-xs text-neutral-500">Lộ trình gồm các cột mốc kèm tiêu chí đạt rõ ràng cho lớp học này.</p>
            </div>
          ) : (
            <ol className="space-y-3">
              {milestones.map((m, index) => (
                <MilestoneRow
                  key={m.milestoneId}
                  milestone={m}
                  sequence={index + 1}
                  levels={overview?.milestoneLevels.find((l) => l.milestoneId === m.milestoneId)}
                />
              ))}
            </ol>
          )}
        </section>

        <section className="p-5 rounded-3xl bg-white border border-stone-200 space-y-4" aria-labelledby="notes-heading">
          <div className="flex items-center gap-2 border-b border-stone-100 pb-3">
            <FileText className="size-5 text-amber-600" />
            <h3 id="notes-heading" className="font-heading font-bold text-base text-neutral-900">
              Sổ liên lạc
            </h3>
          </div>
          {!studentData?.recentNotes || studentData.recentNotes.length === 0 ? (
            <p className="py-10 text-center text-xs text-neutral-500">
              Sau mỗi buổi học, gia sư sẽ ghi phản hồi ngắn tại đây.
            </p>
          ) : (
            <ul className="space-y-3 max-h-[560px] overflow-y-auto pr-1">
              {studentData.recentNotes.map((note) => (
                <NoteItem
                  key={note.sessionId}
                  note={note}
                  canSelfRate={role === "STUDENT"}
                  onRated={(rating) => updateNoteRating(note.sessionId, rating)}
                />
              ))}
            </ul>
          )}
        </section>
      </div>
      </div>
    </div>
  );
}

function OverviewCards({ overview }: { overview: ProgressOverview }) {
  const { onTrack, goalProgress, attendance } = overview;
  return (
    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
      <article className="p-5 rounded-3xl bg-white border border-stone-200 space-y-3" data-testid="card-on-track">
        <div className="flex items-center justify-between text-neutral-500">
          <h3 className="text-xs font-bold uppercase tracking-wider">Có đúng hướng không?</h3>
          <Compass className="size-4 text-amber-600" />
        </div>
        <span className={`inline-flex rounded-full border px-3 py-1 text-sm font-bold ${ON_TRACK_TONE[onTrack.status]}`}>
          {ON_TRACK_LABELS[onTrack.status]}
        </span>
        <p className="text-xs text-neutral-700 leading-relaxed">{onTrack.explanation}</p>
      </article>

      <article className="p-5 rounded-3xl bg-white border border-stone-200 space-y-2" data-testid="card-goal-progress">
        <div className="flex items-center justify-between text-neutral-500">
          <h3 className="text-xs font-bold uppercase tracking-wider">Tiến độ mục tiêu chính</h3>
          <Target className="size-4 text-amber-600" />
        </div>
        <p className="text-xs font-semibold text-neutral-800 truncate">
          {formatGoalTitle(goalProgress.goalTitle) ?? "Mục tiêu học tập"}
        </p>
        <div className="flex items-baseline gap-2">
          <span className="text-3xl font-black text-neutral-900">
            {goalProgress.progressPercentage !== null && goalProgress.progressPercentage !== undefined
              ? `${goalProgress.progressPercentage}%`
              : "—"}
          </span>
          <span className="rounded-full bg-stone-100 px-2 py-0.5 text-[11px] font-bold text-neutral-700" title="So với 30 ngày trước">
            {formatDelta(goalProgress.progressDelta)}
          </span>
        </div>
        {goalProgress.highlight && <p className="text-xs text-neutral-700">{goalProgress.highlight}</p>}
        {goalProgress.highlightKind === "NO_CHANGE" && goalProgress.latestComment && (
          <p className="text-[11px] italic text-neutral-500">“{goalProgress.latestComment}”</p>
        )}
        {goalProgress.reinforcement && (
          <p className="text-xs text-neutral-600 bg-stone-50 rounded-lg px-2 py-1">{goalProgress.reinforcement}</p>
        )}
        {goalProgress.planAdjusted && (
          <p className="text-[11px] text-neutral-500">Lộ trình đã được điều chỉnh trong 30 ngày qua</p>
        )}
      </article>

      <article className="p-5 rounded-3xl bg-white border border-stone-200 space-y-2" data-testid="card-attendance">
        <div className="flex items-center justify-between text-neutral-500">
          <h3 className="text-xs font-bold uppercase tracking-wider">Chuyên cần</h3>
          <CalendarClock className="size-4 text-amber-600" />
        </div>
        <p className="text-xl font-black text-neutral-900">
          {attendance.totalCount > 0 ? `${attendance.countedCount}/${attendance.totalCount} buổi` : "—"}
        </p>
        <p className="text-[11px] text-neutral-500">
          {attendance.totalCount > 0
            ? `Đã học: ${attendance.attendedCount} buổi · Vắng: ${attendance.countedCount - attendance.attendedCount} buổi`
            : "Lớp chưa có buổi học nào"}
        </p>
        {attendance.missedSessions.length > 0 && (
          <details className="text-xs text-neutral-700">
            <summary className="cursor-pointer text-[11px] font-bold text-neutral-600">Chi tiết vắng, hủy</summary>
            <ul className="mt-2 space-y-1">
              {attendance.missedSessions.map((s) => (
                <li key={s.sessionId} className="flex justify-between gap-2">
                  <span>{formatDateTime(s.startAt)}</span>
                  <span className="text-neutral-500">{MISSED_SESSION_LABELS[s.kind]}</span>
                </li>
              ))}
            </ul>
          </details>
        )}
      </article>
    </div>
  );
}

function MilestoneRow({
  milestone: m,
  sequence,
  levels,
}: {
  milestone: MilestoneResponse;
  sequence: number;
  levels?: ProgressOverview["milestoneLevels"][number];
}) {
  const isAchieved = m.status === "ACHIEVED" || m.status === "COMPLETED";
  const isInProgress = m.status === "IN_PROGRESS";
  const StatusIcon = isAchieved ? CheckCircle2 : isInProgress ? CircleDot : Circle;
  const isDone = isAchieved;
  const statusText = isDone ? "Đã đạt" : isInProgress ? "Đang học" : "Chưa bắt đầu";
  const statusTone = isDone
    ? "bg-emerald-50 text-emerald-800 border-emerald-200"
    : isInProgress
      ? "bg-amber-50 text-amber-900 border-amber-200"
      : "bg-stone-100 text-neutral-600 border-stone-200";
  const dateText = isDone
    ? m.achievedDate ? `Đạt ngày ${formatDate(m.achievedDate)}` : ""
    : m.targetDate
      ? `Hạn ${formatDate(m.targetDate)}`
      : m.expectedDate
        ? `Dự kiến ~${formatDate(m.expectedDate)}`
        : "";
  const start = independenceLabel(levels?.startLevel);
  const current = independenceLabel(levels?.currentLevel);
  return (
    <li className="p-4 rounded-2xl border border-stone-200 bg-white shadow-xs space-y-2" data-testid={`milestone-${m.milestoneId}`}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="flex items-center gap-2 text-xs font-bold text-neutral-900">
          <StatusIcon
            className={`size-4 ${isAchieved ? "text-emerald-600" : isInProgress ? "text-amber-600" : "text-stone-400"}`}
          />
          <span className="shrink-0 rounded-full bg-sky-50 px-2 py-0.5 text-[10px] text-sky-800">Bước {sequence}</span>
          {m.title}
        </span>
        <span className="flex flex-wrap items-center gap-1.5 text-[11px] text-neutral-500">
          <span className={`rounded-full border px-2 py-0.5 text-[10px] font-bold ${statusTone}`}>{statusText}</span>
          {dateText}
        </span>
      </div>

      {levels?.currentLevel ? (
        <div className="space-y-1">
          <div className="flex h-2 gap-1" role="progressbar" aria-label={`Mức tự lập: ${m.title}`} aria-valuemin={0} aria-valuemax={MAX_INDEPENDENCE_LEVEL} aria-valuenow={levels.currentLevel} aria-valuetext={current ?? undefined}>
            {Array.from({ length: MAX_INDEPENDENCE_LEVEL }, (_, i) => i + 1).map((lvl) => (
              <span
                key={lvl}
                className={`flex-1 rounded-full ${
                  lvl <= (levels.currentLevel ?? 0)
                    ? "bg-amber-500"
                    : lvl <= (levels.startLevel ?? 0)
                      ? "bg-amber-200"
                      : "bg-stone-100"
                }`}
              />
            ))}
          </div>
          <p className="text-[11px] text-neutral-600">
            {levels.sessionCount >= 2 && start !== current
              ? `Từ “${start}” → “${current}”`
              : `Mức hiện tại: “${current}”`}
          </p>
        </div>
      ) : (
        <p className="text-[11px] text-neutral-400">Chưa có lần đánh giá</p>
      )}

      {m.criteria && m.criteria.length > 0 && (
        <ul className="space-y-0.5">
          {m.criteria.map((c) => (
            <li key={c.criterionId} className="flex items-start gap-1.5 text-[11px] text-neutral-700">
              {c.checked ? (
                <CheckCircle2 className="size-3.5 text-emerald-600 shrink-0 mt-px" aria-label="Đã đạt" />
              ) : (
                <Circle className="size-3.5 text-stone-300 shrink-0 mt-px" aria-label="Chưa đạt" />
              )}
              {c.content}
            </li>
          ))}
        </ul>
      )}
    </li>
  );
}

/** Phụ huynh xem xu hướng cảm nhận 1 chạm của con, tách khỏi điểm chấm của gia sư. */
function SelfRatingTrend({ points }: { points: NonNullable<ProgressOverview["selfRatings"]> }) {
  return (
    <section
      className="p-4 rounded-3xl bg-white border border-stone-200 flex flex-wrap items-center gap-3"
      aria-labelledby="self-rating-trend-heading"
      data-testid="self-rating-trend"
    >
      <h3 id="self-rating-trend-heading" className="text-xs font-bold uppercase tracking-wider text-neutral-500">
        Con tự cảm nhận sau buổi học (30 ngày)
      </h3>
      {points.length === 0 ? (
        <p className="text-xs text-neutral-500">Con chưa chọn cảm nhận cho buổi nào.</p>
      ) : (
        <ol className="flex flex-wrap items-center gap-1.5">
          {points.map((p) => {
            const option = selfRatingOption(p.rating);
            return (
              <li
                key={p.sessionId}
                title={`${formatDateTime(p.startAt)}: ${option?.label ?? ""}`}
                aria-label={`${formatDateTime(p.startAt)}: ${option?.label ?? ""}`}
                className="grid size-8 place-items-center rounded-full bg-stone-50 border border-stone-200 text-base"
              >
                <span aria-hidden="true">{option?.emoji}</span>
              </li>
            );
          })}
        </ol>
      )}
    </section>
  );
}

/** Học sinh chọn 1 trong 3 biểu tượng sau buổi học đã hoàn thành; chọn lại thì ghi đè. */
function SelfRatingPicker({
  sessionId,
  value,
  onRated,
}: {
  sessionId: string;
  value?: SelfRatingValue | null;
  onRated: (rating: SelfRatingValue) => void;
}) {
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const rate = async (rating: SelfRatingValue) => {
    setSaving(true);
    setError(null);
    try {
      const res = await progressApi.rateSession(sessionId, rating);
      if (res.success) onRated(rating);
      else setError(res.message || "Chưa lưu được cảm nhận");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Chưa lưu được cảm nhận");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-1">
      <div role="radiogroup" aria-label="Em thấy buổi học thế nào?" className="flex items-center gap-1.5">
        <span className="text-[11px] text-neutral-500 mr-1">Em thấy buổi này:</span>
        {SELF_RATINGS.map((r) => (
          <button
            key={r.value}
            type="button"
            role="radio"
            aria-checked={value === r.value}
            aria-label={r.label}
            title={r.label}
            disabled={saving}
            onClick={() => rate(r.value)}
            className={`grid size-8 place-items-center rounded-full border text-base cursor-pointer disabled:opacity-50 ${
              value === r.value ? "border-amber-500 bg-amber-50" : "border-stone-200 bg-white"
            }`}
          >
            <span aria-hidden="true">{r.emoji}</span>
          </button>
        ))}
      </div>
      {error && (
        <p role="alert" className="text-[11px] text-rose-700">
          {error}
        </p>
      )}
    </div>
  );
}

function NoteItem({
  note,
  canSelfRate,
  onRated,
}: {
  note: SessionNoteTimelineResponse;
  canSelfRate: boolean;
  onRated: (rating: SelfRatingValue) => void;
}) {
  const feedback = note.feedback;
  const statusInfo = SESSION_STATUS[note.status] ?? { label: "Không xác định", tone: "bg-stone-200 text-neutral-700" };
  return (
    <li className="p-3 rounded-2xl bg-white border border-stone-200 shadow-xs text-xs space-y-2" data-testid={`note-${note.sessionId}`}>
      <div className="flex items-center justify-between text-[11px]">
        <span className="font-bold text-neutral-800">
          {formatDateTime(note.startAt)}
          {note.endAt ? ` – ${new Intl.DateTimeFormat("vi-VN", { hour: "2-digit", minute: "2-digit" }).format(new Date(note.endAt))}` : ""}
        </span>
        <span className="flex items-center gap-1">
          {feedback?.late && (
            <span className="rounded-full bg-stone-200 px-2 py-0.5 text-[10px] font-bold text-neutral-700">
              Bổ sung muộn
            </span>
          )}
          <span className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${statusInfo.tone}`}>{statusInfo.label}</span>
        </span>
      </div>
      {feedback && (
        <div className="space-y-1">
          {feedback.assessments.map((a) => (
            <p key={a.milestoneId} className="flex items-start gap-1.5 text-neutral-800">
              <BookOpen className="size-3.5 text-amber-600 shrink-0 mt-px" />
              <span>
                <strong>{a.milestoneTitle ?? "Cột mốc"}</strong>: {independenceLabel(a.level)}
                {a.totalCount ? ` · đúng ${a.correctCount}/${a.totalCount} bài` : ""}
                {a.evidenceNote ? ` · ${a.evidenceNote}` : ""}
              </span>
            </p>
          ))}
          <div className="flex flex-wrap gap-1">
            <span className="rounded-full bg-white border border-stone-200 px-2 py-0.5 text-[10px] text-neutral-700">
              {homeworkLabel(feedback.homeworkStatus)}
            </span>
            {feedback.attitudeChips.map((chip) => (
              <span key={chip} className="rounded-full bg-sky-50 border border-sky-200 px-2 py-0.5 text-[10px] text-sky-800">
                {attitudeLabel(chip)}
              </span>
            ))}
          </div>
        </div>
      )}
      {!feedback && !note.noteText && note.status === "COMPLETED" && (
        <p className="text-neutral-500">Gia sư chưa ghi phản hồi cho buổi học này.</p>
      )}
      {note.noteText && <p className="text-neutral-700 leading-relaxed">{note.noteText}</p>}
      {feedback?.parentMessage && (
        <p className="rounded-xl bg-amber-50 border border-amber-200 px-2 py-1.5 text-neutral-800">
          <span className="font-bold">Lời nhắn riêng cho phụ huynh:</span> {feedback.parentMessage}
        </p>
      )}
      {canSelfRate && note.status === "COMPLETED" && (
        <SelfRatingPicker sessionId={note.sessionId} value={note.selfRating} onRated={onRated} />
      )}
    </li>
  );
}
