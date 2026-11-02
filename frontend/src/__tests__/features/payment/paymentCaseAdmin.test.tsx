import React from "react";
import { beforeEach, expect, it, vi } from "vitest";
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { AdminPaymentCase } from "@/features/payment/components/AdminPaymentCase";
import {
  paymentApi,
  type PaymentSession,
} from "@/features/payment/api/paymentApi";

vi.mock("@/features/payment/api/paymentApi", async () => ({
  ...(await vi.importActual<typeof import("@/features/payment/api/paymentApi")>(
    "@/features/payment/api/paymentApi",
  )),
  paymentApi: { adminCase: vi.fn(), resolveCase: vi.fn() },
}));
const session: PaymentSession = {
  sessionId: "case-1",
  classId: "class-1",
  studentId: "student-1",
  payerId: "parent-1",
  learnerAccepted: false,
  tutorAccepted: false,
  autoAccepted: false,
  disputeDeadline: "2026-10-22T12:00:00Z",
  learnerReport: "Đề nghị tính một phần",
  tutorReport: "Đã dạy theo lịch",
  canFund: false,
  tutorName: "Gia sư An",
  amount: 300000,
  commissionBps: 1000,
  status: "ADMIN_REVIEW",
  sessionStatus: "COMPLETED",
  startAt: "2026-10-20T10:00:00Z",
  endAt: "2026-10-20T11:00:00Z",
  disputeReason: "Thiếu thời lượng",
  disputeCategory: "DURATION",
  learnerAmount: 100000,
  tutorAmount: 200000,
  canActAsLearner: false,
  canActAsTutor: false,
  comments: [],
  settledAmount: 0,
};
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(paymentApi.adminCase).mockResolvedValue({
    success: true,
    data: session,
  });
  vi.mocked(paymentApi.resolveCase).mockResolvedValue({
    success: true,
    data: { ...session, status: "SETTLED", settledAmount: 150000 },
  });
});
async function preview() {
  await screen.findByLabelText("Học phí được chấp nhận");
  fireEvent.change(screen.getByLabelText("Học phí được chấp nhận"), {
    target: { value: "150000" },
  });
  fireEvent.change(screen.getByLabelText("Lý do quyết định"), {
    target: { value: "Đã đối chiếu bằng chứng hai bên" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Xem lại quyết định" }));
}
it("previews the refund, tutor net and fee before adjudicating an escalated case", async () => {
  render(<AdminPaymentCase id="case-1" />);
  await preview();
  expect(paymentApi.resolveCase).not.toHaveBeenCalled();
  const details = within(
    screen.getByRole("region", { name: "Xem lại quyết định" }),
  );
  expect(details.getByText(/150\.000/)).toBeInTheDocument();
  expect(details.getByText(/135\.000/)).toBeInTheDocument();
  expect(details.getByText(/15\.000/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Xác nhận phân xử" }));
  await waitFor(() =>
    expect(paymentApi.resolveCase).toHaveBeenCalledWith(
      "case-1",
      150000,
      "Đã đối chiếu bằng chứng hai bên",
    ),
  );
  expect(paymentApi.resolveCase).toHaveBeenCalledTimes(1);
  await waitFor(() =>
    expect(
      screen.queryByLabelText("Học phí được chấp nhận"),
    ).not.toBeInTheDocument(),
  );
});
it("keeps the case open and shows the backend error when resolution fails", async () => {
  vi.mocked(paymentApi.resolveCase).mockRejectedValue(
    new Error("Hồ sơ đã thay đổi"),
  );
  render(<AdminPaymentCase id="case-1" />);
  await preview();
  fireEvent.click(screen.getByRole("button", { name: "Xác nhận phân xử" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Hồ sơ đã thay đổi",
  );
  expect(screen.getByLabelText("Lý do quyết định")).toHaveValue(
    "Đã đối chiếu bằng chứng hai bên",
  );
});
