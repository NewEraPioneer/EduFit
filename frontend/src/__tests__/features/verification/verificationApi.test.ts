import { describe, it, expect, vi, beforeEach } from "vitest";
import { verificationApi } from "@/features/verification/api/verification";
import { verificationService } from "@/services/verification";
import { apiClient } from "@/lib/api";

vi.mock("@/lib/api", () => ({
  apiClient: {
    post: vi.fn(),
    get: vi.fn(),
  },
  ApiError: class ApiError extends Error {
    status: number;
    constructor(message: string, status: number) {
      super(message);
      this.status = status;
    }
  },
}));

describe("Verification API Contract Tests (/api/v1/admin/verifications/{id}/review)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("verificationApi.reviewVerification", () => {
    it("gửi đúng payload { action: 'APPROVE', reason: undefined } khi duyệt hồ sơ", async () => {
      vi.mocked(apiClient.post).mockResolvedValueOnce({
        success: true,
        data: {
          requestId: "req-123",
          status: "APPROVED",
        },
      });

      const result = await verificationApi.reviewVerification("req-123", {
        action: "APPROVE",
      });

      expect(apiClient.post).toHaveBeenCalledWith(
        "/api/v1/admin/verifications/req-123/review",
        {
          action: "APPROVE",
          reason: undefined,
        }
      );
      expect(result.status).toBe("APPROVED");
    });

    it("gửi đúng payload { action: 'REJECT', reason: '...' } khi từ chối hồ sơ", async () => {
      vi.mocked(apiClient.post).mockResolvedValueOnce({
        success: true,
        data: {
          requestId: "req-456",
          status: "REJECTED",
        },
      });

      const result = await verificationApi.reviewVerification("req-456", {
        action: "REJECT",
        reason: "Ảnh chụp bằng cấp bị mờ, không nhìn rõ số hiệu",
      });

      expect(apiClient.post).toHaveBeenCalledWith(
        "/api/v1/admin/verifications/req-456/review",
        {
          action: "REJECT",
          reason: "Ảnh chụp bằng cấp bị mờ, không nhìn rõ số hiệu",
        }
      );
      expect(result.status).toBe("REJECTED");
    });
  });

  describe("verificationService.reviewVerification backward compatibility", () => {
    it("chuẩn hóa 'APPROVED' thành { action: 'APPROVE' }", async () => {
      vi.mocked(apiClient.post).mockResolvedValueOnce({
        success: true,
        data: {
          requestId: "req-789",
          status: "APPROVED",
        },
      });

      await verificationService.reviewVerification("req-789", "APPROVED");

      expect(apiClient.post).toHaveBeenCalledWith(
        "/api/v1/admin/verifications/req-789/review",
        {
          action: "APPROVE",
          reason: undefined,
        }
      );
    });

    it("chuẩn hóa 'REJECTED' thành { action: 'REJECT', reason: '...' }", async () => {
      vi.mocked(apiClient.post).mockResolvedValueOnce({
        success: true,
        data: {
          requestId: "req-789",
          status: "REJECTED",
        },
      });

      await verificationService.reviewVerification(
        "req-789",
        "REJECTED",
        "Bằng cấp chưa được công chứng"
      );

      expect(apiClient.post).toHaveBeenCalledWith(
        "/api/v1/admin/verifications/req-789/review",
        {
          action: "REJECT",
          reason: "Bằng cấp chưa được công chứng",
        }
      );
    });
  });

  describe("verificationApi.revokeVerification & verificationService.revokeVerification", () => {
    it("gửi đúng POST /api/v1/admin/verifications/{id}/revoke với payload { reason }", async () => {
      vi.mocked(apiClient.post).mockResolvedValueOnce({
        success: true,
        data: {
          requestId: "req-revoke-1",
          status: "REJECTED",
          rejectionReason: "[REVOKED] Phát hiện bằng cấp giả mạo",
        },
      });

      const res = await verificationApi.revokeVerification(
        "req-revoke-1",
        "  Phát hiện bằng cấp giả mạo  "
      );

      expect(apiClient.post).toHaveBeenCalledWith(
        "/api/v1/admin/verifications/req-revoke-1/revoke",
        {
          reason: "Phát hiện bằng cấp giả mạo",
        }
      );
      expect(res.status).toBe("REJECTED");
    });

    it("verificationService.revokeVerification gọi API với id và reason đã trim", async () => {
      vi.mocked(apiClient.post).mockResolvedValueOnce({
        success: true,
        data: {
          requestId: "req-revoke-2",
          status: "REJECTED",
        },
      });

      await verificationService.revokeVerification(
        "req-revoke-2",
        "Chứng chỉ hết hạn hiệu lực"
      );

      expect(apiClient.post).toHaveBeenCalledWith(
        "/api/v1/admin/verifications/req-revoke-2/revoke",
        {
          reason: "Chứng chỉ hết hạn hiệu lực",
        }
      );
    });
  });

  describe("verificationApi.getVerifications & verificationService.getVerifications", () => {
    it("gọi GET /api/v1/admin/verifications với query params status, page, size", async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce({
        success: true,
        data: {
          items: [
            {
              requestId: "req-app-1",
              tutorId: "tutor-1",
              status: "APPROVED",
              tutorName: "Nguyễn Văn A",
              institution: "ĐH Quốc Gia",
            },
          ],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        },
      });

      const res = await verificationApi.getVerifications("APPROVED", 0, 20);

      expect(apiClient.get).toHaveBeenCalledWith(
        "/api/v1/admin/verifications?status=APPROVED&page=0&size=20"
      );
      expect(res.items.length).toBe(1);
      expect(res.items[0].status).toBe("APPROVED");
    });

    it("verificationService.getVerifications hỗ trợ status=ALL không chèn param status", async () => {
      vi.mocked(apiClient.get).mockResolvedValueOnce({
        success: true,
        data: {
          items: [],
          page: 0,
          size: 10,
          totalElements: 0,
          totalPages: 0,
        },
      });

      await verificationService.getVerifications("ALL", 0, 10);

      expect(apiClient.get).toHaveBeenCalledWith(
        "/api/v1/admin/verifications?page=0&size=10"
      );
    });
  });
});

