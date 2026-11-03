import { describe, it, expect } from "vitest";
import {
  createMilestoneItemSchema,
  createPlanSchema,
  updateMilestoneStatusSchema,
  recordSessionOutcomeSchema,
  sessionFeedbackSchema,
  planTemplateSchema,
} from "@/features/progress/schemas/progressSchemas";

describe("Progress Zod Schemas Validation", () => {
  describe("createMilestoneItemSchema", () => {
    it("hợp lệ khi truyền tiêu đề hợp lệ và trọng số từ 1 đến 5", () => {
      const valid = {
        title: "Nắm vững phương trình bậc 2",
        description: "Giải bài tập SGK trang 45",
        targetDate: "2026-10-30",
        weight: 3,
        criteria: ["Tiêu chí 1"],
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
        criteria: ["Tiêu chí 1"],
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

    const roadmap = (count: number, withFinalDate = true) =>
      Array.from({ length: count }, (_, i) => ({
        title: `Mốc ${i + 1}`,
        weight: 2,
        criteria: ["Tiêu chí 1"],
        targetDate: withFinalDate && i === count - 1 ? "2026-12-20" : undefined,
      }));

    it("hợp lệ với 3–8 cột mốc và cột mốc cuối có hạn", () => {
      expect(createPlanSchema.safeParse({ classId: validClassId, summary: "Lộ trình", milestones: roadmap(3) }).success).toBe(true);
      expect(createPlanSchema.safeParse({ classId: validClassId, milestones: roadmap(8) }).success).toBe(true);
    });

    it("báo lỗi khi classId không phải định dạng UUID", () => {
      const res = createPlanSchema.safeParse({ classId: "not-a-uuid", milestones: roadmap(3) });
      expect(res.success).toBe(false);
      if (!res.success) {
        expect(res.error.issues[0]?.message).toContain("Mã lớp học không hợp lệ");
      }
    });

    it("báo lỗi khi ít hơn 3 hoặc nhiều hơn 8 cột mốc", () => {
      const short = createPlanSchema.safeParse({ classId: validClassId, milestones: roadmap(2) });
      expect(short.success).toBe(false);
      if (!short.success) expect(short.error.issues[0]?.message).toContain("ít nhất 3 cột mốc");
      const long = createPlanSchema.safeParse({ classId: validClassId, milestones: roadmap(9) });
      expect(long.success).toBe(false);
      if (!long.success) expect(long.error.issues[0]?.message).toContain("tối đa 8 cột mốc");
    });

    it("báo lỗi khi cột mốc cuối chưa có hạn", () => {
      const res = createPlanSchema.safeParse({ classId: validClassId, milestones: roadmap(3, false) });
      expect(res.success).toBe(false);
      if (!res.success) expect(res.error.issues[0]?.message).toContain("Cột mốc cuối");
    });
  });

  describe("planTemplateSchema", () => {
    const item = (suggestedSessions: number) => ({ title: "Mốc", weight: 1, suggestedSessions, criteria: ["Tiêu chí"] });

    it("mẫu cần tên, 3–8 cột mốc, mỗi cột mốc 2–6 buổi", () => {
      expect(planTemplateSchema.safeParse({ title: "Mẫu", milestones: [item(2), item(4), item(6)] }).success).toBe(true);
      expect(planTemplateSchema.safeParse({ title: " ", milestones: [item(2), item(4), item(6)] }).success).toBe(false);
      expect(planTemplateSchema.safeParse({ title: "Mẫu", milestones: [item(2), item(4)] }).success).toBe(false);
      expect(planTemplateSchema.safeParse({ title: "Mẫu", milestones: [item(1), item(4), item(7)] }).success).toBe(false);
    });
  });

  describe("updateMilestoneStatusSchema", () => {
    it("chỉ cho chốt ACHIEVED hoặc mở lại IN_PROGRESS kèm lý do", () => {
      expect(updateMilestoneStatusSchema.safeParse({ version: 1, status: "NOT_STARTED" }).success).toBe(false);
      expect(updateMilestoneStatusSchema.safeParse({ version: 1, status: "IN_PROGRESS" }).success).toBe(false);
      expect(
        updateMilestoneStatusSchema.safeParse({
          version: 1,
          status: "IN_PROGRESS",
          reason: "Con chưa vững dạng khó",
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

    it("báo lỗi khi nhận xét vượt quá 140 ký tự", () => {
      expect(recordSessionOutcomeSchema.safeParse({ outcome: "COMPLETED", noteContent: "A".repeat(140) }).success).toBe(true);
      const res = recordSessionOutcomeSchema.safeParse({
        outcome: "COMPLETED",
        noteContent: "A".repeat(141),
      });
      expect(res.success).toBe(false);
      if (!res.success) {
        expect(res.error.issues[0]?.message).toContain("tối đa 140 ký tự");
      }
    });
  });

  describe("createMilestoneItemSchema — tiêu chí đạt", () => {
    it("bắt buộc 1–4 tiêu chí, không để trống", () => {
      expect(createMilestoneItemSchema.safeParse({ title: "Mốc", criteria: [] }).success).toBe(false);
      expect(createMilestoneItemSchema.safeParse({ title: "Mốc", criteria: ["  "] }).success).toBe(false);
      expect(createMilestoneItemSchema.safeParse({ title: "Mốc", criteria: ["a", "b", "c", "d", "e"] }).success).toBe(false);
    });
  });

  describe("sessionFeedbackSchema", () => {
    const milestoneId = "22222222-2222-2222-2222-222222222222";
    const taught = { milestoneId, level: 3, checkedCriterionIds: [], totalCriteria: 2, markAchieved: false };

    it("hợp lệ với mức tự lập và chip việc về nhà", () => {
      expect(
        sessionFeedbackSchema.safeParse({ homeworkStatus: "NONE", attitudeChips: [], milestones: [taught] }).success
      ).toBe(true);
    });

    it("bắt buộc ít nhất một cột mốc, mức tự lập và chip việc về nhà", () => {
      expect(sessionFeedbackSchema.safeParse({ homeworkStatus: "NONE", attitudeChips: [], milestones: [] }).success).toBe(false);
      expect(
        sessionFeedbackSchema.safeParse({ attitudeChips: [], milestones: [taught] }).success
      ).toBe(false);
      expect(
        sessionFeedbackSchema.safeParse({
          homeworkStatus: "NONE",
          attitudeChips: [],
          milestones: [{ ...taught, level: undefined }],
        }).success
      ).toBe(false);
    });

    it("đúng x/y phải nhập đủ cả hai và x ≤ y; chốt đạt cần tick đủ tiêu chí", () => {
      const parse = (patch: object) =>
        sessionFeedbackSchema.safeParse({ homeworkStatus: "NONE", attitudeChips: [], milestones: [{ ...taught, ...patch }] });
      expect(parse({ correctCount: 7 }).success).toBe(false);
      expect(parse({ correctCount: 11, totalCount: 10 }).success).toBe(false);
      expect(parse({ correctCount: 7, totalCount: 10 }).success).toBe(true);
      expect(parse({ markAchieved: true, checkedCriterionIds: ["c1"] }).success).toBe(false);
      expect(parse({ markAchieved: true, checkedCriterionIds: ["c1", "c2"] }).success).toBe(true);
    });
  });
});
