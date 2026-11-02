import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { AvatarUploadCard } from "@/features/settings/components/AvatarUploadCard";
import { authApi } from "@/features/auth/api/authApi";

// Mock authApi
vi.mock("@/features/auth/api/authApi", () => ({
  authApi: {
    uploadAvatar: vi.fn(),
    updateAvatarUrl: vi.fn(),
    removeAvatar: vi.fn(),
  },
}));

// Mock useAuth
const mockUpdateUser = vi.fn();
const mockRefreshUser = vi.fn().mockResolvedValue(undefined);
let mockUser: any = {
  accountId: "acc-123",
  email: "student@edufit.vn",
  fullName: "Lê Văn Học",
  role: "STUDENT",
  status: "ACTIVE",
  avatarUrl: "https://api.dicebear.com/7.x/avataaars/svg?seed=Felix",
};

vi.mock("@/context/AuthContext", () => ({
  useAuth: () => ({
    user: mockUser,
    updateUser: mockUpdateUser,
    refreshUser: mockRefreshUser,
  }),
}));

// Mock useToast
const mockShowToast = vi.fn();
vi.mock("@/shared/ui/Toast", () => ({
  useToast: () => ({
    showToast: mockShowToast,
  }),
}));

describe("AvatarUploadCard Component Tests", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUser = {
      accountId: "acc-123",
      email: "student@edufit.vn",
      fullName: "Lê Văn Học",
      role: "STUDENT",
      status: "ACTIVE",
      avatarUrl: "https://api.dicebear.com/7.x/avataaars/svg?seed=Felix",
    };
  });

  it("render thông tin ảnh đại diện và tên người dùng", () => {
    render(<AvatarUploadCard />);

    expect(screen.getByText("Ảnh đại diện hồ sơ (Avatar)")).toBeInTheDocument();
    expect(screen.getByText("Lê Văn Học")).toBeInTheDocument();
    expect(screen.getByText("Tải ảnh từ thiết bị")).toBeInTheDocument();
    expect(screen.getByText("Xóa ảnh")).toBeInTheDocument();
  });

  it("từ chối khi người dùng chọn tệp không đúng định dạng ảnh", async () => {
    render(<AvatarUploadCard />);

    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    const badFile = new File(["fake content"], "malicious.exe", { type: "application/x-msdownload" });

    fireEvent.change(input, { target: { files: [badFile] } });

    await waitFor(() => {
      expect(screen.getByText(/Định dạng tệp không hợp lệ/i)).toBeInTheDocument();
    });
    expect(mockShowToast).toHaveBeenCalledWith("Định dạng tệp không hợp lệ!", "error");
    expect(authApi.uploadAvatar).not.toHaveBeenCalled();
  });

  it("gọi API uploadAvatar khi người dùng chọn tệp ảnh hợp lệ", async () => {
    const updatedUser = { ...mockUser, avatarUrl: "/api/v1/auth/avatar/acc-123_new.png" };
    vi.mocked(authApi.uploadAvatar).mockResolvedValue({
      success: true,
      data: updatedUser,
    });

    render(<AvatarUploadCard />);

    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    const validFile = new File(["fake image content"], "avatar.png", { type: "image/png" });

    fireEvent.change(input, { target: { files: [validFile] } });

    await waitFor(() => {
      expect(authApi.uploadAvatar).toHaveBeenCalledWith(validFile);
      expect(mockShowToast).toHaveBeenCalledWith("Tải lên và cập nhật ảnh đại diện thành công!", "success");
    });
  });

  it("gọi API updateAvatarUrl khi người dùng bấm vào một avatar mẫu (preset)", async () => {
    const updatedUser = {
      ...mockUser,
      avatarUrl: "https://api.dicebear.com/7.x/avataaars/svg?seed=Aneka&backgroundColor=c0aede",
    };
    vi.mocked(authApi.updateAvatarUrl).mockResolvedValue({
      success: true,
      data: updatedUser,
    });

    render(<AvatarUploadCard />);

    const presetButton = screen.getByText("Năng động").closest("button");
    expect(presetButton).toBeInTheDocument();
    fireEvent.click(presetButton!);

    await waitFor(() => {
      expect(authApi.updateAvatarUrl).toHaveBeenCalledWith(
        "https://api.dicebear.com/7.x/avataaars/svg?seed=Aneka&backgroundColor=c0aede"
      );
      expect(mockShowToast).toHaveBeenCalledWith("Đã cập nhật ảnh đại diện mẫu thành công!", "success");
    });
  });

  it("gọi API removeAvatar khi người dùng bấm Xóa ảnh", async () => {
    const defaultUser = { ...mockUser, avatarUrl: null };
    vi.mocked(authApi.removeAvatar).mockResolvedValue({
      success: true,
      data: defaultUser,
    });

    render(<AvatarUploadCard />);

    const removeBtn = screen.getByRole("button", { name: /Xóa ảnh/i });
    fireEvent.click(removeBtn);

    await waitFor(() => {
      expect(authApi.removeAvatar).toHaveBeenCalled();
      expect(mockShowToast).toHaveBeenCalledWith("Đã xóa ảnh đại diện thành công!", "success");
    });
  });
});
