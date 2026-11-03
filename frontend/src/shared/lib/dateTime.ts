export interface AvailabilitySlotLike {
  dayOfWeek: number;
  startTime: string;
  endTime: string;
}

export interface AvailabilityValidationResult {
  valid: boolean;
  message?: string;
}

export interface DayOfWeekOption {
  day: number;
  name: string;
}

export const DAYS_OF_WEEK: DayOfWeekOption[] = [
  { day: 1, name: "Thứ 2" },
  { day: 2, name: "Thứ 3" },
  { day: 3, name: "Thứ 4" },
  { day: 4, name: "Thứ 5" },
  { day: 5, name: "Thứ 6" },
  { day: 6, name: "Thứ 7" },
  { day: 7, name: "Chủ Nhật" },
];

export const DAY_LABELS: Record<number, string> = {
  1: "Thứ 2",
  2: "Thứ 3",
  3: "Thứ 4",
  4: "Thứ 5",
  5: "Thứ 6",
  6: "Thứ 7",
  7: "Chủ Nhật",
};

export type SlotPresetCategory = "ALL" | "EVENING" | "AFTERNOON" | "MORNING" | "WEEKEND";

export interface SlotPreset {
  id: string;
  label: string;
  category?: SlotPresetCategory;
  slots: AvailabilitySlotLike[];
}

export interface CommonTimeBlock {
  id: string;
  label: string;
  startTime: string; // e.g. "08:00:00"
  endTime: string;   // e.g. "10:00:00"
}

export const COMMON_TIME_BLOCKS: CommonTimeBlock[] = [
  { id: "morning-1", label: "08:00 – 10:00", startTime: "08:00:00", endTime: "10:00:00" },
  { id: "morning-2", label: "09:30 – 11:30", startTime: "09:30:00", endTime: "11:30:00" },
  { id: "afternoon-1", label: "14:00 – 16:00", startTime: "14:00:00", endTime: "16:00:00" },
  { id: "afternoon-2", label: "15:30 – 17:30", startTime: "15:30:00", endTime: "17:30:00" },
  { id: "late-afternoon", label: "17:30 – 19:30", startTime: "17:30:00", endTime: "19:30:00" },
  { id: "evening-1", label: "18:00 – 20:00", startTime: "18:00:00", endTime: "20:00:00" },
  { id: "evening-2", label: "19:00 – 21:00", startTime: "19:00:00", endTime: "21:00:00" },
  { id: "evening-3", label: "19:30 – 21:30", startTime: "19:30:00", endTime: "21:30:00" },
];

export const SLOT_PRESETS: SlotPreset[] = [
  // --- BUỔI TỐI ---
  {
    id: "mon-wed-evening",
    label: "Tối Thứ 2 & Thứ 4 · 19:00–21:00",
    category: "EVENING",
    slots: [
      { dayOfWeek: 1, startTime: "19:00:00", endTime: "21:00:00" },
      { dayOfWeek: 3, startTime: "19:00:00", endTime: "21:00:00" },
    ],
  },
  {
    id: "tue-fri-evening",
    label: "Tối Thứ 3 & Thứ 6 · 19:00–21:00",
    category: "EVENING",
    slots: [
      { dayOfWeek: 2, startTime: "19:00:00", endTime: "21:00:00" },
      { dayOfWeek: 5, startTime: "19:00:00", endTime: "21:00:00" },
    ],
  },
  {
    id: "tue-thu-evening",
    label: "Tối Thứ 3 & Thứ 5 · 19:00–21:00",
    category: "EVENING",
    slots: [
      { dayOfWeek: 2, startTime: "19:00:00", endTime: "21:00:00" },
      { dayOfWeek: 4, startTime: "19:00:00", endTime: "21:00:00" },
    ],
  },
  {
    id: "wed-fri-evening",
    label: "Tối Thứ 4 & Thứ 6 · 19:00–21:00",
    category: "EVENING",
    slots: [
      { dayOfWeek: 3, startTime: "19:00:00", endTime: "21:00:00" },
      { dayOfWeek: 5, startTime: "19:00:00", endTime: "21:00:00" },
    ],
  },
  {
    id: "mon-wed-fri-evening",
    label: "Tối Thứ 2, 4, 6 · 19:30–21:30",
    category: "EVENING",
    slots: [
      { dayOfWeek: 1, startTime: "19:30:00", endTime: "21:30:00" },
      { dayOfWeek: 3, startTime: "19:30:00", endTime: "21:30:00" },
      { dayOfWeek: 5, startTime: "19:30:00", endTime: "21:30:00" },
    ],
  },
  {
    id: "weekend-evening",
    label: "Tối Thứ 7 & Chủ Nhật · 19:00–21:00",
    category: "WEEKEND",
    slots: [
      { dayOfWeek: 6, startTime: "19:00:00", endTime: "21:00:00" },
      { dayOfWeek: 7, startTime: "19:00:00", endTime: "21:00:00" },
    ],
  },

  // --- BUỔI CHIỀU ---
  {
    id: "mon-wed-afternoon",
    label: "Chiều Thứ 2 & Thứ 4 · 14:00–16:00",
    category: "AFTERNOON",
    slots: [
      { dayOfWeek: 1, startTime: "14:00:00", endTime: "16:00:00" },
      { dayOfWeek: 3, startTime: "14:00:00", endTime: "16:00:00" },
    ],
  },
  {
    id: "tue-thu-afternoon",
    label: "Chiều Thứ 3 & Thứ 5 · 14:00–16:00",
    category: "AFTERNOON",
    slots: [
      { dayOfWeek: 2, startTime: "14:00:00", endTime: "16:00:00" },
      { dayOfWeek: 4, startTime: "14:00:00", endTime: "16:00:00" },
    ],
  },
  {
    id: "late-afternoon-mwf",
    label: "Cuối chiều Thứ 2, 4, 6 · 17:30–19:30",
    category: "AFTERNOON",
    slots: [
      { dayOfWeek: 1, startTime: "17:30:00", endTime: "19:30:00" },
      { dayOfWeek: 3, startTime: "17:30:00", endTime: "19:30:00" },
      { dayOfWeek: 5, startTime: "17:30:00", endTime: "19:30:00" },
    ],
  },
  {
    id: "weekend-afternoon",
    label: "Chiều Thứ 7 & Chủ Nhật · 14:00–16:00",
    category: "WEEKEND",
    slots: [
      { dayOfWeek: 6, startTime: "14:00:00", endTime: "16:00:00" },
      { dayOfWeek: 7, startTime: "14:00:00", endTime: "16:00:00" },
    ],
  },

  // --- BUỔI SÁNG ---
  {
    id: "weekend-morning",
    label: "Sáng Thứ 7 & Chủ Nhật · 08:00–10:00",
    category: "WEEKEND",
    slots: [
      { dayOfWeek: 6, startTime: "08:00:00", endTime: "10:00:00" },
      { dayOfWeek: 7, startTime: "08:00:00", endTime: "10:00:00" },
    ],
  },
  {
    id: "weekend-morning-late",
    label: "Sáng Thứ 7 & Chủ Nhật · 09:30–11:30",
    category: "WEEKEND",
    slots: [
      { dayOfWeek: 6, startTime: "09:30:00", endTime: "11:30:00" },
      { dayOfWeek: 7, startTime: "09:30:00", endTime: "11:30:00" },
    ],
  },
  {
    id: "tue-thu-morning",
    label: "Sáng Thứ 3 & Thứ 5 · 08:00–10:00",
    category: "MORNING",
    slots: [
      { dayOfWeek: 2, startTime: "08:00:00", endTime: "10:00:00" },
      { dayOfWeek: 4, startTime: "08:00:00", endTime: "10:00:00" },
    ],
  },
];

export function toMinutes(value: string): number {
  const [hours, minutes] = value.split(":").map(Number);
  return hours * 60 + minutes;
}

export function isValidTime(value: string): boolean {
  return /^([01]\d|2[0-3]):[0-5]\d(?::[0-5]\d)?$/.test(value);
}

export function normalizeAvailabilityTime(value: string): string {
  return value.length === 5 ? `${value}:00` : value;
}

export function sortAvailabilitySlots<T extends AvailabilitySlotLike>(slots: T[]): T[] {
  return [...slots].sort((left, right) => {
    if (left.dayOfWeek !== right.dayOfWeek) {
      return left.dayOfWeek - right.dayOfWeek;
    }
    return toMinutes(left.startTime) - toMinutes(right.startTime);
  });
}

export function validateAvailabilitySlots(
  slots: AvailabilitySlotLike[]
): AvailabilityValidationResult {
  const sortedSlots = sortAvailabilitySlots(slots);

  for (let index = 0; index < sortedSlots.length; index += 1) {
    const current = sortedSlots[index];

    if (!isValidTime(current.startTime) || !isValidTime(current.endTime)) {
      return {
        valid: false,
        message: "Vui lòng nhập đầy đủ giờ bắt đầu và giờ kết thúc.",
      };
    }

    const start = toMinutes(current.startTime);
    const end = toMinutes(current.endTime);

    if (end <= start) {
      return {
        valid: false,
        message: "Giờ kết thúc phải sau giờ bắt đầu.",
      };
    }

    const next = sortedSlots[index + 1];
    if (
      next &&
      next.dayOfWeek === current.dayOfWeek &&
      toMinutes(next.startTime) < end
    ) {
      return {
        valid: false,
        message: "Các khoảng giờ trong cùng một ngày không được chồng lấn.",
      };
    }
  }

  return { valid: true };
}

export function formatMatchingSlots(slots: AvailabilitySlotLike[]): string {
  if (slots.length === 0) return "Chưa chọn khung giờ";
  const sorted = sortAvailabilitySlots(slots);
  const first = sorted[0];
  const sameTime = sorted.every(
    (slot) => slot.startTime === first.startTime && slot.endTime === first.endTime
  );
  const days = sorted.map((slot) => DAY_LABELS[slot.dayOfWeek] || `Ngày ${slot.dayOfWeek}`).join(", ");
  if (!sameTime) {
    return sorted
      .map(
        (slot) =>
          `${DAY_LABELS[slot.dayOfWeek] || `Ngày ${slot.dayOfWeek}`} ${slot.startTime.slice(0, 5)}–${slot.endTime.slice(0, 5)}`
      )
      .join(" · ");
  }
  return `${days} · ${first.startTime.slice(0, 5)}–${first.endTime.slice(0, 5)}`;
}

/**
 * Chuyển đổi Date hoặc chuỗi ngày thành định dạng YYYY-MM-DD theo múi giờ địa phương (local timezone),
 * loại trừ triệt để lỗi lệch múi giờ (UTC shift rollback) do toISOString().slice(0, 10) gây ra.
 */
export function formatLocalDateToIso(date: Date | string | number | null | undefined): string {
  if (!date) return "";
  if (typeof date === "string" && /^\d{4}-\d{2}-\d{2}$/.test(date)) {
    return date;
  }
  const d = date instanceof Date ? date : new Date(date);
  if (Number.isNaN(d.getTime())) return "";
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/**
 * Định dạng thời gian cục bộ HH:mm từ Date hoặc chuỗi ISO / timestamp.
 */
export function formatLocalTime(value: Date | string | number | null | undefined): string {
  if (!value) return "";
  if (typeof value === "string") {
    const timeMatch = value.match(/^([01]?\d|2[0-3]):([0-5]\d)(?::[0-5]\d)?$/);
    if (timeMatch) {
      return `${timeMatch[1].padStart(2, "0")}:${timeMatch[2]}`;
    }
  }
  const d = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(d.getTime())) return "";
  const hours = String(d.getHours()).padStart(2, "0");
  const minutes = String(d.getMinutes()).padStart(2, "0");
  return `${hours}:${minutes}`;
}


/**
 * Hiển thị khoảng thời gian buổi học theo múi giờ địa phương, ví dụ: "18:00 - 19:30".
 * An toàn tuyệt đối với ca lẻ tiếng và múi giờ Việt Nam UTC+7.
 */
export function formatSessionTime(
  startAt: Date | string | number | null | undefined,
  endAt?: Date | string | number | null | undefined
): string {
  const startStr = formatLocalTime(startAt);
  if (!endAt) return startStr;
  const endStr = formatLocalTime(endAt);
  if (!startStr && !endStr) return "";
  if (!endStr) return startStr;
  if (!startStr) return endStr;
  return `${startStr} - ${endStr}`;
}

/**
 * Kiểm tra xem hai thời điểm có rơi vào cùng một ngày theo múi giờ địa phương hay không.
 */
export function isSameDay(
  date1: Date | string | number | null | undefined,
  date2: Date | string | number | null | undefined
): boolean {
  if (!date1 || !date2) return false;
  return formatLocalDateToIso(date1) === formatLocalDateToIso(date2);
}

