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
  BellRing,
} from "lucide-react";
import { schedulingApi } from "../api/schedulingApi";
import { ProposeSessionModal } from "./ProposeSessionModal";
import { SessionDetailModal } from "./SessionDetailModal";
import { ScheduleTimelineView } from "./ScheduleTimelineView";
import { useAuth } from "@/context/AuthContext";
import { progressApi, type SessionMilestones } from "@/features/progress";
import { formatLocalDateToIso, formatLocalTime } from "@/shared/utils/dateTime";
import { FIRST_HOUR, LAST_MINUTE, WeekTimeGrid, type DaySessionRange } from "./WeekTimeGrid";
import { useDeepLinkParams } from "@/shared/hooks";
import { useSessionChangeNotifications } from "@/features/notification";
import {
  type CalendarItem,
  type ScheduleViewMode,
  type ScheduleCalendarViewProps,
  type ConnectedClass,
  formatConnectedClassName,
} from "../types";

const DAY_LABELS =["Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"];
const VN_TIMEZONE = "Asia/Ho_Chi_Minh";
const HIGHLIGHT_MS = 8000;

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
  useEffect(() => {
    if (typeof window.matchMedia === "function" && window.matchMedia("(max-width: 767px)").matches) setViewMode("timeline");
  }, []);

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
  const [isDetailOpen, setIsDetailOpen] = useState(Boolean(initialSession));
  const [linkedSessionStudent, setLinkedSessionStudent] = useState<string | null>(null);

  useEffect(() => {
    if (role === "PARENT" && linkedSessionStudent && initialStudentId && linkedSessionStudent !== initialStudentId) {
      setIsDetailOpen(false);
      setSelectedSessionId(null);
    }
  }, [role, linkedSessionStudent, initialStudentId]);

  // Buổi học được nhấn mạnh (đến từ thông báo) và các buổi có thay đổi chưa xem
  const [highlightedSessionId, setHighlightedSessionId] = useState<string | null>(null);
  const { bySession: changedSessions, markSessionSeen } = useSessionChangeNotifications();

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

  // AC7 & AC8: Deep link từ thông báo /…/schedule?sessionId=<id>&action=<action> qua hook dùng chung useDeepLinkParams
  // Snapshot đổi định danh ở mỗi lần điều hướng mới, kể cả khi bấm lại cùng một buổi học
  const deepLink = useDeepLinkParams(["sessionId", "action", "startAt"]);
  const [detailAction, setDetailAction] = useState<"outcome" | null>(null);

  useEffect(() => {
    if (!deepLink.sessionId) return;
    const targetId = deepLink.sessionId;
    setSelectedSessionId(targetId);
    setIsDetailOpen(true);
    setDetailAction(deepLink.action === "outcome" ? "outcome" : null);
    setHighlightedSessionId(targetId);
    if (deepLink.startAt && Number.isFinite(Date.parse(deepLink.startAt))) {
      setWeekStart(startOfWeek(new Date(deepLink.startAt)));
    }
  }, [deepLink]);

  // Khi chi tiết buổi học tải xong, nhảy tới tuần chứa nó để người dùng thấy nó ngay trên lịch phía sau hộp thoại
  const handleSessionLoaded = useCallback((session: { startAt: string; studentId: string }) => {
    const target = startOfWeek(new Date(session.startAt));
    if (Number.isNaN(target.getTime())) return;
    setWeekStart((current) => (current.getTime() === target.getTime() ? current : target));
    setLinkedSessionStudent(session.studentId);
    if (role === "PARENT") onLinkedStudentResolved?.(session.studentId);
  }, [role, onLinkedStudentResolved]);

  // Hiệu ứng nhấn mạnh tự tắt sau vài giây
  useEffect(() => {
    if (!highlightedSessionId) return;
    const timer = window.setTimeout(() => setHighlightedSessionId(null), HIGHLIGHT_MS);
    return () => window.clearTimeout(timer);
  }, [highlightedSessionId]);

  const openSession = useCallback(
    (sessionId: string) => {
      setSelectedSessionId(sessionId);
      setIsDetailOpen(true);
      markSessionSeen(sessionId);
    },
    [markSessionSeen]
  );

  /** Mở buổi học từ banner thay đổi: nhảy tuần + nhấn mạnh + mở chi tiết. */
  const jumpToSession = useCallback(
    (sessionId: string) => {
      setHighlightedSessionId(sessionId);
      openSession(sessionId);
    },
    [openSession]
  );

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
  const getDayRanges = (date: Date): DaySessionRange[] => {
    const targetDateStr = formatLocalDateToIso(date);
    const dayStart = FIRST_HOUR * 60;
    const dayEnd = LAST_MINUTE;
    const items: DaySessionRange[] = [];
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
    return items;
  };

  const getMilestoneText = (sessionId: string): string => {
    const milestone = sessionMilestones[sessionId];
    if (!milestone) return "";
    return `${milestone.linkType === "ACTUAL" ? "Đã học: " : "Dự kiến: "}${milestone.milestones.map((m) => m.title).join(" · ")}`;
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
    openSession(sessionId);
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
          <div className="mb-2 inline-flex items-center gap-2 rounded-full bg-brand-soft px-3 py-1 text-xs font-medium text-brand-strong">
            <CalendarClock className="size-3.5" />
            Múi giờ Việt Nam UTC+7
          </div>
          <h1 className="font-heading text-2xl font-bold tracking-tight text-ink sm:text-3xl">
            Lịch học
          </h1>
          <p className="mt-1 max-w-3xl text-sm text-muted">{roleDescription}</p>
        </div>

        {/* Toolbar: Navigation, View Toggle, and Propose Action */}
        <div className="flex flex-wrap items-center gap-2">
          {/* View mode toggle: Grid vs Timeline */}
          <div className="flex h-10 items-center rounded-xl border border-line bg-surface p-0.5 shadow-2xs">
            <button
              type="button"
              onClick={() => setViewMode("grid")}
              className={`flex items-center gap-1.5 h-full px-3 rounded-lg text-xs font-medium transition-colors cursor-pointer ${
                viewMode === "grid"
                  ? "bg-brand text-white shadow-xs"
                  : "text-ink-2 hover:text-ink"
              }`}
              title="Xem dạng Lưới lịch tuần"
            >
              <Calendar className="size-3.5" />
              Lưới tuần
            </button>
            <button
              type="button"
              onClick={() => setViewMode("timeline")}
              className={`flex items-center gap-1.5 h-full px-3 rounded-lg text-xs font-medium transition-colors cursor-pointer ${
                viewMode === "timeline"
                  ? "bg-brand text-white shadow-xs"
                  : "text-ink-2 hover:text-ink"
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
            className="h-10 rounded-xl border border-line bg-surface px-3.5 text-xs font-medium text-ink-2 hover:bg-paper cursor-pointer shadow-2xs"
          >
            Hôm nay
          </button>

          <div className="flex h-10 items-center rounded-xl border border-line bg-surface shadow-2xs">
            <button
              type="button"
              onClick={() => setWeekStart((current) => addDays(current, -7))}
              className="grid size-10 place-items-center rounded-l-xl text-muted hover:bg-paper cursor-pointer"
              aria-label="Tuần trước"
            >
              <ChevronLeft className="size-4" />
            </button>
            <span className="min-w-32 px-2 text-center text-xs font-medium text-ink">
              {formatDate(weekStart)} - {formatFullDate(weekEnd)}
            </span>
            <button
              type="button"
              onClick={() => setWeekStart((current) => addDays(current, 7))}
              className="grid size-10 place-items-center rounded-r-xl text-muted hover:bg-paper cursor-pointer"
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
            className="flex h-10 items-center gap-2 rounded-xl bg-brand px-4 text-xs font-medium text-white shadow-xs hover:bg-brand-deep cursor-pointer"
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
          className="flex items-center justify-between rounded-2xl border border-danger-line bg-danger-soft p-4 text-xs text-danger-strong"
        >
          <div className="flex items-center gap-2.5">
            <AlertCircle className="size-4 text-danger-strong shrink-0" />
            <span>{error}</span>
          </div>
          <button
            type="button"
            onClick={fetchSessions}
            className="flex items-center gap-1 rounded-lg bg-danger-soft px-2.5 py-1 font-medium text-danger-strong hover:bg-danger-line cursor-pointer"
          >
            <RotateCw className="size-3" /> Thử lại
          </button>
        </div>
      )}

      {/* Thay đổi lịch chưa xem: chỉ rõ buổi học nào đổi, bấm để tới thẳng buổi đó */}
      {changedSessions.size > 0 && (
        <section
          aria-label="Thay đổi lịch học chưa xem"
          className="rounded-2xl border-2 border-danger-line bg-danger-soft p-4 shadow-xs"
        >
          <div className="mb-2 flex items-center gap-2 text-sm font-medium text-danger-strong">
            <BellRing className="size-4 animate-pulse" />
            Có {changedSessions.size} buổi học vừa thay đổi mà bạn chưa xem
          </div>
          <ul className="flex flex-col gap-1.5">
            {[...changedSessions.entries()].slice(0, 5).map(([sessionId, notes]) => (
              <li key={sessionId}>
                <button
                  type="button"
                  onClick={() => jumpToSession(sessionId)}
                  className="flex w-full items-center justify-between gap-3 rounded-xl bg-surface px-3 py-2 text-left text-xs font-medium text-danger-strong ring-1 ring-danger-line hover:bg-danger-soft cursor-pointer"
                >
                  <span className="truncate">
                    {notes[0].title}
                    <span className="ml-2 font-medium text-danger-strong/80">{notes[0].shortText}</span>
                  </span>
                  <span className="shrink-0 rounded-full bg-danger px-2 py-0.5 text-xs text-white">
                    Xem buổi học
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* Main Content: Grid View or Timeline View */}
      {viewMode === "grid" ? (
        <WeekTimeGrid
          weekDays={weekDays}
          todayIso={todayInVietnam}
          loading={loading && sessions.length === 0}
          toIso={formatLocalDateToIso}
          getDayRanges={getDayRanges}
          getClassName={getClassName}
          formatTime={(session) => formatVnSessionTime(session.startAt, session.endAt)}
          getMilestoneText={getMilestoneText}
          changedSessionIds={changedSessions}
          highlightedSessionId={highlightedSessionId}
          isSlotAvailable={(dayNumber, hour) => role === "TUTOR" && isAvailable(dayNumber, hour)}
          onSlotClick={handleCellClick}
          onSessionClick={handleSessionClick}
        />
      ) : (
        /* Timeline View */
        <ScheduleTimelineView
          sessions={sessions}
          classes={classes}
          role={role}
          changedSessionIds={changedSessions}
          highlightedSessionId={highlightedSessionId}
          onSelectSession={openSession}
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
        isOpen={isDetailOpen}
        initialAction={detailAction}
        onSessionLoaded={handleSessionLoaded}
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
