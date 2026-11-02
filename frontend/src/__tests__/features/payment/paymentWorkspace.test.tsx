import "./dialogSetup";
import React from "react";
import { beforeEach, expect, it, vi } from "vitest";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { PaymentWorkspace } from "@/features/payment/components/PaymentWorkspace";
import {
  paymentApi,
  type Overview,
  type PaymentSession,
} from "@/features/payment/api/paymentApi";
vi.mock("@/features/payment/api/paymentApi", async () => ({
  ...(await vi.importActual<typeof import("@/features/payment/api/paymentApi")>(
    "@/features/payment/api/paymentApi",
  )),
  paymentApi: { wallet: vi.fn(), sessions: vi.fn() },
}));
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
});
it("limits recent activity and loads the full ledger only on request", async () => {
  render(<PaymentWorkspace role="STUDENT" />);
  const recent = await screen.findByRole("region", {
    name: "Hoạt động gần đây",
  });
  expect(within(recent).getAllByRole("listitem")).toHaveLength(4);
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Xem tất cả giao dịch" }));
  const dialog = await screen.findByRole("dialog", {
    name: "Giao dịch của tôi",
  });
  expect(await within(dialog).findByRole("table")).toBeInTheDocument();
  expect(within(dialog).getAllByRole("row")).toHaveLength(9);
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
