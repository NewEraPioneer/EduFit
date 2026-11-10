"use client";

import React, { useMemo, useState, useEffect, useCallback } from "react";
import {
  CalendarClock,
  ChevronLeft,
  ChevronRight,
  Clock3,
  Calendar,
  ListFilter,
  CalendarPlus,
  RotateCw,
  AlertCircle,
  Video,
  MapPin,
} from "lucide-react";
import { schedulingApi } from "../api/schedulingApi";
import { ProposeSessionModal } from "./ProposeSessionModal";
import { SessionDetailModal } from "./SessionDetailModal";
import { ScheduleTimelineView } from "./ScheduleTimelineView";
import { useAuth } from "@/context/AuthContext";
import { progressApi, type SessionMilestones } from "@/features/progress";
import { formatLocalDateToIso, formatSessionTime, formatLocalTime } from "@/shared/utils/dateTime";
import {
  type CalendarItem,
  type ScheduleViewMode,
  type ScheduleCalendarViewProps,
  type ConnectedClass,
  formatConnectedClassName,
} from "../types";

const HOURS = Array.from({ length: 15 }, (_, index) => index + 7);
/** Chiều cao 1 giờ trên lưới (px): đủ chỗ cho thẻ 3 dòng của buổi 60 phút. */
const HOUR_PX = 72;
const FIRST_HOUR = HOURS[0];
const GRID_HEIGHT = HOURS.length * HOUR_PX;
const MIN_BLOCK_MINUTES = 45;

interface PositionedSession {
  session: CalendarItem;
  top: number;
  height: number;
  column: number;
  columns: number;
}

/**
 * Xếp các buổi trong 1 ngày thành các khối theo thời lượng; buổi chồng giờ
 * được chia cột cạnh nhau (thuật toán cụm chồng lấn như Google Calendar).
 */
function layoutDaySessions(
  items: { session: CalendarItem; start: number; end: number }[]
): PositionedSession[] {
  const sorted = [...items].sort((a, b) => a.start - b.start || b.end - a.end);
  const result: PositionedSession[] = [];
  let cluster: typeof sorted = [];
  let clusterEnd = -1;

  const flush = () => {
    const colEnds: number[] = [];
    const placed = cluster.map((item) => {
      let col = colEnds.findIndex((end) => end <= item.start);
      if (col === -1) col = colEnds.length;
      colEnds[col] = item.end;
      return { item, col };
    });
    for (const { item, col } of placed) {
      result.push({
        session: item.session,
        top: ((item.start - FIRST_HOUR * 60) / 60) * HOUR_PX,
        height: (Math.max(item.end - item.start, MIN_BLOCK_MINUTES) / 60) * HOUR_PX,
        column: col,
        columns: colEnds.length,
      });
    }
    cluster = [];
  };

  for (const item of sorted) {
    if (cluster.length > 0 && item.start >= clusterEnd) flush();
    cluster.push(item);
    clusterEnd = cluster.length === 1 ? item.end : Math.max(clusterEnd, item.end);
  }
  if (cluster.length > 0) flush();
  return result;
}

const DAY_LABELS =["Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"];
const VN_TIMEZONE = "Asia/Ho_Chi_Minh";

interface VnTimeInfo {
  dateStr: string;
  hour: number;
  minute: number;
  totalMinutes: number;
}

/**
 * Chuẩn hóa thời gian theo múi giờ Việt Nam (Asia/Ho_Chi_Minh, UTC+7)
 * Đảm bảo phân tích chính xác cả chuỗi UTC (có Z hoặc offset) và chuỗi ISO cục bộ
 */
function getVietnamSessionTime(isoStringOrDate?: Date | string | null): VnTimeInfo | null {
  if (!isoStringOrDate) return null;

  // Nếu là chuỗi ISO không có múi giờ (ví dụ "2026-10-19T18:00:00")
  if (typeof isoStringOrDate === "string") {
    const localIsoMatch = isoStringOrDate.match(
      /^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/
    );
    if (localIsoMatch) {
      const dateStr = localIsoMatch[1];
      const hour = parseInt(localIsoMatch[2], 10);
      const minute = parseInt(localIsoMatch[3], 10);
      return {
        dateStr,
        hour,
        minute,
        totalMinutes: hour * 60 + minute,
      };
    }
  }

  const d = isoStringOrDate instanceof Date ? isoStringOrDate : new Date(isoStringOrDate);
  if (Number.isNaN(d.getTime())) return null;

  try {
    const formatter = new Intl.DateTimeFormat("en-US", {
      timeZone: VN_TIMEZONE,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "numeric",
      minute: "numeric",
      hour12: false,
    });

    const parts = formatter.formatToParts(d);
    const partMap: Record<string, string> = {};
    for (const p of parts) {
      if (p.type !== "literal") {
        partMap[p.type] = p.value;
      }
    }

    const year = partMap.year;
    const month = partMap.month;
    const day = partMap.day;
    let hour = parseInt(partMap.hour, 10);
    if (hour === 24) hour = 0;
    const minute = parseInt(partMap.minute, 10);
    const dateStr = `${year}-${month}-${day}`;

    return {
      dateStr,
      hour,
      minute,
      totalMinutes: hour * 60 + minute,
    };
  } catch {
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, "0");
    const day = String(d.getDate()).padStart(2, "0");
    const hour = d.getHours();
    const minute = d.getMinutes();
    return {
      dateStr: `${year}-${month}-${day}`,
      hour,
      minute,
      totalMinutes: hour * 60 + minute,
    };
  }
}

function startOfWeek(date: Date) {
  const result = new Date(date);
  const timeInfo = getVietnamSessionTime(result);
  if (timeInfo) {
    const [y, m, d] = timeInfo.dateStr.split("-").map(Number);
    const vnDate = new Date(y, m - 1, d);
    const currentDay = vnDate.getDay() || 7;
    vnDate.setDate(vnDate.getDate() - currentDay + 1);
    vnDate.setHours(0, 0, 0, 0);
    return vnDate;
  }
  const currentDay = result.getDay() || 7;
  result.setDate(result.getDate() - currentDay + 1);
  result.setHours(0, 0, 0, 0);
  return result;
}

function addDays(date: Date, days: number) {
  const result = new Date(date);
  result.setDate(result.getDate() + days);
  return result;
}

function toMinutes(value: string) {
  const [hours, minutes] = value.split(":").map(Number);
  return hours * 60 + minutes;
}

function formatDate(date: Date) {
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    timeZone: VN_TIMEZONE,
  }).format(date);
}

function formatFullDate(date: Date) {
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    timeZone: VN_TIMEZONE,
  }).format(date);
}

function formatVnTime(value: Date | string | null | undefined): string {
  if (!value) return "";
  const timeInfo = getVietnamSessionTime(value);
  if (timeInfo) {
    return `${String(timeInfo.hour).padStart(2, "0")}:${String(timeInfo.minute).padStart(2, "0")}`;
  }
  return formatLocalTime(value);
}

function formatVnSessionTime(
  startAt: Date | string | null | undefined,
  endAt?: Date | string | null | undefined
): string {
  const startStr = formatVnTime(startAt);
  if (!endAt) return startStr;
  const endStr = formatVnTime(endAt);
  if (!startStr && !endStr) return "";
  if (!endStr) return startStr;
  if (!startStr) return endStr;
  return `${startStr} - ${endStr}`;
}

export function ScheduleCalendarView({
  role,
  availabilitySlots = [],
  classes = [],
  initialStudentId,
  initialSession,
  onLinkedStudentResolved,
}: ScheduleCalendarViewProps) {
  const { user } = useAuth();

  // Current view mode: "grid" or "timeline"
  const [viewMode, setViewMode] = useState<ScheduleViewMode>("grid");

  // Selected week starting date
  const [weekStart, setWeekStart] = useState(() =>
    startOfWeek(initialSession ? new Date(initialSession.startAt) : new Date())
  );

  // Calendar sessions data state
  const [sessions, setSessions] = useState<CalendarItem[]>([]);
  // Cột mốc hiệu lực của từng buổi (thực tế thắng dự kiến), do module Progress quản lý
  const [sessionMilestones, setSessionMilestones] = useState<Record<string, SessionMilestones>>({});
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Modals state
  const [isProposeOpen, setIsProposeOpen] = useState(false);
  const [proposeInitialDate, setProposeInitialDate] = useState<Date | undefined>(undefined);
  const [proposeInitialHour, setProposeInitialHour] = useState<number>(8);

  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(initialSession?.sessionId ?? null);
  const [linkedSessionStudent, setLinkedSessionStudent] = useState<string | null>(null);

  useEffect(() => {
    if (role === "PARENT" && linkedSessionStudent && initialStudentId && linkedSessionStudent !== initialStudentId) {
      setSelectedSessionId(null);
    }
  }, [role, linkedSessionStudent, initialStudentId]);

  useEffect(() => {
    let alive = true;
    let generation = 0;
    const open = async (id: string) => {
      const request = ++generation;
      try {
        const response = await schedulingApi.getSessionDetail(id);
        if (!alive || request !== generation || !response.success || !response.data) return;
        const session = response.data;
        setWeekStart(startOfWeek(new Date(session.startAt)));
        setSelectedSessionId(session.sessionId);
        setLinkedSessionStudent(session.studentId);
        if (role === "PARENT") onLinkedStudentResolved?.(session.studentId);
      } catch {
        if (alive && request === generation) setError("Không mở được buổi học từ thông báo. Vui lòng kiểm tra quyền truy cập.");
      }
    };
    const id = new URLSearchParams(window.location.search).get("sessionId");
    if (id && !initialSession) void open(id);
    const handle = (event: Event) => {
      const id = (event as CustomEvent<unknown>).detail;
      if (typeof id === "string" && id) void open(id);
    };
    window.addEventListener("edufit:open-session", handle);
    return () => { alive = false; window.removeEventListener("edufit:open-session", handle); };
  }, [role, initialSession, onLinkedStudentResolved]);

  // 7 days of the selected week
  const weekDays = useMemo(
    () => DAY_LABELS.map((label, index) => ({ label, date: addDays(weekStart, index) })),
    [weekStart]
  );
  const weekEnd = weekDays[6].date;
  const todayInVietnam = getVietnamSessionTime(new Date())?.dateStr;

  /** Tên cột mốc chỉ là thông tin bổ sung: lỗi tải không chặn lịch học. */
  const loadSessionMilestones = useCallback(async (sessionIds: string[]) => {
    if (sessionIds.length === 0) {
      setSessionMilestones({});
      return;
    }
    try {
      const res = await progressApi.getSessionMilestones(sessionIds);
      setSessionMilestones(
        res.success && res.data ? Object.fromEntries(res.data.map((m) => [m.sessionId, m])) : {}
      );
    } catch {
      setSessionMilestones({});
    }
  }, []);

  // Fetch sessions from API
  const fetchSessions = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await schedulingApi.getCalendar({
        from: new Date(`${formatLocalDateToIso(weekStart)}T00:00:00+07:00`).toISOString(),
        to: new Date(`${formatLocalDateToIso(weekEnd)}T23:59:59.999+07:00`).toISOString(),
        studentId: initialStudentId,
      });
      if (res.success && res.data) {
        setSessions(res.data);
        void loadSessionMilestones(res.data.map((s) => s.sessionId));
      } else {
        setSessions([]);
        setSessionMilestones({});
      }
    } catch (err: unknown) {
      setError(
        err instanceof Error
          ? err.message
          : "Không thể tải danh sách lịch học. Vui lòng thử lại sau."
      );
    } finally {
      setLoading(false);
    }
  }, [weekStart, weekEnd, initialStudentId, loadSessionMilestones]);

  useEffect(() => {
    fetchSessions();
  }, [fetchSessions]);

  // Listen to Global Event Bus (edufit:schedule-updated) and StorageEvent to auto-reload
  useEffect(() => {
    const handleScheduleUpdated = () => {
      fetchSessions();
    };
    window.addEventListener("edufit:schedule-updated", handleScheduleUpdated);
    const handleStorage = (e: StorageEvent) => {
      if (e.key === "edufit:schedule-updated") {
        fetchSessions();
      }
    };
    window.addEventListener("storage", handleStorage);
    return () => {
      window.removeEventListener("edufit:schedule-updated", handleScheduleUpdated);
      window.removeEventListener("storage", handleStorage);
    };
  }, [fetchSessions]);

  const classMap = useMemo(() => {
    return new Map<string, ConnectedClass>((classes || []).map((c) => [c.classId, c]));
  }, [classes]);

  const getClassName = (classId: string) => {
    return formatConnectedClassName(classMap.get(classId), role, classId);
  };

  const hasExplicitAvailability = Boolean(availabilitySlots && availabilitySlots.length > 0);

  // Map availability slots (for Tutor)
  // When tutor has empty availabilitySlots: implicitly treat isAvailable = true for all grid slots
  const isAvailable = (dayOfWeek: number, hour: number) => {
    if (!hasExplicitAvailability) {
      return true;
    }
    return availabilitySlots.some((slot) => {
      const start = toMinutes(slot.startTime);
      const end = toMinutes(slot.endTime);
      const cellStart = hour * 60;
      return slot.dayOfWeek === dayOfWeek && start <= cellStart && end > cellStart;
    });
  };

  // Only valid active/real sessions (upcoming: SCHEDULED, PROPOSED; past: COMPLETED, ABSENT) are allowed on the calendar grid
  const isValidCalendarGridStatus = (status?: string): boolean => {
    if (!status) return false;
    const normalized = status.trim().toUpperCase();
    return (
      normalized === "SCHEDULED" ||
      normalized === "PROPOSED" ||
      normalized === "COMPLETED" ||
      normalized === "ABSENT"
    );
  };

  // Các khối buổi học trong 1 ngày (múi giờ Việt Nam), đã tính vị trí theo thời lượng và cột chồng lấn
  const getDayBlocks = (date: Date): PositionedSession[] => {
    const targetDateStr = formatLocalDateToIso(date);
    const dayStart = FIRST_HOUR * 60;
    const dayEnd = (FIRST_HOUR + HOURS.length) * 60;
    const items: { session: CalendarItem; start: number; end: number }[] = [];
    for (const s of sessions) {
      if (!s.startAt || !isValidCalendarGridStatus(s.status)) continue;
      const startInfo = getVietnamSessionTime(s.startAt);
      if (!startInfo || startInfo.dateStr !== targetDateStr) continue;
      const endInfo = s.endAt ? getVietnamSessionTime(s.endAt) : null;
      const rawEnd =
        endInfo && endInfo.dateStr === targetDateStr && endInfo.totalMinutes > startInfo.totalMinutes
          ? endInfo.totalMinutes
          : endInfo && endInfo.dateStr > targetDateStr
          ? 24 * 60
          : startInfo.totalMinutes + 60;
      const start = Math.max(startInfo.totalMinutes, dayStart);
      const end = Math.min(rawEnd, dayEnd);
      if (end <= start) continue;
      items.push({ session: s, start, end });
    }
    return layoutDaySessions(items);
  };

  // Open propose modal from clicking empty cell
  const handleCellClick = (date: Date, hour: number) => {
    setProposeInitialDate(date);
    setProposeInitialHour(hour);
    setIsProposeOpen(true);
  };

  // Open detail modal from clicking a session
  const handleSessionClick = (e: React.MouseEvent, sessionId: string) => {
    e.stopPropagation();
    setSelectedSessionId(sessionId);
  };

  const roleDescription =
    role === "TUTOR"
      ? "Lịch rảnh định kỳ của bạn được tô xanh. Buổi học chỉ chính thức sau khi hai bên cùng đồng thuận."
      : "Buổi học được bảo vệ chống trùng lịch và chỉ được kích hoạt khi hai bên cùng xác nhận.";

  return (
    <div className="space-y-6">
      {/* Header */}
      <header className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <div className="mb-2 inline-flex items-center gap-2 rounded-full bg-sky-50 px-3 py-1 text-xs font-bold text-sky-800">
            <CalendarClock className="size-3.5" />
            Múi giờ Việt Nam UTC+7
          </div>
          <h1 className="font-heading text-2xl font-extrabold tracking-tight text-neutral-900 sm:text-3xl">
            Lịch học & Điều phối thời gian
          </h1>
          <p className="mt-1 max-w-3xl text-sm text-neutral-500">{roleDescription}</p>
        </div>

        {/* Toolbar: Navigation, View Toggle, and Propose Action */}
        <div className="flex flex-wrap items-center gap-2">
          {/* View mode toggle: Grid vs Timeline */}
          <div className="flex h-10 items-center rounded-xl border border-stone-200 bg-white p-0.5 shadow-2xs">
            <button
              type="button"
              onClick={() => setViewMode("grid")}
              className={`flex items-center gap-1.5 h-full px-3 rounded-lg text-xs font-bold transition-colors cursor-pointer ${
                viewMode === "grid"
                  ? "bg-sky-600 text-white shadow-xs"
                  : "text-neutral-600 hover:text-neutral-900"
              }`}
              title="Xem dạng Lưới lịch tuần"
            >
              <Calendar className="size-3.5" />
              Lưới tuần
            </button>
            <button
              type="button"
              onClick={() => setViewMode("timeline")}
              className={`flex items-center gap-1.5 h-full px-3 rounded-lg text-xs font-bold transition-colors cursor-pointer ${
                viewMode === "timeline"
                  ? "bg-sky-600 text-white shadow-xs"
                  : "text-neutral-600 hover:text-neutral-900"
              }`}
              title="Xem dạng Dòng thời gian"
            >
              <ListFilter className="size-3.5" />
              Dòng thời gian
            </button>
          </div>

          {/* Week Navigation */}
          <button
            type="button"
            onClick={() => setWeekStart(startOfWeek(new Date()))}
            className="h-10 rounded-xl border border-stone-200 bg-white px-3.5 text-xs font-bold text-neutral-700 hover:bg-stone-50 cursor-pointer shadow-2xs"
          >
            Hôm nay
          </button>

          <div className="flex h-10 items-center rounded-xl border border-stone-200 bg-white shadow-2xs">
            <button
              type="button"
              onClick={() => setWeekStart((current) => addDays(current, -7))}
              className="grid size-10 place-items-center rounded-l-xl text-neutral-500 hover:bg-stone-50 cursor-pointer"
              aria-label="Tuần trước"
            >
              <ChevronLeft className="size-4" />
            </button>
            <span className="min-w-32 px-2 text-center text-xs font-bold text-neutral-800">
              {formatDate(weekStart)} - {formatFullDate(weekEnd)}
            </span>
            <button
              type="button"
              onClick={() => setWeekStart((current) => addDays(current, 7))}
              className="grid size-10 place-items-center rounded-r-xl text-neutral-500 hover:bg-stone-50 cursor-pointer"
              aria-label="Tuần sau"
            >
              <ChevronRight className="size-4" />
            </button>
          </div>

          {/* Đề xuất lịch mới Button */}
          <button
            type="button"
            onClick={() => {
              setProposeInitialDate(undefined);
              setProposeInitialHour(8);
              setIsProposeOpen(true);
            }}
            className="flex h-10 items-center gap-2 rounded-xl bg-sky-600 px-4 text-xs font-bold text-white shadow-xs hover:bg-sky-700 cursor-pointer"
          >
            <CalendarPlus className="size-4" />
            Đề xuất lịch mới
          </button>
        </div>
      </header>

      {/* Error State Banner */}
      {error && (
        <div
          role="alert"
          className="flex items-center justify-between rounded-2xl border border-rose-200 bg-rose-50 p-4 text-xs text-rose-900"
        >
          <div className="flex items-center gap-2.5">
            <AlertCircle className="size-4 text-rose-600 shrink-0" />
            <span>{error}</span>
          </div>
          <button
            type="button"
            onClick={fetchSessions}
            className="flex items-center gap-1 rounded-lg bg-rose-100 px-2.5 py-1 font-bold text-rose-800 hover:bg-rose-200 cursor-pointer"
          >
            <RotateCw className="size-3" /> Thử lại
          </button>
        </div>
      )}

      {/* Main Content: Grid View or Timeline View */}
      {viewMode === "grid" ? (
        <section className="overflow-hidden rounded-3xl border border-stone-200 bg-white shadow-xs">
          <div className="overflow-x-auto">
            <div className="min-w-[880px]">
              {/* Header Days Row */}
              <div className="grid grid-cols-[72px_repeat(7,minmax(112px,1fr))] border-b border-stone-200 bg-stone-50/80">
                <div className="border-r border-stone-200 p-3 text-center text-[10px] font-bold uppercase text-neutral-400">
                  Giờ
                </div>
                {weekDays.map(({ label, date }) => {
                  const isToday = formatLocalDateToIso(date) === todayInVietnam;
                  return (
                    <div
                      key={label}
                      className={`border-r border-stone-200 p-3 text-center last:border-r-0 ${
                        isToday ? "bg-sky-50/50" : ""
                      }`}
                    >
                      <div
                        className={`text-[11px] font-bold ${
                          isToday ? "text-sky-700 font-extrabold" : "text-neutral-500"
                        }`}
                      >
                        {label} {isToday && "· Hôm nay"}
                      </div>
                      <div
                        className={`mt-0.5 text-sm font-extrabold ${
                          isToday ? "text-sky-900" : "text-neutral-900"
                        }`}
                      >
                        {formatDate(date)}
                      </div>
                    </div>
                  );
                })}
              </div>

              {/* Loading Skeleton / Time grid */}
              {loading && sessions.length === 0 ? (
                <div className="p-12 text-center text-xs font-semibold text-neutral-400 animate-pulse">
                  Đang đồng bộ dữ liệu lịch học...
                </div>
              ) : (
                <div className="grid grid-cols-[72px_repeat(7,minmax(112px,1fr))]">
                  {/* Time gutter */}
                  <div className="relative border-r border-stone-200" style={{ height: GRID_HEIGHT }}>
                    {HOURS.map((hour) => (
                      <div
                        key={hour}
                        className="absolute right-2 text-[11px] font-semibold text-neutral-400"
                        style={{ top: (hour - FIRST_HOUR) * HOUR_PX + 4 }}
                      >
                        {String(hour).padStart(2, "0")}:00
                      </div>
                    ))}
                  </div>

                  {weekDays.map((day, dayIndex) => {
                    const blocks = getDayBlocks(day.date);
                    return (
                      <div
                        key={day.label}
                        data-testid={`calendar-day-${dayIndex}`}
                        className="relative border-r border-stone-100 last:border-r-0"
                        style={{ height: GRID_HEIGHT }}
                      >
                        {/* Hour slots: grid lines, availability, click to propose */}
                        {HOURS.map((hour) => {
                          const available = role === "TUTOR" && isAvailable(dayIndex + 1, hour);
                          const label = `${String(hour).padStart(2, "0")}:00 ${day.label}`;
                          return (
                            <button
                              key={hour}
                              type="button"
                              onClick={() => handleCellClick(day.date, hour)}
                              className={`absolute inset-x-0 border-b border-stone-100 cursor-pointer transition-colors hover:bg-sky-50/40 focus-visible:outline-2 focus-visible:outline-sky-500 focus-visible:-outline-offset-2 ${
                                hasExplicitAvailability && available ? "bg-emerald-50/40" : "bg-transparent"
                              }`}
                              style={{ top: (hour - FIRST_HOUR) * HOUR_PX, height: HOUR_PX }}
                              aria-label={`Đề xuất lịch lúc ${label}`}
                              title={`Bấm để đề xuất lịch lúc ${label}`}
                            >
                              {hasExplicitAvailability && available && (
                                <span className="pointer-events-none absolute right-1 top-1 flex items-center gap-0.5 text-[9px] font-bold text-emerald-700 opacity-60">
                                  <Clock3 className="size-2.5" /> Khung rảnh
                                </span>
                              )}
                            </button>
                          );
                        })}

                        {/* Session blocks: height follows duration, overlaps split into columns */}
                        {blocks.map(({ session: s, top, height, column, columns }) => {
                          const isProposed = s.status === "PROPOSED";
                          const isScheduled = s.status === "SCHEDULED";
                          const isCompleted = s.status === "COMPLETED";
                          const isAbsent = s.status === "ABSENT";

                          const badgeColor = isScheduled
                            ? "border-sky-300 bg-sky-50 text-sky-900 hover:bg-sky-100"
                            : isProposed
                            ? "border-amber-300 bg-amber-50 text-amber-900 hover:bg-amber-100"
                            : isCompleted
                            ? "border-emerald-300 bg-emerald-50 text-emerald-900 hover:bg-emerald-100"
                            : isAbsent
                            ? "border-orange-300 bg-orange-50 text-orange-900 hover:bg-orange-100"
                            : "border-stone-300 bg-stone-50 text-stone-700";

                          const statusText = isProposed
                            ? "⚠️ Chờ xác nhận"
                            : isScheduled
                            ? "✓ Đã xếp lịch"
                            : isCompleted
                            ? "✓ Đã hoàn thành"
                            : isAbsent
                            ? "✕ Vắng mặt"
                            : "";
                          const milestone = sessionMilestones[s.sessionId];
                          const milestoneText = milestone
                            ? `${milestone.linkType === "ACTUAL" ? "Đã học: " : "Dự kiến: "}${milestone.milestones
                                .map((m) => m.title)
                                .join(" · ")}`
                            : "";
                          const className = getClassName(s.classId);

                          return (
                            <button
                              key={s.sessionId}
                              type="button"
                              onClick={(e) => handleSessionClick(e, s.sessionId)}
                              data-testid={`calendar-session-${s.sessionId}`}
                              title={[formatVnSessionTime(s.startAt, s.endAt), className, statusText, milestoneText]
                                .filter(Boolean)
                                .join("\n")}
                              className={`absolute z-10 h-(--block-h) overflow-hidden rounded-lg border px-1.5 py-1 text-left text-[10px] font-bold leading-tight shadow-2xs transition-shadow cursor-pointer hover:z-30 hover:h-auto hover:min-h-(--block-h) hover:shadow-md focus-visible:z-30 focus-visible:h-auto ${badgeColor}`}
                              style={
                                {
                                  top: top + 1,
                                  left: `calc(${(column / columns) * 100}% + 2px)`,
                                  width: `calc(${100 / columns}% - 4px)`,
                                  "--block-h": `${height - 2}px`,
                                } as React.CSSProperties
                              }
                            >
                              {/* Dòng 1: giờ + hình thức học */}
                              <div className="flex items-center justify-between gap-1">
                                <span className="whitespace-nowrap">{formatVnSessionTime(s.startAt, s.endAt)}</span>
                                {s.mode === "ONLINE" ? (
                                  <Video className="size-3 shrink-0" />
                                ) : (
                                  <MapPin className="size-3 shrink-0" />
                                )}
                              </div>
                              {/* Dòng 2: tên lớp */}
                              <div className="min-w-0 break-words text-neutral-800">{className}</div>
                              {/* Dòng 3: trạng thái + cột mốc */}
                              <div className="flex min-w-0 flex-wrap gap-x-1 text-[9px] font-semibold">
                                <span className="min-w-0 break-words">{statusText}</span>
                                {milestone && (
                                  <span
                                    className="min-w-0 break-words text-amber-800"
                                    data-testid={`session-milestones-${s.sessionId}`}
                                    title={milestone.milestones.map((m) => m.title).join(" · ")}
                                  >
                                    {milestoneText}
                                  </span>
                                )}
                              </div>
                            </button>
                          );
                        })}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        </section>
      ) : (
        /* Timeline View */
        <ScheduleTimelineView
          sessions={sessions}
          classes={classes}
          role={role}
          onSelectSession={(id) => {
            setSelectedSessionId(id);
          }}
          onProposeNew={() => {
            setProposeInitialDate(undefined);
            setProposeInitialHour(8);
            setIsProposeOpen(true);
          }}
        />
      )}

      {/* Modals */}
      <ProposeSessionModal
        isOpen={isProposeOpen}
        onClose={() => setIsProposeOpen(false)}
        onSuccess={() => {
          fetchSessions();
          if (typeof window !== "undefined") {
            window.dispatchEvent(new CustomEvent("edufit:schedule-updated"));
            try {
              localStorage.setItem("edufit:schedule-updated", String(Date.now()));
            } catch {
              // ignore
            }
          }
        }}
        role={role}
        currentUserId={user?.id}
        initialDate={proposeInitialDate}
        initialHour={proposeInitialHour}
        connectedClasses={classes}
      />

      <SessionDetailModal
        sessionId={selectedSessionId}
        isOpen={selectedSessionId !== null}
        onClose={() => setSelectedSessionId(null)}
        onUpdated={() => {
          fetchSessions();
          if (typeof window !== "undefined") {
            window.dispatchEvent(new CustomEvent("edufit:schedule-updated"));
            try {
              localStorage.setItem("edufit:schedule-updated", String(Date.now()));
            } catch {
              // ignore
            }
          }
        }}
        role={role}
        currentUserId={user?.id}
        classes={classes}
      />
    </div>
  );
}
