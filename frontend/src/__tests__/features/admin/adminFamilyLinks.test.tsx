import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { FamilyLinksManagementView, adminUserApi } from "@/features/admin";

vi.mock("@/features/admin/api/adminUserApi", () => ({
  adminUserApi: {
    getUsers: vi.fn(),
    getUserStats: vi.fn(),
    getUserById: vi.fn(),
    lockUser: vi.fn(),
    unlockUser: vi.fn(),
    updateStatus: vi.fn(),
    getFamilyLinks: vi.fn(),
    getFamilyLinkStats: vi.fn(),
    revokeFamilyLink: vi.fn(),
  },
}));

describe("FamilyLinksManagementView Unit & Integration Tests", () => {
  const mockStats = {
    totalLinks: 12,
    activeLinks: 9,
    revokedLinks: 3,
    uniqueParents: 8,
    uniqueStudents: 10,
  };

  const mockLinks = [
    {
      linkId: "link-1",
      parentUserId: "parent-1",
      parentFullName: "Nguyễn Văn Cha",
      parentEmail: "cha@gmail.com",
      studentId: "student-1",
      studentUserId: "student-user-1",
      studentFullName: "Nguyễn Văn Con",
      studentEmail: "con@gmail.com",
      status: "CONFIRMED" as const,
      confirmedAt: "2026-03-01T10:00:00Z",
    },
    {
      linkId: "link-2",
      parentUserId: "parent-2",
      parentFullName: "Trần Thị Mẹ",
      parentEmail: "me@gmail.com",
      studentId: "student-2",
      studentUserId: "student-user-2",
      studentFullName: "Trần Bé",
      studentEmail: "be@gmail.com",
      status: "REVOKED" as const,
      confirmedAt: "2026-02-15T08:30:00Z",
      revokedAt: "2026-03-05T14:20:00Z",
      revokeReason: "Yêu cầu hủy từ phụ huynh qua tổng đài hỗ trợ",
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(adminUserApi.getFamilyLinkStats).mockResolvedValue({
      success: true,
      data: mockStats,
    });
    vi.mocked(adminUserApi.getFamilyLinks).mockResolvedValue({
      success: true,
      data: {
        items: mockLinks,
        totalElements: 2,
        totalPages: 1,
        page: 0,
        size: 15,
      },
    });
    vi.mocked(adminUserApi.revokeFamilyLink).mockResolvedValue({
      success: true,
      message: "Đã hủy liên kết thành công",
    });
  });

  it("hiển thị tiêu đề, mô tả và các thẻ KPI thống kê liên kết", async () => {
    render(<FamilyLinksManagementView />);

    expect(screen.getByText("Liên kết Phụ huynh - Học sinh")).toBeInTheDocument();
    expect(screen.getByText("Quan hệ bảo trợ")).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText("12")).toBeInTheDocument(); // Tổng số liên kết
      expect(screen.getByText("9")).toBeInTheDocument(); // Đang kết nối
      expect(screen.getByText("3")).toBeInTheDocument(); // Đã thu hồi / Hủy
      expect(screen.getByText("8")).toBeInTheDocument(); // Phụ huynh bảo trợ
    });
  });

  it("hiển thị danh sách liên kết với đầy đủ thông tin cha mẹ và con cái", async () => {
    render(<FamilyLinksManagementView />);

    await waitFor(() => {
      expect(screen.getByText("Nguyễn Văn Cha")).toBeInTheDocument();
      expect(screen.getByText("cha@gmail.com")).toBeInTheDocument();
      expect(screen.getByText("Nguyễn Văn Con")).toBeInTheDocument();
      expect(screen.getByText("con@gmail.com")).toBeInTheDocument();

      expect(screen.getByText("Trần Thị Mẹ")).toBeInTheDocument();
      expect(screen.getByText("Trần Bé")).toBeInTheDocument();
    });

    expect(screen.getByTestId("revoke-btn-link-1")).toBeInTheDocument();
    expect(screen.getByTestId("view-reason-btn-link-2")).toBeInTheDocument();
  });

  it("lọc liên kết theo tab trạng thái (Đang liên kết, Đã thu hồi)", async () => {
    render(<FamilyLinksManagementView />);

    await waitFor(() => {
      expect(adminUserApi.getFamilyLinks).toHaveBeenCalledWith({
        status: undefined,
        search: undefined,
        page: 0,
        size: 15,
      });
    });

    // Click tab "Đang liên kết"
    const activeTab = screen.getByRole("button", { name: "Đang liên kết" });
    fireEvent.click(activeTab);

    await waitFor(() => {
      expect(adminUserApi.getFamilyLinks).toHaveBeenCalledWith({
        status: "CONFIRMED",
        search: undefined,
        page: 0,
        size: 15,
      });
    });

    // Click tab "Đã thu hồi"
    const revokedTab = screen.getByRole("button", { name: "Đã thu hồi" });
    fireEvent.click(revokedTab);

    await waitFor(() => {
      expect(adminUserApi.getFamilyLinks).toHaveBeenCalledWith({
        status: "REVOKED",
        search: undefined,
        page: 0,
        size: 15,
      });
    });
  });

  it("tìm kiếm liên kết theo từ khóa", async () => {
    render(<FamilyLinksManagementView />);

    const searchInput = screen.getByPlaceholderText(
      "Tìm theo tên hoặc email phụ huynh, học sinh..."
    );
    fireEvent.change(searchInput, { target: { value: "Nguyễn Văn Cha" } });

    await waitFor(() => {
      expect(adminUserApi.getFamilyLinks).toHaveBeenCalledWith({
        status: undefined,
        search: "Nguyễn Văn Cha",
        page: 0,
        size: 15,
      });
    });
  });

  it("thực hiện quy trình thu hồi liên kết có nhập lý do bắt buộc", async () => {
    render(<FamilyLinksManagementView />);

    await waitFor(() => {
      expect(screen.getByTestId("revoke-btn-link-1")).toBeInTheDocument();
    });

    // Mở modal thu hồi
    fireEvent.click(screen.getByTestId("revoke-btn-link-1"));
    expect(screen.getByTestId("revoke-confirm-modal")).toBeInTheDocument();
    expect(screen.getByText(/Xác nhận thu hồi liên kết bảo trợ/i)).toBeInTheDocument();

    // Nhấn xác nhận khi chưa nhập lý do -> báo lỗi
    const confirmBtn = screen.getByRole("button", { name: "Xác nhận thu hồi" });
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(screen.getByText("Vui lòng nhập lý do thu hồi liên kết")).toBeInTheDocument();
    });
    expect(adminUserApi.revokeFamilyLink).not.toHaveBeenCalled();

    // Nhập lý do và bấm xác nhận
    const reasonInput = screen.getByPlaceholderText(/Nhập lý do thu hồi/i);
    fireEvent.change(reasonInput, {
      target: { value: "Thông tin bảo trợ không hợp lệ theo phản ánh" },
    });
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(adminUserApi.revokeFamilyLink).toHaveBeenCalledWith(
        "link-1",
        "Thông tin bảo trợ không hợp lệ theo phản ánh"
      );
    });

    await waitFor(() => {
      expect(
        screen.getByText(/Đã hủy liên kết giữa Nguyễn Văn Cha và Nguyễn Văn Con thành công!/i)
      ).toBeInTheDocument();
    });
  });

  it("xem chi tiết lý do thu hồi của liên kết đã bị hủy", async () => {
    render(<FamilyLinksManagementView />);

    await waitFor(() => {
      expect(screen.getByTestId("view-reason-btn-link-2")).toBeInTheDocument();
    });

    // Mở modal xem lý do
    fireEvent.click(screen.getByTestId("view-reason-btn-link-2"));
    expect(screen.getByTestId("link-detail-modal")).toBeInTheDocument();
    expect(screen.getByText("Thông tin thu hồi liên kết")).toBeInTheDocument();
    expect(
      screen.getByText("Yêu cầu hủy từ phụ huynh qua tổng đài hỗ trợ")
    ).toBeInTheDocument();

    // Đóng modal
    const closeBtn = screen.getByRole("button", { name: "Đóng" });
    fireEvent.click(closeBtn);
    expect(screen.queryByTestId("link-detail-modal")).not.toBeInTheDocument();
  });

  it("hiển thị trạng thái trống khi không có liên kết nào", async () => {
    vi.mocked(adminUserApi.getFamilyLinks).mockResolvedValueOnce({
      success: true,
      data: {
        items: [],
        totalElements: 0,
        totalPages: 1,
        page: 0,
        size: 15,
      },
    });

    render(<FamilyLinksManagementView />);

    await waitFor(() => {
      expect(screen.getByText("Không có liên kết nào")).toBeInTheDocument();
      expect(
        screen.getByText("Chưa có liên kết phụ huynh - học sinh nào trong hệ thống.")
      ).toBeInTheDocument();
    });
  });
});
