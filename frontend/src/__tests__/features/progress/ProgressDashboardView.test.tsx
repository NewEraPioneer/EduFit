import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ProgressDashboardView } from "@/features/progress/components/ProgressDashboardView";
import { progressApi } from "@/features/progress/api/progressApi";
import type { ParentDashboardResponse } from "@/features/progress/types";
import { FIXTURE_IDS, FIXTURE_TEXT, buildOverview, buildStudentDashboard } from "./progressFixtures";

const lateFeedbackNote = () => {
  const note = buildStudentDashboard().recentNotes![0];
  return { ...note, feedback: { ...note.feedback!, late: true, parentMessage: FIXTURE_TEXT.parentMessage } };
};

vi.mock("@/features/progress/api/progressApi", () => ({
  progressApi: {
    getStudentDashboard: vi.fn(),
    getParentDashboard: vi.fn(),
    rateSession: vi.fn(),
  },
}));

const renderStudent = () => render(<ProgressDashboardView role="STUDENT" studentId={FIXTURE_IDS.student} />);

describe("ProgressDashboardView — 3 thẻ, lộ trình cột mốc, sổ liên lạc", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("hiển thị trạng thái đang tải", () => {
    vi.mocked(progressApi.getStudentDashboard).mockReturnValue(new Promise(() => {}));
    renderStudent();
    expect(screen.getByText(/Đang tải dữ liệu tiến độ/i)).toBeInTheDocument();
  });

  it("hiển thị lỗi và cho phép thử lại", async () => {
    const user = userEvent.setup();
    vi.mocked(progressApi.getStudentDashboard).mockRejectedValueOnce(new Error("Lỗi kết nối máy chủ"));
    renderStudent();
    expect(await screen.findByText(/Lỗi kết nối máy chủ/i)).toBeInTheDocument();

    vi.mocked(progressApi.getStudentDashboard).mockResolvedValueOnce({ success: true, data: buildStudentDashboard() });
    await user.click(screen.getByRole("button", { name: /Thử lại/i }));
    expect(await screen.findByText("65%")).toBeInTheDocument();
  });

  it("render đúng 3 thẻ từ dữ liệu backend, không tự tính", async () => {
    vi.mocked(progressApi.getStudentDashboard).mockResolvedValue({ success: true, data: buildStudentDashboard() });
    renderStudent();

    const onTrack = await screen.findByTestId("card-on-track");
    expect(within(onTrack).getByText("Cần chú ý")).toBeInTheDocument();
    expect(within(onTrack).getByText(/trễ 2 ngày so với kế hoạch/)).toBeInTheDocument();

    const goal = screen.getByTestId("card-goal-progress");
    expect(within(goal).getByText("65%")).toBeInTheDocument();
    expect(within(goal).getByText("+20%")).toBeInTheDocument();
    expect(within(goal).getByText(/từ “làm được khi có gợi ý” → “tự làm đúng”/)).toBeInTheDocument();

    const attendance = screen.getByTestId("card-attendance");
    expect(within(attendance).getByText("Đi học 7/8 buổi")).toBeInTheDocument();
    expect(within(attendance).getByText(new RegExp(`đang học ${FIXTURE_TEXT.milestoneA}`))).toBeInTheDocument();
    expect(within(attendance).getByText("Có bài về nhà")).toBeInTheDocument();
    expect(within(attendance).getByText("1 buổi dời do gia sư")).toBeInTheDocument();
  });

  it("trạng thái trống: '—' kèm lý do, không hiện số giả", async () => {
    vi.mocked(progressApi.getStudentDashboard).mockResolvedValue({
      success: true,
      data: buildStudentDashboard({
        plan: null,
        progressPercentage: null,
        recentNotes: [],
        overview: buildOverview({
          stage: "NO_PLAN",
          onTrack: { status: "NO_PLAN", explanation: "Gia sư chưa lập lộ trình" },
          goalProgress: { ...buildOverview().goalProgress, progressPercentage: null, progressDelta: null, highlight: null },
          attendance: { ...buildOverview().attendance, attendedCount: 0, countedCount: 0, attendanceRate: null, missedSessions: [] },
          milestoneLevels: [],
        }),
      }),
    });
    renderStudent();

    const goal = await screen.findByTestId("card-goal-progress");
    expect(within(goal).getAllByText("—")).toHaveLength(2);
    expect(within(screen.getByTestId("card-on-track")).getByText("Gia sư chưa lập lộ trình")).toBeInTheDocument();
    expect(within(screen.getByTestId("card-attendance")).getByText(/Chưa có buổi học nào được ghi nhận/)).toBeInTheDocument();
    expect(screen.getByText(/Sau mỗi buổi học, gia sư sẽ ghi phản hồi ngắn/)).toBeInTheDocument();
  });

  it("lộ trình cột mốc hiển thị mức đầu vào → hiện tại và tiêu chí", async () => {
    vi.mocked(progressApi.getStudentDashboard).mockResolvedValue({ success: true, data: buildStudentDashboard() });
    renderStudent();

    const row = await screen.findByTestId(`milestone-${FIXTURE_IDS.milestoneA}`);
    expect(within(row).getByText("Từ “Làm được khi có gợi ý” → “Tự làm đúng”")).toBeInTheDocument();
    expect(within(row).getByText(FIXTURE_TEXT.criterionA1)).toBeInTheDocument();
    expect(within(screen.getByTestId(`milestone-${FIXTURE_IDS.milestoneB}`)).getByText("Chưa có lần đánh giá")).toBeInTheDocument();
  });

  it("sổ liên lạc hiển thị phản hồi có cấu trúc", async () => {
    vi.mocked(progressApi.getStudentDashboard).mockResolvedValue({ success: true, data: buildStudentDashboard() });
    renderStudent();

    expect(await screen.findByText(/đúng 7\/10 bài/)).toBeInTheDocument();
    expect(screen.getByText("Có giao bài về nhà")).toBeInTheDocument();
    expect(screen.getByText("Tập trung tốt")).toBeInTheDocument();
    expect(screen.getByText("Con làm nhanh hơn, cần cẩn thận dấu.")).toBeInTheDocument();
  });

  it("phụ huynh chuyển đổi giữa các con", async () => {
    const user = userEvent.setup();
    const parentData: ParentDashboardResponse = {
      students: [
        { studentId: "student-a", studentName: "Con A", classes: [], activeClassDashboard: buildStudentDashboard() },
        {
          studentId: "student-b",
          studentName: "Con B",
          classes: [],
          activeClassDashboard: buildStudentDashboard({
            overview: buildOverview({ goalProgress: { ...buildOverview().goalProgress, progressPercentage: 40 } }),
          }),
        },
      ],
    };
    vi.mocked(progressApi.getParentDashboard).mockResolvedValue({ success: true, data: parentData });
    render(<ProgressDashboardView role="PARENT" />);

    expect(await screen.findByText("65%")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Con B" }));
    await waitFor(() => expect(screen.getByText("40%")).toBeInTheDocument());
  });
  it("học sinh chọn cảm nhận 1 chạm cho buổi đã hoàn thành, lưu và cập nhật tại chỗ", async () => {
    const user = userEvent.setup();
    vi.mocked(progressApi.getStudentDashboard).mockResolvedValue({ success: true, data: buildStudentDashboard() });
    vi.mocked(progressApi.rateSession).mockResolvedValue({
      success: true,
      data: { sessionId: FIXTURE_IDS.session, rating: 3 },
    });
    renderStudent();

    const confident = await screen.findByRole("radio", { name: "Hiểu bài" });
    expect(confident).toHaveAttribute("aria-checked", "false");
    await user.click(confident);

    expect(progressApi.rateSession).toHaveBeenCalledWith(FIXTURE_IDS.session, 3);
    await waitFor(() => expect(confident).toHaveAttribute("aria-checked", "true"));
  });

  it("phụ huynh thấy xu hướng cảm nhận, lời nhắn riêng và nhãn bổ sung muộn; không có nút 1 chạm", async () => {
    const dashboard = buildStudentDashboard({
      overview: buildOverview({
        selfRatings: [
          { sessionId: FIXTURE_IDS.session, startAt: "2026-10-28T11:00:00Z", rating: 1 },
          { sessionId: FIXTURE_IDS.milestoneB, startAt: "2026-10-30T11:00:00Z", rating: 3 },
        ],
      }),
      recentNotes: [lateFeedbackNote()],
    });
    const parentData: ParentDashboardResponse = {
      students: [
        { studentId: FIXTURE_IDS.student, studentName: FIXTURE_TEXT.studentName, classes: dashboard.classes, activeClassDashboard: dashboard },
      ],
    };
    vi.mocked(progressApi.getParentDashboard).mockResolvedValue({ success: true, data: parentData });
    render(<ProgressDashboardView role="PARENT" />);

    const trend = await screen.findByTestId("self-rating-trend");
    expect(within(trend).getAllByRole("listitem")).toHaveLength(2);
    expect(within(trend).getByLabelText(/Còn thấy khó/)).toBeInTheDocument();
    expect(screen.getByText(FIXTURE_TEXT.parentMessage)).toBeInTheDocument();
    expect(screen.getByText("Bổ sung muộn")).toBeInTheDocument();
    expect(screen.queryByRole("radiogroup", { name: /Em thấy buổi học/ })).not.toBeInTheDocument();
  });
});
