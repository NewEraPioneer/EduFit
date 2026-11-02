import { describe, it, expect } from "vitest";
import { calculateWeightedProgress } from "@/features/progress/utils/progressCalculator";

describe("ProgressCalculator - Weighted Average Formula", () => {
  it("trả về null nếu danh sách milestones rỗng hoặc null", () => {
    expect(calculateWeightedProgress([])).toBeNull();
    expect(calculateWeightedProgress(null)).toBeNull();
    expect(calculateWeightedProgress(undefined)).toBeNull();
  });

  it("trả về 0% nếu chưa có cột mốc nào đạt trạng thái ACHIEVED", () => {
    const milestones = [
      { weight: 1, status: "NOT_STARTED" },
      { weight: 3, status: "IN_PROGRESS" },
    ];
    expect(calculateWeightedProgress(milestones)).toBe(0);
  });

  it("trả về 100% nếu tất cả các cột mốc active đều đạt trạng thái ACHIEVED", () => {
    const milestones = [
      { weight: 2, status: "ACHIEVED" },
      { weight: 3, status: "ACHIEVED" },
      { weight: 5, status: "ACHIEVED" },
    ];
    expect(calculateWeightedProgress(milestones)).toBe(100);
  });

  it("tính bình quân gia quyền chính xác theo công thức Σ weight(ACHIEVED) / Σ weight(ACTIVE) * 100", () => {
    // Tổng weight: 1 + 2 + 3 + 4 = 10
    // Achieved weight: mốc 2 (weight 2) + mốc 4 (weight 4) = 6
    // Progress: 6 / 10 * 100 = 60%
    const milestones = [
      { weight: 1, status: "NOT_STARTED" },
      { weight: 2, status: "ACHIEVED" },
      { weight: 3, status: "IN_PROGRESS" },
      { weight: 4, status: "ACHIEVED" },
    ];
    expect(calculateWeightedProgress(milestones)).toBe(60);
  });

  it("làm tròn HALF_UP đúng quy chuẩn (ví dụ 1/3 = 33%, 2/3 = 67%)", () => {
    // 1 / 3 = 33.333% -> 33%
    const m1 = [
      { weight: 1, status: "ACHIEVED" },
      { weight: 2, status: "NOT_STARTED" },
    ];
    expect(calculateWeightedProgress(m1)).toBe(33);

    // 2 / 3 = 66.666% -> 67%
    const m2 = [
      { weight: 2, status: "ACHIEVED" },
      { weight: 1, status: "NOT_STARTED" },
    ];
    expect(calculateWeightedProgress(m2)).toBe(67);

    // 1 / 2 = 50%
    const m3 = [
      { weight: 1, status: "ACHIEVED" },
      { weight: 1, status: "NOT_STARTED" },
    ];
    expect(calculateWeightedProgress(m3)).toBe(50);
  });

  it("bỏ qua các cột mốc đã bị lưu trữ (archived) khỏi cả tử số và mẫu số", () => {
    const milestones = [
      { weight: 5, status: "ACHIEVED", isArchived: true }, // Không tính
      { weight: 2, status: "ACHIEVED", isArchived: false }, // Tính
      { weight: 2, status: "NOT_STARTED", isArchived: false }, // Tính
    ];
    // Active weight: 2 + 2 = 4
    // Active achieved: 2
    // 2 / 4 * 100 = 50%
    expect(calculateWeightedProgress(milestones)).toBe(50);
  });

  it("nếu tất cả milestones đều bị archived, trả về null", () => {
    const milestones = [
      { weight: 5, status: "ACHIEVED", isArchived: true },
      { weight: 2, status: "NOT_STARTED", isArchived: true },
    ];
    expect(calculateWeightedProgress(milestones)).toBeNull();
  });

  it("hỗ trợ cả trạng thái COMPLETED tương đương ACHIEVED", () => {
    const milestones = [
      { weight: 3, status: "COMPLETED" },
      { weight: 3, status: "NOT_STARTED" },
    ];
    expect(calculateWeightedProgress(milestones)).toBe(50);
  });
});
