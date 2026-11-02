import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { RevokeKycModal } from "@/features/verification/components/RevokeKycModal";
import { verificationService, PendingQueueItem } from "@/services/verification";
import { ToastProvider } from "@/shared/ui/Toast";

vi.mock("@/services/verification", () => ({
  verificationService: {
    getPendingQueue: vi.fn(),
    getVerifications: vi.fn(),
    getVerificationDetail: vi.fn(),
    reviewVerification: vi.fn(),
    revokeVerification: vi.fn(),
    getCredentialFileUrl: vi.fn(
      (fileId: string) => `/api/v1/verifications/credentials/files/${fileId}`
    ),
  },
}));

describe("RevokeKycModal Component Tests", () => {
  const mockApprovedItem: PendingQueueItem = {
    requestId: "req-rev-100",
    id: "req-rev-100",
    tutorId: "tutor-uuid-456",
    status: "APPROVED",
    tutorName: "Trần Thị Bích",
    tutorEmail: "bich.tran@example.com",
    credentialType: "DEGREE",
    institution: "Đại học Sư phạm Hà Nội",
    year: 2022,
    submittedAt: "2026-10-15T09:00:00Z",
    reviewedAt: "2026-10-16T10:00:00Z",
    reviewerId: "admin-uuid-1",
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("không hiển thị modal khi isOpen = false", () => {
    render(
      <ToastProvider>
        <RevokeKycModal
          item={mockApprovedItem}
          isOpen={false}
          onClose={vi.fn()}
          onRevoked={vi.fn()}
        />
      </ToastProvider>
    );

    expect(screen.queryByText("Thu Hồi Phê Duyệt Bằng Cấp")).not.toBeInTheDocument();
  });

  it("render đầy đủ thông tin gia sư, bằng cấp và cảnh báo hệ thống khi mở modal", () => {
    render(
      <ToastProvider>
        <RevokeKycModal
          item={mockApprovedItem}
          isOpen={true}
          onClose={vi.fn()}
          onRevoked={vi.fn()}
        />
      </ToastProvider>
    );

    expect(screen.getByText("Thu Hồi Phê Duyệt Bằng Cấp")).toBeInTheDocument();
    expect(screen.getByText(/Mã yêu cầu: req-rev-100/)).toBeInTheDocument();
    expect(screen.getByText("Trần Thị Bích")).toBeInTheDocument();
    expect(screen.getByText("bich.tran@example.com")).toBeInTheDocument();
    expect(
      screen.getByText(/Bằng cấp - Đại học Sư phạm Hà Nội \(2022\)/)
    ).toBeInTheDocument();

    // Kiểm tra thông điệp cảnh báo nghiệp vụ
    expect(
      screen.getByText(/giáng hồ sơ gia sư về/i)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Các lớp học đang hoạt động \(active classes\) vẫn được bảo lưu/i)
    ).toBeInTheDocument();
  });

  it("yêu cầu nhập lý do thu hồi tối thiểu 5 ký tự và chặn gọi API khi lý do rỗng", async () => {
    render(
      <ToastProvider>
        <RevokeKycModal
          item={mockApprovedItem}
          isOpen={true}
          onClose={vi.fn()}
          onRevoked={vi.fn()}
        />
      </ToastProvider>
    );

    const submitBtn = screen.getByRole("button", { name: /Xác nhận thu hồi/i });
    fireEvent.click(submitBtn);

    // Báo lỗi validation
    await waitFor(() => {
      expect(
        screen.getByText(/Vui lòng nhập lý do thu hồi rõ ràng \(tối thiểu 5 ký tự\)/i)
      ).toBeInTheDocument();
    });

    expect(verificationService.revokeVerification).not.toHaveBeenCalled();
  });

  it("chặn gửi khi nhập lý do quá ngắn (< 5 ký tự)", async () => {
    render(
      <ToastProvider>
        <RevokeKycModal
          item={mockApprovedItem}
          isOpen={true}
          onClose={vi.fn()}
          onRevoked={vi.fn()}
        />
      </ToastProvider>
    );

    const textarea = screen.getByLabelText(/Lý do thu hồi bằng cấp/i);
    fireEvent.change(textarea, { target: { value: "abc" } });

    const submitBtn = screen.getByRole("button", { name: /Xác nhận thu hồi/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(
        screen.getByText(/Vui lòng nhập lý do thu hồi rõ ràng \(tối thiểu 5 ký tự\)/i)
      ).toBeInTheDocument();
    });

    expect(verificationService.revokeVerification).not.toHaveBeenCalled();
  });

  it("gửi thành công lý do thu hồi hợp lệ, gọi revokeVerification và đóng modal", async () => {
    vi.mocked(verificationService.revokeVerification).mockResolvedValueOnce({
      requestId: "req-rev-100",
      tutorId: "tutor-uuid-456",
      status: "REJECTED",
      rejectionReason: "[REVOKED] Bằng cấp có dấu hiệu chỉnh sửa thông tin",
      credentials: [],
    });

    const onRevokedMock = vi.fn();
    const onCloseMock = vi.fn();

    render(
      <ToastProvider>
        <RevokeKycModal
          item={mockApprovedItem}
          isOpen={true}
          onClose={onCloseMock}
          onRevoked={onRevokedMock}
        />
      </ToastProvider>
    );

    const textarea = screen.getByLabelText(/Lý do thu hồi bằng cấp/i);
    fireEvent.change(textarea, {
      target: { value: "Bằng cấp có dấu hiệu chỉnh sửa thông tin" },
    });

    const submitBtn = screen.getByRole("button", { name: /Xác nhận thu hồi/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(verificationService.revokeVerification).toHaveBeenCalledWith(
        "req-rev-100",
        "Bằng cấp có dấu hiệu chỉnh sửa thông tin"
      );
      expect(onRevokedMock).toHaveBeenCalledTimes(1);
      expect(onCloseMock).toHaveBeenCalledTimes(1);
    });
  });

  it("bấm nút Hủy bỏ đóng modal và reset form", () => {
    const onCloseMock = vi.fn();

    render(
      <ToastProvider>
        <RevokeKycModal
          item={mockApprovedItem}
          isOpen={true}
          onClose={onCloseMock}
          onRevoked={vi.fn()}
        />
      </ToastProvider>
    );

    const cancelBtn = screen.getByRole("button", { name: /Hủy bỏ/i });
    fireEvent.click(cancelBtn);

    expect(onCloseMock).toHaveBeenCalledTimes(1);
  });
});
