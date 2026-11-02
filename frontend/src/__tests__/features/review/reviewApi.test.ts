import { describe, it, expect, vi, beforeEach } from "vitest";
import { reviewApi } from "@/features/review/api/reviewApi";
import * as apiModule from "@/lib/api";

describe("Review API Client Tests", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("getTutorReviews: gọi GET /api/v1/reviews/tutors/{tutorId} kèm phân trang", async () => {
    const fetchApiSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: {
        content: [],
        totalElements: 0,
        totalPages: 0,
        size: 10,
        number: 0,
        first: true,
        last: true,
        empty: true,
      },
      message: "OK",
    });

    const tutorId = "tut-123";
    const result = await reviewApi.getTutorReviews(tutorId, 1, 5);

    expect(fetchApiSpy).toHaveBeenCalledWith(
      "/api/v1/reviews/tutors/tut-123?page=1&size=5",
      { method: "GET" }
    );
    expect(result.success).toBe(true);
  });

  it("getMyReview: gọi GET /api/v1/reviews/tutors/{tutorId}/my-review", async () => {
    const mockReview = {
      reviewId: "rev-1",
      tutorId: "tut-1",
      studentId: "stu-1",
      reviewerName: "Em Minh",
      reviewerType: "STUDENT" as const,
      rating: 5,
      comment: "Thầy dạy rất tận tâm và dễ hiểu.",
      isEdited: false,
      completedSessionsAtReview: 5,
      isAuthor: true,
      createdAt: "2026-10-17T12:00:00Z",
      updatedAt: "2026-10-17T12:00:00Z",
    };

    const fetchApiSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: mockReview,
      message: "OK",
    });

    const result = await reviewApi.getMyReview("tut-1");

    expect(fetchApiSpy).toHaveBeenCalledWith(
      "/api/v1/reviews/tutors/tut-1/my-review",
      { method: "GET" }
    );
    expect(result.data?.rating).toBe(5);
  });

  it("getTutorRatingSummary: gọi GET /api/v1/reviews/tutors/{tutorId}/summary", async () => {
    const mockSummary = {
      tutorId: "tut-1",
      averageRating: 4.8,
      totalReviews: 25,
      ratingDistribution: { 5: 20, 4: 5 },
    };

    const fetchApiSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: mockSummary,
      message: "OK",
    });

    const result = await reviewApi.getTutorRatingSummary("tut-1");

    expect(fetchApiSpy).toHaveBeenCalledWith(
      "/api/v1/reviews/tutors/tut-1/summary",
      { method: "GET" }
    );
    expect(result.data?.averageRating).toBe(4.8);
    expect(result.data?.totalReviews).toBe(25);
  });

  it("createReview: gọi POST /api/v1/reviews với payload hợp lệ", async () => {
    const payload = {
      tutorId: "tut-1",
      studentId: "stu-1",
      rating: 5,
      comment: "[Nhiệt tình, Dễ hiểu] Gia sư giảng bài rất nhiệt tình và chu đáo.",
    };

    const fetchApiSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: {
        reviewId: "rev-new",
        ...payload,
        reviewerName: "Học sinh",
        reviewerType: "STUDENT" as const,
        isEdited: false,
        completedSessionsAtReview: 1,
        createdAt: "2026-10-17T12:00:00Z",
        updatedAt: "2026-10-17T12:00:00Z",
      },
      message: "Gửi đánh giá gia sư thành công",
    });

    const result = await reviewApi.createReview(payload);

    expect(fetchApiSpy).toHaveBeenCalledWith("/api/v1/reviews", {
      method: "POST",
      body: JSON.stringify(payload),
    });
    expect(result.success).toBe(true);
    expect(result.data?.reviewId).toBe("rev-new");
  });

  it("updateReview: gọi PUT /api/v1/reviews/{id} với payload cập nhật", async () => {
    const payload = {
      rating: 4,
      comment: "Cập nhật lại nhận xét: Gia sư rất đúng giờ và giảng dạy bài bản.",
    };

    const fetchApiSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: {
        reviewId: "rev-1",
        tutorId: "tut-1",
        studentId: "stu-1",
        rating: 4,
        comment: payload.comment,
        reviewerName: "Học sinh",
        reviewerType: "STUDENT" as const,
        isEdited: true,
        completedSessionsAtReview: 1,
        createdAt: "2026-10-17T12:00:00Z",
        updatedAt: "2026-10-17T12:30:00Z",
      },
      message: "Cập nhật đánh giá thành công",
    });

    const result = await reviewApi.updateReview("rev-1", payload);

    expect(fetchApiSpy).toHaveBeenCalledWith("/api/v1/reviews/rev-1", {
      method: "PUT",
      body: JSON.stringify(payload),
    });
    expect(result.success).toBe(true);
    expect(result.data?.isEdited).toBe(true);
  });
});
