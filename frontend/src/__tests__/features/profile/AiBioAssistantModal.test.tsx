import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { AiBioAssistantModal } from "@/features/profile/components/AiBioAssistantModal";
import { profileService } from "@/services/profile";

vi.mock("@/shared/ui/Toast", () => ({ useToast: () => ({ showToast: vi.fn() }) }));

afterEach(() => vi.restoreAllMocks());

const bio = {
  original: "Tôi có 3 năm kinh nghiệm dạy Toán.",
  edited: "Tôi dạy Toán với phương pháp rõ ràng.",
  generated: "Bản thảo AI",
  remainingQuota: 2,
  tone: "professional",
} as const;

const labels = {
  generate: "Tạo bản thảo Bio bằng AI",
  editor: "Chỉnh sửa bản thảo trước khi dùng",
  apply: "Dùng bản thảo này",
  rewrite: "Soạn thảo lại với AI",
  professional: /Chuyên nghiệp/,
};

async function generateDraft() {
  const generateTutorBio = vi.spyOn(profileService, "generateTutorBio").mockResolvedValue({
    bio: bio.generated,
    isAiGenerated: true,
    remainingQuota: bio.remainingQuota,
  });
  const onApplyBio = vi.fn();
  render(
    <AiBioAssistantModal isOpen currentBio={bio.original} onClose={vi.fn()} onApplyBio={onApplyBio} />,
  );

  fireEvent.click(screen.getByRole("button", { name: labels.generate }));
  const editor = await screen.findByRole("textbox", { name: labels.editor });
  return { editor, generateTutorBio, onApplyBio };
}

it("keeps the editor open when the draft is cleared and applies the replacement", async () => {
  const { editor, onApplyBio } = await generateDraft();
  fireEvent.change(editor, { target: { value: "" } });

  expect(screen.getByRole("textbox", { name: labels.editor })).toBe(editor);
  const applyButton = screen.getByRole("button", { name: labels.apply });
  expect(applyButton).toBeDisabled();

  fireEvent.change(editor, { target: { value: bio.edited } });
  fireEvent.click(applyButton);
  expect(onApplyBio).toHaveBeenCalledWith(bio.edited);
});

it("uses the edited draft and selected tone when rewriting", async () => {
  const { editor, generateTutorBio } = await generateDraft();
  expect(generateTutorBio).toHaveBeenLastCalledWith(
    expect.objectContaining({ currentBio: bio.original }),
  );

  fireEvent.change(editor, { target: { value: bio.edited } });
  fireEvent.click(screen.getByRole("button", { name: labels.professional }));
  fireEvent.click(screen.getByRole("button", { name: labels.rewrite }));

  await waitFor(() =>
    expect(generateTutorBio).toHaveBeenLastCalledWith(
      expect.objectContaining({ currentBio: bio.edited, tone: bio.tone }),
    ),
  );
});
