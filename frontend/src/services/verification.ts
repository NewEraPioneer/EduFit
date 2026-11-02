import { apiClient, ApiError } from "@/lib/api";
import type { TutorType } from "@/services/profile";

export type VerificationStatus = "DRAFT" | "PENDING" | "APPROVED" | "VERIFIED" | "REJECTED";

export interface CredentialFileResponse {
  fileId: string;
  fileName: string;
  storageKey: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
}

export type CredentialType =
  | "DEGREE"
  | "CERTIFICATE"
  | "TEACHING_LICENCE"
  | "STUDENT_CARD"
  | "OTHER"
  | "IDENTITY_CARD";

export interface CredentialResponse {
  credentialId: string;
  type: CredentialType | string;
  institution: string;
  year?: number | null;
  note?: string;
  /** Gói dạy mà giấy tờ này chứng minh (mục tiêu cần duyệt minh chứng). */
  offeringId?: string | null;
  files: CredentialFileResponse[];
}

export interface VerificationRequestDetail {
  requestId: string;
  tutorId: string;
  status: VerificationStatus;
  rejectionReason?: string;
  submittedAt?: string;
  reviewedBy?: string;
  reviewedAt?: string;
  credentials: CredentialResponse[];
}

export interface PendingQueueItem {
  requestId: string;
  id?: string;
  tutorId: string;
  status: string;
  submittedAt: string;
  tutorName?: string | null;
  tutorEmail?: string | null;
  email?: string | null;
  credentialType?: string | null;
  institution?: string | null;
  year?: number | null;
  documentUrls?: string[];
  rejectionReason?: string | null;
  reviewedAt?: string | null;
  reviewerId?: string | null;
  reviewedBy?: string | null;
}

export interface PendingQueueResponse {
  items: PendingQueueItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SubmitVerificationPayload {
  credentials: Array<{
    type: CredentialType | string;
    institution: string;
    year?: number | null;
    note?: string;
    fileIndexes: number[];
    /** Gói dạy mà giấy tờ này chứng minh (cho mục tiêu cần duyệt minh chứng như HSG, IELTS). */
    offeringId?: string | null;
  }>;
}

export const verificationService = {
  /**
   * Lấy trạng thái hồ sơ xác minh mới nhất của gia sư (TUTOR)
   */
  async getMyRequest(): Promise<VerificationRequestDetail | null> {
    try {
      const res = await apiClient.get<VerificationRequestDetail>("/api/v1/verifications/my-request");
      return (res.data || null) as VerificationRequestDetail | null;
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        return null;
      }
      throw error;
    }
  },

  /**
   * Nộp hồ sơ xác minh KYC kèm tệp tin thật (TUTOR)
   */
  async submitVerification(payload: SubmitVerificationPayload, files: File[]): Promise<VerificationRequestDetail> {
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
   * Lấy danh sách hồ sơ xác minh KYC theo trạng thái (ADMIN)
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
   * Lấy hàng đợi hồ sơ KYC đang chờ duyệt (ADMIN - backward compatible)
   */
  async getPendingQueue(page = 0, size = 20): Promise<PendingQueueResponse> {
    return this.getVerifications("PENDING", page, size);
  },

  /**
   * Thu hồi bằng cấp / phê duyệt hồ sơ KYC của gia sư (ADMIN)
   * POST /api/v1/admin/verifications/${requestId}/revoke
   * Payload: { reason } (min 5 chars)
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
   * Duyệt hoặc từ chối hồ sơ xác minh của gia sư (ADMIN)
   * Gửi { action, reason?, confirmedTutorType? } khớp ReviewVerificationRequest backend.
   * confirmedTutorType: loại gia sư admin xác nhận theo giấy tờ khi duyệt.
   */
  async reviewVerification(
    requestId: string,
    actionOrDecision: "APPROVE" | "REJECT" | "APPROVED" | "REJECTED",
    reasonOrRejectionReason?: string,
    confirmedTutorType?: TutorType | null
  ): Promise<VerificationRequestDetail> {
    const action: "APPROVE" | "REJECT" =
      actionOrDecision === "APPROVE" || actionOrDecision === "APPROVED"
        ? "APPROVE"
        : "REJECT";
    const reason = reasonOrRejectionReason?.trim() || undefined;

    const res = await apiClient.post<VerificationRequestDetail>(
      `/api/v1/admin/verifications/${requestId}/review`,
      {
        action,
        reason,
        confirmedTutorType: action === "APPROVE" ? confirmedTutorType || undefined : undefined,
      }
    );
    return res.data as VerificationRequestDetail;
  },

  /**
   * Lấy chi tiết hồ sơ xác minh KYC bao gồm danh sách bằng cấp và file (ADMIN)
   */
  async getVerificationDetail(requestId: string): Promise<VerificationRequestDetail> {
    const res = await apiClient.get<VerificationRequestDetail>(
      `/api/v1/admin/verifications/${requestId}`
    );
    return res.data as VerificationRequestDetail;
  },

  /**
   * Tạo URL xem/tải trực tiếp file bằng cấp trên trình duyệt (ADMIN / TUTOR)
   */
  getCredentialFileUrl(fileId: string): string {
    return `/api/v1/verifications/credentials/files/${fileId}`;
  },
};
