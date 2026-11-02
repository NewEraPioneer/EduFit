import "./dialogSetup";
import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { PaymentAdminWorkspace } from "@/features/payment/components/PaymentAdminWorkspace";
import { paymentApi } from "@/features/payment/api/paymentApi";
vi.mock("@/features/payment/api/paymentApi", async () => {
  const actual = await vi.importActual<
    typeof import("@/features/payment/api/paymentApi")
  >("@/features/payment/api/paymentApi");
  return {
    ...actual,
    paymentApi: {
      admin: vi.fn(),
      disputes: vi.fn(),
      review: vi.fn(),
      payout: vi.fn(),
      refreshPayout: vi.fn(),
    },
  };
});
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(paymentApi.admin).mockResolvedValue({
    success: true,
    data: {
      topups: [
        {
          id: "topup-1",
          user_id: "parent-1",
          order_code: 12,
          amount: 100000,
          provider: "MANUAL",
          status: "PENDING",
          created_at: "2026-10-21",
        },
      ],
      withdrawals: [],
    },
  });
  vi.mocked(paymentApi.disputes).mockResolvedValue({ success: true, data: [] });
  vi.mocked(paymentApi.review).mockResolvedValue({ success: true });
});
describe("Admin payment verification", () => {
  it("requires a bank reference and note before confirming a manual deposit", async () => {
    render(<PaymentAdminWorkspace />);
    fireEvent.click(
      await screen.findByRole("button", { name: "Xác minh giao dịch" }),
    );
    expect(paymentApi.review).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText("Ghi chú / lý do"), {
      target: { value: "Đã đối chiếu sao kê" },
    });
    fireEvent.change(
      screen.getByLabelText(/Mã giao dịch ngân hàng đã xác minh/),
      { target: { value: "BANKREF123" } },
    );
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận xử lý" }));
    await waitFor(() =>
      expect(paymentApi.review).toHaveBeenCalledWith(
        "topups",
        "topup-1",
        true,
        "Đã đối chiếu sao kê",
        "BANKREF123",
      ),
    );
  });
  it("shows API errors instead of claiming the transaction was approved", async () => {
    vi.mocked(paymentApi.review).mockRejectedValue(
      new Error("Mã giao dịch đã được dùng"),
    );
    render(<PaymentAdminWorkspace />);
    fireEvent.click(
      await screen.findByRole("button", { name: "Xác minh giao dịch" }),
    );
    fireEvent.change(screen.getByLabelText("Ghi chú / lý do"), {
      target: { value: "Kiểm tra" },
    });
    fireEvent.change(
      screen.getByLabelText(/Mã giao dịch ngân hàng đã xác minh/),
      { target: { value: "BANKREF123" } },
    );
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận xử lý" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Mã giao dịch đã được dùng",
    );
    expect(
      screen.getByRole("button", { name: "Xác nhận xử lý" }),
    ).toBeInTheDocument();
  });
});

describe("Provider payout approval", () => {
  const withdrawal = {
    id: "w-1",
    user_id: "tutor-1",
    amount: 90000,
    status: "PENDING" as const,
    bank_name: "Bank",
    bank_account: "12345678",
    bank_holder: "Tutor",
    bank_bin: "970422",
    created_at: "2026-10-23",
  };
  it("requires explicit approval and note before dispatching payOS", async () => {
    vi.mocked(paymentApi.admin).mockResolvedValue({
      success: true,
      data: {
        topups: [],
        withdrawals: [withdrawal],
        configuration: {
          topupReady: true,
          manualReady: false,
          payoutReady: true,
          payoutEnabled: true,
          webhookPath: "/api/v1/payment/webhooks/payos",
          frontendUrl: "http://localhost:3000",
        },
      },
    });
    vi.mocked(paymentApi.payout).mockResolvedValue({
      success: true,
      data: { ...withdrawal, status: "PROCESSING" },
    });
    render(<PaymentAdminWorkspace />);
    fireEvent.click(
      await screen.findByRole("button", { name: "Duyệt và chuyển qua payOS" }),
    );
    expect(paymentApi.payout).not.toHaveBeenCalled();
    expect(
      screen.queryByLabelText(/Mã giao dịch ngân hàng đã xác minh/),
    ).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Ghi chú / lý do"), {
      target: { value: "Đã kiểm tra người nhận" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận xử lý" }));
    await waitFor(() =>
      expect(paymentApi.payout).toHaveBeenCalledWith(
        "w-1",
        "Đã kiểm tra người nhận",
      ),
    );
    expect(paymentApi.review).not.toHaveBeenCalled();
  });
  it("processing payout only exposes reconciliation, never manual paid or rejection", async () => {
    vi.mocked(paymentApi.admin).mockResolvedValue({
      success: true,
      data: {
        topups: [],
        withdrawals: [
          {
            ...withdrawal,
            status: "PROCESSING",
            provider_message: "Đang chờ ngân hàng",
          },
        ],
      },
    });
    render(<PaymentAdminWorkspace />);
    expect(
      await screen.findByRole("button", { name: "Đối soát kết quả chi" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Đã chuyển ngân hàng" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Từ chối" }),
    ).not.toBeInTheDocument();
  });
});

it("separates the admin queues and opens verification in a dialog", async () => {
  render(<PaymentAdminWorkspace />);
  const queue = await screen.findByRole("button", { name: /Nạp tiền/ });
  fireEvent.click(queue);
  fireEvent.click(
    await screen.findByRole("button", { name: "Xác minh giao dịch" }),
  );
  expect(
    screen.getByRole("dialog", { name: /Xác minh đã nhận/ }),
  ).toBeInTheDocument();
  expect(paymentApi.review).not.toHaveBeenCalled();
});
