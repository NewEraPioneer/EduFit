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
import { formatLocalDateToIso, formatLocalTime } from "@/shared/utils/dateTime";
import { useDeepLinkParams } from "@/shared/hooks";
import {
  type CalendarItem,
  type ScheduleViewMode,
  type ScheduleCalendarViewProps,
  type ConnectedClass,
  formatConnectedClassName,
} from "../types";

const HOURS = Array.from({ length: 15 }, (_, index) => index + 7);
const DAY_LABELS = ["Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"];
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
}: ScheduleCalendarViewProps) {
  const { user } = useAuth();

  // Current view mode: "grid" or "timeline"
  const [viewMode, setViewMode] = useState<ScheduleViewMode>("grid");

  // Selected week starting date
  const [weekStart, setWeekStart] = useState(() => startOfWeek(new Date()));

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

  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);
  const [isDetailOpen, setIsDetailOpen] = useState(false);

  // 7 days of the selected week
  const weekDays = useMemo(
    () => DAY_LABELS.map((label, index) => ({ label, date: addDays(weekStart, index) })),
    [weekStart]
  );
  const weekEnd = weekDays[6].date;

  // End of week date at 23:59:59.999
  const weekEndDateTime = useMemo(() => {
    const end = new Date(weekDays[6].date);
    end.setHours(23, 59, 59, 999);
    return end;
  }, [weekDays]);

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
        from: weekStart.toISOString(),
        to: weekEndDateTime.toISOString(),
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
  }, [weekStart, weekEndDateTime, initialStudentId, loadSessionMilestones]);

  useEffect(() => {
    fetchSessions();
  }, [fetchSessions]);

  // AC7 & AC8: Deep link từ thông báo /…/schedule?sessionId=<id>&action=<action> qua hook dùng chung useDeepLinkParams
  // Snapshot đổi định danh ở mỗi lần điều hướng mới, kể cả khi bấm lại cùng một buổi học
  const deepLink = useDeepLinkParams(["sessionId", "action"]);
  const [detailAction, setDetailAction] = useState<"outcome" | null>(null);

  useEffect(() => {
    if (!deepLink.sessionId) return;
    setSelectedSessionId(deepLink.sessionId);
    setIsDetailOpen(true);
    setDetailAction(deepLink.action === "outcome" ? "outcome" : null);
  }, [deepLink]);

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

  // Helper to format date of calendar grid column
  const getCellDateStr = (date: Date): string => {
    const timeInfo = getVietnamSessionTime(date);
    return timeInfo ? timeInfo.dateStr : formatLocalDateToIso(date);
  };

  // Get sessions that start in this specific hour on this day (origin) theo múi giờ Việt Nam
  const getOriginSessionsForCell = (date: Date, hour: number): CalendarItem[] => {
    const targetDateStr = getCellDateStr(date);
    return sessions.filter((s) => {
      if (!s.startAt) return false;
      if (!isValidCalendarGridStatus(s.status)) return false;
      const timeInfo = getVietnamSessionTime(s.startAt);
      if (!timeInfo) return false;
      return timeInfo.dateStr === targetDateStr && timeInfo.hour === hour;
    });
  };

  // Get continuation sessions for non-hour durations (90m/150m) theo múi giờ Việt Nam
  const getContinuationSessionsForCell = (date: Date, hour: number): CalendarItem[] => {
    const targetDateStr = getCellDateStr(date);
    return sessions.filter((s) => {
      if (!s.startAt || !s.endAt) return false;
      if (!isValidCalendarGridStatus(s.status)) return false;
      const startTimeInfo = getVietnamSessionTime(s.startAt);
      const endTimeInfo = getVietnamSessionTime(s.endAt);
      if (!startTimeInfo || !endTimeInfo) return false;
      if (startTimeInfo.dateStr !== targetDateStr) return false;
      const slotStartMin = hour * 60;
      return startTimeInfo.totalMinutes < slotStartMin && endTimeInfo.totalMinutes > slotStartMin;
    });
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
    setIsDetailOpen(true);
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
                  const isToday =
                    getCellDateStr(date) === getCellDateStr(new Date());
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

              {/* Loading Skeleton Rows */}
              {loading && sessions.length === 0 ? (
                <div className="p-12 text-center text-xs font-semibold text-neutral-400 animate-pulse">
                  Đang đồng bộ dữ liệu lịch học...
                </div>
              ) : (
                /* Hours Rows */
                HOURS.map((hour) => (
                  <div
                    key={hour}
                    className="grid min-h-16 grid-cols-[72px_repeat(7,minmax(112px,1fr))] border-b border-stone-100 last:border-b-0"
                  >
                    <div className="border-r border-stone-200 px-2 py-2 text-right text-[11px] font-semibold text-neutral-400">
                      {String(hour).padStart(2, "0")}:00
                    </div>

                    {weekDays.map((day, index) => {
                      const available = role === "TUTOR" && isAvailable(index + 1, hour);
                      const cellSessions = getOriginSessionsForCell(day.date, hour);
                      const continuationSessions = getContinuationSessionsForCell(day.date, hour);
                      const isCellBusy = cellSessions.length > 0 || continuationSessions.length > 0;

                      return (
                        <div
                          key={`${day.label}-${hour}`}
                          className={`relative border-r border-stone-100 p-1 last:border-r-0 transition-colors group ${
                            hasExplicitAvailability && available ? "bg-emerald-50/40" : "bg-white"
                          } ${!isCellBusy ? "hover:bg-sky-50/30" : ""}`}
                          title={
                            continuationSessions.length > 0
                              ? `Khung giờ đang có ca học tiếp nối (${formatVnSessionTime(continuationSessions[0].startAt, continuationSessions[0].endAt)})`
                              : undefined
                          }
                        >
                          {!isCellBusy && (
                            <button
                              type="button"
                              onClick={() => handleCellClick(day.date, hour)}
                              className="absolute inset-0 cursor-pointer focus-visible:outline-2 focus-visible:outline-sky-500 focus-visible:-outline-offset-2"
                              aria-label={`Đề xuất lịch lúc ${String(hour).padStart(2, "0")}:00 ${day.label}`}
                              title={`Bấm để đề xuất lịch lúc ${String(hour).padStart(2, "0")}:00 ${day.label}`}
                            />
                          )}
                          {/* If availability slot */}
                          {hasExplicitAvailability && available && !isCellBusy && (
                            <div className="absolute inset-1 pointer-events-none flex items-center justify-center opacity-60">
                              <span className="text-[9px] font-bold text-emerald-700 flex items-center gap-0.5">
                                <Clock3 className="size-2.5" /> Khung rảnh
                              </span>
                            </div>
                          )}

                          {/* Sessions inside this cell */}
                          {cellSessions.map((s) => {
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

                            return (
                              <button
                                key={s.sessionId}
                                type="button"
                                onClick={(e) => handleSessionClick(e, s.sessionId)}
                                className={`w-full rounded-xl border p-1.5 text-left text-[10px] font-bold shadow-2xs transition-all cursor-pointer ${badgeColor}`}
                              >
                                <div className="flex items-center justify-between">
                                  <span className="truncate">
                                    {formatVnSessionTime(s.startAt, s.endAt)}
                                  </span>
                                  {s.mode === "ONLINE" ? (
                                    <Video className="size-3 shrink-0" />
                                  ) : (
                                    <MapPin className="size-3 shrink-0" />
                                  )}
                                </div>
                                <div className="truncate text-[10px] font-bold text-neutral-800 dark:text-zinc-100 mt-0.5" title={getClassName(s.classId)}>
                                  {getClassName(s.classId)}
                                </div>
                                {sessionMilestones[s.sessionId] && (
                                  <div
                                    className="truncate text-[9px] font-semibold text-amber-800 mt-0.5"
                                    data-testid={`session-milestones-${s.sessionId}`}
                                    title={sessionMilestones[s.sessionId].milestones.map((m) => m.title).join(" · ")}
                                  >
                                    {sessionMilestones[s.sessionId].linkType === "ACTUAL" ? "Đã học: " : "Dự kiến: "}
                                    {sessionMilestones[s.sessionId].milestones.map((m) => m.title).join(" · ")}
                                  </div>
                                )}
                                <div className="truncate text-[9px] font-semibold mt-0.5">
                                  {isProposed && "⚠️ Chờ xác nhận"}
                                  {isScheduled && "✓ Đã xếp lịch"}
                                  {isCompleted && "✓ Đã hoàn thành"}
                                  {isAbsent && "✕ Vắng mặt"}
                                </div>
                              </button>
                            );
                          })}

                          {/* Continuation slots for non-hour durations (90m/150m) */}
                          {continuationSessions.map((s) => (
                            <button
                              type="button"
                              key={`cont-${s.sessionId}`}
                              onClick={(e) => handleSessionClick(e, s.sessionId)}
                              className="block w-full rounded-xl border border-dashed border-sky-300 bg-sky-50/70 p-1.5 text-left text-[10px] font-bold text-sky-900 shadow-2xs transition-all cursor-pointer hover:bg-sky-100/80"
                              title={`Ca học tiếp nối (${formatVnSessionTime(s.startAt, s.endAt)})`}
                            >
                              <div className="flex items-center justify-between">
                                <span className="truncate flex items-center gap-1 text-[9px] font-bold text-sky-800">
                                  <Clock3 className="size-2.5 shrink-0 text-sky-600" />
                                  ↳ Tiếp nối ({formatVnSessionTime(s.startAt, s.endAt)})
                                </span>
                                {s.mode === "ONLINE" ? (
                                  <Video className="size-3 shrink-0 text-sky-600" />
                                ) : (
                                  <MapPin className="size-3 shrink-0 text-sky-600" />
                                )}
                              </div>
                              <div className="truncate text-[9px] font-bold text-sky-800 dark:text-sky-300 mt-0.5" title={getClassName(s.classId)}>
                                {getClassName(s.classId)}
                              </div>
                              <div className="truncate text-[9px] font-semibold text-sky-600 mt-0.5">
                                Khóa giờ: Đến {formatVnTime(s.endAt)}
                              </div>
                            </button>
                          ))}
                        </div>
                      );
                    })}
                  </div>
                ))
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
            setIsDetailOpen(true);
          }}
          onProposeNew={() => {
            setProposeInitialDate(undefined);
            setProposeInitialHour(8);
            setIsProposeOpen(true);
          }}
        />
      )}

      {/* Guide Cards */}
      <section className="grid gap-4 lg:grid-cols-3">
        {[
          ["1", "Đề xuất thời gian", "Bấm vào bất kỳ ô trống nào trên lịch để đề xuất khung giờ học."],
          [
            "2",
            "Kiểm tra & Chống trùng GiST",
            "Hệ thống tự động phát hiện xung đột lịch và bẫy lỗi 409 nếu có ca học khác cùng giờ.",
          ],
          [
            "3",
            "Xác nhận & Đưa vào lịch",
            "Chỉ khi bên còn lại xác nhận, buổi học mới chính thức khóa lịch và hiển thị xanh.",
          ],
        ].map(([step, title, description]) => (
          <div key={step} className="rounded-2xl border border-stone-200 bg-white p-5 shadow-2xs">
            <div className="mb-3 flex items-center gap-2">
              <span className="grid size-7 place-items-center rounded-full bg-sky-100 text-xs font-black text-sky-800">
                {step}
              </span>
              <h2 className="text-sm font-bold text-neutral-900">{title}</h2>
            </div>
            <p className="text-xs leading-relaxed text-neutral-500">{description}</p>
          </div>
        ))}
      </section>

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
        isOpen={isDetailOpen}
        initialAction={detailAction}
        onClose={() => {
          setIsDetailOpen(false);
          setSelectedSessionId(null);
          setDetailAction(null);
        }}
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
