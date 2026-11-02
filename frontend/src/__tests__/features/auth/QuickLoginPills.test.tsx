import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { QuickLoginPills } from "@/features/auth/components/QuickLoginPills";

const DEMO_FLAG = "NEXT_PUBLIC_ENABLE_DEMO_LOGIN";

describe("QuickLoginPills", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("không hiển thị tài khoản demo khi chưa bật cờ dev", () => {
    vi.stubEnv(DEMO_FLAG, "");
    const { container } = render(<QuickLoginPills onSelect={vi.fn()} />);

    expect(container).toBeEmptyDOMElement();
    expect(screen.queryByRole("button", { name: "Admin" })).not.toBeInTheDocument();
  });

  it("hiển thị tài khoản demo khi bật cờ dev", () => {
    vi.stubEnv(DEMO_FLAG, "true");
    render(<QuickLoginPills onSelect={vi.fn()} />);

    expect(screen.getByRole("button", { name: "Admin" })).toBeInTheDocument();
  });
});
