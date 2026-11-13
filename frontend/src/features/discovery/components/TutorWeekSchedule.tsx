"use client";

import React, { useMemo } from "react";
import { CalendarX2 } from "lucide-react";
import type { TutorAvailabilitySlot, TutorBusySlot } from "../types";

const DAY_LABELS: Record<number, string> = {
  1: "Thứ Hai",
  2: "Thứ Ba",
  3: "Thứ Tư",
  4: "Thứ Năm",
  5: "Thứ Sáu",
  6: "Thứ Bảy",
  7: "Chủ Nhật",
};

interface BusyEntry {
  key: string;
  date: string;
  range: string;
}

const hhmm = (value: string) => value.slice(0, 5);

/** Thứ trong tuần theo quy ước 1 = Thứ 2 ... 7 = Chủ nhật. */
const isoDay = (date: Date) => (date.getDay() === 0 ? 7 : date.getDay());

const timeOf = (date: Date) =>
  `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;

interface TutorWeekScheduleProps {
  availabilitySlots: TutorAvailabilitySlot[];
  busySlots?: TutorBusySlot[];
}

/**
 * Lịch tuần của gia sư: khung giờ rảnh cố định và các buổi đã có lịch trong 4 tuần tới,
 * để người xem biết gia sư đang kín giờ nào trước khi gửi yêu cầu.
 */
export function TutorWeekSchedule({ availabilitySlots, busySlots = [] }: TutorWeekScheduleProps) {
  const busyByDay = useMemo(() => {
    const map = new Map<number, BusyEntry[]>();
    for (const slot of busySlots) {
      const start = new Date(slot.startAt);
      const end = new Date(slot.endAt);
      if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) continue;
      const day = isoDay(start);
      const list = map.get(day) ?? [];
      list.push({
        key: `${slot.startAt}-${slot.endAt}`,
        date: start.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" }),
        range: `${timeOf(start)}–${timeOf(end)}`,
      });
      map.set(day, list);
    }
    return map;
  }, [busySlots]);

  const days = useMemo(() => {
    const free = new Map<number, TutorAvailabilitySlot[]>();
    for (const slot of availabilitySlots) {
      free.set(slot.dayOfWeek, [...(free.get(slot.dayOfWeek) ?? []), slot]);
    }
    return [1, 2, 3, 4, 5, 6, 7]
      .filter((day) => free.has(day) || busyByDay.has(day))
      .map((day) => ({ day, free: free.get(day) ?? [], busy: busyByDay.get(day) ?? [] }));
  }, [availabilitySlots, busyByDay]);

  if (days.length === 0) {
    return (
      <p className="text-xs text-muted p-3 bg-paper rounded-xl">
        Gia sư chưa đăng lịch rảnh. Bạn có thể gửi yêu cầu để thống nhất giờ học.
      </p>
    );
  }

  return (
    <div className="space-y-2">
      <ul className="divide-y divide-line rounded-2xl border border-line bg-surface">
        {days.map(({ day, free, busy }) => (
          <li key={day} className="flex flex-col gap-2 p-3 sm:flex-row sm:items-start sm:gap-4">
            <span className="w-20 shrink-0 text-sm font-medium text-ink">{DAY_LABELS[day]}</span>
            <div className="flex flex-1 flex-wrap gap-1.5">
              {free.map((slot, idx) => (
                <span
                  key={`free-${idx}`}
                  className="inline-flex items-center rounded-lg border border-success-line bg-success-soft px-2.5 py-1 text-xs font-medium text-success-strong"
                >
                  Rảnh {hhmm(slot.startTime)}–{hhmm(slot.endTime)}
                </span>
              ))}
              {busy.map((entry) => (
                <span
                  key={entry.key}
                  className="inline-flex items-center gap-1 rounded-lg border border-danger-line bg-danger-soft px-2.5 py-1 text-xs font-medium text-danger-strong"
                >
                  <CalendarX2 className="size-3" aria-hidden="true" />
                  Đã có lịch {entry.date} · {entry.range}
                </span>
              ))}
            </div>
          </li>
        ))}
      </ul>
      <p className="text-xs text-muted">Giờ địa phương. Lịch đã có hiển thị cho 4 tuần tới.</p>
    </div>
  );
}
