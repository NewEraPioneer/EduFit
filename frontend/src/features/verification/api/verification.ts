import { apiClient } from "@/lib/api";
import type {
  VerificationRequestDetail,
  PendingQueueResponse,
  SubmitVerificationPayload,
} from "@/services/verification";

export type VerificationAction = "APPROVE" | "REJECT";

export interface ReviewVerificationRequest {
  action: VerificationAction;
  reason?: string;
}

/**
 * Type-Safe Verification API Client matching Backend contracts 1:1
 * Backend endpoint: /api/v1/admin/verifications/${requestId}/review
 * Backend request DTO: ReviewVerificationRequest { action: APPROVE|REJECT, reason }
 */
export const verificationApi = {
  /**
   * Duyệt hoặc từ chối hồ sơ xác minh KYC (ADMIN)
   * Gửi payload { action: "APPROVE" | "REJECT", reason?: string }
   */
  async reviewVerification(
    requestId: string,
    actionOrPayload:
      | VerificationAction
      | "APPROVED"
      | "REJECTED"
      | ReviewVerificationRequest,
    reason?: string
  ): Promise<VerificationRequestDetail> {
    let action: VerificationAction;
    let resolvedReason: string | undefined;

    if (typeof actionOrPayload === "object" && actionOrPayload !== null) {
      action = actionOrPayload.action;
      resolvedReason = actionOrPayload.reason;
    } else {
      action =
        actionOrPayload === "APPROVE" || actionOrPayload === "APPROVED"
          ? "APPROVE"
          : "REJECT";
      resolvedReason = reason;
    }

    const payload: ReviewVerificationRequest = {
      action,
      reason: resolvedReason?.trim() || undefined,
    };

    const res = await apiClient.post<VerificationRequestDetail>(
      `/api/v1/admin/verifications/${requestId}/review`,
      payload
    );
    return res.data as VerificationRequestDetail;
  },

  /**
   * Lấy chi tiết hồ sơ xác minh KYC (ADMIN)
   */
  async getVerificationDetail(requestId: string): Promise<VerificationRequestDetail> {
    const res = await apiClient.get<VerificationRequestDetail>(
      `/api/v1/admin/verifications/${requestId}`
    );
    return res.data as VerificationRequestDetail;
  },

  /**
   * Lấy danh sách hồ sơ xác minh theo trạng thái (ADMIN)
   * GET /api/v1/admin/verifications?status=...&page=...&size=...
   */
  async getVerifications(
    status?: string,
    page = 0,
    size = 20
  ): Promise<PendingQueueResponse> {
    const params = new URLSearchParams();
    if (status && status !== "ALL") {
      params.append("status", status);
    }
    params.append("page", String(page));
    params.append("size", String(size));

    const res = await apiClient.get<PendingQueueResponse>(
      `/api/v1/admin/verifications?${params.toString()}`
    );
    return res.data as PendingQueueResponse;
  },

  /**
   * Thu hồi bằng cấp / phê duyệt của gia sư (ADMIN)
   * POST /api/v1/admin/verifications/${requestId}/revoke
   * Gửi payload { reason }
   */
  async revokeVerification(
    requestId: string,
    reason: string
  ): Promise<VerificationRequestDetail> {
    const res = await apiClient.post<VerificationRequestDetail>(
      `/api/v1/admin/verifications/${requestId}/revoke`,
      { reason: reason.trim() }
    );
    return res.data as VerificationRequestDetail;
  },

  /**
   * Lấy hàng đợi hồ sơ KYC chờ duyệt (ADMIN - backward compatible)
   */
  async getPendingQueue(page = 0, size = 20): Promise<PendingQueueResponse> {
    return this.getVerifications("PENDING", page, size);
  },

  /**
   * Lấy trạng thái hồ sơ KYC của gia sư hiện tại (TUTOR)
   */
  async getMyRequest(): Promise<VerificationRequestDetail | null> {
    try {
      const res = await apiClient.get<VerificationRequestDetail>("/api/v1/verifications/my-request");
      return (res.data || null) as VerificationRequestDetail | null;
    } catch {
      return null;
    }
  },

  /**
   * Nộp hồ sơ KYC kèm tệp tin thật (TUTOR)
   */
  async submitVerification(
    payload: SubmitVerificationPayload,
    files: File[]
  ): Promise<VerificationRequestDetail> {
    const formData = new FormData();
    const payloadBlob = new Blob([JSON.stringify(payload)], { type: "application/json" });
    formData.append("payload", payloadBlob);

    files.forEach((file) => {
      formData.append("files", file);
    });

    const res = await apiClient.post<VerificationRequestDetail>("/api/v1/verifications", formData);
    return res.data as VerificationRequestDetail;
  },

  /**
   * Tạo URL xem/tải trực tiếp file bằng cấp
   */
  getCredentialFileUrl(fileId: string): string {
    return `/api/v1/verifications/credentials/files/${fileId}`;
  },
};
