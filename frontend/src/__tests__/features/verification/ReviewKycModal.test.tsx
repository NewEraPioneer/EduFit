import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { ReviewKycModal } from "@/features/verification/components/ReviewKycModal";
import { verificationService, PendingQueueItem } from "@/services/verification";
import { ToastProvider } from "@/shared/ui/Toast";

vi.mock("@/services/verification", () => ({
  verificationService: {
    getPendingQueue: vi.fn(),
    getVerificationDetail: vi.fn(),
    reviewVerification: vi.fn(),
    getCredentialFileUrl: vi.fn((fileId: string) => `/api/v1/verifications/credentials/files/${fileId}`),
  },
}));

describe("ReviewKycModal Component Tests", () => {
  const mockItem: PendingQueueItem = {
    requestId: "req-abc-123",
    tutorId: "tutor-xyz",
    status: "PENDING",
    submittedAt: "2026-10-14T08:00:00Z",
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("render thông tin hồ sơ và danh sách file bằng cấp nộp từ backend", async () => {
    vi.mocked(verificationService.getVerificationDetail).mockResolvedValue({
      requestId: "req-abc-123",
      tutorId: "tutor-xyz",
      status: "PENDING",
      credentials: [
        {
          credentialId: "c1",
          type: "DEGREE",
          institution: "Đại học Bách Khoa",
          year: 2024,
          note: "Bằng xuất sắc",
          files: [
            {
              fileId: "f-111",
              fileName: "bang_tot_nghiep.pdf",
              storageKey: "key-111",
              contentType: "application/pdf",
              sizeBytes: 1048576, // 1MB
              uploadedAt: "2026-10-14T08:00:00Z",
            },
          ],
        },
      ],
    });

    render(
      <ToastProvider>
        <ReviewKycModal
          item={mockItem}
          isOpen={true}
          onClose={vi.fn()}
          onReviewed={vi.fn()}
        />
      </ToastProvider>
    );

    // Kiểm tra thông tin header modal
    expect(screen.getByText("Thẩm Định Hồ Sơ Xác Minh KYC")).toBeInTheDocument();
    expect(screen.getByText("tutor-xyz")).toBeInTheDocument();

    // Chờ nạp chi tiết bằng cấp
    await waitFor(() => {
      expect(screen.getByText("Đại học Bách Khoa")).toBeInTheDocument();
      expect(screen.getByText("bang_tot_nghiep.pdf")).toBeInTheDocument();
      expect(screen.getByText("(1024 KB)")).toBeInTheDocument();
    });

    // File link kiểm tra URL
    const fileLink = screen.getByRole("link", { name: /bang_tot_nghiep.pdf/i });
    expect(fileLink).toHaveAttribute("href", "/api/v1/verifications/credentials/files/f-111");
    expect(fileLink).toHaveAttribute("target", "_blank");
  });

  it("gửi loại gia sư admin xác nhận theo giấy tờ khi phê duyệt", async () => {
    vi.mocked(verificationService.getVerificationDetail).mockResolvedValue({
      requestId: "req-abc-123",
      tutorId: "tutor-xyz",
      status: "PENDING",
      credentials: [],
    });
    vi.mocked(verificationService.reviewVerification).mockResolvedValue({
      requestId: "req-abc-123",
      tutorId: "tutor-xyz",
      status: "APPROVED",
      credentials: [],
    });

    render(
      <ToastProvider>
        <ReviewKycModal item={mockItem} isOpen={true} onClose={vi.fn()} onReviewed={vi.fn()} />
      </ToastProvider>
    );

    fireEvent.change(screen.getByLabelText(/Xác nhận loại gia sư/), { target: { value: "IN_SERVICE_TEACHER" } });
    fireEvent.click(screen.getByRole("button", { name: /Xác nhận quyết định/i }));

    await waitFor(() => {
      expect(verificationService.reviewVerification).toHaveBeenCalledWith(
        "req-abc-123",
        "APPROVED",
        undefined,
        "IN_SERVICE_TEACHER"
      );
    });
  });

  it("phê duyệt hồ sơ (APPROVE) thành công", async () => {
    vi.mocked(verificationService.getVerificationDetail).mockResolvedValue({
      requestId: "req-abc-123",
      tutorId: "tutor-xyz",
      status: "PENDING",
      credentials: [],
    });

    vi.mocked(verificationService.reviewVerification).mockResolvedValue({
      requestId: "req-abc-123",
      tutorId: "tutor-xyz",
      status: "APPROVED",
      credentials: [],
    });

    const onReviewedMock = vi.fn();
    const onCloseMock = vi.fn();

    render(
      <ToastProvider>
        <ReviewKycModal
          item={mockItem}
          isOpen={true}
          onClose={onCloseMock}
          onReviewed={onReviewedMock}
        />
      </ToastProvider>
    );

    // Mặc định decision là APPROVED, bấm xác nhận
    const submitBtn = screen.getByRole("button", { name: /Xác nhận quyết định/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(verificationService.reviewVerification).toHaveBeenCalledWith(
        "req-abc-123",
        "APPROVED",
        undefined,
        null
      );
      expect(onReviewedMock).toHaveBeenCalled();
      expect(onCloseMock).toHaveBeenCalled();
    });
  });

  it("từ chối hồ sơ (REJECT) yêu cầu bắt buộc nhập lý do (Quy tắc BR-06)", async () => {
    vi.mocked(verificationService.getVerificationDetail).mockResolvedValue({
      requestId: "req-abc-123",
      tutorId: "tutor-xyz",
      status: "PENDING",
      credentials: [],
    });

    render(
      <ToastProvider>
        <ReviewKycModal
          item={mockItem}
          isOpen={true}
          onClose={vi.fn()}
          onReviewed={vi.fn()}
        />
      </ToastProvider>
    );

    // Chuyển sang REJECT
    const rejectBtn = screen.getByRole("button", { name: /TỪ CHỐI/i });
    fireEvent.click(rejectBtn);

    // Ô nhập lý do xuất hiện
    const textarea = screen.getByPlaceholderText(/Ảnh bằng tốt nghiệp bị mờ/i);
    expect(textarea).toBeInTheDocument();

    // Nhập lý do hợp lệ
    fireEvent.change(textarea, { target: { value: "Ảnh mờ không rõ con dấu nhà trường" } });

    vi.mocked(verificationService.reviewVerification).mockResolvedValue({
      requestId: "req-abc-123",
      tutorId: "tutor-xyz",
      status: "REJECTED",
      rejectionReason: "Ảnh mờ không rõ con dấu nhà trường",
      credentials: [],
    });

    const submitBtn = screen.getByRole("button", { name: /Xác nhận quyết định/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(verificationService.reviewVerification).toHaveBeenCalledWith(
        "req-abc-123",
        "REJECTED",
        "Ảnh mờ không rõ con dấu nhà trường",
        null
      );
    });
  });
});
