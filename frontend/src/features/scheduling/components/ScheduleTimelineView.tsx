"use client";

import React, { useMemo, useState } from "react";
import { Clock, Video, MapPin, Calendar, ChevronRight, Sparkles, AlertCircle } from "lucide-react";
import { EmptyState } from "@/shared/ui/EmptyState";
import { formatLocalDateToIso, formatSessionTime } from "@/shared/utils/dateTime";
import { type CalendarItem, type ConnectedClass, type UserRole, formatConnectedClassName } from "../types";

export interface ScheduleTimelineViewProps {
  sessions: CalendarItem[];
  classes?: ConnectedClass[];
  role?: UserRole;
  onSelectSession: (sessionId: string) => void;
  onProposeNew: () => void;
}

export function ScheduleTimelineView({
  sessions,
  classes = [],
  role,
  onSelectSession,
  onProposeNew,
}: ScheduleTimelineViewProps) {
  const [activeTab, setActiveTab] = useState<"active" | "cancelled">("active");

  const classMap = useMemo(() => {
    return new Map<string, ConnectedClass>(classes.map((c) => [c.classId, c]));
  }, [classes]);

  const getClassName = (classId: string) => {
    return formatConnectedClassName(classMap.get(classId), role, classId);
  };

  // 1. Phân loại: Buổi học đang có hiệu lực / sắp diễn ra (SCHEDULED, PROPOSED, COMPLETED, ABSENT)
  const activeSessions = useMemo(() => {
    return sessions.filter((s) => {
      const st = s.status?.trim().toUpperCase();
      return st === "SCHEDULED" || st === "PROPOSED" || st === "COMPLETED" || st === "ABSENT";
    });
  }, [sessions]);

  // 2. Phân loại: Buổi học đã hủy / từ chối (CANCELLED, REJECTED, EXPIRED, WITHDRAWN)
  const cancelledSessions = useMemo(() => {
    return sessions.filter((s) => {
      const st = s.status?.trim().toUpperCase();
      return (
        st === "CANCELLED" ||
        st === "REJECTED" ||
        st === "EXPIRED" ||
        st === "WITHDRAWN" ||
        (Boolean(st) && !["SCHEDULED", "PROPOSED", "COMPLETED", "ABSENT"].includes(st as string))
      );
    });
  }, [sessions]);

  const displayedSessions = activeTab === "active" ? activeSessions : cancelledSessions;

  // Sort sessions chronologically
  const sortedSessions = useMemo(() => {
    return [...displayedSessions].sort(
      (a, b) => new Date(a.startAt).getTime() - new Date(b.startAt).getTime()
    );
  }, [displayedSessions]);

  // Group by date (YYYY-MM-DD)
  const groupedSessions = useMemo(() => {
    const map = new Map<string, CalendarItem[]>();
    for (const session of sortedSessions) {
      const dateKey = session.startAt ? formatLocalDateToIso(session.startAt) : "unknown";
      const existing = map.get(dateKey) || [];
      existing.push(session);
      map.set(dateKey, existing);
    }
    return Array.from(map.entries());
  }, [sortedSessions]);

  const formatDateHeader = (dateStr: string) => {
    try {
      const d = new Date(`${dateStr}T00:00:00`);
      return new Intl.DateTimeFormat("vi-VN", {
        weekday: "long",
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
      }).format(d);
    } catch {
      return dateStr;
    }
  };

  const formatTimeRange = (startIso: string, endIso: string) => {
    return formatSessionTime(startIso, endIso);
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case "PROPOSED":
        return { text: "Chờ xác nhận", bg: "bg-amber-100 text-amber-800 border-amber-200" };
      case "SCHEDULED":
        return { text: "Đã xếp lịch", bg: "bg-sky-100 text-sky-800 border-sky-200" };
      case "COMPLETED":
        return { text: "Đã hoàn thành", bg: "bg-emerald-100 text-emerald-800 border-emerald-200" };
      case "ABSENT":
        return { text: "Vắng mặt", bg: "bg-orange-100 text-orange-800 border-orange-200" };
      case "CANCELLED":
        return { text: "Đã hủy", bg: "bg-rose-100 text-rose-800 border-rose-200" };
      case "REJECTED":
        return { text: "Từ chối", bg: "bg-stone-200 text-stone-700 border-stone-300" };
      case "EXPIRED":
        return { text: "Hết hạn", bg: "bg-stone-200 text-stone-700 border-stone-300" };
      case "WITHDRAWN":
        return { text: "Đã rút", bg: "bg-stone-200 text-stone-700 border-stone-300" };
      default:
        return { text: status, bg: "bg-stone-100 text-stone-700 border-stone-200" };
    }
  };

  return (
    <div className="space-y-6">
      {/* Tabs Switcher: Sắp diễn ra vs Lịch đã hủy */}
      <div className="flex items-center gap-2 border-b border-stone-200 pb-3 dark:border-zinc-800">
        <button
          type="button"
          onClick={() => setActiveTab("active")}
          className={`flex items-center gap-2 rounded-xl px-4 py-2 text-xs font-bold transition-all cursor-pointer ${
            activeTab === "active"
              ? "bg-sky-600 text-white shadow-xs"
              : "bg-stone-100 text-neutral-600 hover:bg-stone-200 dark:bg-zinc-800 dark:text-zinc-300"
          }`}
        >
          <Calendar className="size-3.5" />
          Sắp diễn ra & Hiệu lực
          <span
            className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${
              activeTab === "active"
                ? "bg-white/20 text-white"
                : "bg-stone-200 text-neutral-700 dark:bg-zinc-700 dark:text-zinc-300"
            }`}
          >
            {activeSessions.length}
          </span>
        </button>
        <button
          type="button"
          onClick={() => setActiveTab("cancelled")}
          className={`flex items-center gap-2 rounded-xl px-4 py-2 text-xs font-bold transition-all cursor-pointer ${
            activeTab === "cancelled"
              ? "bg-rose-600 text-white shadow-xs"
              : "bg-stone-100 text-neutral-600 hover:bg-stone-200 dark:bg-zinc-800 dark:text-zinc-300"
          }`}
        >
          <Clock className="size-3.5" />
          Lịch đã hủy
          <span
            className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${
              activeTab === "cancelled"
                ? "bg-white/20 text-white"
                : "bg-stone-200 text-neutral-700 dark:bg-zinc-700 dark:text-zinc-300"
            }`}
          >
            {cancelledSessions.length}
          </span>
        </button>
      </div>

      {displayedSessions.length === 0 ? (
        <div className="rounded-3xl border border-dashed border-stone-200 bg-white p-8 dark:border-zinc-800 dark:bg-zinc-900">
          {activeTab === "active" ? (
            <EmptyState
              icon={Calendar}
              theme="sky"
              compact
              title="Không có buổi học nào trong khoảng thời gian này"
              description="Bạn chưa có buổi học hoặc đề xuất nào đang có hiệu lực trong tuần đã chọn. Bấm nút dưới đây để đề xuất buổi học mới."
              action={{
                label: "Đề xuất lịch mới ngay",
                onClick: onProposeNew,
                icon: Sparkles,
              }}
            />
          ) : (
            <EmptyState
              icon={AlertCircle}
              theme="neutral"
              compact
              title="Không có buổi học nào bị hủy"
              description="Tất cả các buổi học của bạn trong khoảng thời gian này đều được duy trì bình thường, không có buổi nào bị hủy hoặc từ chối."
            />
          )}
        </div>
      ) : (
        <div className="space-y-6">
          {groupedSessions.map(([dateKey, items]) => (
            <section key={dateKey} className="space-y-3">
              {/* Ngày trong tuần Header */}
              <div className="flex items-center gap-2">
                <span
                  className={`h-2 w-2 rounded-full ${
                    activeTab === "active" ? "bg-sky-500" : "bg-rose-500"
                  }`}
                />
                <h3 className="font-heading text-sm font-bold capitalize text-neutral-800 dark:text-zinc-200">
                  {formatDateHeader(dateKey)}
                </h3>
                <span className="text-xs font-semibold text-neutral-400">
                  ({items.length} {activeTab === "active" ? "buổi" : "buổi đã hủy"})
                </span>
              </div>

              {/* Danh sách thẻ buổi học */}
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {items.map((session) => {
                  const badge = getStatusBadge(session.status);
                  const classNameFormatted = getClassName(session.classId);

                  return (
                    <button
                      type="button"
                      key={session.sessionId}
                      onClick={() => onSelectSession(session.sessionId)}
                      className={`group relative flex flex-col justify-between w-full text-left rounded-2xl border bg-white p-5 shadow-2xs hover:shadow-md transition-all cursor-pointer dark:bg-zinc-900 ${
                        activeTab === "active"
                          ? "border-stone-200 hover:border-sky-300 dark:border-zinc-800"
                          : "border-rose-100 bg-rose-50/20 hover:border-rose-300 dark:border-rose-950/40"
                      }`}
                    >
                      <div>
                        {/* Top row: Time + Status badge */}
                        <div className="flex items-center justify-between gap-2">
                          <div className="flex items-center gap-1.5 text-xs font-bold text-neutral-900 dark:text-zinc-100">
                            <Clock
                              className={`size-3.5 ${
                                activeTab === "active" ? "text-sky-600" : "text-rose-500"
                              }`}
                            />
                            {formatTimeRange(session.startAt, session.endAt)}
                          </div>
                          <span
                            className={`inline-flex items-center rounded-md border px-2 py-0.5 text-[10px] font-bold ${badge.bg}`}
                          >
                            {badge.text}
                          </span>
                        </div>

                        {/* Mode and place */}
                        <div className="mt-3 flex items-center gap-2 text-xs text-neutral-600 dark:text-zinc-400">
                          {session.mode === "ONLINE" ? (
                            <>
                              <Video className="size-3.5 text-sky-500 shrink-0" />
                              <span className="truncate">Học trực tuyến (Online)</span>
                            </>
                          ) : (
                            <>
                              <MapPin className="size-3.5 text-amber-500 shrink-0" />
                              <span className="truncate">
                                {session.placeOrLink || "Dạy trực tiếp (Offline)"}
                              </span>
                            </>
                          )}
                        </div>

                        {/* Unified Friendly Class Name */}
                        <div
                          className="mt-2 text-xs font-bold text-neutral-800 dark:text-zinc-200 truncate"
                          title={classNameFormatted}
                        >
                          {classNameFormatted}
                        </div>
                      </div>

                      {/* Bottom row: Action link */}
                      <div className="mt-4 pt-3 border-t border-stone-100 flex items-center justify-between text-xs font-bold text-sky-700 group-hover:text-sky-900 dark:border-zinc-800 dark:text-sky-400">
                        <span>Xem chi tiết / Thao tác</span>
                        <ChevronRight className="size-4 transition-transform group-hover:translate-x-0.5" />
                      </div>
                    </button>
                  );
                })}
              </div>
            </section>
          ))}
        </div>
      )}
    </div>
  );
}
