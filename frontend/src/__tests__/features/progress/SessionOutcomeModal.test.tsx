import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SessionOutcomeModal } from "@/features/progress/components/SessionOutcomeModal";
import { progressApi } from "@/features/progress/api/progressApi";
import { FIXTURE_IDS, FIXTURE_TEXT, buildPlan } from "./progressFixtures";

vi.mock("@/features/progress/api/progressApi", () => ({
  progressApi: {
    recordSessionOutcome: vi.fn(),
    submitSessionFeedback: vi.fn(),
  },
}));

const session = {
  sessionId: FIXTURE_IDS.session,
  classId: FIXTURE_IDS.class,
  startAt: "2026-10-30T11:00:00Z",
  endAt: "2026-10-30T12:30:00Z",
  studentName: FIXTURE_TEXT.studentName,
};

function renderModal(overrides: Partial<React.ComponentProps<typeof SessionOutcomeModal>> = {}) {
  const props = { isOpen: true, onClose: vi.fn(), onSuccess: vi.fn(), session, plan: buildPlan(), ...overrides };
  render(<SessionOutcomeModal {...props} />);
  return props;
}

describe("SessionOutcomeModal — biểu mẫu phản hồi 10 giây", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(progressApi.recordSessionOutcome).mockResolvedValue({ success: true, data: {} });
    vi.mocked(progressApi.submitSessionFeedback).mockResolvedValue({
      success: true,
      data: { homeworkStatus: "ASSIGNED", attitudeChips: [], assessments: [], createdAt: session.endAt },
    });
  });

  it("không render khi isOpen=false", () => {
    const { container } = render(
      <SessionOutcomeModal isOpen={false} onClose={vi.fn()} session={session} plan={buildPlan()} />
    );
    expect(container.firstChild).toBeNull();
  });

  it("mặc định chọn cột mốc đang học (1 chạm) và hiện 4 nút mức tự lập", () => {
    renderModal();
    expect(screen.getByRole("button", { name: FIXTURE_TEXT.milestoneA })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: FIXTURE_TEXT.milestoneB })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getAllByRole("radio")).toHaveLength(4);
  });

  it("chặn gửi khi chưa chọn mức tự lập hoặc chip việc về nhà", async () => {
    const user = userEvent.setup();
    renderModal();

    await user.click(screen.getByRole("button", { name: /Lưu buổi học/i }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Cần chọn mức tự lập");

    await user.click(screen.getByRole("radio", { name: /3\. Tự làm đúng/i }));
    await user.click(screen.getByRole("button", { name: /Lưu buổi học/i }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Cần chọn chip việc về nhà");
    expect(progressApi.recordSessionOutcome).not.toHaveBeenCalled();
  });

  it("chỉ cho chốt đạt khi tick đủ tiêu chí", async () => {
    const user = userEvent.setup();
    renderModal();
    const achieve = screen.getByRole("checkbox", { name: /Chốt đạt cột mốc này/i });
    expect(achieve).toBeDisabled();

    await user.click(screen.getByRole("checkbox", { name: FIXTURE_TEXT.criterionA1 }));
    expect(achieve).toBeDisabled();
    await user.click(screen.getByRole("checkbox", { name: FIXTURE_TEXT.criterionA2 }));
    expect(achieve).toBeEnabled();
  });

  it("gửi điểm danh rồi phản hồi có cấu trúc đúng hợp đồng API", async () => {
    const user = userEvent.setup();
    const props = renderModal();

    await user.click(screen.getByRole("radio", { name: /2\. Cần gợi ý/i }));
    await user.click(screen.getByRole("checkbox", { name: FIXTURE_TEXT.criterionA1 }));
    await user.type(screen.getByLabelText(new RegExp(`Số bài đúng — ${FIXTURE_TEXT.milestoneA}`)), "7");
    await user.type(screen.getByLabelText(new RegExp(`Tổng số bài — ${FIXTURE_TEXT.milestoneA}`)), "10");
    await user.click(screen.getByRole("button", { name: "Có giao bài về nhà" }));
    await user.click(screen.getByRole("button", { name: "Tập trung tốt" }));
    await user.type(screen.getByLabelText(/Một câu nhận xét/i), "Con tiến bộ rõ.");
    await user.click(screen.getByRole("button", { name: /Lưu buổi học/i }));

    await waitFor(() => {
      expect(progressApi.recordSessionOutcome).toHaveBeenCalledWith(FIXTURE_IDS.session, {
        outcome: "COMPLETED",
        noteContent: "Con tiến bộ rõ.",
      });
      expect(progressApi.submitSessionFeedback).toHaveBeenCalledWith(FIXTURE_IDS.session, {
        homeworkStatus: "ASSIGNED",
        attitudeChips: ["FOCUSED"],
        milestones: [
          {
            milestoneId: FIXTURE_IDS.milestoneA,
            level: 2,
            correctCount: 7,
            totalCount: 10,
            checkedCriterionIds: [FIXTURE_IDS.criterionA1],
            markAchieved: false,
          },
        ],
      });
      expect(props.onSuccess).toHaveBeenCalled();
      expect(props.onClose).toHaveBeenCalled();
    });
  });

  it("vắng mặt: chỉ ghi điểm danh, không gửi phản hồi", async () => {
    const user = userEvent.setup();
    renderModal();
    await user.click(screen.getByRole("button", { name: /Vắng mặt/i }));
    expect(screen.queryAllByRole("radio")).toHaveLength(0);

    await user.click(screen.getByRole("button", { name: /Lưu buổi học/i }));
    await waitFor(() => expect(progressApi.recordSessionOutcome).toHaveBeenCalled());
    expect(progressApi.submitSessionFeedback).not.toHaveBeenCalled();
  });

  it("phản hồi lỗi sau khi đã điểm danh: giữ modal, gửi lại chỉ phản hồi", async () => {
    const user = userEvent.setup();
    vi.mocked(progressApi.submitSessionFeedback)
      .mockResolvedValueOnce({ success: false, message: "Xung đột phiên bản" })
      .mockResolvedValueOnce({
        success: true,
        data: { homeworkStatus: "NONE", attitudeChips: [], assessments: [], createdAt: session.endAt },
      });
    const props = renderModal();

    await user.click(screen.getByRole("radio", { name: /1\. Chưa làm được/i }));
    await user.click(screen.getByRole("button", { name: "Không có bài về nhà" }));
    await user.click(screen.getByRole("button", { name: /Lưu buổi học/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Đã ghi nhận điểm danh");
    expect(props.onClose).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: /Gửi lại phản hồi/i }));
    await waitFor(() => expect(props.onSuccess).toHaveBeenCalled());
    expect(progressApi.recordSessionOutcome).toHaveBeenCalledTimes(1);
    expect(progressApi.submitSessionFeedback).toHaveBeenCalledTimes(2);
  });

  it("lớp chưa có lộ trình: báo chỉ ghi điểm danh", () => {
    renderModal({ plan: null });
    expect(screen.getByText(/Lớp chưa có lộ trình nên chỉ ghi điểm danh/i)).toBeInTheDocument();
  });
});
