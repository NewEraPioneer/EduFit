import React from "react";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { AdminAuditLogsView } from "@/features/admin";
import type { PagedAuditLogs } from "@/features/admin";
import { adminApi } from "@/features/admin/api/adminApi";

describe("AdminAuditLogsView Component Tests", () => {
  const mockLogs: PagedAuditLogs = {
    items: [
      {
        id: "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
        actorUserId: "admin-user-id",
        actor: "admin@edufit.vn",
        action: "TUTOR_VERIFICATION_REVOKED",
        targetType: "TUTOR_PROFILE",
        targetId: "tutor-123",
        reason: "Phát hiện chứng chỉ giả mạo bằng cấp",
        metadata: { ip: "127.0.0.1" },
        createdAt: "2026-10-18T15:30:00Z",
      },
      {
        id: "b2c3d4e5-f6a7-8901-bcde-f12345678901",
        actorUserId: "admin-user-id",
        actor: "admin@edufit.vn",
        action: "SUBJECT_STATUS_UPDATED",
        targetType: "SUBJECT",
        targetId: "1",
        reason: "Subject status changed to false",
        metadata: {},
        createdAt: "2026-10-18T16:00:00Z",
      },
    ],
    page: 0,
    size: 20,
    totalElements: 2,
    totalPages: 1,
  };

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("hiển thị danh sách nhật ký kiểm toán với các badge hành động tương ứng", async () => {
    vi.spyOn(adminApi, "getAuditLogs").mockResolvedValue(mockLogs);

    render(<AdminAuditLogsView />);

    await waitFor(() => {
      expect(screen.getByText("Nhật ký Kiểm toán Hệ thống")).toBeInTheDocument();
      expect(screen.getByText("TUTOR_VERIFICATION_REVOKED")).toBeInTheDocument();
      expect(screen.getByText("SUBJECT_STATUS_UPDATED")).toBeInTheDocument();
    });

    expect(screen.getByText(/Phát hiện chứng chỉ giả mạo bằng cấp/i)).toBeInTheDocument();
    expect(screen.getAllByText("admin@edufit.vn").length).toBeGreaterThanOrEqual(1);
  });

  it("lọc nhật ký theo nút bấm nhanh", async () => {
    vi.spyOn(adminApi, "getAuditLogs").mockResolvedValue(mockLogs);

    render(<AdminAuditLogsView />);

    await waitFor(() => {
      expect(screen.getByText("TUTOR_VERIFICATION_REVOKED")).toBeInTheDocument();
    });

    const filterButton = screen.getByRole("button", { name: /Thu hồi KYC \(REVOKED\)/i });
    fireEvent.click(filterButton);

    await waitFor(() => {
      expect(adminApi.getAuditLogs).toHaveBeenCalledWith(
        expect.objectContaining({
          action: "TUTOR_VERIFICATION_REVOKED",
        })
      );
    });
  });

  it("mở Modal xem chi tiết bản ghi kiểm toán", async () => {
    vi.spyOn(adminApi, "getAuditLogs").mockResolvedValue(mockLogs);

    render(<AdminAuditLogsView />);

    await waitFor(() => {
      expect(screen.getByText("TUTOR_VERIFICATION_REVOKED")).toBeInTheDocument();
    });

    const viewButtons = screen.getAllByRole("button", { name: /Xem/i });
    fireEvent.click(viewButtons[0]);

    await waitFor(() => {
      expect(screen.getByText("Chi tiết bản ghi kiểm toán")).toBeInTheDocument();
      expect(screen.getByText("a1b2c3d4-e5f6-7890-abcd-ef1234567890")).toBeInTheDocument();
    });
  });
});
