import { describe, expect, it } from "vitest";
import {
  DAY_LABELS,
  DAYS_OF_WEEK,
  formatMatchingSlots,
  normalizeAvailabilityTime,
  SLOT_PRESETS,
  sortAvailabilitySlots,
  validateAvailabilitySlots,
  formatLocalDateToIso,
  formatSessionTime,
} from "@/shared/lib/dateTime";


describe("shared/lib/dateTime", () => {
  it("exports valid day of week constants", () => {
    expect(DAYS_OF_WEEK).toHaveLength(7);
    expect(DAY_LABELS[1]).toBe("Thứ 2");
    expect(DAY_LABELS[7]).toBe("Chủ Nhật");
  });

  it("validates built-in slot presets", () => {
    expect(SLOT_PRESETS.length).toBeGreaterThanOrEqual(3);
    for (const preset of SLOT_PRESETS) {
      expect(preset.id).toBeTruthy();
      expect(preset.label).toBeTruthy();
      const validation = validateAvailabilitySlots(preset.slots);
      expect(validation.valid).toBe(true);
    }
  });

  it("sorts availability slots by day of week and start time", () => {
    const slots = [
      { dayOfWeek: 5, startTime: "20:00:00", endTime: "21:00:00" },
      { dayOfWeek: 2, startTime: "19:00:00", endTime: "21:00:00" },
      { dayOfWeek: 2, startTime: "08:00:00", endTime: "10:00:00" },
    ];
    const sorted = sortAvailabilitySlots(slots);
    expect(sorted[0]).toEqual({ dayOfWeek: 2, startTime: "08:00:00", endTime: "10:00:00" });
    expect(sorted[1]).toEqual({ dayOfWeek: 2, startTime: "19:00:00", endTime: "21:00:00" });
    expect(sorted[2]).toEqual({ dayOfWeek: 5, startTime: "20:00:00", endTime: "21:00:00" });
  });

  it("detects invalid time or overlapping slots in validation", () => {
    expect(
      validateAvailabilitySlots([{ dayOfWeek: 1, startTime: "10:00", endTime: "09:00" }]).valid
    ).toBe(false);

    expect(
      validateAvailabilitySlots([
        { dayOfWeek: 1, startTime: "08:00", endTime: "10:00" },
        { dayOfWeek: 1, startTime: "09:00", endTime: "11:00" },
      ]).valid
    ).toBe(false);

    expect(
      validateAvailabilitySlots([
        { dayOfWeek: 1, startTime: "08:00", endTime: "10:00" },
        { dayOfWeek: 1, startTime: "10:00", endTime: "12:00" },
      ]).valid
    ).toBe(true);
  });

  it("formats matching slots for display", () => {
    expect(formatMatchingSlots([])).toBe("Chưa chọn khung giờ");

    // Same time across days
    expect(
      formatMatchingSlots([
        { dayOfWeek: 2, startTime: "19:00:00", endTime: "21:00:00" },
        { dayOfWeek: 5, startTime: "19:00:00", endTime: "21:00:00" },
      ])
    ).toBe("Thứ 3, Thứ 6 · 19:00–21:00");

    // Different times across days
    expect(
      formatMatchingSlots([
        { dayOfWeek: 2, startTime: "08:00:00", endTime: "10:00:00" },
        { dayOfWeek: 5, startTime: "19:00:00", endTime: "21:00:00" },
      ])
    ).toBe("Thứ 3 08:00–10:00 · Thứ 6 19:00–21:00");

  });

  it("normalizes availability time strings", () => {
    expect(normalizeAvailabilityTime("08:00")).toBe("08:00:00");
    expect(normalizeAvailabilityTime("19:30:00")).toBe("19:30:00");
  });

  it("formats local date to ISO YYYY-MM-DD safely without UTC shift", () => {
    const d = new Date(2026, 9, 19, 0, 0, 0); // Oct 19, 2026 local
    expect(formatLocalDateToIso(d)).toBe("2026-10-19");
    expect(formatLocalDateToIso("2026-10-19")).toBe("2026-10-19");
    expect(formatLocalDateToIso(null)).toBe("");
  });

  it("formats session time range cleanly for non-hour durations", () => {
    expect(formatSessionTime("18:00:00", "19:30:00")).toBe("18:00 - 19:30");
    expect(formatSessionTime("08:00", "09:30")).toBe("08:00 - 09:30");
    expect(formatSessionTime("18:00")).toBe("18:00");
    expect(formatSessionTime(null)).toBe("");
  });
});

