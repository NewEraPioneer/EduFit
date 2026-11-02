import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { AdminDashboardView } from "@/features/admin";
import { adminApi } from "@/features/admin/api/adminApi";

vi.mock("@/services/verification", () => ({
  verificationService: {
    getPendingQueue: vi.fn().mockResolvedValue({ totalElements: 8 }),
  },
}));

describe("AdminDashboardView Component Tests", () => {
  const mockUser = {
    fullName: "Quản trị viên EduFit",
    email: "admin@edufit.vn",
    role: "ADMIN",
  };

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("nạp số liệu thực tế từ API và hiển thị lên header cùng KPI grid", async () => {
    vi.spyOn(adminApi, "getDashboardStats").mockResolvedValue({
      totalUsers: 1248,
      totalTutors: 320,
      totalStudents: 750,
      totalParents: 170,
      activeClasses: 142,
      completedSessions: 580,
      pendingVerifications: 8,
    });

    render(<AdminDashboardView user={mockUser} />);

    await waitFor(() => {
      expect(
        screen.getByText(/Bảng điều hành Tổng thể Hệ thống EduFit/i)
      ).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(
        screen.getByText(/Toàn hệ thống hiện có 1,248 tài khoản, 142 lớp học đang diễn ra và 8 hồ sơ gia sư chờ duyệt./i)
      ).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByText("1,248")).toBeInTheDocument();
      expect(screen.getByText("142")).toBeInTheDocument();
      expect(screen.getByText("580")).toBeInTheDocument();
      expect(screen.getAllByText("8").length).toBeGreaterThanOrEqual(1);
    });

    // Xác nhận không còn thẻ Tranh chấp giả lập & GMV
    expect(screen.queryByText(/Tranh chấp & Khiếu nại cần can thiệp/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Doanh số nền tảng \(GMV\)/i)).not.toBeInTheDocument();
  });

  it("hiển thị các liên kết điều hướng trọng yếu tới Catalogs và Audit Logs", async () => {
    vi.spyOn(adminApi, "getDashboardStats").mockResolvedValue({
      totalUsers: 10,
      totalTutors: 2,
      totalStudents: 6,
      totalParents: 2,
      activeClasses: 3,
      completedSessions: 12,
      pendingVerifications: 0,
    });

    render(<AdminDashboardView user={mockUser} />);

    await waitFor(() => {
      const catalogLinks = screen.getAllByRole("link", { name: /Quản lý môn học/i });
      expect(catalogLinks.length).toBeGreaterThanOrEqual(1);
      expect(catalogLinks[0]).toHaveAttribute("href", "/admin/catalogs");

      const auditLinks = screen.getAllByRole("link", { name: /Nhật ký kiểm toán/i });
      expect(auditLinks.length).toBeGreaterThanOrEqual(1);
      expect(auditLinks[0]).toHaveAttribute("href", "/admin/audit-logs");
    });
  });
});
