import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ProgressDashboardView } from "@/features/progress/components/ProgressDashboardView";
import { progressApi } from "@/features/progress/api/progressApi";
import type { ParentDashboardResponse } from "@/features/progress/types";
import {
  FIXTURE_IDS,
  FIXTURE_TEXT,
  FIXTURE_TOPICS,
  buildOverview,
  buildPeriodReport,
  buildStudentDashboard,
} from "./progressFixtures";

const lateFeedbackNote = () => {
  const note = buildStudentDashboard().recentNotes![0];
  return { ...note, feedback: { ...note.feedback!, late: true, parentMessage: FIXTURE_TEXT.parentMessage } };
};

vi.mock("@/features/progress/api/progressApi", () => ({
  progressApi: {
    getStudentDashboard: vi.fn(),
    getParentDashboard: vi.fn(),
    rateSession: vi.fn(),
    listPeriodReports: vi.fn(),
  },
}));

const renderStudent = () => render(<ProgressDashboardView role="STUDENT" studentId={FIXTURE_IDS.student} />);

describe("ProgressDashboardView — 3 thẻ, lộ trình cột mốc, sổ liên lạc", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(progressApi.listPeriodReports).mockResolvedValue({ success: true, data: [] });
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

    const nextSession = await screen.findByTestId("card-next-session");
    expect(within(nextSession).getByText(/trễ 2 ngày so với kế hoạch/)).toBeInTheDocument();

    const goal = screen.getByTestId("card-goal-progress");
    expect(within(goal).getByText("65%")).toBeInTheDocument();
    expect(within(goal).getByText("Tăng từ 45% lên 65% trong 30 ngày qua")).toBeInTheDocument();

    const attendance = screen.getByTestId("card-attendance");
    expect(within(attendance).getByText("8/12 buổi")).toBeInTheDocument();
    expect(within(attendance).getByText("Có mặt 7 · Vắng 1")).toBeInTheDocument();
    expect(within(attendance).getByText("Buổi vắng, hủy")).toBeInTheDocument();
    expect(within(attendance).queryByText(/Buổi tới/)).not.toBeInTheDocument();
    expect(within(attendance).queryByText("Có bài về nhà")).not.toBeInTheDocument();
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
          attendance: { ...buildOverview().attendance, nextSessionStart: null, attendedCount: 0, countedCount: 0, totalCount: 0, attendanceRate: null, missedSessions: [] },
          milestoneLevels: [],
        }),
      }),
    });
    renderStudent();

    const goal = await screen.findByTestId("card-goal-progress");
    expect(within(goal).getByText("—")).toBeInTheDocument();
    expect(within(goal).queryByRole("progressbar")).not.toBeInTheDocument();
    expect(within(screen.getByTestId("card-next-session")).getByText("Chưa có lịch")).toBeInTheDocument();
    expect(within(screen.getByTestId("card-attendance")).getByText(/Chưa có buổi học/)).toBeInTheDocument();
    expect(screen.getByText("Chưa có phản hồi.")).toBeInTheDocument();
  });

  it("lộ trình cột mốc hiển thị mức đầu vào → hiện tại và tiêu chí", async () => {
    vi.mocked(progressApi.getStudentDashboard).mockResolvedValue({ success: true, data: buildStudentDashboard() });
    renderStudent();

    const row = await screen.findByTestId(`milestone-${FIXTURE_IDS.milestoneA}`);
    expect(within(row).getByText("Từ “Làm được khi có gợi ý” → “Tự làm đúng”")).toBeInTheDocument();
    expect(within(row).getByText(FIXTURE_TEXT.criterionA1)).toBeInTheDocument();
    expect(within(screen.getByTestId(`milestone-${FIXTURE_IDS.milestoneB}`)).getByText("Chưa đánh giá")).toBeInTheDocument();
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
    expect(progressApi.getParentDashboard).toHaveBeenCalledTimes(1);
  });

  it("phụ huynh chọn lớp chỉ tải tiến độ một lần và báo lỗi khi đổi lớp thất bại", async () => {
    const user = userEvent.setup();
    const classes = [
      { classId: FIXTURE_IDS.class, title: "Lớp Toán", status: "ACTIVE", progressPercentage: 65 },
      { classId: "class-two", title: "Lớp Văn", status: "ACTIVE", progressPercentage: 40 },
    ];
    vi.mocked(progressApi.getParentDashboard).mockResolvedValue({
      success: true,
      data: {
        students: [{ studentId: "student-a", studentName: "Con A", classes: [], activeClassDashboard: buildStudentDashboard({ classes }) }],
      },
    });
    vi.mocked(progressApi.getStudentDashboard).mockRejectedValueOnce(new Error("Không đổi được lớp"));
    render(<ProgressDashboardView role="PARENT" />);

    await user.click(await screen.findByRole("tab", { name: "Lớp Văn" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Không đổi được lớp");
    expect(progressApi.getStudentDashboard).toHaveBeenCalledWith("student-a", "class-two");
    expect(progressApi.getStudentDashboard).toHaveBeenCalledTimes(1);
    expect(progressApi.getParentDashboard).toHaveBeenCalledTimes(1);
  });

  it("bỏ qua phản hồi cũ khi đổi lớp nhanh", async () => {
    const user = userEvent.setup();
    const classes = [
      { classId: FIXTURE_IDS.class, title: "Lớp Toán", status: "ACTIVE", progressPercentage: 65 },
      { classId: "class-two", title: "Lớp Văn", status: "ACTIVE", progressPercentage: 40 },
      { classId: "class-three", title: "Lớp Anh", status: "ACTIVE", progressPercentage: 10 },
    ];
    const mk = (pct: number) =>
      buildStudentDashboard({
        classes,
        overview: buildOverview({ goalProgress: { ...buildOverview().goalProgress, progressPercentage: pct } }),
      });
    let resolveSlow: (v: { success: boolean; data: ReturnType<typeof mk> }) => void = () => {};
    vi.mocked(progressApi.getStudentDashboard)
      .mockResolvedValueOnce({ success: true, data: mk(65) })
      .mockReturnValueOnce(new Promise((r) => { resolveSlow = r; }))
      .mockResolvedValueOnce({ success: true, data: mk(10) });
    renderStudent();

    await user.click(await screen.findByRole("tab", { name: "Lớp Văn" }));
    await user.click(screen.getByRole("tab", { name: "Lớp Anh" }));
    expect(await screen.findByText("10%")).toBeInTheDocument();
    resolveSlow({ success: true, data: mk(40) });
    await waitFor(() => expect(screen.getByText("10%")).toBeInTheDocument());
    expect(screen.queryByText("40%")).not.toBeInTheDocument();
  });

  it("dưới 3 chủ đề: hiện thanh trạng thái thay vì radar", async () => {
    vi.mocked(progressApi.getStudentDashboard).mockResolvedValue({
      success: true,
      data: buildStudentDashboard({
        overview: buildOverview({
          radar: { axes: [{ topicId: FIXTURE_TOPICS[0].topicId, topicName: FIXTURE_TOPICS[0].name, level: 2 }] },
        }),
      }),
    });
    renderStudent();
    const radar = await screen.findByTestId("card-radar");
    expect(within(radar).queryByRole("img")).not.toBeInTheDocument();
    expect(within(radar).getByRole("progressbar", { name: `Mức tự lập: ${FIXTURE_TOPICS[0].name}` })).toHaveAttribute("aria-valuenow", "2");
  });

  it("thẻ buổi học hiện trạng thái tiếng Việt, không in enum thô", async () => {
    const base = buildStudentDashboard();
    vi.mocked(progressApi.getStudentDashboard).mockResolvedValue({
      success: true,
      data: { ...base, recentNotes: [{ ...base.recentNotes![0], status: "CANCELLED", feedback: null }] },
    });
    renderStudent();
    expect(await screen.findByText("Đã hủy")).toBeInTheDocument();
    expect(screen.queryByText("CANCELLED")).not.toBeInTheDocument();
  });

  it("chọn lớp chỉ tải tiến độ một lần", async () => {
    const user = userEvent.setup();
    vi.mocked(progressApi.getStudentDashboard).mockResolvedValue({ success: true, data: buildStudentDashboard({
      classes: [
        { classId: FIXTURE_IDS.class, title: "Lớp Toán", status: "ACTIVE", progressPercentage: 65 },
        { classId: "class-two", title: "Lớp Văn", status: "ACTIVE", progressPercentage: 40 },
      ],
    }) });
    renderStudent();
    await user.click(await screen.findByRole("tab", { name: "Lớp Văn" }));
    await waitFor(() => expect(progressApi.getStudentDashboard).toHaveBeenCalledWith(FIXTURE_IDS.student, "class-two"));
    expect(progressApi.getStudentDashboard).toHaveBeenCalledTimes(2);
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

  it("dự báo và radar hiển thị số do backend tính; thiếu dữ liệu thì '—' kèm lý do", async () => {
    vi.mocked(progressApi.getStudentDashboard).mockResolvedValue({
      success: true,
      data: buildStudentDashboard({
        overview: buildOverview({
          forecast: {
            remainingSessions: 9,
            remainingMilestones: 2,
            basis: "PACE",
            estimatedFinishDate: "2026-12-10",
            unavailableReason: null,
          },
          radar: {
            axes: FIXTURE_TOPICS.map((t, i) => ({ topicId: t.topicId, topicName: t.name, level: (i + 2) as 2 | 3 | 4 })),
            unavailableReason: null,
          },
        }),
      }),
    });
    const { unmount } = renderStudent();

    const forecast = await screen.findByTestId("card-forecast");
    expect(within(forecast).getByText("Còn khoảng 9 buổi cho 2 cột mốc")).toBeInTheDocument();
    expect(within(forecast).getByText(/10\/12\/2026/)).toBeInTheDocument();
    const radar = screen.getByTestId("card-radar");
    expect(within(radar).getByRole("img", { name: /radar/i })).toBeInTheDocument();
    for (const t of FIXTURE_TOPICS) expect(within(radar).getAllByText(t.name).length).toBeGreaterThan(0);
    unmount();

    vi.mocked(progressApi.getStudentDashboard).mockResolvedValue({
      success: true,
      data: buildStudentDashboard({
        overview: buildOverview({
          forecast: { unavailableReason: FIXTURE_TEXT.forecastUnavailable },
          radar: { axes: [], unavailableReason: "Chưa có điểm chấm theo chủ đề" },
        }),
      }),
    });
    renderStudent();
    const emptyForecast = await screen.findByTestId("card-forecast");
    expect(within(emptyForecast).getByText("—")).toBeInTheDocument();
    expect(within(emptyForecast).getByText(FIXTURE_TEXT.forecastUnavailable)).toBeInTheDocument();
    expect(within(screen.getByTestId("card-radar")).queryByRole("img")).not.toBeInTheDocument();
  });

  it("báo cáo kỳ đã gửi hiện kèm câu tổng kết của gia sư", async () => {
    vi.mocked(progressApi.getStudentDashboard).mockResolvedValue({ success: true, data: buildStudentDashboard() });
    vi.mocked(progressApi.listPeriodReports).mockResolvedValue({
      success: true,
      data: [buildPeriodReport({ status: "SENT", sentAt: "2026-10-02T03:00:00Z", tutorSummary: FIXTURE_TEXT.reportSummary })],
    });
    renderStudent();

    const section = await screen.findByRole("region", { name: "Báo cáo kỳ" });
    expect(progressApi.listPeriodReports).toHaveBeenCalledWith(FIXTURE_IDS.class);
    expect(within(section).getByText(/Báo cáo kỳ 1 · 01\/09\/2026 – 30\/09\/2026/)).toBeInTheDocument();
    expect(within(section).getByText(new RegExp(FIXTURE_TEXT.reportSummary))).toBeInTheDocument();
    expect(within(section).queryByText(/Bản nháp/)).not.toBeInTheDocument();
  });
});
