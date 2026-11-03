import { describe, it, expect } from "vitest";
import { formatGoalTitle } from "@/features/progress";

describe("formatGoalTitle", () => {
  it("giữ nguyên tiêu đề dễ đọc và cắt khoảng trắng thừa", () => {
    expect(formatGoalTitle("Lớp học")).toBe("Lớp học");
    expect(formatGoalTitle("  Toán lớp 9  ")).toBe("Toán lớp 9");
  });

  it("trả null khi không có tiêu đề", () => {
    expect(formatGoalTitle(null)).toBeNull();
    expect(formatGoalTitle(undefined)).toBeNull();
    expect(formatGoalTitle("   ")).toBeNull();
  });
});
