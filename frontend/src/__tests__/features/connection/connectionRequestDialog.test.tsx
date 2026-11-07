import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ConnectionRequestDialog } from "@/features/connection";
import { connectionService } from "@/services/connections";
import { learningNeedService } from "@/services/learningNeeds";
import { discoveryApi } from "@/features/discovery/api/discoveryApi";
import { makeFit, makeNeed, makeOffering, makeTutorCard } from "../../fixtures/learningNeeds";

vi.mock("@/features/learning-needs/hooks/useLearningCatalog", () => ({
  useLearningCatalog: () => ({ catalog: null, error: null }),
}));

const mathNeed = makeNeed();
const tutor = { tutorId: "tutor", displayName: "Thầy A", offerings: [makeOffering()] };

beforeEach(() => {
  vi.spyOn(learningNeedService, "list").mockResolvedValue([mathNeed]);
  vi.spyOn(discoveryApi, "getFit").mockResolvedValue({
    success: true,
    data: makeTutorCard({ fit: makeFit() }),
  });
});
afterEach(() => vi.restoreAllMocks());

describe("Connection request dialog", () => {
  it("shows server errors and never reports success for a failed connection", async () => {
    vi.spyOn(connectionService, "send").mockRejectedValue(new Error("Đã có yêu cầu đang chờ"));
    const onSent = vi.fn();
    render(<ConnectionRequestDialog tutorId="tutor" tutor={tutor} onClose={vi.fn()} onSent={onSent} />);
    fireEvent.click(await screen.findByRole("button", { name: "Xác nhận gửi yêu cầu" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Đã có yêu cầu đang chờ");
    expect(onSent).not.toHaveBeenCalled();
  });

  it("sends the selected need, tutor, its slots and the optional message before success", async () => {
    const send = vi.spyOn(connectionService, "send").mockResolvedValue({
      requestId: "request", studentId: "student", tutorId: "tutor", needId: "need-1",
      status: "PENDING", createdAt: "2030-01-01", expiresAt: "2030-01-08",
    });
    const onSent = vi.fn();
    render(<ConnectionRequestDialog tutorId="tutor" tutor={tutor} onClose={vi.fn()} onSent={onSent} />);
    fireEvent.change(await screen.findByRole("textbox"), { target: { value: "Xin học thử" } });
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận gửi yêu cầu" }));
    await waitFor(() => expect(onSent).toHaveBeenCalledTimes(1));
    expect(send).toHaveBeenCalledWith({
      needId: "need-1",
      tutorId: "tutor",
      desiredSlots: ["Thứ 3 · 19:00–21:00"],
      message: "Xin học thử",
    });
  });

  it("allows a different proposed schedule for this tutor without changing the learning need", async () => {
    const send = vi.spyOn(connectionService, "send").mockResolvedValue({
      requestId: "request", studentId: "student", tutorId: "tutor", needId: "need-1",
      status: "PENDING", createdAt: "2030-01-01", expiresAt: "2030-01-08",
    });
    render(<ConnectionRequestDialog tutorId="tutor" tutor={tutor} onClose={vi.fn()} onSent={vi.fn()} />);
    fireEvent.click(await screen.findByText("Đổi lịch đề xuất cho gia sư này"));
    fireEvent.change(await screen.findByLabelText("Giờ bắt đầu Thứ 3"), { target: { value: "18:00" } });
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận gửi yêu cầu" }));
    await waitFor(() => expect(send).toHaveBeenCalled());
    expect(send).toHaveBeenCalledWith(expect.objectContaining({
      desiredSlots: ["Thứ 3 · 18:00–21:00"],
    }));
    expect(mathNeed.availabilitySlots[0].startTime).toBe("19:00:00");
  });

  it("shows soft fit warnings but still allows sending", async () => {
    render(<ConnectionRequestDialog tutorId="tutor" tutor={tutor} onClose={vi.fn()} onSent={vi.fn()} />);
    expect(await screen.findByText("Học phí vượt ngân sách")).toBeInTheDocument();
    expect(screen.getByText(/Vẫn có thể gửi/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Xác nhận gửi yêu cầu" })).toBeEnabled();
    expect(discoveryApi.getFit).toHaveBeenCalledWith("need-1", "tutor");
  });

  it("only offers needs in subjects the tutor teaches", async () => {
    vi.mocked(learningNeedService.list).mockResolvedValue([
      mathNeed,
      makeNeed({ needId: "english", subjectId: 2, subjectName: "Tiếng Anh" }),
    ]);
    render(<ConnectionRequestDialog tutorId="tutor" tutor={tutor} onClose={vi.fn()} onSent={vi.fn()} />);
    const picker = await screen.findByRole("combobox");
    expect(picker).toHaveTextContent("Toán");
    expect(picker).not.toHaveTextContent("Tiếng Anh");
  });

  it("asks to create a need for a taught subject when none matches", async () => {
    vi.mocked(learningNeedService.list).mockResolvedValue([makeNeed({ subjectId: 2, subjectName: "Tiếng Anh" })]);
    render(<ConnectionRequestDialog tutorId="tutor" tutor={tutor} onClose={vi.fn()} onSent={vi.fn()} />);
    expect(await screen.findByText("Chưa có nhu cầu học cho môn gia sư này dạy")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Tạo nhu cầu môn Toán" }));
    expect(screen.getByRole("dialog", { name: "Thêm nhu cầu học" })).toBeInTheDocument();
  });

  it("blocks sending an incomplete need and offers to complete it", async () => {
    vi.mocked(learningNeedService.list).mockResolvedValue([
      makeNeed({ status: "INCOMPLETE", missingFields: ["availabilitySlots"], availabilitySlots: [] }),
    ]);
    const send = vi.spyOn(connectionService, "send");
    render(<ConnectionRequestDialog tutorId="tutor" tutor={tutor} onClose={vi.fn()} onSent={vi.fn()} />);
    expect(await screen.findByRole("button", { name: /Bổ sung:/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Xác nhận gửi yêu cầu" })).toBeDisabled();
    expect(discoveryApi.getFit).not.toHaveBeenCalled();
    expect(send).not.toHaveBeenCalled();
  });

  it("loads the linked child's needs for parents", async () => {
    render(<ConnectionRequestDialog tutorId="tutor" tutor={tutor} studentId="child" onClose={vi.fn()} onSent={vi.fn()} />);
    await screen.findByRole("combobox");
    expect(learningNeedService.list).toHaveBeenCalledWith("child");
  });
});
