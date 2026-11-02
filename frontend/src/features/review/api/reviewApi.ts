import { fetchApi, ApiResponse } from "@/lib/api";
import type {
  ReviewItem,
  TutorRatingSummary,
  CreateReviewPayload,
  UpdateReviewPayload,
  SpringPage,
} from "../types";

export const reviewApi = {
  /**
   * Lấy danh sách đánh giá của gia sư với phân trang (GET /api/v1/reviews/tutors/{tutorId})
   */
  async getTutorReviews(
    tutorId: string,
    page = 0,
    size = 10
  ): Promise<ApiResponse<SpringPage<ReviewItem>>> {
    const searchParams = new URLSearchParams({
      page: String(page),
      size: String(size),
    });
    return fetchApi<SpringPage<ReviewItem>>(
      `/api/v1/reviews/tutors/${encodeURIComponent(tutorId)}?${searchParams.toString()}`,
      { method: "GET" }
    );
  },

  /**
   * Lấy đánh giá của chính người dùng hiện tại đối với gia sư (nếu có)
   * GET /api/v1/reviews/tutors/{tutorId}/my-review
   */
  async getMyReview(tutorId: string): Promise<ApiResponse<ReviewItem | null>> {
    return fetchApi<ReviewItem | null>(
      `/api/v1/reviews/tutors/${encodeURIComponent(tutorId)}/my-review`,
      { method: "GET" }
    );
  },

  /**
   * Lấy tóm tắt điểm đánh giá và phân bổ sao của gia sư (GET /api/v1/reviews/tutors/{tutorId}/summary)
   */
  async getTutorRatingSummary(tutorId: string): Promise<ApiResponse<TutorRatingSummary>> {
    return fetchApi<TutorRatingSummary>(
      `/api/v1/reviews/tutors/${encodeURIComponent(tutorId)}/summary`,
      { method: "GET" }
    );
  },

  /**
   * Tạo đánh giá mới cho gia sư (POST /api/v1/reviews)
   */
  async createReview(payload: CreateReviewPayload): Promise<ApiResponse<ReviewItem>> {
    return fetchApi<ReviewItem>("/api/v1/reviews", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  /**
   * Chỉnh sửa đánh giá của chính mình (PUT /api/v1/reviews/{id})
   */
  async updateReview(
    reviewId: string,
    payload: UpdateReviewPayload
  ): Promise<ApiResponse<ReviewItem>> {
    return fetchApi<ReviewItem>(`/api/v1/reviews/${encodeURIComponent(reviewId)}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    });
  },
};

