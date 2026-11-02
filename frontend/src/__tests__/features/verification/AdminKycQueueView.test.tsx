import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { AdminKycQueueView } from "@/features/verification/components/AdminKycQueueView";
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

describe("AdminKycQueueView Component Tests (3 Tabs & Revocation)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("hiển thị trạng thái đang tải (Loading) khi mới vào trang", () => {
    vi.mocked(verificationService.getVerifications).mockReturnValue(
      new Promise(() => {})
    );

    render(
      <ToastProvider>
        <AdminKycQueueView />
      </ToastProvider>
    );

    expect(
      screen.getByText(/Đang tải danh sách hồ sơ chờ duyệt từ máy chủ.../i)
    ).toBeInTheDocument();
  });

  it("hiển thị EmptyState khi không có hồ sơ nào trong tab Chờ duyệt", async () => {
    vi.mocked(verificationService.getVerifications).mockResolvedValue({
      items: [],
      page: 0,
      size: 50,
      totalElements: 0,
      totalPages: 0,
    });

    render(
      <ToastProvider>
        <AdminKycQueueView />
      </ToastProvider>
    );

    await waitFor(() => {
      expect(
        screen.getByText("Không có hồ sơ nào đang chờ duyệt")
      ).toBeInTheDocument();
    });
    expect(screen.getByText("HÀNG ĐỢI HOÀN TẤT")).toBeInTheDocument();
  });

  it("hiển thị đúng 3 Tabs: Chờ duyệt, Đã duyệt, Đã từ chối / Thu hồi", async () => {
    vi.mocked(verificationService.getVerifications).mockResolvedValue({
      items: [],
      page: 0,
      size: 50,
      totalElements: 0,
      totalPages: 0,
    });

    render(
      <ToastProvider>
        <AdminKycQueueView />
      </ToastProvider>
    );

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /Chờ duyệt/i })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Đã duyệt/i })).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /Đã từ chối \/ Thu hồi/i })
      ).toBeInTheDocument();
    });
  });

  it("Tab 1 (PENDING): hiển thị hồ sơ chờ duyệt, thông tin phong phú và mở ReviewKycModal khi bấm Thẩm định", async () => {
    const mockPendingItems: PendingQueueItem[] = [
      {
        requestId: "req-pending-001",
        id: "req-pending-001",
        tutorId: "tutor-123",
        tutorName: "Nguyễn Văn An",
        tutorEmail: "an.nguyen@example.com",
        credentialType: "DEGREE",
        institution: "Đại học Bách Khoa Hà Nội",
        year: 2023,
        documentUrls: [
          "/api/v1/verifications/credentials/files/file-001",
        ],
        status: "PENDING",
        submittedAt: "2026-10-14T08:00:00Z",
      },
    ];

    vi.mocked(verificationService.getVerifications).mockResolvedValue({
      items: mockPendingItems,
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    });

    vi.mocked(verificationService.getVerificationDetail).mockResolvedValue({
      requestId: "req-pending-001",
      tutorId: "tutor-123",
      status: "PENDING",
      credentials: [
        {
          credentialId: "c1",
          type: "DEGREE",
          institution: "Đại học Bách Khoa Hà Nội",
          year: 2023,
          files: [],
        },
      ],
    });

    render(
      <ToastProvider>
        <AdminKycQueueView />
      </ToastProvider>
    );

    await waitFor(() => {
      expect(screen.getByText("req-pending-001")).toBeInTheDocument();
      expect(screen.getByText("Nguyễn Văn An")).toBeInTheDocument();
      expect(screen.getByText("an.nguyen@example.com")).toBeInTheDocument();
      expect(screen.getByText("Đại học Bách Khoa Hà Nội")).toBeInTheDocument();
      expect(screen.getByText(/Năm 2023/)).toBeInTheDocument();
      expect(screen.getByText("Tệp 1")).toBeInTheDocument();
    });

    // Bấm nút thẩm định
    const reviewButton = screen.getByRole("button", { name: /Thẩm định/i });
    fireEvent.click(reviewButton);

    // Review Modal mở ra
    await waitFor(() => {
      expect(
        screen.getByText("Thẩm Định Hồ Sơ Xác Minh KYC")
      ).toBeInTheDocument();
      expect(screen.getByText(/Mã yêu cầu: req-pending-001/)).toBeInTheDocument();
    });
  });

  it("Tab 2 (APPROVED): chuyển tab gọi API APPROVED, hiển thị nút Thu hồi bằng cấp và mở RevokeKycModal", async () => {
    const mockApprovedItems: PendingQueueItem[] = [
      {
        requestId: "req-app-002",
        id: "req-app-002",
        tutorId: "tutor-456",
        tutorName: "Lê Thị Mai",
        tutorEmail: "mai.le@example.com",
        credentialType: "CERTIFICATE",
        institution: "IELTS British Council 8.0",
        year: 2024,
        documentUrls: [
          "/api/v1/verifications/credentials/files/file-ielts",
        ],
        status: "APPROVED",
        submittedAt: "2026-10-10T08:00:00Z",
        reviewedAt: "2026-10-11T09:00:00Z",
      },
    ];

    // Lần đầu vào PENDING
    vi.mocked(verificationService.getVerifications).mockResolvedValueOnce({
      items: [],
      page: 0,
      size: 50,
      totalElements: 0,
      totalPages: 0,
    });

    // Lần 2 khi chuyển sang APPROVED
    vi.mocked(verificationService.getVerifications).mockResolvedValueOnce({
      items: mockApprovedItems,
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    });

    render(
      <ToastProvider>
        <AdminKycQueueView />
      </ToastProvider>
    );

    // Chờ tab đầu nạp xong
    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /Đã duyệt/i })
      ).toBeInTheDocument();
    });

    // Bấm tab "Đã duyệt"
    const approvedTabBtn = screen.getByRole("button", { name: /Đã duyệt/i });
    fireEvent.click(approvedTabBtn);

    // Kiểm tra gọi getVerifications với "APPROVED"
    await waitFor(() => {
      expect(verificationService.getVerifications).toHaveBeenCalledWith(
        "APPROVED",
        0,
        50
      );
      expect(screen.getByText("req-app-002")).toBeInTheDocument();
      expect(screen.getByText("Lê Thị Mai")).toBeInTheDocument();
      expect(screen.getByText("IELTS British Council 8.0")).toBeInTheDocument();
    });

    // Nút "Thu hồi bằng cấp" hiển thị
    const revokeBtn = screen.getByRole("button", { name: /Thu hồi bằng cấp/i });
    expect(revokeBtn).toBeInTheDocument();

    // Bấm nút thu hồi mở RevokeKycModal
    fireEvent.click(revokeBtn);

    await waitFor(() => {
      expect(
        screen.getByText("Thu Hồi Phê Duyệt Bằng Cấp")
      ).toBeInTheDocument();
      expect(screen.getByText(/Mã yêu cầu: req-app-002/)).toBeInTheDocument();
      expect(screen.getByText(/giáng hồ sơ gia sư về/i)).toBeInTheDocument();
    });
  });

  it("Tab 3 (REJECTED): hiển thị lý do thu hồi [REVOKED], badge ĐÃ THU HỒI và thông tin kiểm toán", async () => {
    const mockRejectedItems: PendingQueueItem[] = [
      {
        requestId: "req-rej-003",
        id: "req-rej-003",
        tutorId: "tutor-789",
        tutorName: "Phạm Hoàng Long",
        tutorEmail: "long.pham@example.com",
        credentialType: "DEGREE",
        institution: "Đại học Kinh Tế",
        year: 2021,
        documentUrls: [],
        status: "REJECTED",
        rejectionReason: "[REVOKED] Chứng chỉ hết hiệu lực hoặc không thể đối soát",
        submittedAt: "2026-10-01T08:00:00Z",
        reviewedAt: "2026-10-18T14:30:00Z",
        reviewedBy: "admin-system-1",
      },
    ];

    vi.mocked(verificationService.getVerifications).mockResolvedValueOnce({
      items: [],
      page: 0,
      size: 50,
      totalElements: 0,
      totalPages: 0,
    });

    vi.mocked(verificationService.getVerifications).mockResolvedValueOnce({
      items: mockRejectedItems,
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    });

    render(
      <ToastProvider>
        <AdminKycQueueView />
      </ToastProvider>
    );

    // Chờ tab đầu nạp xong
    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /Đã từ chối \/ Thu hồi/i })
      ).toBeInTheDocument();
    });

    // Bấm tab "Đã từ chối / Thu hồi"
    const rejectedTabBtn = screen.getByRole("button", {
      name: /Đã từ chối \/ Thu hồi/i,
    });
    fireEvent.click(rejectedTabBtn);

    await waitFor(() => {
      expect(verificationService.getVerifications).toHaveBeenCalledWith(
        "REJECTED",
        0,
        50
      );
      expect(screen.getByText("req-rej-003")).toBeInTheDocument();
      expect(screen.getByText("Phạm Hoàng Long")).toBeInTheDocument();
      expect(screen.getByText("ĐÃ THU HỒI")).toBeInTheDocument();
      expect(
        screen.getByText(/"Chứng chỉ hết hiệu lực hoặc không thể đối soát"/)
      ).toBeInTheDocument();
      expect(screen.getByText(/Bởi: admin-system-1/)).toBeInTheDocument();
    });
  });
});
