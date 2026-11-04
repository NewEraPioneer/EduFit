import React from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { VerifyEmailForm } from "@/features/auth/components/VerifyEmailForm";
import { ForgotPasswordForm } from "@/features/auth/components/ForgotPasswordForm";
import { authApi } from "@/features/auth/api/authApi";
import { verifyOtpSchema } from "@/features/auth/schemas/authSchemas";
import { buildVerifyEmailPath } from "@/features/auth/hooks/useVerifyEmailForm";
import * as apiModule from "@/lib/api";

const EMAIL = "new.student@example.test";
const CODE = "042137";
const NEW_PASSWORD = "BrandNew456@";
const RESET_TOKEN = "a".repeat(64);
const FUTURE = new Date(Date.now() + 60_000).toISOString();
const PAST = new Date(Date.now() - 1_000).toISOString();
const STUDENT = { id: "u-1", email: EMAIL, fullName: "Học sinh", role: "STUDENT" as const, status: "ACTIVE" as const };

const push = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push, replace: vi.fn() }),
}));

const { authMocks } = vi.hoisted(() => ({
  authMocks: { verifyRegistration: vi.fn(), resetPassword: vi.fn() },
}));
vi.mock("@/context/AuthContext", () => ({
  useAuth: () => authMocks,
}));

vi.mock("@/features/auth/utils/authRedirect", () => ({
  resolvePostLoginRedirect: vi.fn(async ({ router }: { router: { push: (p: string) => void } }) =>
    router.push("/onboarding/student")
  ),
}));

describe("Xác thực email sau đăng ký (VerifyEmailForm)", () => {
  beforeEach(() => vi.clearAllMocks());

  it("chỉ cho gửi khi đủ 6 chữ số, lọc ký tự không phải số và chuyển sang onboarding khi đúng mã", async () => {
    authMocks.verifyRegistration.mockResolvedValue(STUDENT);
    render(<VerifyEmailForm email={EMAIL} resendAvailableAt={FUTURE} />);

    const input = screen.getByLabelText("Mã xác thực 6 số");
    const submit = screen.getByRole("button", { name: /Xác thực và tiếp tục/ });
    fireEvent.change(input, { target: { value: "04a21" } });
    expect(input).toHaveValue("0421");
    expect(submit).toBeDisabled();

    fireEvent.change(input, { target: { value: CODE } });
    fireEvent.click(submit);

    await waitFor(() => expect(push).toHaveBeenCalledWith("/onboarding/student"));
    expect(authMocks.verifyRegistration).toHaveBeenCalledWith({ email: EMAIL, code: CODE });
  });

  it("hiển thị lỗi từ backend khi mã sai và không điều hướng", async () => {
    authMocks.verifyRegistration.mockRejectedValue(new Error("Mã xác thực không đúng. Bạn còn 4 lần thử."));
    render(<VerifyEmailForm email={EMAIL} resendAvailableAt={FUTURE} />);

    fireEvent.change(screen.getByLabelText("Mã xác thực 6 số"), { target: { value: CODE } });
    fireEvent.click(screen.getByRole("button", { name: /Xác thực và tiếp tục/ }));

    expect(await screen.findByRole("alert")).toHaveTextContent("còn 4 lần thử");
    expect(push).not.toHaveBeenCalled();
  });

  it("khóa nút gửi lại theo đếm ngược của server, hết hạn chờ thì gửi lại được", async () => {
    const resend = vi.spyOn(authApi, "resendRegistrationCode").mockResolvedValue({
      success: true,
      data: { email: EMAIL, codeExpiresAt: FUTURE, resendAvailableAt: FUTURE },
    });
    const { unmount } = render(<VerifyEmailForm email={EMAIL} resendAvailableAt={FUTURE} />);
    expect(screen.getByRole("button", { name: /Gửi lại mã sau/ })).toBeDisabled();
    unmount();

    render(<VerifyEmailForm email={EMAIL} resendAvailableAt={PAST} />);
    fireEvent.click(screen.getByRole("button", { name: "Gửi lại mã" }));

    expect(await screen.findByRole("status")).toHaveTextContent("Đã gửi mã mới");
    expect(resend).toHaveBeenCalledWith(EMAIL);
  });
});

describe("Quên mật khẩu 3 bước (ForgotPasswordForm)", () => {
  beforeEach(() => vi.clearAllMocks());

  it("email → mã OTP → mật khẩu mới rồi đăng nhập luôn; token không xuất hiện trên URL", async () => {
    const forgot = vi.spyOn(authApi, "forgotPassword").mockResolvedValue({
      success: true,
      data: { email: EMAIL, codeExpiresAt: FUTURE, resendAvailableAt: FUTURE },
    });
    const verify = vi.spyOn(authApi, "verifyPasswordResetCode").mockResolvedValue({
      success: true,
      data: { resetToken: RESET_TOKEN, expiresAt: FUTURE },
    });
    authMocks.resetPassword.mockResolvedValue(STUDENT);
    render(<ForgotPasswordForm />);

    fireEvent.change(screen.getByLabelText("Địa chỉ Email đã đăng ký"), { target: { value: ` ${EMAIL} ` } });
    fireEvent.click(screen.getByRole("button", { name: "Gửi mã xác thực" }));
    await screen.findByLabelText("Mã xác thực 6 số");
    expect(forgot).toHaveBeenCalledWith({ email: EMAIL });

    fireEvent.change(screen.getByLabelText("Mã xác thực 6 số"), { target: { value: CODE } });
    fireEvent.click(screen.getByRole("button", { name: "Xác thực mã" }));
    await screen.findByLabelText("Mật khẩu mới");
    expect(verify).toHaveBeenCalledWith({ email: EMAIL, code: CODE });

    fireEvent.change(screen.getByLabelText("Mật khẩu mới"), { target: { value: NEW_PASSWORD } });
    fireEvent.change(screen.getByLabelText("Xác nhận mật khẩu mới"), { target: { value: NEW_PASSWORD } });
    fireEvent.click(screen.getByRole("button", { name: /Lưu mật khẩu mới/ }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/onboarding/student"));
    expect(authMocks.resetPassword).toHaveBeenCalledWith({ token: RESET_TOKEN, newPassword: NEW_PASSWORD });
    expect(push.mock.calls.flat().join(" ")).not.toContain(RESET_TOKEN);
  });

  it("mật khẩu xác nhận không khớp thì không gọi API đặt lại", async () => {
    vi.spyOn(authApi, "forgotPassword").mockResolvedValue({
      success: true,
      data: { email: EMAIL, codeExpiresAt: FUTURE, resendAvailableAt: FUTURE },
    });
    vi.spyOn(authApi, "verifyPasswordResetCode").mockResolvedValue({
      success: true,
      data: { resetToken: RESET_TOKEN, expiresAt: FUTURE },
    });
    render(<ForgotPasswordForm />);
    fireEvent.change(screen.getByLabelText("Địa chỉ Email đã đăng ký"), { target: { value: EMAIL } });
    fireEvent.click(screen.getByRole("button", { name: "Gửi mã xác thực" }));
    fireEvent.change(await screen.findByLabelText("Mã xác thực 6 số"), { target: { value: CODE } });
    fireEvent.click(screen.getByRole("button", { name: "Xác thực mã" }));

    fireEvent.change(await screen.findByLabelText("Mật khẩu mới"), { target: { value: NEW_PASSWORD } });
    fireEvent.change(screen.getByLabelText("Xác nhận mật khẩu mới"), { target: { value: "Different789@" } });
    fireEvent.click(screen.getByRole("button", { name: /Lưu mật khẩu mới/ }));

    expect(await screen.findByText("Mật khẩu xác nhận không khớp!")).toBeInTheDocument();
    expect(authMocks.resetPassword).not.toHaveBeenCalled();
  });
});

describe("Hợp đồng dữ liệu OTP", () => {
  beforeEach(() => vi.restoreAllMocks());

  it("verifyOtpSchema chỉ nhận đúng 6 chữ số và chuẩn hóa email", () => {
    expect(verifyOtpSchema.safeParse({ email: " A@B.TEST ", code: CODE }).data?.email).toBe("a@b.test");
    for (const bad of ["12345", "1234567", "12a456", ""]) {
      expect(verifyOtpSchema.safeParse({ email: EMAIL, code: bad }).success).toBe(false);
    }
  });

  it("đường dẫn màn hình xác thực mã hóa email và thời điểm gửi lại", () => {
    expect(buildVerifyEmailPath("a+b@x.test", FUTURE)).toBe(
      `/verify-email?email=a%2Bb%40x.test&resendAt=${encodeURIComponent(FUTURE)}`
    );
  });

  it("xác thực đăng ký chuẩn hóa accountId thành id như đăng nhập", async () => {
    vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: { accountId: "acc-1", email: EMAIL, fullName: "Học sinh", role: "STUDENT", status: "ACTIVE" },
    });

    const response = await authApi.verifyRegistration({ email: EMAIL, code: CODE });

    expect(response.data?.id).toBe("acc-1");
  });
});
