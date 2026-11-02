import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import ParentChildrenPage from "@/app/parent/children/page";
import { StudentLinkInvitations } from "@/features/connection/components/StudentLinkInvitations";
import { connectionApi } from "@/features/connection/api/connectionApi";
import { progressApi } from "@/features/progress";

const showToast = vi.fn();
vi.mock("@/context/AuthContext", () => ({ useAuth: () => ({ user: { id: "parent-1" } }) }));
vi.mock("@/shared/ui/Toast", () => ({ useToast: () => ({ showToast }) }));

describe("liên kết gia đình", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    showToast.mockClear();
    vi.spyOn(progressApi, "getParentDashboard").mockResolvedValue({ success: true, data: { students: [] } } as never);
  });

  it("phụ huynh chỉ thấy liên kết thật, gửi email và rút lời mời", async () => {
    vi.spyOn(connectionApi, "getLinks").mockResolvedValue([{ linkId: "link-1", parentUserId: "parent-1", studentId: "student-1", status: "CONFIRMED", confirmedAt: "2026-10-18T00:00:00Z" }]);
    vi.spyOn(connectionApi, "getLinkInvitations").mockResolvedValue([{ invitationId: "invite-1", inviterUserId: "parent-1", invitedUserId: "student-2", status: "PENDING", createdAt: "2026-10-18T00:00:00Z", expiresAt: "2026-11-01T00:00:00Z" }]);
    const invite = vi.spyOn(connectionApi, "inviteParentStudent").mockResolvedValue();
    const withdraw = vi.spyOn(connectionApi, "withdrawLinkInvitation").mockResolvedValue();
    render(<ParentChildrenPage />);
    expect(await screen.findByText("Mã học sinh: student-1")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Email học sinh"), { target: { value: "child@example.com" } });
    fireEvent.click(screen.getByRole("button", { name: "Gửi lời mời" }));
    await waitFor(() => expect(invite).toHaveBeenCalledWith("child@example.com"));
    fireEvent.click(screen.getByRole("button", { name: "Rút lời mời" }));
    await waitFor(() => expect(withdraw).toHaveBeenCalledWith("invite-1"));
  });

  it("phụ huynh mở modern modal xác nhận trước khi hủy liên kết", async () => {
    vi.spyOn(connectionApi, "getLinks").mockResolvedValue([{ linkId: "link-1", parentUserId: "parent-1", studentId: "student-1", status: "CONFIRMED", confirmedAt: "2026-10-18T00:00:00Z" }]);
    vi.spyOn(connectionApi, "getLinkInvitations").mockResolvedValue([]);
    const revoke = vi.spyOn(connectionApi, "revokeLink").mockResolvedValue();
    render(<ParentChildrenPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Hủy liên kết" }));

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText("Hủy liên kết học sinh")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Xác nhận hủy liên kết" }));
    await waitFor(() => expect(revoke).toHaveBeenCalledWith("link-1", undefined));
  });

  it("học sinh chấp nhận hoặc từ chối lời mời nhận được", async () => {
    vi.spyOn(connectionApi, "getLinks").mockResolvedValue([]);
    vi.spyOn(connectionApi, "getLinkInvitations").mockResolvedValue([{ invitationId: "invite-2", inviterUserId: "parent-2", invitedUserId: "parent-1", inviterName: "Phụ huynh Lan", status: "PENDING", createdAt: "2026-10-18T00:00:00Z", expiresAt: "2026-11-01T00:00:00Z" }]);
    const respond = vi.spyOn(connectionApi, "respondLinkInvitation").mockResolvedValue(null);
    render(<StudentLinkInvitations />);
    expect(await screen.findByText("Phụ huynh Lan mời bạn liên kết")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Đồng ý" }));
    await waitFor(() => expect(respond).toHaveBeenCalledWith("invite-2", true));
    fireEvent.click(screen.getByRole("button", { name: "Từ chối" }));
    await waitFor(() => expect(respond).toHaveBeenCalledWith("invite-2", false));
  });

  it("vẫn hiển thị lời mời khi chưa tải được danh sách liên kết", async () => {
    vi.spyOn(connectionApi, "getLinks").mockRejectedValue(new Error("Hồ sơ học sinh chưa hoàn tất"));
    vi.spyOn(connectionApi, "getLinkInvitations").mockResolvedValue([{ invitationId: "invite-3", inviterUserId: "parent-3", invitedUserId: "parent-1", inviterName: "Phụ huynh Hà", status: "PENDING", createdAt: "2026-10-18T00:00:00Z", expiresAt: "2026-11-01T00:00:00Z" }]);
    render(<StudentLinkInvitations />);
    expect(await screen.findByText("Phụ huynh Hà mời bạn liên kết")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Đồng ý" })).toBeEnabled();
  });

  it("học sinh mở modern modal xác nhận và hủy được liên kết phụ huynh đã xác nhận", async () => {
    vi.spyOn(connectionApi, "getLinkInvitations").mockResolvedValue([]);
    vi.spyOn(connectionApi, "getLinks").mockResolvedValue([{
      linkId: "student-link-1",
      parentUserId: "parent-account-1",
      studentId: "student-profile-1",
      status: "CONFIRMED",
      confirmedAt: "2026-10-18T00:00:00Z",
    }]);
    const revoke = vi.spyOn(connectionApi, "revokeLink").mockResolvedValue();

    render(<StudentLinkInvitations />);

    expect(await screen.findByText("Mã tài khoản: parent-account-1")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Hủy liên kết" }));

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText("Hủy liên kết phụ huynh")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Xác nhận hủy liên kết" }));
    await waitFor(() => expect(revoke).toHaveBeenCalledWith("student-link-1", undefined));
  });

  it("kiểm tra lại lời mời khi học sinh đang mở dashboard", async () => {
    vi.useFakeTimers();
    try {
      vi.spyOn(connectionApi, "getLinks").mockResolvedValue([]);
      const getInvitations = vi.spyOn(connectionApi, "getLinkInvitations").mockResolvedValue([]);
      const view = render(<StudentLinkInvitations />);
      await act(async () => { await Promise.resolve(); });
      expect(getInvitations).toHaveBeenCalledTimes(1);
      await act(async () => { await vi.advanceTimersByTimeAsync(60_000); });
      expect(getInvitations).toHaveBeenCalledTimes(2);
      await act(async () => { window.dispatchEvent(new Event("edufit:family-invitations-refresh")); await Promise.resolve(); });
      expect(getInvitations).toHaveBeenCalledTimes(3);
      view.unmount();
      await act(async () => { await vi.advanceTimersByTimeAsync(60_000); });
      expect(getInvitations).toHaveBeenCalledTimes(3);
    } finally {
      vi.useRealTimers();
    }
  });
});
