import "./dialogSetup";
import React from "react";
import { beforeEach, expect, it, vi } from "vitest";
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { PaymentWorkspace } from "@/features/payment/components/PaymentWorkspace";
import { DUE_BEFORE_MS } from "@/features/payment/paymentPresentation";
import {
  money,
  paymentApi,
  type Overview,
  type PaymentSession,
  type SessionPaymentRecord,
  type TopupRecord,
} from "@/features/payment/api/paymentApi";
vi.mock("@/features/payment/api/paymentApi", async () => ({
  ...(await vi.importActual<typeof import("@/features/payment/api/paymentApi")>(
    "@/features/payment/api/paymentApi",
  )),
  paymentApi: {
    wallet: vi.fn(),
    sessions: vi.fn(),
    history: vi.fn(),
    topup: vi.fn(),
  },
}));
const paidTopup: TopupRecord = {
  id: "8b0c2f9e-0000-4000-8000-000000000001",
  order_code: 1794470279123456,
  payment_link_id: "f453f2e1086649dca4915eefbc9b3746",
  bank_reference: "FT26316BANK",
  amount: 200000,
  provider: "PAYOS",
  paid_at: "2026-11-12T08:00:00Z",
};
/** Tất toán 300.000đ đang giữ: `refund` phần hoàn về ví, phần còn lại đã thanh toán cho gia sư. */
const settled = (id: string, refund: number): SessionPaymentRecord => ({
  id,
  kind: "SESSION_SETTLED",
  available_delta: refund,
  held_delta: -300000,
  session_id: `session-${id}`,
  created_at: "2026-11-12T09:00:00Z",
  session_end_at: "2026-11-11T10:30:00Z",
  charge_status: refund === 300000 ? "REFUNDED" : "SETTLED",
});
const sessionPayments = [
  settled("paid", 0),
  settled("partial", 100000),
  settled("refunded", 300000),
];
const data: Overview = {
  wallet: { available: 90000, held: 0 },
  pendingIncome: 180000,
  topupEnabled: true,
  topupProvider: "PAYOS",
  topups: [],
  withdrawals: [],
  entries: Array.from({ length: 8 }, (_, i) => ({
    id: String(i),
    kind: "TOPUP",
    available_delta: 10000 + i,
    held_delta: 0,
    created_at: "2026-10-26T10:00:00Z",
    reference_id: "ref",
  })),
};
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(paymentApi.wallet).mockResolvedValue({ success: true, data });
  vi.mocked(paymentApi.sessions).mockResolvedValue({ success: true, data: [] });
  vi.mocked(paymentApi.history).mockImplementation(async (section) => ({
    success: true,
    data: section === "topups" ? [paidTopup] : sessionPayments,
  }));
});
it("shows recent tutor payments and refunds instead of the internal hold", async () => {
  vi.mocked(paymentApi.wallet).mockResolvedValue({
    success: true,
    data: {
      ...data,
      entries: sessionPayments.map((e) => ({
        ...e,
        reference_id: e.session_id,
      })),
    },
  });
  render(<PaymentWorkspace role="PARENT" />);
  const recent = await screen.findByRole("region", {
    name: "Hoạt động gần đây",
  });
  const [paid, partial, refunded] = within(recent).getAllByRole("listitem");
  expect(
    within(paid).getByText("Đã thanh toán cho gia sư"),
  ).toBeInTheDocument();
  expect(within(paid).getByText("-300.000đ")).toBeInTheDocument();
  expect(within(partial).getByText("Thanh toán 200.000đ")).toBeInTheDocument();
  expect(within(partial).getByText("Hoàn +100.000đ")).toBeInTheDocument();
  expect(within(refunded).getByText("Hoàn học phí")).toBeInTheDocument();
  expect(within(refunded).getByText("+300.000đ")).toBeInTheDocument();
  expect(
    within(recent).queryByText(/Giữ học phí|Tất toán/),
  ).not.toBeInTheDocument();
});
it("limits recent activity and loads history only on request", async () => {
  render(<PaymentWorkspace role="STUDENT" />);
  const recent = await screen.findByRole("region", {
    name: "Hoạt động gần đây",
  });
  expect(within(recent).getAllByRole("listitem")).toHaveLength(4);
  expect(paymentApi.history).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Xem tất cả giao dịch" }));
  expect(
    await screen.findByRole("dialog", { name: "Giao dịch của tôi" }),
  ).toBeInTheDocument();
});
it("splits learner history into credited top-ups and session payments", async () => {
  render(<PaymentWorkspace role="STUDENT" />);
  fireEvent.click(
    await screen.findByRole("button", { name: "Xem tất cả giao dịch" }),
  );
  const dialog = await screen.findByRole("dialog", {
    name: "Giao dịch của tôi",
  });
  const topups = within(dialog).getByRole("tab", { name: "Nạp tiền" });
  expect(topups).toHaveAttribute("aria-selected", "true");
  expect(paymentApi.history).toHaveBeenCalledWith("topups", 0);
  const row = await within(dialog).findByRole("listitem");
  expect(within(row).getByText("Nạp 200.000đ")).toBeInTheDocument();
  expect(within(row).getByText(/Mã đơn 1794470279123456/)).toBeInTheDocument();
  expect(within(row).getByText(/Mã giao dịch FT26316BANK/)).toBeInTheDocument();
  expect(
    within(dialog).queryByRole("button", { name: "Xem QR" }),
  ).not.toBeInTheDocument();

  fireEvent.click(
    within(dialog).getByRole("tab", { name: "Thanh toán buổi học" }),
  );
  expect(paymentApi.history).toHaveBeenCalledWith("sessions", 0);
  const [paid, partial, refunded] =
    await within(dialog).findAllByRole("listitem");
  expect(
    within(paid).getByText("Đã thanh toán cho gia sư"),
  ).toBeInTheDocument();
  expect(within(paid).getByText("-300.000đ")).toBeInTheDocument();
  expect(within(partial).getByText("Thanh toán 200.000đ")).toBeInTheDocument();
  expect(within(partial).getByText("Hoàn +100.000đ")).toBeInTheDocument();
  expect(within(refunded).getByText("Hoàn học phí")).toBeInTheDocument();
  expect(
    within(dialog).queryByText(/Giữ học phí|Tất toán/),
  ).not.toBeInTheDocument();
});
it("names the tutor history sections after tutor money flows", async () => {
  render(<PaymentWorkspace role="TUTOR" />);
  fireEvent.click(
    await screen.findByRole("button", { name: "Xem tất cả giao dịch" }),
  );
  const dialog = await screen.findByRole("dialog", {
    name: "Giao dịch của tôi",
  });
  expect(
    within(dialog).getByRole("tab", { name: "Thu nhập buổi dạy" }),
  ).toHaveAttribute("aria-selected", "true");
  expect(
    within(dialog).getByRole("tab", { name: "Rút tiền" }),
  ).toBeInTheDocument();
  expect(
    within(dialog).queryByRole("tab", { name: "Nạp tiền" }),
  ).not.toBeInTheDocument();
  expect(paymentApi.history).toHaveBeenCalledWith("sessions", 0);
});
it("gives parents a family wallet without tutor withdrawal controls", async () => {
  render(<PaymentWorkspace role="PARENT" />);
  expect(
    await screen.findByRole("heading", { name: "Ví học phí gia đình" }),
  ).toBeInTheDocument();
  expect(
    await screen.findByRole("button", { name: "Tạo mã QR" }),
  ).toBeInTheDocument();
  expect(screen.queryByLabelText("Số tiền rút")).not.toBeInTheDocument();
});
it("keeps tutor banking forms off the main page until requested", async () => {
  render(<PaymentWorkspace role="TUTOR" />);
  expect(
    await screen.findByRole("button", { name: "Thêm tài khoản nhận tiền" }),
  ).toBeInTheDocument();
  expect(screen.queryByLabelText("Số tài khoản")).not.toBeInTheDocument();
  fireEvent.click(
    screen.getByRole("button", { name: "Thêm tài khoản nhận tiền" }),
  );
  expect(
    await screen.findByRole("dialog", { name: "Tài khoản nhận tiền" }),
  ).toBeInTheDocument();
  expect(screen.getByLabelText("Số tài khoản")).toBeInTheDocument();
});
it("filters completed sessions while retaining role-specific detail links", async () => {
  const base = {
    classId: "class",
    studentId: "student",
    tutorName: "Gia sư An",
    startAt: "2026-10-26T10:00:00Z",
    endAt: "2026-10-26T11:00:00Z",
    amount: 100000,
    commissionBps: 1000,
    sessionStatus: "COMPLETED",
    canFund: false,
    canActAsLearner: true,
    canActAsTutor: false,
  } as PaymentSession;
  vi.mocked(paymentApi.sessions).mockResolvedValue({
    success: true,
    data: [
      { ...base, sessionId: "active", status: "AWAITING_CONFIRMATION" },
      { ...base, sessionId: "finished", status: "SETTLED" },
    ],
  });
  render(<PaymentWorkspace role="PARENT" />);
  await screen.findByRole("link", { name: /Chi tiết buổi học active/ });
  fireEvent.click(screen.getByRole("button", { name: /Đã xong/ }));
  expect(
    screen.queryByRole("link", { name: /Chi tiết buổi học active/ }),
  ).not.toBeInTheDocument();
  expect(
    screen.getByRole("link", { name: /Chi tiết buổi học finished/ }),
  ).toHaveAttribute("href", "/parent/billing/sessions/finished");
});
it("lists sessions due before and after class with one top-up for the shortfall", async () => {
  const hour = 3600 * 1000;
  const at = (offset: number) => new Date(Date.now() + offset).toISOString();
  const unpaid = {
    classId: "class",
    studentId: "student",
    tutorName: "Gia sư An",
    amount: 100000,
    commissionBps: 1000,
    status: "UNFUNDED",
    canFund: false,
    canActAsLearner: true,
    canActAsTutor: false,
  } as PaymentSession;
  const due = [
    {
      ...unpaid,
      sessionId: "taught",
      sessionStatus: "COMPLETED",
      startAt: at(-3 * hour),
      endAt: at(-2 * hour),
    },
    {
      ...unpaid,
      sessionId: "soon",
      sessionStatus: "SCHEDULED",
      startAt: at(DUE_BEFORE_MS - hour),
      endAt: at(DUE_BEFORE_MS),
    },
  ];
  const later = {
    ...unpaid,
    sessionId: "later",
    sessionStatus: "SCHEDULED",
    startAt: at(3 * DUE_BEFORE_MS),
    endAt: at(3 * DUE_BEFORE_MS + hour),
  };
  vi.mocked(paymentApi.sessions).mockResolvedValue({
    success: true,
    data: [...due, later] as PaymentSession[],
  });
  vi.mocked(paymentApi.topup).mockResolvedValue({
    success: true,
    data: undefined,
  });
  render(<PaymentWorkspace role="STUDENT" />);
  const panel = await screen.findByRole("region", { name: "Cần thanh toán" });
  const total = due.reduce((sum, s) => sum + s.amount, 0);
  const shortfall = total - data.wallet.available;
  expect(panel).toHaveTextContent(
    `${due.length} buổi học đến hạn, tổng ${money(total)}`,
  );
  expect(within(panel).getAllByRole("listitem")).toHaveLength(due.length);
  expect(panel).not.toHaveTextContent(/hủy/);
  fireEvent.click(
    within(panel).getByRole("button", { name: `Nạp ${money(shortfall)}` }),
  );
  await waitFor(() => expect(paymentApi.topup).toHaveBeenCalledWith(shortfall));
});
it("does not show a payment-due panel to tutors", async () => {
  render(<PaymentWorkspace role="TUTOR" />);
  await screen.findByRole("button", { name: "Thêm tài khoản nhận tiền" });
  expect(
    screen.queryByRole("region", { name: "Cần thanh toán" }),
  ).not.toBeInTheDocument();
  expect(paymentApi.sessions).toHaveBeenCalledTimes(1);
});
