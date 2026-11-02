import React from "react";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { AdminLinksView } from "@/features/admin";
import { adminApi } from "@/features/admin/api/adminApi";

describe("AdminLinksView Component Tests", () => {
  const mockLinks = [
    {
      linkId: "link-abc-123",
      parentId: "parent-uuid-1",
      parentEmail: "parent1@test.com",
      parentName: "Nguyễn Văn Phụ Huynh",
      studentId: "student-uuid-1",
      studentEmail: "student1@test.com",
      studentName: "Nguyễn Văn Học Sinh",
      status: "CONFIRMED",
      createdAt: "2026-10-18T10:00:00Z",
    },
  ];

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("hiển thị danh sách liên kết gia đình và thông điệp chuẩn UC3.3", async () => {
    vi.spyOn(adminApi, "getParentStudentLinks").mockResolvedValue(mockLinks);

    render(<AdminLinksView />);

    await waitFor(() => {
      expect(
        screen.getByText("Giám sát & Thu hồi Liên kết Gia đình (UC3.3)")
      ).toBeInTheDocument();
      expect(screen.getByText("Nguyễn Văn Phụ Huynh")).toBeInTheDocument();
      expect(screen.getByText("Nguyễn Văn Học Sinh")).toBeInTheDocument();
      expect(screen.getByText("CONFIRMED")).toBeInTheDocument();
    });

    expect(screen.getByText(/Quy chuẩn nghiệp vụ UC3.3 & BR-38/i)).toBeInTheDocument();
  });

  it("mở Modal xác nhận và thực hiện thu hồi liên kết theo UC3.3", async () => {
    vi.spyOn(adminApi, "getParentStudentLinks").mockResolvedValue(mockLinks);
    vi.spyOn(adminApi, "revokeParentStudentLink").mockResolvedValue(undefined);

    render(<AdminLinksView />);

    await waitFor(() => {
      expect(screen.getByText("Nguyễn Văn Phụ Huynh")).toBeInTheDocument();
    });

    const revokeButton = screen.getByRole("button", { name: /Thu hồi/i });
    fireEvent.click(revokeButton);

    expect(
      screen.getByText(/Xác nhận thu hồi liên kết gia đình\?/i)
    ).toBeInTheDocument();

    const confirmButton = screen.getByRole("button", { name: /Xác nhận thu hồi/i });
    fireEvent.click(confirmButton);

    await waitFor(() => {
      expect(adminApi.revokeParentStudentLink).toHaveBeenCalledWith("link-abc-123");
    });
  });
});
