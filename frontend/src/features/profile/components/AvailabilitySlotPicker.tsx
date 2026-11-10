"use client";

import { TimeField } from "@/shared/ui/TimeField";
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
  showValidation?: boolean;
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
  showValidation = true,
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
            <Clock className="size-4 text-action-strong" aria-hidden="true" />
            <h3
              id={headingId}
              className="text-xs font-medium text-ink"
            >
              {title}
            </h3>
          </div>
          <p className="mt-1.5 max-w-2xl text-xs leading-relaxed text-muted">
            {description}
          </p>
        </div>
        <span className="text-xs font-medium text-muted">
          Đã khai báo: <strong className="font-medium text-action-strong">{slots.length}</strong> khoảng
        </span>
      </div>

      {showValidation && !validation.valid && (
        <p
          role="alert"
          className="rounded-xl border border-danger-line bg-danger-soft px-3 py-2 text-xs font-medium text-danger-strong"
        >
          {validation.message}
        </p>
      )}

      <div className="divide-y divide-line overflow-hidden rounded-2xl border border-line bg-surface">
        {DAYS_OF_WEEK.map((day) => {
          const daySlots = slots
            .map((slot, index) => ({ slot, index }))
            .filter(({ slot }) => slot.dayOfWeek === day.day)
            .sort((left, right) => left.slot.startTime.localeCompare(right.slot.startTime));

          return (
            <div
              key={day.day}
              className="grid gap-3 bg-surface p-3 sm:grid-cols-[5.5rem_minmax(0,1fr)_auto] sm:items-start sm:p-4"
            >
              <div className="flex h-10 items-center justify-between sm:justify-start">
                <span className="text-xs font-medium text-ink">{day.name}</span>
                <span className="text-xs text-muted sm:hidden">
                  {daySlots.length > 0 ? `${daySlots.length} khoảng` : "Chưa thiết lập"}
                </span>
              </div>

              <div className="space-y-2">
                {daySlots.length === 0 ? (
                  <div className="flex h-10 items-center rounded-xl border border-dashed border-line bg-paper px-3 text-xs text-muted">
                    Chưa có thời gian rảnh
                  </div>
                ) : (
                  daySlots.map(({ slot, index }) => (
                    <div
                      key={slot.slotId ?? `${day.day}-${index}`}
                      className="flex items-center gap-2 rounded-xl border border-line bg-paper p-2"
                    >
                      <label className="sr-only" htmlFor={`availability-${day.day}-${index}-start`}>
                        Giờ bắt đầu {day.name}
                      </label>
                      <TimeField
                        id={`availability-${day.day}-${index}-start`}
                        value={toInputTime(slot.startTime)}
                        onChange={(value) => updateSlot(index, "startTime", value)}
                        className="flex-1"
                      />
                      <span className="text-xs text-muted">đến</span>
                      <label className="sr-only" htmlFor={`availability-${day.day}-${index}-end`}>
                        Giờ kết thúc {day.name}
                      </label>
                      <TimeField
                        id={`availability-${day.day}-${index}-end`}
                        value={toInputTime(slot.endTime)}
                        onChange={(value) => updateSlot(index, "endTime", value)}
                        startFrom={toInputTime(slot.startTime)}
                        className="flex-1"
                      />
                      <button
                        type="button"
                        onClick={() => removeSlot(index)}
                        aria-label={`Xóa khoảng giờ ${toInputTime(slot.startTime)} đến ${toInputTime(
                          slot.endTime
                        )} ${day.name}`}
                        className="flex size-9 shrink-0 items-center justify-center rounded-lg text-muted transition-colors hover:bg-danger-soft hover:text-danger-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-danger"
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
                className="inline-flex h-10 items-center justify-center gap-1.5 rounded-xl border border-action-line bg-action-soft px-3 text-xs font-medium text-action-strong hover:border-action hover:bg-action hover:text-on-action focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-action"
              >
                <Plus className="size-3.5" aria-hidden="true" />
                Thêm khoảng
              </button>
            </div>
          );
        })}
      </div>

      <p className="text-xs leading-relaxed text-muted">
        Có thể thêm nhiều khoảng trong cùng một ngày. Các khoảng không được chồng lấn và giờ kết
        thúc phải sau giờ bắt đầu.
      </p>
    </section>
  );
}
