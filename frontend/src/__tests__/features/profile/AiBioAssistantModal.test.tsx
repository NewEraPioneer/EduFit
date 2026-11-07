import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { AiBioAssistantModal } from "@/features/profile/components/AiBioAssistantModal";
import { profileService } from "@/services/profile";

vi.mock("@/shared/ui/Toast", () => ({ useToast: () => ({ showToast: vi.fn() }) }));

afterEach(() => vi.restoreAllMocks());

it("lets the tutor edit the generated bio before applying it", async () => {
  vi.spyOn(profileService, "generateTutorBio").mockResolvedValue({
    bio: "Bản thảo AI", isAiGenerated: true, remainingQuota: 2,
  });
  const onApplyBio = vi.fn();
  render(<AiBioAssistantModal isOpen onClose={vi.fn()} onApplyBio={onApplyBio} />);

  fireEvent.click(screen.getByRole("button", { name: "Tạo bản thảo Bio bằng AI" }));
  const editor = await screen.findByRole("textbox", { name: "Chỉnh sửa bản thảo trước khi dùng" });
  fireEvent.change(editor, { target: { value: "Tôi dạy Toán với phương pháp rõ ràng." } });
  fireEvent.click(screen.getByRole("button", { name: "Dùng bản thảo này" }));
  await waitFor(() => expect(onApplyBio).toHaveBeenCalledWith("Tôi dạy Toán với phương pháp rõ ràng."));
});
