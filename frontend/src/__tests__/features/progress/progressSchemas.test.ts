import { describe, it, expect } from "vitest";
import {
  createMilestoneItemSchema,
  createPlanSchema,
  updateMilestoneStatusSchema,
  recordSessionOutcomeSchema,
} from "@/features/progress/schemas/progressSchemas";

describe("Progress Zod Schemas Validation", () => {
  describe("createMilestoneItemSchema", () => {
    it("hợp lệ khi truyền tiêu đề hợp lệ và trọng số từ 1 đến 5", () => {
      const valid = {
        title: "Nắm vững phương trình bậc 2",
        description: "Giải bài tập SGK trang 45",
        targetDate: "2026-10-30",
        weight: 3,
      };
      const res = createMilestoneItemSchema.safeParse(valid);
      expect(res.success).toBe(true);
      if (res.success) {
        expect(res.data.weight).toBe(3);
      }
    });

    it("mặc định trọng số là 1 nếu không truyền", () => {
      const res = createMilestoneItemSchema.safeParse({
        title: "Cột mốc cơ bản",
      });
      expect(res.success).toBe(true);
      if (res.success) {
        expect(res.data.weight).toBe(1);
      }
    });

    it("báo lỗi khi tiêu đề để trống hoặc chỉ có khoảng trắng", () => {
      const res = createMilestoneItemSchema.safeParse({
        title: "   ",
        weight: 1,
      });
      expect(res.success).toBe(false);
      if (!res.success) {
        expect(res.error.issues[0]?.message).toContain("Tiêu đề cột mốc không được để trống");
      }
    });

    it("báo lỗi khi tiêu đề vượt quá 200 ký tự", () => {
      const res = createMilestoneItemSchema.safeParse({
        title: "A".repeat(201),
        weight: 1,
      });
      expect(res.success).toBe(false);
      if (!res.success) {
        expect(res.error.issues[0]?.message).toContain("tối đa 200 ký tự");
      }
    });

    it("báo lỗi khi trọng số nhỏ hơn 1 hoặc lớn hơn 5", () => {
      const resLow = createMilestoneItemSchema.safeParse({
        title: "Mốc",
        weight: 0,
      });
      expect(resLow.success).toBe(false);

      const resHigh = createMilestoneItemSchema.safeParse({
        title: "Mốc",
        weight: 6,
      });
      expect(resHigh.success).toBe(false);
    });

    it("báo lỗi khi trọng số không phải số nguyên", () => {
      const res = createMilestoneItemSchema.safeParse({
        title: "Mốc",
        weight: 2.5,
      });
      expect(res.success).toBe(false);
    });
  });

  describe("createPlanSchema", () => {
    const validClassId = "11111111-1111-1111-1111-111111111111";

    it("hợp lệ khi có classId UUID và từ 1 đến 20 cột mốc", () => {
      const res = createPlanSchema.safeParse({
        classId: validClassId,
        summary: "Kế hoạch học tập 1:1",
        milestones: [
          { title: "Mốc 1", weight: 2 },
          { title: "Mốc 2", weight: 3 },
        ],
      });
      expect(res.success).toBe(true);
    });

    it("báo lỗi khi classId không phải định dạng UUID", () => {
      const res = createPlanSchema.safeParse({
        classId: "not-a-uuid",
        milestones: [{ title: "Mốc 1" }],
      });
      expect(res.success).toBe(false);
      if (!res.success) {
        expect(res.error.issues[0]?.message).toContain("Mã lớp học không hợp lệ");
      }
    });

    it("báo lỗi khi danh sách milestones rỗng (0 mốc)", () => {
      const res = createPlanSchema.safeParse({
        classId: validClassId,
        milestones: [],
      });
      expect(res.success).toBe(false);
      if (!res.success) {
        expect(res.error.issues[0]?.message).toContain("ít nhất 1 cột mốc");
      }
    });

    it("báo lỗi khi danh sách milestones vượt quá 20 mốc", () => {
      const milestones = Array.from({ length: 21 }, (_, i) => ({
        title: `Mốc ${i + 1}`,
      }));
      const res = createPlanSchema.safeParse({
        classId: validClassId,
        milestones,
      });
      expect(res.success).toBe(false);
      if (!res.success) {
        expect(res.error.issues[0]?.message).toContain("tối đa 20 cột mốc");
      }
    });
  });

  describe("updateMilestoneStatusSchema", () => {
    it("hợp lệ với các trạng thái NOT_STARTED, IN_PROGRESS, ACHIEVED", () => {
      expect(
        updateMilestoneStatusSchema.safeParse({
          version: 1,
          status: "NOT_STARTED",
        }).success
      ).toBe(true);

      expect(
        updateMilestoneStatusSchema.safeParse({
          version: 1,
          status: "IN_PROGRESS",
        }).success
      ).toBe(true);

      expect(
        updateMilestoneStatusSchema.safeParse({
          version: 1,
          status: "ACHIEVED",
          achievedDate: "2026-10-17",
        }).success
      ).toBe(true);
    });

    it("báo lỗi với trạng thái không xác định", () => {
      const res = updateMilestoneStatusSchema.safeParse({
        version: 1,
        status: "INVALID_STATUS",
      });
      expect(res.success).toBe(false);
    });
  });

  describe("recordSessionOutcomeSchema", () => {
    it("hợp lệ khi chọn outcome COMPLETED hoặc ABSENT", () => {
      const resCompleted = recordSessionOutcomeSchema.safeParse({
        outcome: "COMPLETED",
        noteContent: "Buổi học diễn ra tốt đẹp",
      });
      expect(resCompleted.success).toBe(true);

      const resAbsent = recordSessionOutcomeSchema.safeParse({
        outcome: "ABSENT",
        noteContent: "Học sinh báo bận đột xuất",
      });
      expect(resAbsent.success).toBe(true);
    });

    it("báo lỗi khi outcome không hợp lệ", () => {
      const res = recordSessionOutcomeSchema.safeParse({
        outcome: "UNKNOWN",
      });
      expect(res.success).toBe(false);
    });

    it("báo lỗi khi noteContent vượt quá 5000 ký tự", () => {
      const res = recordSessionOutcomeSchema.safeParse({
        outcome: "COMPLETED",
        noteContent: "A".repeat(5001),
      });
      expect(res.success).toBe(false);
      if (!res.success) {
        expect(res.error.issues[0]?.message).toContain("tối đa 5000 ký tự");
      }
    });
  });
});
