"use client";

import React, { useId } from "react";
import { Clock, Plus, Trash2 } from "lucide-react";
import { DAYS_OF_WEEK, type AvailabilitySlotLike } from "@/shared/lib/dateTime";
import {
  normalizeAvailabilityTime,
  sortAvailabilitySlots,
  validateAvailabilitySlots,
} from "./availabilitySlotUtils";

export { DAYS_OF_WEEK };

type EditableSlot = AvailabilitySlotLike & { slotId?: string };

interface AvailabilitySlotPickerProps<T extends EditableSlot> {
  slots: T[];
  onChange: (slots: T[]) => void;
  /** Tiêu đề khối; mặc định là lịch rảnh giảng dạy của gia sư. */
  title?: string;
  /** Mô tả ngắn dưới tiêu đề. */
  description?: string;
}

function toInputTime(value: string): string {
  return value.slice(0, 5);
}

function addMinutes(value: string, minutesToAdd: number): string {
  const [hours, minutes] = value.split(":").map(Number);
  const totalMinutes = Math.min(hours * 60 + minutes + minutesToAdd, 23 * 60 + 30);
  return `${String(Math.floor(totalMinutes / 60)).padStart(2, "0")}:${String(
    totalMinutes % 60
  ).padStart(2, "0")}`;
}

export function AvailabilitySlotPicker<T extends EditableSlot>({
  slots,
  onChange,
  title = "Lịch rảnh giảng dạy hằng tuần",
  description = "Thêm các khoảng bạn có thể nhận lịch. Giờ học chính thức sẽ do hai bên đề xuất và xác nhận sau.",
}: AvailabilitySlotPickerProps<T>) {
  const headingId = useId();
  const validation = validateAvailabilitySlots(slots);

  const addSlot = (dayOfWeek: number) => {
    const daySlots = sortAvailabilitySlots(slots.filter((slot) => slot.dayOfWeek === dayOfWeek));
    const previousEnd = daySlots.at(-1)?.endTime;
    const startTime = previousEnd ? toInputTime(previousEnd) : "08:00";
    const endTime = addMinutes(startTime, 60);

    onChange(
      sortAvailabilitySlots([
        ...slots,
        {
          dayOfWeek,
          startTime: normalizeAvailabilityTime(startTime),
          endTime: normalizeAvailabilityTime(endTime),
        } as T,
      ])
    );
  };

  const updateSlot = (
    slotIndex: number,
    field: "startTime" | "endTime",
    value: string
  ) => {
    onChange(
      slots.map((slot, index) =>
        index === slotIndex
          ? { ...slot, [field]: normalizeAvailabilityTime(value) }
          : slot
      )
    );
  };

  const removeSlot = (slotIndex: number) => {
    onChange(slots.filter((_, index) => index !== slotIndex));
  };

  return (
    <section aria-labelledby={headingId} className="space-y-5">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <Clock className="size-4 text-amber-600" aria-hidden="true" />
            <h3
              id={headingId}
              className="text-xs font-bold uppercase tracking-wider text-neutral-900"
            >
              {title}
            </h3>
          </div>
          <p className="mt-1.5 max-w-2xl text-[11px] leading-relaxed text-neutral-500">
            {description}
          </p>
        </div>
        <span className="text-[11px] font-medium text-neutral-500">
          Đã khai báo: <strong className="font-bold text-amber-600">{slots.length}</strong> khoảng
        </span>
      </div>

      {!validation.valid && (
        <p
          role="alert"
          className="rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-xs font-medium text-rose-700"
        >
          {validation.message}
        </p>
      )}

      <div className="divide-y divide-stone-200 overflow-hidden rounded-2xl border border-stone-200 bg-white">
        {DAYS_OF_WEEK.map((day) => {
          const daySlots = slots
            .map((slot, index) => ({ slot, index }))
            .filter(({ slot }) => slot.dayOfWeek === day.day)
            .sort((left, right) => left.slot.startTime.localeCompare(right.slot.startTime));

          return (
            <div
              key={day.day}
              className="grid gap-3 bg-white p-3 sm:grid-cols-[7rem_minmax(0,1fr)_auto] sm:items-start sm:p-4"
            >
              <div className="flex h-10 items-center justify-between sm:justify-start">
                <span className="text-xs font-bold text-neutral-800">{day.name}</span>
                <span className="text-[11px] text-neutral-400 sm:hidden">
                  {daySlots.length > 0 ? `${daySlots.length} khoảng` : "Chưa thiết lập"}
                </span>
              </div>

              <div className="space-y-2">
                {daySlots.length === 0 ? (
                  <div className="flex h-10 items-center rounded-xl border border-dashed border-stone-200 bg-stone-50 px-3 text-[11px] text-neutral-400">
                    Chưa có thời gian rảnh
                  </div>
                ) : (
                  daySlots.map(({ slot, index }) => (
                    <div
                      key={slot.slotId ?? `${day.day}-${index}`}
                      className="flex flex-wrap items-center gap-2 rounded-xl border border-stone-200 bg-stone-50 p-2"
                    >
                      <label className="sr-only" htmlFor={`availability-${day.day}-${index}-start`}>
                        Giờ bắt đầu {day.name}
                      </label>
                      <input
                        id={`availability-${day.day}-${index}-start`}
                        type="time"
                        step={1800}
                        value={toInputTime(slot.startTime)}
                        onChange={(event) => updateSlot(index, "startTime", event.target.value)}
                        className="h-9 min-w-0 flex-1 rounded-lg border border-stone-200 bg-white px-2 text-xs font-semibold text-neutral-800 outline-hidden focus:border-amber-500 focus:ring-2 focus:ring-amber-500/15"
                      />
                      <span className="text-[11px] text-neutral-400">đến</span>
                      <label className="sr-only" htmlFor={`availability-${day.day}-${index}-end`}>
                        Giờ kết thúc {day.name}
                      </label>
                      <input
                        id={`availability-${day.day}-${index}-end`}
                        type="time"
                        step={1800}
                        value={toInputTime(slot.endTime)}
                        onChange={(event) => updateSlot(index, "endTime", event.target.value)}
                        className="h-9 min-w-0 flex-1 rounded-lg border border-stone-200 bg-white px-2 text-xs font-semibold text-neutral-800 outline-hidden focus:border-amber-500 focus:ring-2 focus:ring-amber-500/15"
                      />
                      <button
                        type="button"
                        onClick={() => removeSlot(index)}
                        aria-label={`Xóa khoảng giờ ${toInputTime(slot.startTime)} đến ${toInputTime(
                          slot.endTime
                        )} ${day.name}`}
                        className="flex size-9 shrink-0 items-center justify-center rounded-lg text-neutral-400 transition-colors hover:bg-rose-50 hover:text-rose-600 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-rose-500"
                      >
                        <Trash2 className="size-4" aria-hidden="true" />
                      </button>
                    </div>
                  ))
                )}
              </div>

              <button
                type="button"
                onClick={() => addSlot(day.day)}
                className="inline-flex h-10 items-center justify-center gap-1.5 rounded-xl border border-amber-200 bg-amber-50 px-3 text-[11px] font-bold text-amber-700 transition-colors hover:border-amber-300 hover:bg-amber-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-amber-500"
              >
                <Plus className="size-3.5" aria-hidden="true" />
                Thêm khoảng
              </button>
            </div>
          );
        })}
      </div>

      <p className="text-[11px] leading-relaxed text-neutral-500">
        Có thể thêm nhiều khoảng trong cùng một ngày. Các khoảng không được chồng lấn và giờ kết
        thúc phải sau giờ bắt đầu.
      </p>
    </section>
  );
}
