import React from "react";
import { beforeEach, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { SessionBillingPanel } from "@/features/payment/components/SessionBillingPanel";
import { paymentApi, type PaymentSession, type LearningAccess } from "@/features/payment/api/paymentApi";

const session: PaymentSession = {
  sessionId: "lesson", classId: "class", studentId: "student", tutorName: "Gia sư",
  sessionStatus: "SCHEDULED", startAt: new Date(Date.now()-60000).toISOString(), endAt: new Date(Date.now()+3600000).toISOString(),
  amount: 250000, commissionBps: 1000, payerId: "parent", status: "FUNDED", learnerAccepted: false,
  tutorAccepted: false, autoAccepted: false, disputeDeadline: null, disputeReason: null, learnerAmount: null,
  tutorAmount: null, learnerReport: null, tutorReport: null, settledAmount: 0, dueAt: null, due: false, canActAsLearner: true, canActAsTutor: false,
};
let readiness: LearningAccess;
beforeEach(() => {
  vi.restoreAllMocks();
  readiness = { funded: true, startedAt: null, canStart: true, message: "Đã giữ học phí", meetingLink: null };
  vi.spyOn(paymentApi,"session").mockResolvedValue({success:true,data:session});
  vi.spyOn(paymentApi,"learningAccess").mockImplementation(async () => ({success:true,data:readiness}));
});

it("chưa thu học phí vẫn bắt đầu học được và thấy hệ thống sẽ tự thu", async () => {
  readiness = {funded:false,startedAt:null,canStart:true,message:"Sẵn sàng bắt đầu buổi học. Học phí chưa được thu, hệ thống sẽ tự thu khi ví đủ tiền.",meetingLink:null};
  render(<SessionBillingPanel sessionId="lesson" role="STUDENT" mode="ONLINE" />);
  expect(await screen.findByText(/hệ thống sẽ tự thu khi ví đủ tiền/)).toBeInTheDocument();
  expect(screen.getByRole("button",{name:"Bắt đầu buổi học"})).toBeInTheDocument();
  expect(screen.queryByRole("link",{name:"Vào phòng học"})).not.toBeInTheDocument();
});

it("đủ học phí thì xác nhận bắt đầu trước khi hiện phòng học", async () => {
  const start = vi.spyOn(paymentApi,"startLesson").mockImplementation(async () => {
    readiness={...readiness,startedAt:new Date().toISOString(),meetingLink:"https://meet.google.com/demo"};
    return {success:true,data:readiness};
  });
  const topup = vi.spyOn(paymentApi,"topup");
  render(<SessionBillingPanel sessionId="lesson" role="STUDENT" mode="ONLINE" />);
  fireEvent.click(await screen.findByRole("button",{name:"Bắt đầu buổi học"}));
  await waitFor(() => expect(start).toHaveBeenCalledWith("lesson"));
  expect(await screen.findByRole("link",{name:"Vào phòng học"})).toHaveAttribute("href","https://meet.google.com/demo");
  expect(topup).not.toHaveBeenCalled();
});
