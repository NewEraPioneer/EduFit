"use client";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { ArrowUpRight, Building2, Clock3, Wallet } from "lucide-react";
import { PaymentDialog } from "./PaymentDialog";
import banks from "../data/banks.json";
import {
  money,
  paymentApi,
  paymentError,
  type Overview,
  type PayoutAccount,
} from "../api/paymentApi";

type Execute = (work: () => Promise<unknown>) => Promise<boolean>;
const mask = (value: string) => `•••• ${value.slice(-4)}`;

function RecipientEditor({
  recipient,
  busy,
  execute,
  close,
}: {
  recipient: PayoutAccount | null;
  busy: boolean;
  execute: Execute;
  close: () => void;
}) {
  const [bin, setBin] = useState(recipient?.bank_bin ?? "");
  const [account, setAccount] = useState(recipient?.bank_account ?? "");
  const [holder, setHolder] = useState(recipient?.bank_holder ?? "");
  const [checked, setChecked] = useState(false);
  async function save(event: FormEvent) {
    event.preventDefault();
    const bank = banks.find((item) => item.bin === bin);
    if (!checked || !bank || busy) return;
    const ok = await execute(() =>
      paymentApi.savePayoutAccount({
        bank: bank.name,
        bankBin: bin,
        account: account.trim(),
        holder: holder.trim(),
        expectedAccountId: recipient?.id ?? null,
      }),
    );
    if (ok) close();
  }
  return (
    <form
      onSubmit={save}
      className="space-y-4 rounded-xl border border-stone-200 bg-stone-50 p-4"
    >
      <h3 className="font-semibold">
        {recipient
          ? "Đổi tài khoản nhận tiền"
          : "Lưu tài khoản nhận tiền một lần"}
      </h3>
      <p className="text-sm text-neutral-600">
        Các lần rút sau sẽ dùng tài khoản này. Yêu cầu rút đang chờ vẫn giữ tài
        khoản đã chọn trước đó.
      </p>
      <fieldset disabled={busy} className="grid gap-3 sm:grid-cols-2">
        <label className="text-sm">
          Ngân hàng
          <select
            required
            value={bin}
            onChange={(event) => {
              setBin(event.target.value);
              setChecked(false);
            }}
            className="mt-1 w-full rounded-lg border bg-white p-2"
          >
            <option value="">Chọn ngân hàng nhận</option>
            {banks.map((bank) => (
              <option key={bank.bin} value={bank.bin}>
                {bank.name} — {bank.fullName}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm">
          Số tài khoản
          <input
            required
            inputMode="numeric"
            pattern="[0-9]{5,34}"
            maxLength={34}
            value={account}
            onChange={(event) => {
              setAccount(event.target.value);
              setChecked(false);
            }}
            className="mt-1 w-full rounded-lg border p-2"
          />
        </label>
        <label className="text-sm sm:col-span-2">
          Chủ tài khoản
          <input
            required
            maxLength={150}
            value={holder}
            onChange={(event) => {
              setHolder(event.target.value);
              setChecked(false);
            }}
            className="mt-1 w-full rounded-lg border p-2"
          />
        </label>
        <label className="flex items-start gap-2 text-sm sm:col-span-2">
          <input
            type="checkbox"
            required
            checked={checked}
            onChange={(event) => setChecked(event.target.checked)}
            className="mt-1"
          />
          Tôi đã kiểm tra ngân hàng, số tài khoản và tên người nhận.
        </label>
      </fieldset>
      <p className="text-xs text-neutral-500">
        Thông tin do bạn cung cấp; Admin đối soát người nhận trước khi chi trả.
        Lưu tài khoản chưa tạo yêu cầu rút tiền.
      </p>
      <div className="flex flex-wrap gap-3">
        <button
          disabled={busy || !checked}
          className="rounded-lg bg-amber-500 px-4 py-2 text-sm font-bold disabled:opacity-50"
        >
          Lưu tài khoản nhận tiền
        </button>
        {recipient && (
          <button
            type="button"
            disabled={busy}
            onClick={close}
            className="rounded-lg border bg-white px-4 py-2 text-sm"
          >
            Hủy thay đổi
          </button>
        )}
      </div>
    </form>
  );
}

export function TutorWithdrawalPanel({
  data,
  busy,
  execute,
}: {
  data: Overview;
  busy: boolean;
  execute: Execute;
}) {
  const recipient = data.payoutAccount ?? null;
  const [editing, setEditing] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);
  async function perform(work: () => Promise<unknown>) {
    setLocalError(null);
    try {
      return await execute(async () => {
        try {
          return await work();
        } catch (e) {
          setLocalError(paymentError(e));
          throw e;
        }
      });
    } catch (e) {
      setLocalError(paymentError(e));
      return false;
    }
  }

  const [withdrawing, setWithdrawing] = useState(false);
  const [amount, setAmount] = useState("");
  const [confirmation, setConfirmation] = useState<{
    amount: number;
    recipient: PayoutAccount;
  } | null>(null);
  const reviewRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (confirmation) reviewRef.current?.focus();
  }, [confirmation]);
  const pending = data.withdrawals.find(
    (item) => item.status === "PENDING" || item.status === "PROCESSING",
  );
  const maximum = Math.min(data.wallet.available, 100000000);
  const value = Number(amount);
  const valid =
    Number.isSafeInteger(value) && value >= 10000 && value <= maximum;
  async function confirm() {
    if (
      !confirmation ||
      busy ||
      pending ||
      !recipient ||
      recipient.id !== confirmation.recipient.id ||
      confirmation.amount > data.wallet.available
    )
      return;
    const ok = await perform(() =>
      paymentApi.withdrawSaved(confirmation.amount, confirmation.recipient.id),
    );
    if (ok) {
      setConfirmation(null);
      setAmount("");
      setWithdrawing(false);
    }
  }
  return (
    <section
      aria-label="Ví thu nhập gia sư"
      className="overflow-hidden rounded-2xl border border-stone-200 bg-white"
    >
      <div className="grid lg:grid-cols-2">
        <div className="space-y-5 bg-stone-50 p-6 text-stone-900 dark:text-stone-100 sm:p-8">
          <div className="flex items-center gap-2 text-sm font-semibold text-orange-800 dark:text-orange-300">
            <span className="rounded-lg bg-orange-100 p-2 dark:bg-orange-950/60">
              <Wallet className="size-4" />
            </span>
            Ví thu nhập gia sư
          </div>
          <div>
            <p className="text-sm text-stone-600 dark:text-stone-300">
              Có thể yêu cầu rút
            </p>
            <p className="mt-2 text-4xl font-bold tracking-tight sm:text-5xl">
              {money(data.wallet.available)}
            </p>
          </div>
          <button
            disabled={busy || !!pending || !recipient || maximum < 10000}
            onClick={() => {
              setLocalError(null);
              setWithdrawing(true);
              setConfirmation(null);
              setAmount("");
            }}
            className="inline-flex items-center gap-2 rounded-xl bg-orange-700 px-5 py-3 text-sm font-bold text-white hover:bg-orange-800 disabled:cursor-not-allowed disabled:bg-stone-200 disabled:text-stone-500 dark:disabled:bg-stone-700 dark:disabled:text-stone-300"
          >
            Rút tiền
            <ArrowUpRight className="size-4" />
          </button>
          {!pending && maximum < 10000 && (
            <p className="text-xs font-medium text-stone-600 dark:text-stone-300">
              Số dư khả dụng tối thiểu để rút là 10.000đ.
            </p>
          )}
          <p className="text-xs leading-relaxed text-stone-600 dark:text-stone-300">
            Thu nhập đã trừ phí nền tảng. Không thu thêm phí EduFit khi rút.
          </p>
        </div>
        <div className="grid grid-cols-2 gap-5 p-6 sm:p-8">
          <div>
            <Clock3 className="mb-4 size-5 text-amber-600" />
            <p className="text-sm text-neutral-500">Thu nhập chờ tất toán</p>
            <p className="mt-2 break-words text-2xl font-bold">
              {data.pendingIncome === undefined
                ? "—"
                : money(data.pendingIncome)}
            </p>
            <p className="mt-2 text-xs leading-relaxed text-neutral-500">
              Dự kiến sau phí, chưa thể rút; có thể đổi theo kết quả hồ sơ.
            </p>
          </div>
          <div>
            <ArrowUpRight className="mb-4 size-5 text-neutral-500" />
            <p className="text-sm text-neutral-500">Đang chờ chi trả</p>
            <p className="mt-2 break-words text-2xl font-bold">
              {money(data.wallet.held)}
            </p>
            <p className="mt-2 text-xs leading-relaxed text-neutral-500">
              Tiền trong yêu cầu rút đang được xử lý.
            </p>
          </div>
        </div>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-4 border-t border-stone-100 px-6 py-5">
        <div className="flex items-center gap-3">
          <span className="rounded-xl bg-stone-100 p-3">
            <Building2 className="size-5 text-neutral-500" />
          </span>
          <div>
            <p className="text-xs text-neutral-500">
              Tài khoản nhận tiền mặc định
            </p>
            <p className="mt-1 text-sm font-semibold">
              {recipient
                ? `${recipient.bank_name} · ${mask(recipient.bank_account)}`
                : "Chưa thêm tài khoản ngân hàng"}
            </p>
            {recipient && (
              <p className="mt-1 text-xs text-neutral-500">
                {recipient.bank_holder}
              </p>
            )}
          </div>
        </div>
        <button
          disabled={busy}
          onClick={() => {
            setLocalError(null);
            setEditing(true);
            setConfirmation(null);
          }}
          className="rounded-lg border border-stone-200 px-3 py-2 text-sm font-semibold"
        >
          {recipient ? "Đổi tài khoản" : "Thêm tài khoản nhận tiền"}
        </button>
      </div>
      {pending && (
        <p
          role="status"
          className="border-t border-amber-100 bg-amber-50 px-6 py-3 text-sm text-amber-900"
        >
          Bạn có yêu cầu rút đang chờ duyệt hoặc chuyển tiền:{" "}
          {money(pending.amount)} đến {pending.bank_name} ·{" "}
          {mask(pending.bank_account)}.
        </p>
      )}
      {editing && (
        <PaymentDialog
          title="Tài khoản nhận tiền"
          onClose={() => setEditing(false)}
          busy={busy}
        >
          {localError && (
            <p
              role="alert"
              className="mb-4 rounded-xl bg-red-50 p-3 text-sm text-red-700"
            >
              {localError}
            </p>
          )}
          <RecipientEditor
            key={recipient?.id ?? "new"}
            recipient={recipient}
            busy={busy}
            execute={perform}
            close={() => setEditing(false)}
          />
        </PaymentDialog>
      )}
      {withdrawing && recipient && (
        <PaymentDialog
          title="Rút tiền về ngân hàng"
          onClose={() => setWithdrawing(false)}
          busy={busy}
        >
          {localError && (
            <p
              role="alert"
              className="mb-4 rounded-xl bg-red-50 p-3 text-sm text-red-700"
            >
              {localError}
            </p>
          )}
          <div className="mb-5 flex items-center justify-between rounded-xl bg-stone-50 p-4 text-sm">
            <div>
              <p className="text-xs text-neutral-500">Nhận vào</p>
              <p className="mt-1 font-semibold">
                {recipient.bank_name} · {mask(recipient.bank_account)}
              </p>
              <p className="mt-1 text-xs text-neutral-500">
                {recipient.bank_holder}
              </p>
            </div>
            <Building2 className="size-5 text-neutral-400" />
          </div>
          {!confirmation ? (
            <form
              onSubmit={(event) => {
                event.preventDefault();
                if (valid && recipient && !pending && !busy)
                  setConfirmation({ amount: value, recipient });
              }}
              className="space-y-4"
            >
              <label className="block text-sm font-medium">
                Số tiền rút
                <input
                  type="number"
                  required
                  min={10000}
                  max={maximum}
                  step={1}
                  value={amount}
                  onChange={(event) => setAmount(event.target.value)}
                  placeholder="Nhập số tiền cần rút"
                  className="mt-2 w-full rounded-xl border border-stone-300 px-4 py-3 text-xl font-semibold"
                />
              </label>
              <div className="flex items-center justify-between gap-3 text-xs text-neutral-500">
                <p>Khả dụng: {money(data.wallet.available)}</p>
                <button
                  type="button"
                  disabled={busy || maximum < 10000 || !!pending}
                  onClick={() => setAmount(String(maximum))}
                  className="font-semibold text-amber-800 underline"
                >
                  Rút toàn bộ
                </button>
              </div>
              <p className="text-xs text-neutral-500">
                Tối thiểu 10.000đ, tối đa 100.000.000đ mỗi yêu cầu.
              </p>
              <button
                disabled={busy || !valid || !!pending}
                className="w-full rounded-xl bg-amber-500 py-3 text-sm font-bold disabled:opacity-50"
              >
                Tiếp tục rút tiền
              </button>
            </form>
          ) : (
            <div
              role="region"
              aria-label="Xác nhận yêu cầu rút"
              ref={reviewRef}
              tabIndex={-1}
              className="space-y-4"
            >
              <div className="text-center">
                <p className="text-sm text-neutral-500">Số tiền yêu cầu nhận</p>
                <p className="mt-2 text-3xl font-bold">
                  {money(confirmation.amount)}
                </p>
              </div>
              <p className="text-sm text-neutral-600">
                Nhận vào {confirmation.recipient.bank_name} ·{" "}
                {mask(confirmation.recipient.bank_account)} ·{" "}
                {confirmation.recipient.bank_holder}
              </p>
              <div className="flex justify-between border-y border-stone-100 py-3 text-sm">
                <span>Phí rút EduFit</span>
                <strong>0đ</strong>
              </div>
              <p className="text-xs text-neutral-500">
                Admin duyệt trước khi chuyển. Bạn có thể theo dõi trạng thái
                trong giao dịch.
              </p>
              {recipient.id !== confirmation.recipient.id && (
                <p
                  role="alert"
                  className="rounded-xl bg-red-50 p-3 text-sm text-red-700"
                >
                  Tài khoản nhận đã đổi. Hãy kiểm tra lại và tạo xác nhận mới.
                </p>
              )}
              <button
                type="button"
                disabled={
                  busy ||
                  !!pending ||
                  recipient.id !== confirmation.recipient.id ||
                  confirmation.amount > data.wallet.available
                }
                onClick={() => void confirm()}
                className="w-full rounded-xl bg-amber-500 py-3 text-sm font-bold disabled:opacity-50"
              >
                Xác nhận rút tiền
              </button>
              <button
                type="button"
                disabled={busy}
                onClick={() => setConfirmation(null)}
                className="w-full rounded-xl border border-stone-200 py-3 text-sm font-semibold"
              >
                Quay lại
              </button>
            </div>
          )}
        </PaymentDialog>
      )}
    </section>
  );
}
