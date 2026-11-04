"use client";

import React, { useState, useId } from "react";
import {
  DAYS_OF_WEEK,
  DAY_LABELS,
  SLOT_PRESETS,
  COMMON_TIME_BLOCKS,
  type SlotPresetCategory,
  type AvailabilitySlotLike,
  sortAvailabilitySlots,
  toMinutes,
} from "@/shared/lib/dateTime";
import {
  Clock,
  Plus,
  X,
  Check,
  ChevronDown,
  ChevronUp,
  AlertCircle,
  CalendarDays,
  Sparkles,
} from "lucide-react";

export interface LearningNeedSlotPickerProps {
  slots: AvailabilitySlotLike[];
  onChange: (slots: AvailabilitySlotLike[]) => void;
  error?: string;
  maxSlots?: number;
}

const CATEGORY_TABS: { id: SlotPresetCategory; label: string }[] = [
  { id: "ALL", label: "Tất cả" },
  { id: "EVENING", label: "Buổi Tối" },
  { id: "AFTERNOON", label: "Buổi Chiều" },
  { id: "MORNING", label: "Buổi Sáng" },
  { id: "WEEKEND", label: "Cuối tuần" },
];

function isSameSlot(a: AvailabilitySlotLike, b: AvailabilitySlotLike): boolean {
  return (
    a.dayOfWeek === b.dayOfWeek &&
    a.startTime.slice(0, 5) === b.startTime.slice(0, 5) &&
    a.endTime.slice(0, 5) === b.endTime.slice(0, 5)
  );
}

function normalizeSlotTime(t: string): string {
  if (!t) return "00:00:00";
  const parts = t.trim().split(":");
  const h = parts[0]?.padStart(2, "0") || "00";
  const m = parts[1]?.padStart(2, "0") || "00";
  return `${h}:${m}:00`;
}

export function LearningNeedSlotPicker({
  slots,
  onChange,
  error,
  maxSlots = 28,
}: LearningNeedSlotPickerProps) {
  const [selectedCategory, setSelectedCategory] = useState<SlotPresetCategory>("ALL");
  const [isCustomOpen, setIsCustomOpen] = useState(false);

  // Custom Builder State
  const [customDays, setCustomDays] = useState<number[]>([1]); // Mặc định Thứ 2
  const [startTime, setStartTime] = useState("19:00");
  const [endTime, setEndTime] = useState("21:00");
  const [builderError, setBuilderError] = useState<string | null>(null);

  const builderErrorId = useId();

  // Lọc presets theo tab
  const filteredPresets = SLOT_PRESETS.filter((preset) => {
    if (selectedCategory === "ALL") return true;
    return preset.category === selectedCategory;
  });

  // Kiểm tra 1 preset đã được chọn hoàn toàn chưa
  const isPresetActive = (presetSlots: AvailabilitySlotLike[]) => {
    if (presetSlots.length === 0) return false;
    return presetSlots.every((ps) => slots.some((s) => isSameSlot(s, ps)));
  };

  // Bật / tắt cả preset
  const handleTogglePreset = (presetSlots: AvailabilitySlotLike[]) => {
    const active = isPresetActive(presetSlots);
    if (active) {
      // Bỏ các slot của preset này
      const remaining = slots.filter((s) => !presetSlots.some((ps) => isSameSlot(s, ps)));
      onChange(remaining);
    } else {
      // Thêm các slot chưa có
      const toAdd = presetSlots.filter((ps) => !slots.some((s) => isSameSlot(s, ps)));
      const combined = [...slots, ...toAdd];
      if (combined.length > maxSlots) {
        setBuilderError(`Bạn chỉ có thể chọn tối đa ${maxSlots} khung giờ.`);
        return;
      }
      setBuilderError(null);
      onChange(sortAvailabilitySlots(combined));
    }
  };

  // Toggle chọn ngày trong Custom Builder
  const handleToggleDay = (day: number) => {
    setCustomDays((prev) =>
      prev.includes(day)
        ? prev.filter((d) => d !== day)
        : [...prev, day].sort((a, b) => a - b)
    );
    setBuilderError(null);
  };

  // Áp dụng ca mẫu nhanh trong Custom Builder
  const handleSelectTimeBlock = (block: { startTime: string; endTime: string }) => {
    setStartTime(block.startTime.slice(0, 5));
    setEndTime(block.endTime.slice(0, 5));
    setBuilderError(null);
  };

  // Thêm slot tùy chọn từ Custom Builder
  const handleAddCustomSlots = () => {
    setBuilderError(null);
    if (customDays.length === 0) {
      setBuilderError("Vui lòng chọn ít nhất 1 ngày trong tuần.");
      return;
    }

    const startMin = toMinutes(startTime);
    const endMin = toMinutes(endTime);

    if (isNaN(startMin) || isNaN(endMin) || endMin <= startMin) {
      setBuilderError("Giờ kết thúc phải muộn hơn giờ bắt đầu.");
      return;
    }

    if (endMin - startMin < 30) {
      setBuilderError("Khung giờ học tối thiểu là 30 phút.");
      return;
    }

    const newSlots: AvailabilitySlotLike[] = customDays.map((dayOfWeek) => ({
      dayOfWeek,
      startTime: normalizeSlotTime(startTime),
      endTime: normalizeSlotTime(endTime),
    }));

    // Kiểm tra trùng lặp hoặc trùng lấn với slot hiện có
    for (const newSlot of newSlots) {
      const dayLabel = DAY_LABELS[newSlot.dayOfWeek];
      const conflict = slots.find((existing) => {
        if (existing.dayOfWeek !== newSlot.dayOfWeek) return false;
        const eStart = toMinutes(existing.startTime);
        const eEnd = toMinutes(existing.endTime);
        const nStart = toMinutes(newSlot.startTime);
        const nEnd = toMinutes(newSlot.endTime);
        // Trùng lặp chính xác hoặc trùng lấn
        return Math.max(eStart, nStart) < Math.min(eEnd, nEnd);
      });

      if (conflict) {
        setBuilderError(
          `Khung giờ bị trùng lấn vào ${dayLabel} (${conflict.startTime.slice(0, 5)}–${conflict.endTime.slice(0, 5)}). Vui lòng chọn giờ khác.`
        );
        return;
      }
    }

    if (slots.length + newSlots.length > maxSlots) {
      setBuilderError(`Bạn chỉ có thể chọn tối đa ${maxSlots} khung giờ.`);
      return;
    }

    const merged = sortAvailabilitySlots([...slots, ...newSlots]);
    onChange(merged);
    setBuilderError(null);
  };

  // Xóa 1 slot đơn lẻ
  const handleRemoveSingleSlot = (target: AvailabilitySlotLike) => {
    const next = slots.filter((s) => !isSameSlot(s, target));
    onChange(next);
  };

  // Xóa toàn bộ slots
  const handleClearAll = () => {
    onChange([]);
    setBuilderError(null);
  };

  const sortedActiveSlots = sortAvailabilitySlots(slots);

  return (
    <fieldset className="space-y-4 font-sans text-neutral-900 dark:text-stone-100 border-0 p-0 m-0">
      {/* 1. Header & Quick Category Filter */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5">
        <div className="flex items-center gap-1.5">
          <Clock className="size-3.5 text-[#006DCC] dark:text-sky-400" />
          <legend className="text-xs font-bold text-neutral-700 dark:text-stone-200 inline">
            Khung giờ có thể học
          </legend>
          <span className="text-[11px] font-normal text-neutral-500 dark:text-stone-400">
            (Có thể chọn nhiều khung giờ)
          </span>
        </div>

        {/* Tab Ca Học */}
        <div className="flex items-center gap-1 overflow-x-auto pb-1 sm:pb-0 scrollbar-none">
          {CATEGORY_TABS.map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setSelectedCategory(tab.id)}
              className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold whitespace-nowrap transition-colors ${
                selectedCategory === tab.id
                  ? "bg-[#006DCC] text-white shadow-2xs"
                  : "bg-stone-100 dark:bg-stone-800 text-neutral-600 dark:text-stone-300 hover:bg-stone-200 dark:hover:bg-stone-700"
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* 2. Grid Presets Chọn Nhanh */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
        {filteredPresets.map((preset) => {
          const active = isPresetActive(preset.slots);
          return (
            <button
              key={preset.id}
              type="button"
              aria-pressed={active}
              onClick={() => handleTogglePreset(preset.slots)}
              className={`text-left px-3.5 py-2.5 rounded-xl border text-xs font-medium flex items-center justify-between gap-2 transition-all ${
                active
                  ? "border-[#006DCC] dark:border-sky-400 bg-sky-50/80 dark:bg-sky-950/60 text-[#006DCC] dark:text-sky-300 font-semibold shadow-2xs ring-1 ring-[#006DCC]/20 dark:ring-sky-400/20"
                  : "border-stone-200 dark:border-stone-700/80 bg-white dark:bg-stone-800/80 text-neutral-700 dark:text-stone-200 hover:bg-stone-50 dark:hover:bg-stone-700/80"
              }`}
            >
              <span className="truncate">{preset.label}</span>
              <span
                className={`size-4 rounded-md flex items-center justify-center shrink-0 border transition-colors ${
                  active
                    ? "bg-[#006DCC] dark:bg-sky-500 border-[#006DCC] text-white"
                    : "border-stone-300 dark:border-stone-600 bg-stone-50 dark:bg-stone-900"
                }`}
              >
                {active && <Check className="size-3 stroke-[3]" />}
              </span>
            </button>
          );
        })}
      </div>

      {/* 3. Panel Tự Chọn Khung Giờ Tùy Biến (Custom Builder) */}
      <div className="rounded-2xl border border-stone-200 dark:border-stone-700/80 bg-stone-50/60 dark:bg-stone-900/40 p-3 sm:p-4 space-y-3">
        <button
          type="button"
          onClick={() => setIsCustomOpen((prev) => !prev)}
          className="w-full flex items-center justify-between text-xs font-bold text-neutral-800 dark:text-stone-200 hover:text-[#006DCC] dark:hover:text-sky-400 transition-colors"
        >
          <span className="inline-flex items-center gap-1.5">
            <Plus className={`size-3.5 transition-transform duration-200 ${isCustomOpen ? "rotate-45" : ""}`} />
            <span>Tự chọn khung giờ tùy biến</span>
            <span className="text-[10px] font-normal text-neutral-500 dark:text-stone-400">
              (Chọn thứ & giờ học riêng)
            </span>
          </span>
          {isCustomOpen ? (
            <ChevronUp className="size-4 text-neutral-400" />
          ) : (
            <ChevronDown className="size-4 text-neutral-400" />
          )}
        </button>

        {isCustomOpen && (
          <div className="pt-2 border-t border-stone-200/80 dark:border-stone-800 space-y-3.5 animate-in fade-in duration-150">
            {/* Bước a: Chọn ngày trong tuần */}
            <div className="space-y-1.5">
              <span className="text-[11px] font-semibold text-neutral-600 dark:text-stone-300">
                1. Chọn ngày học trong tuần:
              </span>
              <div className="flex flex-wrap gap-1.5">
                {DAYS_OF_WEEK.map((item) => {
                  const selected = customDays.includes(item.day);
                  return (
                    <button
                      key={item.day}
                      type="button"
                      aria-pressed={selected}
                      onClick={() => handleToggleDay(item.day)}
                      className={`px-3 py-1.5 rounded-xl text-xs font-semibold border transition-all ${
                        selected
                          ? "bg-[#006DCC] dark:bg-sky-600 border-[#006DCC] dark:border-sky-500 text-white shadow-2xs"
                          : "bg-white dark:bg-stone-800 border-stone-200 dark:border-stone-700 text-neutral-700 dark:text-stone-300 hover:bg-stone-100 dark:hover:bg-stone-700"
                      }`}
                    >
                      {item.name}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Bước b: Chọn ca mẫu hoặc nhập giờ */}
            <div className="space-y-1.5">
              <span className="text-[11px] font-semibold text-neutral-600 dark:text-stone-300 flex items-center justify-between">
                <span>2. Chọn ca mẫu nhanh:</span>
                <span className="text-[10px] text-neutral-400">Hoặc chỉnh giờ bên dưới</span>
              </span>
              <div className="flex flex-wrap gap-1.5">
                {COMMON_TIME_BLOCKS.map((block) => {
                  const isCurrent =
                    startTime === block.startTime.slice(0, 5) &&
                    endTime === block.endTime.slice(0, 5);
                  return (
                    <button
                      key={block.id}
                      type="button"
                      onClick={() => handleSelectTimeBlock(block)}
                      className={`px-2.5 py-1 rounded-lg text-[11px] font-medium border transition-colors ${
                        isCurrent
                          ? "bg-sky-100 dark:bg-sky-950/80 border-sky-400 text-sky-800 dark:text-sky-300 font-bold"
                          : "bg-white dark:bg-stone-800 border-stone-200 dark:border-stone-700 text-neutral-600 dark:text-stone-300 hover:bg-stone-50 dark:hover:bg-stone-700"
                      }`}
                    >
                      {block.label}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Bước c: Ô nhập giờ thủ công & Nút thêm */}
            <div className="flex flex-wrap items-end gap-3 pt-1">
              <div className="flex items-center gap-2">
                <div className="space-y-1">
                  <label htmlFor="custom-slot-start-time" className="text-[10px] font-semibold text-neutral-500 dark:text-stone-400">Từ:</label>
                  <input
                    id="custom-slot-start-time"
                    type="time"
                    value={startTime}
                    onChange={(e) => {
                      setStartTime(e.target.value);
                      setBuilderError(null);
                    }}
                    className="px-2.5 py-1.5 bg-white dark:bg-stone-800 border border-stone-200 dark:border-stone-700 rounded-xl text-xs font-semibold focus:outline-none focus:ring-2 focus:ring-sky-300"
                  />
                </div>
                <span className="text-neutral-400 font-bold pt-4">–</span>
                <div className="space-y-1">
                  <label htmlFor="custom-slot-end-time" className="text-[10px] font-semibold text-neutral-500 dark:text-stone-400">Đến:</label>
                  <input
                    id="custom-slot-end-time"
                    type="time"
                    value={endTime}
                    onChange={(e) => {
                      setEndTime(e.target.value);
                      setBuilderError(null);
                    }}
                    className="px-2.5 py-1.5 bg-white dark:bg-stone-800 border border-stone-200 dark:border-stone-700 rounded-xl text-xs font-semibold focus:outline-none focus:ring-2 focus:ring-sky-300"
                  />
                </div>
              </div>

              <button
                type="button"
                onClick={handleAddCustomSlots}
                className="px-4 py-2 rounded-xl bg-[#006DCC] hover:bg-[#005BB0] text-white text-xs font-bold inline-flex items-center gap-1.5 shadow-2xs transition-colors"
              >
                <Plus className="size-3.5" />
                <span>Thêm vào lịch ({customDays.length} ngày)</span>
              </button>
            </div>

            {builderError && (
              <p
                id={builderErrorId}
                role="alert"
                className="text-xs text-rose-600 dark:text-rose-400 font-medium inline-flex items-center gap-1 animate-in fade-in"
              >
                <AlertCircle className="size-3.5 shrink-0" />
                <span>{builderError}</span>
              </p>
            )}
          </div>
        )}
      </div>

      {/* 4. Danh sách Khung giờ đã chọn (Active Chips) */}
      <div className="space-y-2 pt-1">
        <div className="flex items-center justify-between text-xs">
          <span className="font-semibold text-neutral-700 dark:text-stone-300 flex items-center gap-1.5">
            <CalendarDays className="size-3.5 text-emerald-600 dark:text-emerald-400" />
            <span>Đã chọn ({sortedActiveSlots.length} khung giờ):</span>
          </span>
          {sortedActiveSlots.length > 0 && (
            <button
              type="button"
              onClick={handleClearAll}
              className="text-[11px] font-semibold text-rose-600 dark:text-rose-400 hover:underline"
            >
              Xóa tất cả
            </button>
          )}
        </div>

        {sortedActiveSlots.length === 0 ? (
          <p className="text-xs text-neutral-500 dark:text-stone-400 italic py-1">
            Chưa có khung giờ nào được chọn. Vui lòng chọn ít nhất 1 khung giờ bên trên.
          </p>
        ) : (
          <div className="flex flex-wrap gap-1.5">
            {sortedActiveSlots.map((slot, index) => {
              const dayName = DAY_LABELS[slot.dayOfWeek] || `Thứ ${slot.dayOfWeek}`;
              const sTime = slot.startTime.slice(0, 5);
              const eTime = slot.endTime.slice(0, 5);
              return (
                <span
                  key={`${slot.dayOfWeek}-${sTime}-${eTime}-${index}`}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-white dark:bg-stone-800 border border-stone-200 dark:border-stone-700 text-xs font-semibold text-neutral-800 dark:text-stone-200 shadow-2xs group"
                >
                  <span className="text-[#006DCC] dark:text-sky-400 font-bold">{dayName}:</span>
                  <span>{sTime} – {eTime}</span>
                  <button
                    type="button"
                    aria-label={`Xóa khung giờ ${dayName} ${sTime} đến ${eTime}`}
                    onClick={() => handleRemoveSingleSlot(slot)}
                    className="p-0.5 text-neutral-400 hover:text-rose-600 dark:hover:text-rose-400 rounded-md transition-colors"
                  >
                    <X className="size-3.5" />
                  </button>
                </span>
              );
            })}
          </div>
        )}
      </div>

      {/* Thông báo lỗi tổng nếu form validation phát hiện */}
      {error && (
        <p role="alert" className="text-xs font-medium text-rose-600 dark:text-rose-400 inline-flex items-center gap-1">
          <AlertCircle className="size-3.5 shrink-0" />
          <span>{error}</span>
        </p>
      )}
    </fieldset>
  );
}
