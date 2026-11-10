import { describe, expect, it } from "vitest";
import {
  buildMonthGrid,
  buildTimeOptions,
  formatDuration,
  matchTimeOptions,
  parseDateInput,
  parseTimeInput,
} from "@/shared/lib/fieldParsers";

const NOW = new Date(2026, 10, 9);

describe("parseTimeInput", () => {
  it.each([
    ["8", "08:00"],
    ["08", "08:00"],
    ["8:30", "08:30"],
    ["8h30", "08:30"],
    ["8h", "08:00"],
    ["830", "08:30"],
    ["1430", "14:30"],
    ["14.30", "14:30"],
  ])("đọc %s thành %s", (raw, expected) => {
    expect(parseTimeInput(raw)).toBe(expected);
  });

  it.each(["25", "8:75", "abc", ""])("từ chối %s", (raw) => {
    expect(parseTimeInput(raw)).toBeNull();
  });
});

describe("danh sách giờ", () => {
  it("bước 30 phút có 48 mốc", () => {
    expect(buildTimeOptions(30)).toHaveLength(48);
  });

  it("lọc theo số đã gõ", () => {
    const options = buildTimeOptions(30);
    expect(matchTimeOptions(options, "14")).toEqual(["14:00", "14:30"]);
    expect(matchTimeOptions(options, "83")).toEqual(["08:30"]);
  });

  it("ghi thời lượng dễ đọc", () => {
    expect(formatDuration("08:00", "08:30")).toBe("30 phút");
    expect(formatDuration("08:00", "09:00")).toBe("1 giờ");
    expect(formatDuration("08:00", "09:30")).toBe("1 giờ 30 phút");
  });
});

describe("parseDateInput", () => {
  it.each([
    ["15/11/2026", "2026-11-15"],
    ["15-11-26", "2026-11-15"],
    ["15112026", "2026-11-15"],
    ["2026-11-15", "2026-11-15"],
    ["15/11", "2026-11-15"],
    ["1511", "2026-11-15"],
  ])("đọc %s thành %s", (raw, expected) => {
    expect(parseDateInput(raw, NOW)).toBe(expected);
  });

  it("thiếu năm mà ngày đã qua thì lấy năm sau", () => {
    expect(parseDateInput("01/02", NOW)).toBe("2027-02-01");
  });

  it.each(["31/02/2026", "45/11", "abc"])("từ chối %s", (raw) => {
    expect(parseDateInput(raw, NOW)).toBeNull();
  });
});

describe("buildMonthGrid", () => {
  it("bắt đầu từ thứ Hai và đủ 42 ô", () => {
    const grid = buildMonthGrid(2026, 11);
    expect(grid).toHaveLength(42);
    expect(grid[0]).toBe("2026-10-26");
    expect(grid).toContain("2026-11-30");
  });
});
