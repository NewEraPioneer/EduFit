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
  /** Các buổi có thay đổi chưa xem (khóa = sessionId) */
  changedSessionIds?: ReadonlyMap<string, unknown>;
  /** Buổi học đang được nhấn mạnh (đến từ thông báo) */
  highlightedSessionId?: string | null;
  onSelectSession: (sessionId: string) => void;
  onProposeNew: () => void;
}

export function ScheduleTimelineView({
  sessions,
  classes = [],
  role,
  changedSessionIds,
  highlightedSessionId,
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
        return { text: "Chờ xác nhận", bg: "bg-action-soft text-action-strong border-action-line" };
      case "SCHEDULED":
        return { text: "Đã xếp lịch", bg: "bg-brand-soft text-brand-strong border-brand-line" };
      case "COMPLETED":
        return { text: "Đã hoàn thành", bg: "bg-success-soft text-success-strong border-success-line" };
      case "ABSENT":
        return { text: "Vắng mặt", bg: "bg-action-soft text-action-strong border-action-line" };
      case "CANCELLED":
        return { text: "Đã hủy", bg: "bg-danger-soft text-danger-strong border-danger-line" };
      case "REJECTED":
        return { text: "Từ chối", bg: "bg-line text-ink-2 border-line-strong" };
      case "EXPIRED":
        return { text: "Hết hạn", bg: "bg-line text-ink-2 border-line-strong" };
      case "WITHDRAWN":
        return { text: "Đã rút", bg: "bg-line text-ink-2 border-line-strong" };
      default:
        return { text: status, bg: "bg-sunken text-ink-2 border-line" };
    }
  };

  return (
    <div className="space-y-6">
      {/* Tabs Switcher: Sắp diễn ra vs Lịch đã hủy */}
      <div className="flex items-center gap-2 border-b border-line pb-3">
        <button
          type="button"
          onClick={() => setActiveTab("active")}
          className={`flex items-center gap-2 rounded-xl px-4 py-2 text-xs font-medium transition-all cursor-pointer ${
            activeTab === "active"
              ? "bg-brand text-white shadow-xs"
              : "bg-sunken text-ink-2 hover:bg-line"
          }`}
        >
          <Calendar className="size-3.5" />
          Sắp diễn ra & Hiệu lực
          <span
            className={`rounded-full px-2 py-0.5 text-xs font-medium ${
              activeTab === "active"
                ? "bg-white/20 text-white"
                : "bg-line text-ink-2"
            }`}
          >
            {activeSessions.length}
          </span>
        </button>
        <button
          type="button"
          onClick={() => setActiveTab("cancelled")}
          className={`flex items-center gap-2 rounded-xl px-4 py-2 text-xs font-medium transition-all cursor-pointer ${
            activeTab === "cancelled"
              ? "bg-danger text-white shadow-xs"
              : "bg-sunken text-ink-2 hover:bg-line"
          }`}
        >
          <Clock className="size-3.5" />
          Lịch đã hủy
          <span
            className={`rounded-full px-2 py-0.5 text-xs font-medium ${
              activeTab === "cancelled"
                ? "bg-white/20 text-white"
                : "bg-line text-ink-2"
            }`}
          >
            {cancelledSessions.length}
          </span>
        </button>
      </div>

      {displayedSessions.length === 0 ? (
        <div className="rounded-3xl border border-dashed border-line bg-surface p-8">
          {activeTab === "active" ? (
            <EmptyState
              icon={Calendar}
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
                    activeTab === "active" ? "bg-brand" : "bg-danger"
                  }`}
                />
                <h3 className="font-heading text-sm font-bold capitalize text-ink">
                  {formatDateHeader(dateKey)}
                </h3>
                <span className="text-xs font-medium text-muted">
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
                      data-changed={changedSessionIds?.has(session.sessionId) ? "true" : undefined}
                      className={`group relative flex flex-col justify-between w-full text-left rounded-2xl border bg-surface p-5 shadow-2xs hover:shadow-md transition-all cursor-pointer ${
                        highlightedSessionId === session.sessionId
                          ? "ring-4 ring-danger animate-pulse"
                          : changedSessionIds?.has(session.sessionId)
                          ? "ring-2 ring-danger"
                          : ""
                      } ${
                        activeTab === "active"
                          ? "border-line hover:border-brand-line"
                          : "border-danger-line bg-danger-soft/20 hover:border-danger-line"
                      }`}
                    >
                      {changedSessionIds?.has(session.sessionId) && (
                        <span className="absolute -top-2 right-3 rounded-full bg-danger px-2 py-0.5 text-xs font-medium text-white shadow">
                          Mới thay đổi
                        </span>
                      )}
                      <div>
                        {/* Top row: Time + Status badge */}
                        <div className="flex items-center justify-between gap-2">
                          <div className="flex items-center gap-1.5 text-xs font-medium text-ink">
                            <Clock
                              className={`size-3.5 ${
                                activeTab === "active" ? "text-brand-strong" : "text-danger-strong"
                              }`}
                            />
                            {formatTimeRange(session.startAt, session.endAt)}
                          </div>
                          <span
                            className={`inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium ${badge.bg}`}
                          >
                            {badge.text}
                          </span>
                        </div>

                        {/* Mode and place */}
                        <div className="mt-3 flex items-center gap-2 text-xs text-ink-2">
                          {session.mode === "ONLINE" ? (
                            <>
                              <Video className="size-3.5 text-brand-strong shrink-0" />
                              <span className="truncate">Học trực tuyến (Online)</span>
                            </>
                          ) : (
                            <>
                              <MapPin className="size-3.5 text-action-strong shrink-0" />
                              <span className="truncate">
                                {session.placeOrLink || "Dạy trực tiếp (Offline)"}
                              </span>
                            </>
                          )}
                        </div>

                        {/* Unified Friendly Class Name */}
                        <div
                          className="mt-2 text-xs font-medium text-ink truncate"
                          title={classNameFormatted}
                        >
                          {classNameFormatted}
                        </div>
                      </div>

                      {/* Bottom row: Action link */}
                      <div className="mt-4 pt-3 border-t border-line flex items-center justify-between text-xs font-medium text-brand-strong group-hover:text-brand-strong">
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
