import { fetchApi, ApiResponse } from "@/lib/api";
import type {
  TutorDiscoveryCard,
  TutorDetail,
  TutorSearchParams,
  SpringPage,
  TutorMatchRequest,
  TutorMatchResult,
} from "../types";

export const discoveryApi = {
  /**
   * Tìm kiếm gia sư công khai với bộ lọc đa tiêu chí và phân trang (GET /api/v1/discovery/tutors).
   * Có needId thì kết quả kèm độ khớp và mặc định xếp theo độ khớp.
   */
  async searchTutors(params: TutorSearchParams = {}): Promise<ApiResponse<SpringPage<TutorDiscoveryCard>>> {
    const searchParams = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
      if (value === undefined || value === null) continue;
      const text = typeof value === "string" ? value.trim() : String(value);
      if (text) searchParams.set(key, text);
    }

    const qs = searchParams.toString();
    return fetchApi<SpringPage<TutorDiscoveryCard>>(`/api/v1/discovery/tutors${qs ? `?${qs}` : ""}`, {
      method: "GET",
    });
  },

  /**
   * Lấy chi tiết hồ sơ công khai của gia sư đã xác minh KYC (GET /api/v1/discovery/tutors/{tutorId})
   */
  async getTutorDetail(tutorId: string): Promise<ApiResponse<TutorDetail>> {
    return fetchApi<TutorDetail>(`/api/v1/discovery/tutors/${encodeURIComponent(tutorId)}`, {
      method: "GET",
    });
  },

  /**
   * Độ khớp (✔/⚠ từng tiêu chí) của một gia sư với nhu cầu học, dùng trước khi gửi yêu cầu kết nối.
   */
  async getFit(needId: string, tutorId: string): Promise<ApiResponse<TutorDiscoveryCard>> {
    return fetchApi<TutorDiscoveryCard>(
      `/api/v1/discovery/needs/${encodeURIComponent(needId)}/tutors/${encodeURIComponent(tutorId)}/fit`,
      { method: "GET" }
    );
  },

  /**
   * Ghép gia sư theo nhu cầu học đã đủ thông tin (POST /api/v1/discovery/match)
   */
  async matchTutors(payload: TutorMatchRequest): Promise<ApiResponse<TutorMatchResult[]>> {
    return fetchApi<TutorMatchResult[]>("/api/v1/discovery/match", {
      method: "POST",
      body: JSON.stringify({ needId: payload.needId, topN: payload.topN ?? 5 }),
    });
  },
};
