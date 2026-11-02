import { describe, it, expect } from "vitest";
import {
  proposeSessionSchema,
  rescheduleSessionSchema,
  cancelSessionSchema,
} from "@/features/scheduling/schemas/schedulingSchemas";

describe("Scheduling Validation Schemas", () => {
  describe("proposeSessionSchema", () => {
    const validData = {
      classId: "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
      tutorId: "6ba7b811-9dad-11d1-80b4-00c04fd430c8",
      studentId: "6ba7b812-9dad-11d1-80b4-00c04fd430c8",
      startAt: "2026-10-20T08:00:00.000Z",
      endAt: "2026-10-20T10:00:00.000Z",
      mode: "ONLINE" as const,
      placeOrLink: "https://meet.google.com/abc-xyz",
      repeatNote: "Thứ 3 hàng tuần",
      message: "Nhớ chuẩn bị bài tập",
    };

    it("pass với dữ liệu hợp lệ", () => {
      const result = proposeSessionSchema.safeParse(validData);
      expect(result.success).toBe(true);
    });

    it("fails nếu classId không đúng định dạng UUID", () => {
      const result = proposeSessionSchema.safeParse({
        ...validData,
        classId: "invalid-uuid",
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toContain("Vui lòng chọn lớp học hợp lệ");
      }
    });

    it("fails nếu endAt diễn ra trước hoặc bằng startAt", () => {
      const result = proposeSessionSchema.safeParse({
        ...validData,
        startAt: "2026-10-20T10:00:00.000Z",
        endAt: "2026-10-20T09:00:00.000Z",
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toContain("Thời gian kết thúc phải diễn ra sau");
      }
    });

    it("fails nếu mode không phải ONLINE hoặc OFFLINE", () => {
      const result = proposeSessionSchema.safeParse({
        ...validData,
        mode: "HYBRID",
      });
      expect(result.success).toBe(false);
    });
  });

  describe("rescheduleSessionSchema", () => {
    it("pass với thời gian mới hợp lệ", () => {
      const result = rescheduleSessionSchema.safeParse({
        newStartAt: "2026-10-22T08:00:00.000Z",
        newEndAt: "2026-10-22T10:00:00.000Z",
        reason: "Bận việc gia đình",
      });
      expect(result.success).toBe(true);
    });

    it("fails nếu newEndAt trước newStartAt", () => {
      const result = rescheduleSessionSchema.safeParse({
        newStartAt: "2026-10-22T10:00:00.000Z",
        newEndAt: "2026-10-22T08:00:00.000Z",
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toContain("Thời gian kết thúc mới phải sau");
      }
    });
  });

  describe("cancelSessionSchema", () => {
    it("pass với lý do hủy hợp lệ", () => {
      const result = cancelSessionSchema.safeParse({
        reason: "SCHEDULE_CONFLICT",
        comment: "Trùng ca kiểm tra",
      });
      expect(result.success).toBe(true);
    });

    it("fails nếu lý do hủy không thuộc enum định nghĩa", () => {
      const result = cancelSessionSchema.safeParse({
        reason: "TIRED",
      });
      expect(result.success).toBe(false);
    });
  });
});
