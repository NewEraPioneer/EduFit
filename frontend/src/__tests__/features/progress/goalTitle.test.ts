import { describe, it, expect } from "vitest";
import { formatGoalTitle } from "@/features/progress";
import { GOAL_TYPE_OPTIONS } from "@/features/discovery/utils/matchingNeed";

describe("formatGoalTitle", () => {
  it.each(GOAL_TYPE_OPTIONS)("đổi mã $value sang nhãn tiếng Việt", ({ value, label }) => {
    expect(formatGoalTitle(`Mục tiêu ${value}`)).toBe(label);
  });

  it("giữ nguyên tiêu đề không chứa mã enum", () => {
    expect(formatGoalTitle("Lớp học")).toBe("Lớp học");
  });

  it("trả null khi không có tiêu đề", () => {
    expect(formatGoalTitle(null)).toBeNull();
    expect(formatGoalTitle(undefined)).toBeNull();
  });
});
