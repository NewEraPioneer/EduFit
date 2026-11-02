import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import {
  UserStatusBadge,
  UserRoleBadge,
  LearnersManagementView,
  TutorsManagementView,
  adminUserApi,
} from "@/features/admin";

// Mock adminUserApi
vi.mock("@/features/admin/api/adminUserApi", () => ({
  adminUserApi: {
    getUsers: vi.fn(),
    getUserStats: vi.fn(),
    getUserById: vi.fn(),
    lockUser: vi.fn(),
    unlockUser: vi.fn(),
    updateStatus: vi.fn(),
  },
}));

describe("Admin User Management Unit & Integration Tests", () => {
  beforeEach(() => {
    vi.clearAllMocks();

    vi.mocked(adminUserApi.getUserStats).mockResolvedValue({
      success: true,
      data: {
        totalUsers: 10,
        totalStudents: 5,
        totalTutors: 3,
        totalParents: 2,
        totalActive: 8,
        totalDeactivated: 2,
        activeStudents: 4,
        deactivatedStudents: 1,
        activeParents: 2,
        deactivatedParents: 0,
        activeTutors: 2,
        deactivatedTutors: 1,
      },
    });
  });

  describe("UserStatusBadge", () => {
    it("hiển thị nhãn Hoạt động khi status là ACTIVE", () => {
      render(<UserStatusBadge status="ACTIVE" />);
      expect(screen.getByText("Hoạt động")).toBeInTheDocument();
    });

    it("hiển thị nhãn Đã khóa khi status là DEACTIVATED", () => {
      render(<UserStatusBadge status="DEACTIVATED" />);
      expect(screen.getByText("Đã khóa")).toBeInTheDocument();
    });
  });

  describe("UserRoleBadge", () => {
    it("hiển thị đúng các vai trò người dùng", () => {
      const { rerender } = render(<UserRoleBadge role="STUDENT" />);
      expect(screen.getByText("Học sinh")).toBeInTheDocument();

      rerender(<UserRoleBadge role="PARENT" />);
      expect(screen.getByText("Phụ huynh")).toBeInTheDocument();

      rerender(<UserRoleBadge role="TUTOR" />);
      expect(screen.getByText("Gia sư")).toBeInTheDocument();

      rerender(<UserRoleBadge role="ADMIN" />);
      expect(screen.getByText("Quản trị viên")).toBeInTheDocument();
    });
  });

  describe("LearnersManagementView", () => {
    it("render tiêu đề, thẻ thống kê và danh sách học sinh / phụ huynh", async () => {
      vi.mocked(adminUserApi.getUsers).mockResolvedValue({
        success: true,
        data: {
          users: [
            {
              id: "student-1",
              email: "student@edufit.vn",
              fullName: "Lê Văn Học",
              role: "STUDENT",
              status: "ACTIVE",
              avatarUrl: null,
              createdAt: "2026-10-01T10:00:00Z",
              lastLoginAt: null,
            },
            {
              id: "parent-1",
              email: "parent@edufit.vn",
              fullName: "Trần Thị Mẹ",
              role: "PARENT",
              status: "DEACTIVATED",
              avatarUrl: null,
              createdAt: "2026-10-02T10:00:00Z",
              lastLoginAt: null,
            },
          ],
          totalElements: 2,
          totalPages: 1,
          page: 0,
          size: 15,
        },
      });

      render(<LearnersManagementView />);

      expect(screen.getByText("Học Sinh & Phụ Huynh")).toBeInTheDocument();

      // Đợi danh sách tải xong
      await waitFor(() => {
        expect(screen.getByText("Lê Văn Học")).toBeInTheDocument();
        expect(screen.getByText("Trần Thị Mẹ")).toBeInTheDocument();
      });

      // Kiểm tra email
      expect(screen.getByText("student@edufit.vn")).toBeInTheDocument();
      expect(screen.getByText("parent@edufit.vn")).toBeInTheDocument();

      // Kiểm tra nút Khóa và Mở khóa
      expect(screen.getByRole("button", { name: "Khóa tài khoản Lê Văn Học" })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Mở khóa tài khoản Trần Thị Mẹ" })).toBeInTheDocument();
    });

    it("mở modal và thực hiện khóa tài khoản học sinh", async () => {
      vi.mocked(adminUserApi.getUsers).mockResolvedValue({
        success: true,
        data: {
          users: [
            {
              id: "student-1",
              email: "student@edufit.vn",
              fullName: "Lê Văn Học",
              role: "STUDENT",
              status: "ACTIVE",
              avatarUrl: null,
              createdAt: "2026-10-01T10:00:00Z",
              lastLoginAt: null,
            },
          ],
          totalElements: 1,
          totalPages: 1,
          page: 0,
          size: 15,
        },
      });

      vi.mocked(adminUserApi.lockUser).mockResolvedValue({
        success: true,
        data: {
          id: "student-1",
          email: "student@edufit.vn",
          fullName: "Lê Văn Học",
          role: "STUDENT",
          status: "DEACTIVATED",
          createdAt: "2026-10-01T10:00:00Z",
        },
      });

      render(<LearnersManagementView />);

      await waitFor(() => {
        expect(screen.getByText("Lê Văn Học")).toBeInTheDocument();
      });

      // Bấm nút khóa
      const lockBtn = screen.getByRole("button", { name: "Khóa tài khoản Lê Văn Học" });
      fireEvent.click(lockBtn);

      // Modal hiển thị
      expect(screen.getByText("Khóa tài khoản người dùng")).toBeInTheDocument();
      expect(screen.getByText("Lý do khóa tài khoản")).toBeInTheDocument();

      // Bấm xác nhận khóa
      const confirmBtn = screen.getByText("Xác nhận khóa");
      fireEvent.click(confirmBtn);

      await waitFor(() => {
        expect(adminUserApi.lockUser).toHaveBeenCalledWith(
          "student-1",
          expect.stringContaining("Khóa tài khoản")
        );
      });
    });

    it("mở modal và thực hiện mở khóa tài khoản phụ huynh", async () => {
      vi.mocked(adminUserApi.getUsers).mockResolvedValue({
        success: true,
        data: {
          users: [
            {
              id: "parent-1",
              email: "parent@edufit.vn",
              fullName: "Trần Thị Mẹ",
              role: "PARENT",
              status: "DEACTIVATED",
              avatarUrl: null,
              createdAt: "2026-10-02T10:00:00Z",
              lastLoginAt: null,
            },
          ],
          totalElements: 1,
          totalPages: 1,
          page: 0,
          size: 15,
        },
      });

      vi.mocked(adminUserApi.unlockUser).mockResolvedValue({
        success: true,
        data: {
          id: "parent-1",
          email: "parent@edufit.vn",
          fullName: "Trần Thị Mẹ",
          role: "PARENT",
          status: "ACTIVE",
          createdAt: "2026-10-02T10:00:00Z",
        },
      });

      render(<LearnersManagementView />);

      await waitFor(() => {
        expect(screen.getByText("Trần Thị Mẹ")).toBeInTheDocument();
      });

      // Bấm nút mở khóa
      const unlockBtn = screen.getByRole("button", { name: "Mở khóa tài khoản Trần Thị Mẹ" });
      fireEvent.click(unlockBtn);

      // Modal hiển thị
      expect(screen.getByText("Mở khóa tài khoản")).toBeInTheDocument();

      // Bấm xác nhận mở khóa
      const confirmBtn = screen.getByText("Xác nhận mở khóa");
      fireEvent.click(confirmBtn);

      await waitFor(() => {
        expect(adminUserApi.unlockUser).toHaveBeenCalledWith("parent-1");
      });
    });
  });

  describe("TutorsManagementView", () => {
    it("render danh sách gia sư và nút liên kết tới KYC", async () => {
      vi.mocked(adminUserApi.getUsers).mockResolvedValue({
        success: true,
        data: {
          users: [
            {
              id: "tutor-1",
              email: "tutor@edufit.vn",
              fullName: "Nguyễn Gia Sư",
              role: "TUTOR",
              status: "ACTIVE",
              avatarUrl: null,
              createdAt: "2026-10-01T10:00:00Z",
              lastLoginAt: null,
            },
          ],
          totalElements: 1,
          totalPages: 1,
          page: 0,
          size: 15,
        },
      });

      render(<TutorsManagementView />);

      expect(screen.getByText("Danh Sách Gia Sư")).toBeInTheDocument();
      expect(screen.getByText("Duyệt bằng cấp KYC")).toBeInTheDocument();

      await waitFor(() => {
        expect(screen.getByText("Nguyễn Gia Sư")).toBeInTheDocument();
        expect(screen.getByText("tutor@edufit.vn")).toBeInTheDocument();
      });

      expect(adminUserApi.getUsers).toHaveBeenCalledWith(
        expect.objectContaining({ role: "TUTOR" })
      );
    });
  });
});
