import { describe, it, expect } from "vitest";
import { reviewFormSchema } from "@/features/review/schemas/reviewSchema";

describe("Review Form Schema Validation", () => {
  it("chấp nhận đánh giá hợp lệ từ 1 đến 5 sao và nhận xét từ 10 đến 1000 ký tự", () => {
    const validData = {
      rating: 5,
      comment: "Gia sư giảng bài rất dễ hiểu và tận tâm.",
    };
    const result = reviewFormSchema.safeParse(validData);
    expect(result.success).toBe(true);
  });

  it("từ chối đánh giá dưới 1 sao hoặc trên 5 sao", () => {
    const zeroStar = { rating: 0, comment: "Gia sư giảng bài rất dễ hiểu." };
    const sixStar = { rating: 6, comment: "Gia sư giảng bài rất dễ hiểu." };

    expect(reviewFormSchema.safeParse(zeroStar).success).toBe(false);
    expect(reviewFormSchema.safeParse(sixStar).success).toBe(false);
  });

  it("từ chối nhận xét ngắn hơn 10 ký tự", () => {
    const shortComment = { rating: 4, comment: "Quá ngắn" };
    const result = reviewFormSchema.safeParse(shortComment);
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues[0].message).toContain("ít nhất 10 ký tự");
    }
  });

  it("từ chối nhận xét vượt quá 1000 ký tự", () => {
    const longComment = { rating: 5, comment: "a".repeat(1001) };
    const result = reviewFormSchema.safeParse(longComment);
    expect(result.success).toBe(false);
  });

  it("chấp nhận danh sách nhãn nhận xét nhanh (quick tags) hợp lệ", () => {
    const validWithTags = {
      rating: 5,
      comment: "[Nhiệt tình, Dễ hiểu] Gia sư dạy rất dễ hiểu.",
      tags: ["Nhiệt tình", "Dễ hiểu"],
    };
    const result = reviewFormSchema.safeParse(validWithTags);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.tags).toEqual(["Nhiệt tình", "Dễ hiểu"]);
    }
  });
});
