import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { CreateLearningPlanModal } from "@/features/progress/components/CreateLearningPlanModal";
import { SessionOutcomeModal } from "@/features/progress/components/SessionOutcomeModal";
import { progressApi } from "@/features/progress/api/progressApi";
import { FIXTURE_IDS, FIXTURE_TEXT, buildPlan } from "../features/progress/progressFixtures";
import { MAX_PLAN_MILESTONES, MIN_PLAN_MILESTONES } from "@/features/progress/constants/roadmap";

const CLASS_ID = FIXTURE_IDS.class;

describe("QA R3: Kế hoạch học tập & Phản hồi buổi học (Progress & Learning Plan)", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("1. Ca biên CreateLearningPlanModal (trọng số 1–5, 3–8 cột mốc, 1–4 tiêu chí)", () => {
    beforeEach(() => {
      vi.spyOn(progressApi, "listPlanTemplates").mockResolvedValue({ success: true, data: [] });
    });

    it(`mặc định ${MIN_PLAN_MILESTONES} cột mốc, không có nút xóa khi chỉ còn ${MIN_PLAN_MILESTONES}`, () => {
      render(<CreateLearningPlanModal isOpen={true} onClose={vi.fn()} classId={CLASS_ID} className="Toán 9" />);
      expect(screen.getByText(`Cột mốc ${MIN_PLAN_MILESTONES}`)).toBeInTheDocument();
      expect(screen.queryByTitle("Xóa cột mốc này")).not.toBeInTheDocument();
    });

    it("đổi trọng số 1→5 cập nhật tổng trọng số", () => {
      render(<CreateLearningPlanModal isOpen={true} onClose={vi.fn()} classId={CLASS_ID} />);
      expect(screen.getByText(new RegExp(`Tổng trọng số: ${MIN_PLAN_MILESTONES}`))).toBeInTheDocument();
      fireEvent.click(screen.getAllByRole("button", { name: "5" })[0]);
      expect(screen.getByText(new RegExp(`Tổng trọng số: ${MIN_PLAN_MILESTONES + 4}`))).toBeInTheDocument();
    });

    it(`giới hạn tối đa ${MAX_PLAN_MILESTONES} cột mốc`, () => {
      render(<CreateLearningPlanModal isOpen={true} onClose={vi.fn()} classId={CLASS_ID} />);
      const addBtn = screen.getByRole("button", { name: /Thêm cột mốc/i });
      for (let i = MIN_PLAN_MILESTONES; i < MAX_PLAN_MILESTONES; i++) fireEvent.click(addBtn);
      expect(screen.getByText(`Cột mốc ${MAX_PLAN_MILESTONES}`)).toBeInTheDocument();
      expect(addBtn).toBeDisabled();
    });

    it("giới hạn tối đa 4 tiêu chí mỗi cột mốc", () => {
      render(<CreateLearningPlanModal isOpen={true} onClose={vi.fn()} classId={CLASS_ID} />);
      const addCriterion = screen.getAllByRole("button", { name: /Thêm tiêu chí/i })[0];
      for (let i = 0; i < 3; i++) fireEvent.click(addCriterion);
      expect(screen.getByLabelText("Tiêu chí 4 của cột mốc 1")).toBeInTheDocument();
      expect(addCriterion).toBeDisabled();
    });

    it("chặn submit khi thiếu tiêu đề hoặc thiếu tiêu chí đạt", async () => {
      const handleSuccess = vi.fn();
      render(<CreateLearningPlanModal isOpen={true} onClose={vi.fn()} classId={CLASS_ID} onSuccess={handleSuccess} />);

      fireEvent.click(screen.getByRole("button", { name: "Tạo kế hoạch" }));
      expect(await screen.findByRole("alert")).toBeInTheDocument();
      expect(screen.getAllByText("Tiêu đề cột mốc không được để trống").length).toBeGreaterThanOrEqual(1);

      screen.getAllByPlaceholderText(/Nắm vững Định lý Vi-ét/i).forEach((input) =>
        fireEvent.change(input, { target: { value: FIXTURE_TEXT.milestoneA } })
      );
      fireEvent.click(screen.getByRole("button", { name: "Tạo kế hoạch" }));
      expect(await screen.findAllByText("Mỗi cột mốc cần ít nhất 1 tiêu chí đạt")).not.toHaveLength(0);
      expect(handleSuccess).not.toHaveBeenCalled();
    });

    it("hiển thị lỗi máy chủ khi API thất bại mà không crash modal", async () => {
      vi.spyOn(progressApi, "createPlan").mockRejectedValue(new Error("Máy chủ quá tải"));
      render(<CreateLearningPlanModal isOpen={true} onClose={vi.fn()} classId={CLASS_ID} />);

      screen.getAllByPlaceholderText(/Nắm vững Định lý Vi-ét/i).forEach((input, idx) => {
        fireEvent.change(input, { target: { value: `${FIXTURE_TEXT.milestoneA} ${idx + 1}` } });
        fireEvent.change(screen.getByLabelText(`Tiêu chí 1 của cột mốc ${idx + 1}`), {
          target: { value: FIXTURE_TEXT.criterionA1 },
        });
      });
      fireEvent.change(screen.getByLabelText(/Hạn dự kiến \*/), { target: { value: FIXTURE_TEXT.finalTargetDate } });
      fireEvent.click(screen.getByRole("button", { name: "Tạo kế hoạch" }));

      expect(await screen.findByRole("alert")).toBeInTheDocument();
      expect(screen.getByText("Máy chủ quá tải")).toBeInTheDocument();
    });
  });

  describe("2. SessionOutcomeModal: vắng mặt bỏ qua phản hồi, có mặt gửi phản hồi", () => {
    const session = {
      sessionId: FIXTURE_IDS.session,
      classId: CLASS_ID,
      startAt: "2026-10-17T08:00:00.000Z",
      endAt: "2026-10-17T09:30:00.000Z",
    };

    it("chọn Vắng mặt thì ẩn phần chấm mức tự lập", () => {
      render(<SessionOutcomeModal isOpen={true} onClose={vi.fn()} session={session} plan={buildPlan()} />);
      expect(screen.getByText(/Cột mốc dạy hôm nay/i)).toBeInTheDocument();
      fireEvent.click(screen.getByRole("button", { name: /Vắng mặt/i }));
      expect(screen.queryByText(/Cột mốc dạy hôm nay/i)).not.toBeInTheDocument();
    });

    it("có mặt: gửi điểm danh và phản hồi, chốt đạt khi tick đủ tiêu chí", async () => {
      const recordOutcome = vi.spyOn(progressApi, "recordSessionOutcome").mockResolvedValue({ success: true });
      const submitFeedback = vi.spyOn(progressApi, "submitSessionFeedback").mockResolvedValue({
        success: true,
        data: { homeworkStatus: "NONE", attitudeChips: [], assessments: [], createdAt: session.endAt },
      });
      const handleSuccess = vi.fn();
      render(
        <SessionOutcomeModal isOpen={true} onClose={vi.fn()} session={session} plan={buildPlan()} onSuccess={handleSuccess} />
      );

      fireEvent.click(screen.getByRole("checkbox", { name: FIXTURE_TEXT.criterionA1 }));
      fireEvent.click(screen.getByRole("checkbox", { name: FIXTURE_TEXT.criterionA2 }));
      fireEvent.click(screen.getByRole("radio", { name: /4\. Vững/i }));
      fireEvent.click(screen.getByRole("checkbox", { name: /Chốt đạt cột mốc này/i }));
      fireEvent.click(screen.getByRole("button", { name: "Không có bài về nhà" }));
      fireEvent.click(screen.getByRole("button", { name: /Lưu buổi học/i }));

      await waitFor(() => {
        expect(recordOutcome).toHaveBeenCalledWith(FIXTURE_IDS.session, { outcome: "COMPLETED", noteContent: undefined });
        expect(submitFeedback).toHaveBeenCalledWith(
          FIXTURE_IDS.session,
          expect.objectContaining({
            homeworkStatus: "NONE",
            milestones: [
              expect.objectContaining({
                milestoneId: FIXTURE_IDS.milestoneA,
                level: 4,
                checkedCriterionIds: [FIXTURE_IDS.criterionA1, FIXTURE_IDS.criterionA2],
                markAchieved: true,
              }),
            ],
          })
        );
        expect(handleSuccess).toHaveBeenCalled();
      });
    });
  });
  describe("3. Giai đoạn 2: mức 4 cần đủ tiêu chí, lời nhắn riêng, để sau", () => {
    const session = {
      sessionId: FIXTURE_IDS.session,
      classId: CLASS_ID,
      startAt: "2026-10-17T08:00:00.000Z",
      endAt: "2026-10-17T09:30:00.000Z",
    };

    it("khóa mức 4 khi chưa tick đủ tiêu chí; bỏ tick thì bỏ chọn mức 4", () => {
      render(<SessionOutcomeModal isOpen={true} onClose={vi.fn()} session={session} plan={buildPlan()} />);
      const mastery = screen.getByRole("radio", { name: /4\. Vững/i });
      expect(mastery).toBeDisabled();
      fireEvent.click(screen.getByRole("checkbox", { name: FIXTURE_TEXT.criterionA1 }));
      fireEvent.click(screen.getByRole("checkbox", { name: FIXTURE_TEXT.criterionA2 }));
      expect(mastery).toBeEnabled();
      fireEvent.click(mastery);
      expect(mastery).toHaveAttribute("aria-checked", "true");
      fireEvent.click(screen.getByRole("checkbox", { name: FIXTURE_TEXT.criterionA2 }));
      expect(mastery).toHaveAttribute("aria-checked", "false");
    });

    it("gửi lời nhắn riêng cho phụ huynh và câu bằng chứng cùng phản hồi", async () => {
      vi.spyOn(progressApi, "recordSessionOutcome").mockResolvedValue({ success: true });
      const submitFeedback = vi.spyOn(progressApi, "submitSessionFeedback").mockResolvedValue({
        success: true,
        data: { homeworkStatus: "NONE", attitudeChips: [], assessments: [], createdAt: session.endAt },
      });
      render(<SessionOutcomeModal isOpen={true} onClose={vi.fn()} session={session} plan={buildPlan()} />);

      fireEvent.click(screen.getByRole("radio", { name: /3\. Tự làm đúng/i }));
      fireEvent.change(screen.getByRole("textbox", { name: /Bằng chứng/i }), {
        target: { value: FIXTURE_TEXT.evidence },
      });
      fireEvent.click(screen.getByRole("button", { name: "Không có bài về nhà" }));
      fireEvent.change(screen.getByLabelText(/Lời nhắn riêng cho phụ huynh/i), {
        target: { value: FIXTURE_TEXT.parentMessage },
      });
      fireEvent.click(screen.getByRole("button", { name: /Lưu buổi học/i }));

      await waitFor(() =>
        expect(submitFeedback).toHaveBeenCalledWith(
          FIXTURE_IDS.session,
          expect.objectContaining({
            parentMessage: FIXTURE_TEXT.parentMessage,
            milestones: [expect.objectContaining({ level: 3, evidenceNote: FIXTURE_TEXT.evidence })],
          })
        )
      );
    });

    it("Để sau: chỉ ghi điểm danh, không gửi phản hồi", async () => {
      const recordOutcome = vi.spyOn(progressApi, "recordSessionOutcome").mockResolvedValue({ success: true });
      const submitFeedback = vi.spyOn(progressApi, "submitSessionFeedback");
      const handleSuccess = vi.fn();
      render(
        <SessionOutcomeModal isOpen={true} onClose={vi.fn()} session={session} plan={buildPlan()} onSuccess={handleSuccess} />
      );

      fireEvent.click(screen.getByRole("button", { name: "Để sau" }));

      await waitFor(() => expect(handleSuccess).toHaveBeenCalled());
      expect(recordOutcome).toHaveBeenCalledWith(FIXTURE_IDS.session, { outcome: "COMPLETED", noteContent: undefined });
      expect(submitFeedback).not.toHaveBeenCalled();
    });

    it("bổ sung phản hồi cho buổi đã điểm danh: không gọi lại điểm danh, không có nút Để sau", async () => {
      const recordOutcome = vi.spyOn(progressApi, "recordSessionOutcome");
      vi.spyOn(progressApi, "submitSessionFeedback").mockResolvedValue({
        success: true,
        data: { homeworkStatus: "NONE", attitudeChips: [], assessments: [], createdAt: session.endAt },
      });
      const handleSuccess = vi.fn();
      render(
        <SessionOutcomeModal
          isOpen={true}
          onClose={vi.fn()}
          session={session}
          plan={buildPlan()}
          outcomeAlreadyRecorded
          onSuccess={handleSuccess}
        />
      );
      expect(screen.getByText("Bổ sung phản hồi buổi học")).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: "Để sau" })).not.toBeInTheDocument();

      fireEvent.click(screen.getByRole("radio", { name: /2\. Cần gợi ý/i }));
      fireEvent.click(screen.getByRole("button", { name: "Không có bài về nhà" }));
      fireEvent.click(screen.getByRole("button", { name: "Lưu phản hồi" }));

      await waitFor(() => expect(handleSuccess).toHaveBeenCalled());
      expect(recordOutcome).not.toHaveBeenCalled();
    });
  });
});
