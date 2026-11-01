import { describe, it, expect } from "vitest";
import {
  tutorSearchFilterSchema,
  tutorMatchRequestSchema,
} from "@/features/discovery/schemas/discoverySchemas";

describe("Discovery Schemas & Validation Tests", () => {
  describe("tutorSearchFilterSchema", () => {
    it("chấp nhận bộ lọc tìm kiếm hợp lệ với đầy đủ tham số", () => {
      const validFilter = {
        subjectId: 1,
        levelId: 9,
        area: "Cầu Giấy, Hà Nội",
        mode: "ONLINE",
        minPrice: 200000,
        maxPrice: 500000,
        minRating: 4.5,
        keyword: "Toán hình",
        dayOfWeek: 2,
        availableFrom: "18:00",
        availableTo: "20:00",
        page: 0,
        size: 10,
        sortBy: "rating",
        sortDirection: "desc",
      };

      const result = tutorSearchFilterSchema.safeParse(validFilter);
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.subjectId).toBe(1);
        expect(result.data.mode).toBe("ONLINE");
      }
    });

    it("áp dụng các giá trị mặc định cho phân trang và sắp xếp", () => {
      const result = tutorSearchFilterSchema.safeParse({});
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.page).toBe(0);
        expect(result.data.size).toBe(10);
        // Mặc định xếp theo độ liên quan (độ khớp khi tìm kèm nhu cầu học)
        expect(result.data.sortBy).toBe("relevance");
        expect(result.data.sortDirection).toBe("desc");
      }
    });

    it("từ chối khi học phí tối thiểu lớn hơn học phí tối đa", () => {
      const invalidPrice = {
        minPrice: 500000,
        maxPrice: 200000,
      };

      const result = tutorSearchFilterSchema.safeParse(invalidPrice);
      expect(result.success).toBe(false);
      if (!result.success) {
        const issues = result.error.issues;
        expect(issues.some((i) => i.message.includes("không được lớn hơn"))).toBe(true);
      }
    });

    it("từ chối khi định dạng giờ không đúng chuẩn HH:mm", () => {
      const invalidTime = {
        availableFrom: "25:70",
      };

      const result = tutorSearchFilterSchema.safeParse(invalidTime);
      expect(result.success).toBe(false);
    });
  });

  describe("tutorMatchRequestSchema", () => {
    it("chấp nhận yêu cầu matching với UUID hợp lệ", () => {
      const validReq = {
        needId: "123e4567-e89b-12d3-a456-426614174000",
        topN: 5,
      };

      const result = tutorMatchRequestSchema.safeParse(validReq);
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.topN).toBe(5);
      }
    });

    it("mặc định topN = 5 nếu không truyền", () => {
      const validReq = {
        needId: "123e4567-e89b-12d3-a456-426614174000",
      };

      const result = tutorMatchRequestSchema.safeParse(validReq);
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.topN).toBe(5);
      }
    });

    it("từ chối khi needId không phải định dạng UUID", () => {
      const invalidReq = {
        needId: "invalid-uuid-string",
      };

      const result = tutorMatchRequestSchema.safeParse(invalidReq);
      expect(result.success).toBe(false);
    });

    it("từ chối khi topN vượt quá 10", () => {
      const invalidReq = {
        needId: "123e4567-e89b-12d3-a456-426614174000",
        topN: 15,
      };

      const result = tutorMatchRequestSchema.safeParse(invalidReq);
      expect(result.success).toBe(false);
    });
  });
});
