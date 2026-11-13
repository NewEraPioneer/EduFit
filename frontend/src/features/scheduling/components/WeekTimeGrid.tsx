"use client";

import React, { useEffect, useRef, useState } from "react";
import { MapPin, Video } from "lucide-react";
import { cn } from "@/lib/utils";
import type { CalendarItem } from "../types";

export const FIRST_HOUR = 7;
const HOUR_COUNT = 15;
const HOURS = Array.from({ length: HOUR_COUNT }, (_, index) => index + FIRST_HOUR);
/** 1 phút = 1px: chiều cao thẻ luôn đúng thời lượng, thẻ không tràn sang giờ khác. */
const HOUR_PX = 60;
const GRID_HEIGHT = HOUR_COUNT * HOUR_PX;
const MIN_BLOCK_MINUTES = 30;
export const LAST_MINUTE = (FIRST_HOUR + HOUR_COUNT) * 60;
const GRID_COLUMNS = "grid-cols-[52px_repeat(7,minmax(104px,1fr))]";
const MAX_SIDE_BY_SIDE = 2;
const VN_OFFSET_MS = 7 * 60 * 60 * 1000;

export interface DaySessionRange {
  session: CalendarItem;
  start: number;
  end: number;
}

interface PositionedSession {
  session: CalendarItem;
  top: number;
  height: number;
  left: number;
  width: number;
  layer: number;
}

/**
 * Xếp các buổi trùng giờ giống Google Calendar: tối đa 2 buổi nằm cạnh nhau,
 * từ 3 buổi trở lên thì buổi sau xếp chồng lệch sang phải, nên chữ vẫn đọc được.
 */
export function layoutDaySessions(items: DaySessionRange[]): PositionedSession[] {
  const sorted = [...items].sort((a, b) => a.start - b.start || b.end - a.end);
  const result: PositionedSession[] = [];
  let cluster: DaySessionRange[] = [];
  let clusterEnd = -1;

  const flush = () => {
    const columnEnds: number[] = [];
    const placed = cluster.map((item) => {
      let column = columnEnds.findIndex((end) => end <= item.start);
      if (column === -1) column = columnEnds.length;
      columnEnds[column] = item.end;
      return { item, column };
    });
    const columns = columnEnds.length;
    const sideBySide = columns <= MAX_SIDE_BY_SIDE;
    placed.forEach(({ item, column }, index) => {
      const left = sideBySide ? (column / columns) * 100 : (column / columns) * 62;
      result.push({
        session: item.session,
        top: ((item.start - FIRST_HOUR * 60) / 60) * HOUR_PX,
        height: (Math.max(item.end - item.start, MIN_BLOCK_MINUTES) / 60) * HOUR_PX,
        left,
        width: sideBySide ? 100 / columns : 100 - left,
        layer: index + 1,
      });
    });
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

const STATUS_STYLE: Record<string, { chip: string; text: string }> = {
  SCHEDULED: { chip: "border-l-brand bg-brand-soft text-brand-strong", text: "Đã xếp lịch" },
  PROPOSED: { chip: "border-l-action bg-action-soft text-action-strong", text: "Chờ xác nhận" },
  COMPLETED: { chip: "border-l-success bg-success-soft text-success-strong", text: "Đã hoàn thành" },
  ABSENT: { chip: "border-l-danger bg-danger-soft text-danger-strong", text: "Vắng mặt" },
};
const OTHER_STATUS = { chip: "border-l-line-strong bg-sunken text-ink-2", text: "" };

function vietnamNowMinutes() {
  const now = new Date(Date.now() + VN_OFFSET_MS);
  return now.getUTCHours() * 60 + now.getUTCMinutes();
}

interface SessionChipProps {
  block: PositionedSession;
  className: string;
  timeText: string;
  milestoneText: string;
  changed: boolean;
  highlighted: boolean;
  onOpen: (event: React.MouseEvent, sessionId: string) => void;
}

function scrollIntoViewOnce(element: HTMLElement | null) {
  element?.scrollIntoView?.({ block: "center", behavior: "smooth" });
}

/** Thẻ buổi học: nội dung giảm dần theo chiều cao, mọi dòng đều cắt gọn. */
function SessionChip({ block, className, timeText, milestoneText, changed, highlighted, onOpen }: SessionChipProps) {
  const { session, top, height, left, width, layer } = block;
  const status = STATUS_STYLE[session.status] ?? OTHER_STATUS;
  const roomForTime = height >= 44;
  const roomForStatus = height >= 76;
  const roomForMilestone = height >= 92 && Boolean(milestoneText);

  return (
    <button
      type="button"
      data-still
      ref={highlighted ? scrollIntoViewOnce : undefined}
      data-testid={`calendar-session-${session.sessionId}`}
      data-changed={changed ? "true" : undefined}
      onClick={(event) => onOpen(event, session.sessionId)}
      title={[timeText, className, status.text, milestoneText].filter(Boolean).join("\n")}
      className={cn(
        "absolute flex flex-col overflow-hidden rounded-md border-l-[3px] px-1.5 py-0.5 text-left text-xs leading-4 ring-1 ring-surface transition-[filter] hover:z-30 hover:brightness-95",
        status.chip,
        session.status === "PROPOSED" && "border-dashed",
        highlighted && "z-30 ring-[3px] ring-danger animate-pulse",
        changed && !highlighted && "ring-2 ring-danger",
      )}
      style={{ top: top + 1, height: height - 2, left: `${left}%`, width: `calc(${width}% - 4px)`, zIndex: highlighted ? 30 : 10 + layer }}
    >
      <span className="flex min-w-0 items-center gap-1 font-medium">
        {changed && <span aria-hidden="true" className="size-1.5 shrink-0 rounded-full bg-danger" />}
        <span className="min-w-0 flex-1 truncate">
          {className}
          {!roomForTime && <span className="font-normal">, {timeText}</span>}
        </span>
      </span>
      {changed && <span className="sr-only">Mới thay đổi</span>}
      <span className={cn("flex min-w-0 items-center gap-1", !roomForTime && "sr-only")}>
        <span className="truncate">{timeText}</span>
        {session.mode === "ONLINE" ? <Video aria-hidden="true" className="size-3 shrink-0" /> : <MapPin aria-hidden="true" className="size-3 shrink-0" />}
      </span>
      <span className={cn("truncate", !roomForStatus && "sr-only")}>{status.text}</span>
      {milestoneText && (
        <span className={cn("truncate", !roomForMilestone && "sr-only")} data-testid={`session-milestones-${session.sessionId}`}>
          {milestoneText}
        </span>
      )}
    </button>
  );
}

interface WeekTimeGridProps {
  weekDays: { label: string; date: Date }[];
  todayIso?: string;
  loading: boolean;
  toIso: (date: Date) => string;
  getDayRanges: (date: Date) => DaySessionRange[];
  getClassName: (classId: string) => string;
  formatTime: (session: CalendarItem) => string;
  getMilestoneText: (sessionId: string) => string;
  changedSessionIds: { has: (sessionId: string) => boolean };
  highlightedSessionId: string | null;
  isSlotAvailable: (dayNumber: number, hour: number) => boolean;
  onSlotClick: (date: Date, hour: number) => void;
  onSessionClick: (event: React.MouseEvent, sessionId: string) => void;
}

/** Lưới tuần theo bố cục Google Calendar: tiêu đề và cột giờ cố định, chỉ có đường kẻ giờ. */
export function WeekTimeGrid({
  weekDays,
  todayIso,
  loading,
  toIso,
  getDayRanges,
  getClassName,
  formatTime,
  getMilestoneText,
  changedSessionIds,
  highlightedSessionId,
  isSlotAvailable,
  onSlotClick,
  onSessionClick,
}: WeekTimeGridProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [nowMinutes, setNowMinutes] = useState(vietnamNowMinutes);
  const hasToday = weekDays.some((day) => toIso(day.date) === todayIso);

  useEffect(() => {
    const timer = window.setInterval(() => setNowMinutes(vietnamNowMinutes()), 60_000);
    return () => window.clearInterval(timer);
  }, []);

  // Tuần có hôm nay: mở sẵn ở giờ hiện tại, không cần cuộn tìm
  useEffect(() => {
    if (loading || !hasToday || highlightedSessionId) return;
    const offset = ((vietnamNowMinutes() - FIRST_HOUR * 60) / 60) * HOUR_PX - HOUR_PX;
    scrollRef.current?.scrollTo?.({ top: Math.max(offset, 0) });
  }, [loading, hasToday, highlightedSessionId]);

  const nowInRange = nowMinutes >= FIRST_HOUR * 60 && nowMinutes <= LAST_MINUTE;
  const nowTop = ((nowMinutes - FIRST_HOUR * 60) / 60) * HOUR_PX;

  return (
    <section className="isolate overflow-hidden rounded-2xl border border-line bg-surface">
      <div ref={scrollRef} className="max-h-[max(32rem,calc(100dvh-15rem))] overflow-auto overscroll-contain">
        <div className="min-w-[780px]">
          <div className={cn("sticky top-0 z-40 grid border-b border-line bg-surface", GRID_COLUMNS)}>
            <div className="sticky left-0 z-10 bg-surface">
              <span className="sr-only">Giờ</span>
            </div>
            {weekDays.map(({ label, date }) => {
              const isToday = toIso(date) === todayIso;
              return (
                <div key={label} className="flex flex-col items-center gap-0.5 border-l border-line py-2">
                  <span className={cn("text-xs", isToday ? "font-medium text-brand-strong" : "text-muted")}>{label}</span>
                  <span className={cn("flex size-9 items-center justify-center rounded-full font-heading text-lg", isToday ? "bg-brand font-bold text-white" : "text-ink")}>
                    {date.getDate()}
                  </span>
                  {isToday && <span className="sr-only">Hôm nay</span>}
                </div>
              );
            })}
          </div>

          {loading ? (
            <div className="p-12 text-center text-sm text-muted animate-pulse">Đang tải lịch học...</div>
          ) : (
            <div className={cn("grid", GRID_COLUMNS)}>
              <div className="sticky left-0 z-20 bg-surface" style={{ height: GRID_HEIGHT }}>
                {HOURS.slice(1).map((hour) => (
                  <span key={hour} className="absolute right-2 -translate-y-1/2 text-xs text-muted tabular-nums" style={{ top: (hour - FIRST_HOUR) * HOUR_PX }}>
                    {String(hour).padStart(2, "0")}:00
                  </span>
                ))}
              </div>

              {weekDays.map((day, dayIndex) => {
                const isToday = toIso(day.date) === todayIso;
                return (
                  <div key={day.label} data-testid={`calendar-day-${dayIndex}`} className="relative border-l border-line" style={{ height: GRID_HEIGHT }}>
                    {HOURS.map((hour) => {
                      const available = isSlotAvailable(dayIndex + 1, hour);
                      const slotLabel = `${String(hour).padStart(2, "0")}:00 ${day.label}`;
                      return (
                        <button
                          key={hour}
                          type="button"
                          data-still
                          onClick={() => onSlotClick(day.date, hour)}
                          aria-label={`Đề xuất lịch lúc ${slotLabel}`}
                          title={available ? `Khung rảnh. Bấm để đề xuất lịch lúc ${slotLabel}` : `Bấm để đề xuất lịch lúc ${slotLabel}`}
                          className={cn("absolute inset-x-0 border-b border-line/70 transition-colors hover:bg-brand-soft/40", available && "bg-success-soft/50")}
                          style={{ top: (hour - FIRST_HOUR) * HOUR_PX, height: HOUR_PX }}
                        >
                          {available && <span className="sr-only">Khung rảnh</span>}
                        </button>
                      );
                    })}

                    {layoutDaySessions(getDayRanges(day.date)).map((block) => (
                      <SessionChip
                        key={block.session.sessionId}
                        block={block}
                        className={getClassName(block.session.classId)}
                        timeText={formatTime(block.session)}
                        milestoneText={getMilestoneText(block.session.sessionId)}
                        changed={changedSessionIds.has(block.session.sessionId)}
                        highlighted={highlightedSessionId === block.session.sessionId}
                        onOpen={onSessionClick}
                      />
                    ))}

                    {isToday && nowInRange && (
                      <div aria-hidden="true" data-testid="calendar-now-line" className="pointer-events-none absolute inset-x-0 z-[35] h-0.5 bg-danger" style={{ top: nowTop }}>
                        <span className="absolute -top-1 -left-1 size-2.5 rounded-full bg-danger" />
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
