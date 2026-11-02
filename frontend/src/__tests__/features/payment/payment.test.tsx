import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { SessionPaymentCard } from "@/features/payment/components/SessionPaymentCard";
import {
  paymentApi,
  type PaymentSession,
} from "@/features/payment/api/paymentApi";
vi.mock("@/features/payment/api/paymentApi", async () => {
  const actual = await vi.importActual<
    typeof import("@/features/payment/api/paymentApi")
  >("@/features/payment/api/paymentApi");
  return {
    ...actual,
    paymentApi: {
      fund: vi.fn(),
      confirm: vi.fn(),
      dispute: vi.fn(),
      report: vi.fn(),
      comment: vi.fn(),
      completeSession: vi.fn(),
    },
  };
});
const session = (patch: Partial<PaymentSession> = {}): PaymentSession => ({
  sessionId: "session-1",
  classId: "class-1",
  studentId: "student-1",
  tutorName: "Gia sư An",
  sessionStatus: "COMPLETED",
  startAt: new Date(Date.now() - 7200000).toISOString(),
  endAt: new Date(Date.now() - 1800000).toISOString(),
  amount: 300000,
  commissionBps: 1000,
  payerId: "parent-1",
  status: "AWAITING_CONFIRMATION",
  learnerAccepted: false,
  tutorAccepted: false,
  autoAccepted: false,
  reviewDeadline: new Date(Date.now() + 84600000).toISOString(),
  canSubmitProposal: true,
  isPayer: true,
  comments: [],
  disputeDeadline: null,
  disputeReason: null,
  learnerAmount: null,
  tutorAmount: null,
  learnerReport: null,
  tutorReport: null,
  settledAmount: 0,
  canFund: false,
  canActAsLearner: true,
  canActAsTutor: false,
  ...patch,
});
const execute = async (work: () => Promise<unknown>) => {
  await work();
  return true;
};
beforeEach(() => vi.clearAllMocks());
function show(patch: Partial<PaymentSession> = {}) {
  return render(
    <SessionPaymentCard
      session={session(patch)}
      disabled={false}
      execute={execute}
    />,
  );
}
describe("Review window and shared payment case", () => {
  it("records attendance without asking to release money early", async () => {
    show();
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận đã học" }));
    expect(
      screen.getByText(/Chỉ xác nhận việc học đã diễn ra/),
    ).toBeInTheDocument();
    expect(paymentApi.confirm).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Ghi nhận xác nhận" }));
    await waitFor(() =>
      expect(paymentApi.confirm).toHaveBeenCalledWith("session-1"),
    );
  });
  it("allows a problem after both attendance confirmations until the persisted deadline", () => {
    const view = show({ learnerAccepted: true, tutorAccepted: true });
    expect(
      screen.getByRole("button", { name: "Báo vấn đề" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Xác nhận đã học" }),
    ).not.toBeInTheDocument();
    view.rerender(
      <SessionPaymentCard
        session={session({
          reviewDeadline: new Date(Date.now() - 1000).toISOString(),
        })}
        disabled={false}
        execute={execute}
      />,
    );
    expect(
      screen.queryByRole("button", { name: "Báo vấn đề" }),
    ).not.toBeInTheDocument();
    expect(screen.getByText(/Đang chờ hệ thống tất toán/)).toBeInTheDocument();
  });
  it("uses the stored deadline for a late completion instead of scheduled end plus 24h", () => {
    show({ endAt: new Date(Date.now() - 172800000).toISOString() });
    expect(
      screen.getByRole("button", { name: "Báo vấn đề" }),
    ).toBeInTheDocument();
  });
  it("opens one case with category and evidence", async () => {
    show();
    fireEvent.click(screen.getByRole("button", { name: "Báo vấn đề" }));
    fireEvent.change(screen.getByLabelText("Bạn gặp vấn đề gì?"), {
      target: { value: "DURATION" },
    });
    fireEvent.change(screen.getByLabelText("Nội dung vấn đề"), {
      target: { value: "Chỉ học 20 phút" },
    });
    fireEvent.change(
      screen.getByLabelText("Bằng chứng (mô tả hoặc đường dẫn)"),
      { target: { value: "Lịch sử phòng học" } },
    );
    fireEvent.click(screen.getByRole("button", { name: "Gửi vấn đề" }));
    await waitFor(() =>
      expect(paymentApi.dispute).toHaveBeenCalledWith(
        "session-1",
        "Chỉ học 20 phút",
        "DURATION",
        "Lịch sử phòng học",
      ),
    );
  });
  it("lets a nonpayer student add evidence without proposing money", async () => {
    show({
      status: "DISPUTED",
      disputeReason: "Thiếu giờ",
      isPayer: false,
      canSubmitProposal: false,
      disputeDeadline: new Date(Date.now() + 172800000).toISOString(),
    });
    expect(
      screen.queryByRole("button", { name: "Đề xuất cách giải quyết" }),
    ).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Bổ sung thông tin" }));
    fireEvent.change(screen.getByLabelText("Thông tin bổ sung"), {
      target: { value: "Tôi có mặt lúc 19h" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Gửi phản hồi" }));
    await waitFor(() =>
      expect(paymentApi.comment).toHaveBeenCalledWith(
        "session-1",
        expect.any(String),
        "Tôi có mặt lúc 19h",
      ),
    );
  });
  it("keeps an overdue case frozen for Admin instead of promising an automatic refund", () => {
    show({ status: "ADMIN_REVIEW", disputeReason: "Chưa thống nhất" });
    expect(screen.getByText("Chờ Admin phân xử")).toBeInTheDocument();
    expect(
      screen.getByText(/Tiền được giữ đến khi có quyết định/),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Đề xuất cách giải quyết" }),
    ).not.toBeInTheDocument();
  });
  it("asks before reserving the displayed tuition", async () => {
    show({ status: "UNFUNDED", canFund: true, canActAsLearner: false });
    fireEvent.click(screen.getByRole("button", { name: "Giữ học phí từ ví" }));
    expect(paymentApi.fund).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận giữ tiền" }));
    await waitFor(() =>
      expect(paymentApi.fund).toHaveBeenCalledWith("session-1", 300000),
    );
  });
  it("offers tutors the existing scheduling completion action before opening a review window", async () => {
    show({
      status: "FUNDED",
      sessionStatus: "SCHEDULED",
      canActAsTutor: true,
      canActAsLearner: false,
      reviewDeadline: null,
    });
    expect(
      screen.queryByRole("button", { name: "Ghi nhận đã dạy" }),
    ).not.toBeInTheDocument();
    fireEvent.click(
      screen.getByRole("button", { name: "Ghi nhận buổi học hoàn thành" }),
    );
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận kết thúc" }));
    await waitFor(() =>
      expect(paymentApi.completeSession).toHaveBeenCalledWith("session-1"),
    );
  });
});
