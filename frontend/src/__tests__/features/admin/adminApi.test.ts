import { describe, it, expect, vi, beforeEach } from "vitest";
import { adminApi } from "@/features/admin/api/adminApi";
import { apiClient } from "@/lib/api";

describe("adminApi Client Unit Tests", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  describe("getDashboardStats", () => {
    it("gọi GET /api/v1/admin/dashboard/stats và chuẩn hóa pendingVerifications", async () => {
      vi.spyOn(apiClient, "get").mockResolvedValueOnce({
        success: true,
        data: {
          totalUsers: 1248,
          totalTutors: 320,
          totalStudents: 750,
          totalParents: 170,
          activeClasses: 142,
          completedSessions: 580,
          pendingKyc: 8,
        },
      });

      const stats = await adminApi.getDashboardStats();

      expect(apiClient.get).toHaveBeenCalledWith("/api/v1/admin/dashboard/stats");
      expect(stats.totalUsers).toBe(1248);
      expect(stats.totalTutors).toBe(320);
      expect(stats.totalStudents).toBe(750);
      expect(stats.totalParents).toBe(170);
      expect(stats.activeClasses).toBe(142);
      expect(stats.completedSessions).toBe(580);
      expect(stats.pendingVerifications).toBe(8);
    });

    it("fallback về số 0 khi data rỗng", async () => {
      vi.spyOn(apiClient, "get").mockResolvedValueOnce({
        success: true,
        data: undefined,
      });

      const stats = await adminApi.getDashboardStats();

      expect(stats.totalUsers).toBe(0);
      expect(stats.pendingVerifications).toBe(0);
    });
  });

  describe("getSubjects & createSubject & toggleSubjectStatus", () => {
    it("getSubjects gọi GET /api/v1/admin/catalogs/subjects", async () => {
      const mockList = [
        {
          id: 1,
          name: "Toán học",
          code: "MATH",
          slug: "toan-hoc",
          category: "Khoa học Tự nhiên",
          description: "Môn toán phổ thông",
          isActive: true,
          createdAt: "2026-10-18T10:00:00Z",
        },
      ];
      vi.spyOn(apiClient, "get").mockResolvedValueOnce({
        success: true,
        data: mockList,
      });

      const subjects = await adminApi.getSubjects();

      expect(apiClient.get).toHaveBeenCalledWith("/api/v1/admin/catalogs/subjects");
      expect(subjects).toHaveLength(1);
      expect(subjects[0].name).toBe("Toán học");
      expect(subjects[0].isActive).toBe(true);
    });

    it("createSubject gửi payload POST /api/v1/admin/catalogs/subjects", async () => {
      const payload = {
        name: "Vật lý 12",
        category: "Khoa học Tự nhiên",
        code: "PHYS_12",
      };
      vi.spyOn(apiClient, "post").mockResolvedValueOnce({
        success: true,
        data: {
          id: 2,
          ...payload,
          slug: "vat-ly-12",
          description: "",
          isActive: true,
          createdAt: "2026-10-18T12:00:00Z",
        },
      });

      const res = await adminApi.createSubject(payload);

      expect(apiClient.post).toHaveBeenCalledWith("/api/v1/admin/catalogs/subjects", payload);
      expect(res.id).toBe(2);
      expect(res.name).toBe("Vật lý 12");
    });

    it("toggleSubjectStatus gọi PATCH /api/v1/admin/catalogs/subjects/:id/toggle-status", async () => {
      vi.spyOn(apiClient, "patch").mockResolvedValueOnce({
        success: true,
        data: {
          id: 1,
          name: "Toán học",
          isActive: false,
        },
      });

      const res = await adminApi.toggleSubjectStatus(1);

      expect(apiClient.patch).toHaveBeenCalledWith(
        "/api/v1/admin/catalogs/subjects/1/toggle-status"
      );
      expect(res.isActive).toBe(false);
    });
  });

  describe("getAuditLogs", () => {
    it("gọi GET /api/v1/admin/audit-logs với params phân trang và action filter", async () => {
      vi.spyOn(apiClient, "get").mockResolvedValueOnce({
        success: true,
        data: {
          items: [
            {
              id: "audit-1",
              actorUserId: "admin-id",
              action: "TUTOR_VERIFICATION_REVOKED",
              reason: "Bằng cấp giả mạo",
              createdAt: "2026-10-18T14:00:00Z",
            },
          ],
          page: 1,
          size: 10,
          totalElements: 15,
          totalPages: 2,
        },
      });

      const logs = await adminApi.getAuditLogs({
        page: 1,
        size: 10,
        action: "TUTOR_VERIFICATION_REVOKED",
      });

      expect(apiClient.get).toHaveBeenCalledWith(
        "/api/v1/admin/audit-logs?page=1&size=10&action=TUTOR_VERIFICATION_REVOKED"
      );
      expect(logs.items).toHaveLength(1);
      expect(logs.items[0].action).toBe("TUTOR_VERIFICATION_REVOKED");
      expect(logs.totalElements).toBe(15);
    });
  });

  describe("getParentStudentLinks & revokeParentStudentLink", () => {
    it("gọi admin links API và thu hồi link theo UC3.3", async () => {
      vi.spyOn(apiClient, "get").mockResolvedValueOnce({
        success: true,
        data: [{ linkId: "link-1", parentEmail: "parent@test.com", status: "CONFIRMED" }],
      });
      vi.spyOn(apiClient, "delete").mockResolvedValueOnce({
        success: true,
      });

      const links = await adminApi.getParentStudentLinks("parent@test.com");
      expect(links).toHaveLength(1);

      await adminApi.revokeParentStudentLink("link-1");
      expect(apiClient.delete).toHaveBeenCalledWith("/api/v1/connections/parent-student/links/link-1");
    });
  });
});
