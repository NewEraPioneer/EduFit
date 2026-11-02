import React from "react";
import { beforeAll, beforeEach, afterEach, expect, it, vi } from "vitest";
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { WalletPanel } from "@/features/payment/components/WalletPanel";
import { paymentApi, type Overview } from "@/features/payment/api/paymentApi";

vi.mock("@/features/payment/api/paymentApi", async () => ({
  ...(await vi.importActual<typeof import("@/features/payment/api/paymentApi")>(
    "@/features/payment/api/paymentApi",
  )),
  paymentApi: { topup: vi.fn(), refreshTopup: vi.fn() },
}));
const exit = vi.fn();
const open = vi.fn();
const sdk = vi.fn((config: { onSuccess: () => void }) => { void config; return { open, exit }; });
const overview: Overview = {
  wallet: { available: 100000, held: 0 },
  entries: [],
  topups: [],
  withdrawals: [],
  topupEnabled: true,
  topupProvider: "PAYOS",
};
const execute = async (work: () => Promise<unknown>) => {
  try {
    await work();
    return true;
  } catch {
    return false;
  }
};
beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute("open", "");
  };
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute("open");
  };
});
beforeEach(() => {
  vi.clearAllMocks();
  Object.assign(window, { PayOSCheckout: { usePayOS: sdk } });
  vi.mocked(paymentApi.topup).mockResolvedValue({
    success: true,
    data: {
      id: "deposit-1",
      provider: "PAYOS",
      checkoutUrl: "https://pay.payos.vn/web/checkout/fixture",
    },
  });
  vi.mocked(paymentApi.refreshTopup).mockResolvedValue({
    success: true,
    data: { id: "deposit-1", status: "PENDING" } as never,
  });
});
afterEach(() => {
  Reflect.deleteProperty(window, "PayOSCheckout");
});
function show(data = overview) {
  return render(
    <WalletPanel data={data} tutor={false} busy={false} execute={execute} />,
  );
}
async function createQR() {
  fireEvent.click(screen.getByRole("button", { name: "Tạo mã QR" }));
  return screen.findByRole("dialog", { name: "Nạp tiền vào ví" });
}
it("opens an embedded checkout in a dialog and disposes it when closed", async () => {
  show();
  await createQR();
  await waitFor(() => expect(open).toHaveBeenCalledTimes(1));
  expect(sdk).toHaveBeenCalledWith(
    expect.objectContaining({
      CHECKOUT_URL: "https://pay.payos.vn/web/checkout/fixture",
      embedded: true,
    }),
  );
  expect(
    screen.queryByRole("link", { name: /Mở trang QR/ }),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Đóng popup" }));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(exit).toHaveBeenCalledTimes(1);
  sdk.mock.calls[0][0].onSuccess();
  expect(paymentApi.refreshTopup).not.toHaveBeenCalled();
});
it("never claims credit from the SDK callback until the backend verifies PAID", async () => {
  const { rerender } = show();
  await createQR();
  await waitFor(() => expect(sdk).toHaveBeenCalled());
  await act(async () => {
    sdk.mock.calls[0][0].onSuccess();
  });
  await waitFor(() =>
    expect(paymentApi.refreshTopup).toHaveBeenCalledWith("deposit-1"),
  );
  expect(screen.queryByText("Nạp tiền thành công")).not.toBeInTheDocument();
  rerender(
    <WalletPanel
      data={{
        ...overview,
        topups: [{ id: "deposit-1", status: "PAID" } as never],
      }}
      tutor={false}
      busy={false}
      execute={execute}
    />,
  );
  expect(await screen.findByText("Nạp tiền thành công")).toBeInTheDocument();
});
it("renders manual QR inside the popup and explains manual verification", async () => {
  vi.mocked(paymentApi.topup).mockResolvedValue({
    success: true,
    data: {
      id: "manual-1",
      provider: "MANUAL",
      amount: 100000,
      qrUrl: "https://img.vietqr.io/image/970422-12345678-compact2.png",
      description: "EF1",
      bankAccount: "12345678",
      bankHolder: "EDUFIT",
    },
  });
  show();
  await createQR();
  expect(
    screen.getByRole("img", { name: "Mã QR chuyển khoản" }),
  ).toHaveAttribute("src", expect.stringContaining("img.vietqr.io"));
  expect(
    screen.getByText(/Admin xác minh giao dịch thực tế/),
  ).toBeInTheDocument();
  expect(sdk).not.toHaveBeenCalled();
});
it("resumes an existing pending checkout without creating another order", async () => {
  show({
    ...overview,
    topups: [
      {
        id: "existing",
        provider: "PAYOS",
        amount: 200000,
        status: "PENDING",
        checkout_url: "https://pay.payos.vn/web/checkout/existing",
        order_code: 42,
        created_at: new Date().toISOString(),
        user_id: "user",
      },
    ],
  });
  fireEvent.click(screen.getByRole("button", { name: "Tiếp tục nạp tiền" }));
  await screen.findByRole("dialog");
  await waitFor(() => expect(open).toHaveBeenCalled());
  expect(paymentApi.topup).not.toHaveBeenCalled();
});
it("refuses to embed an untrusted checkout URL", async () => {
  vi.mocked(paymentApi.topup).mockResolvedValue({
    success: true,
    data: {
      id: "invalid",
      provider: "PAYOS",
      checkoutUrl: "https://pay.payos.vn.evil.example/checkout",
    },
  });
  show();
  await createQR();
  expect(await screen.findByRole("alert")).toHaveTextContent(
    /đường dẫn thanh toán không hợp lệ/i,
  );
  expect(sdk).not.toHaveBeenCalled();
});

it("shows a recoverable SDK loading error without creating another deposit", async () => {
  Reflect.deleteProperty(window, "PayOSCheckout");
  show();
  await createQR();
  const script = document.querySelector(
    'script[src="https://cdn.payos.vn/payos-checkout/v1/stable/payos-initialize.js"]',
  );
  expect(script).not.toBeNull();
  fireEvent.error(script!);
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Không tải được mã QR payOS",
  );
  expect(
    screen.getByRole("button", { name: "Hiển thị lại mã QR" }),
  ).toBeEnabled();
  expect(paymentApi.topup).toHaveBeenCalledTimes(1);
});
it("does not resume an expired pending order", () => {
  show({
    ...overview,
    topups: [
      {
        id: "old",
        provider: "PAYOS",
        amount: 100000,
        status: "PENDING",
        checkout_url: "https://pay.payos.vn/web/checkout/old",
        expires_at: "2020-01-01T00:00:00Z",
        order_code: 42,
        created_at: "2020-01-01",
        user_id: "user",
      },
    ],
  });
  expect(
    screen.queryByRole("button", { name: "Tiếp tục nạp tiền" }),
  ).not.toBeInTheDocument();
});
