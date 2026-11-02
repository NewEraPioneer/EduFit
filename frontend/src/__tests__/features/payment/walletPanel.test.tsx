import "./dialogSetup";
import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { WalletPanel } from "@/features/payment/components/WalletPanel";
import { paymentApi, type Overview } from "@/features/payment/api/paymentApi";
vi.mock("@/features/payment/api/paymentApi", async () => {
  const actual = await vi.importActual<
    typeof import("@/features/payment/api/paymentApi")
  >("@/features/payment/api/paymentApi");
  return {
    ...actual,
    paymentApi: {
      topup: vi.fn(),
      withdraw: vi.fn(),
      withdrawSaved: vi.fn(),
      savePayoutAccount: vi.fn(),
    },
  };
});
const execute = async (work: () => Promise<unknown>) => {
  await work();
  return true;
};
beforeEach(() => vi.clearAllMocks());
const overview: Overview = {
  wallet: { available: 1000000, held: 300000 },
  entries: [],
  topups: [],
  withdrawals: [],
  topupEnabled: true,
  topupProvider: "MANUAL",
};
describe("Wallet funding", () => {
  it("disabled provider cannot create a topup or show fake success", () => {
    render(
      <WalletPanel
        data={{ ...overview, topupEnabled: false }}
        tutor={false}
        busy={false}
        execute={execute}
      />,
    );
    expect(screen.getByRole("button", { name: "Tạo mã QR" })).toBeDisabled();
    expect(
      screen.getByText(/Kênh nạp tiền chưa được cấu hình/),
    ).toBeInTheDocument();
  });
  it("manual checkout only shows bank instructions, never increments the balance", async () => {
    vi.mocked(paymentApi.topup).mockResolvedValue({
      success: true,
      data: {
        id: "deposit-1",
        provider: "MANUAL",
        amount: 100000,
        bankAccount: "12345678",
        bankHolder: "EDUFIT",
        description: "EF1",
      },
    });
    render(
      <WalletPanel
        data={overview}
        tutor={false}
        busy={false}
        execute={execute}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Tạo mã QR" }));
    await screen.findByText("EF1");
    expect(
      screen.getByText(/Admin xác minh giao dịch thực tế/),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Đã nạp thành công/)).not.toBeInTheDocument();
  });
});

const recipient = {
  id: "saved-1",
  bank_name: "MBBank",
  bank_bin: "970422",
  bank_account: "12345678",
  bank_holder: "Tutor",
};

it("reuses the saved recipient, fills the available amount and asks before withdrawing", async () => {
  render(
    <WalletPanel
      data={{
        ...overview,
        wallet: { available: 90000, held: 0 },
        payoutAccount: recipient,
      }}
      tutor={true}
      busy={false}
      execute={execute}
    />,
  );
  expect(screen.queryByLabelText("Số tài khoản")).not.toBeInTheDocument();
  expect(screen.queryByLabelText("Số tiền rút")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Rút tiền" }));
  expect(screen.getByLabelText("Số tiền rút")).toHaveValue(null);
  fireEvent.click(screen.getByRole("button", { name: "Rút toàn bộ" }));
  expect(screen.getByLabelText("Số tiền rút")).toHaveValue(90000);
  fireEvent.click(screen.getByRole("button", { name: "Tiếp tục rút tiền" }));
  expect(paymentApi.withdrawSaved).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Xác nhận rút tiền" }));
  await waitFor(() =>
    expect(paymentApi.withdrawSaved).toHaveBeenCalledWith(90000, "saved-1"),
  );
});

it("saves a recipient separately without creating a withdrawal", async () => {
  render(
    <WalletPanel data={overview} tutor={true} busy={false} execute={execute} />,
  );
  fireEvent.click(
    screen.getByRole("button", { name: "Thêm tài khoản nhận tiền" }),
  );
  fireEvent.change(screen.getByRole("combobox", { name: "Ngân hàng" }), {
    target: { value: "970422" },
  });
  fireEvent.change(screen.getByLabelText("Số tài khoản"), {
    target: { value: "12345678" },
  });
  fireEvent.change(screen.getByLabelText("Chủ tài khoản"), {
    target: { value: "Tutor" },
  });
  fireEvent.click(screen.getByRole("checkbox", { name: /Tôi đã kiểm tra/ }));
  fireEvent.click(
    screen.getByRole("button", { name: "Lưu tài khoản nhận tiền" }),
  );
  await waitFor(() =>
    expect(paymentApi.savePayoutAccount).toHaveBeenCalledWith({
      bank: "MBBank",
      bankBin: "970422",
      account: "12345678",
      holder: "Tutor",
      expectedAccountId: null,
    }),
  );
  expect(paymentApi.withdrawSaved).not.toHaveBeenCalled();
});

it("explains why withdrawal is disabled and does not allow overspending", () => {
  render(
    <WalletPanel
      data={{
        ...overview,
        wallet: { available: 9000, held: 0 },
        payoutAccount: recipient,
      }}
      tutor={true}
      busy={false}
      execute={execute}
    />,
  );
  expect(screen.getByRole("button", { name: "Rút tiền" })).toBeDisabled();
  expect(screen.getByText(/Số dư khả dụng tối thiểu/)).toBeInTheDocument();
});

it("blocks new withdrawals while an earlier request is pending", () => {
  render(
    <WalletPanel
      data={{
        ...overview,
        payoutAccount: recipient,
        withdrawals: [
          {
            id: "pending",
            user_id: "tutor",
            amount: 10000,
            status: "PENDING",
            bank_name: "MBBank",
            bank_account: "12345678",
            bank_holder: "Tutor",
            created_at: "2026-10-25",
          },
        ],
      }}
      tutor
      busy={false}
      execute={execute}
    />,
  );
  expect(screen.getByRole("button", { name: "Rút tiền" })).toBeDisabled();
  expect(screen.getByText(/Bạn có yêu cầu rút đang chờ/)).toBeInTheDocument();
});
it("invalidates a confirmation if the saved recipient changes", () => {
  const { rerender } = render(
    <WalletPanel
      data={{ ...overview, payoutAccount: recipient }}
      tutor
      busy={false}
      execute={execute}
    />,
  );
  fireEvent.click(screen.getByRole("button", { name: "Rút tiền" }));
  fireEvent.click(screen.getByRole("button", { name: "Rút toàn bộ" }));
  fireEvent.click(screen.getByRole("button", { name: "Tiếp tục rút tiền" }));
  rerender(
    <WalletPanel
      data={{ ...overview, payoutAccount: { ...recipient, id: "changed" } }}
      tutor
      busy={false}
      execute={execute}
    />,
  );
  expect(
    screen.getByRole("button", { name: "Xác nhận rút tiền" }),
  ).toBeDisabled();
  expect(screen.getByRole("alert")).toHaveTextContent(/Tài khoản nhận đã đổi/);
});

it("keeps a failed withdrawal visible inside the popup without claiming success", async () => {
  vi.mocked(paymentApi.withdrawSaved).mockRejectedValueOnce(
    new Error("Số dư đã thay đổi"),
  );
  render(
    <WalletPanel
      data={{ ...overview, payoutAccount: recipient }}
      tutor
      busy={false}
      execute={execute}
    />,
  );
  fireEvent.click(screen.getByRole("button", { name: "Rút tiền" }));
  fireEvent.change(screen.getByLabelText("Số tiền rút"), {
    target: { value: "50000" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Tiếp tục rút tiền" }));
  fireEvent.click(screen.getByRole("button", { name: "Xác nhận rút tiền" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Số dư đã thay đổi",
  );
  expect(
    screen.getByRole("dialog", { name: "Rút tiền về ngân hàng" }),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("button", { name: "Xác nhận rút tiền" }),
  ).toBeEnabled();
});
