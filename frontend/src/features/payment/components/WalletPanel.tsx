"use client";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { ArrowUpRight, QrCode, ShieldCheck, Wallet } from "lucide-react";
import { TutorWithdrawalPanel } from "./TutorWithdrawalPanel";
import { TopupCheckoutDialog } from "./TopupCheckoutDialog";
import {
  paymentApi,
  money,
  type Overview,
  type Checkout,
  type Topup,
} from "../api/paymentApi";
interface Props {
  data: Overview;
  tutor: boolean;
  busy: boolean;
  execute: (work: () => Promise<unknown>) => Promise<boolean>;
}
export function WalletPanel(props: Props) {
  return props.tutor ? (
    <TutorWithdrawalPanel {...props} />
  ) : (
    <TopupPanel {...props} />
  );
}
export function checkoutFromTopup(t: Topup): Checkout {
  return {
    id: t.id,
    provider: t.provider,
    amount: t.amount,
    description: `EF${t.order_code.toString(36).toUpperCase()}`,
    ...(t.provider === "PAYOS"
      ? { checkoutUrl: t.checkout_url }
      : { qrUrl: t.checkout_url }),
  };
}
function TopupPanel({ data, busy, execute }: Props) {
  const [amount, setAmount] = useState("100000");
  const [checkout, setCheckout] = useState<Checkout | null>(null);
  const request = useRef(false);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 15000);
    return () => clearInterval(timer);
  }, []);
  const pending = data.topups.find(
    (t) =>
      t.status === "PENDING" &&
      t.checkout_url &&
      (!t.expires_at || new Date(t.expires_at).getTime() > now),
  );
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (busy || request.current || !data.topupEnabled) return;
    request.current = true;
    try {
      await execute(async () => {
        const response = await paymentApi.topup(Number(amount));
        if (response.data)
          setCheckout({
            ...response.data,
            amount: response.data.amount ?? Number(amount),
          });
      });
    } finally {
      request.current = false;
    }
  }
  return (
    <section
      aria-label="Ví học phí"
      className="overflow-hidden rounded-2xl border border-stone-300/80 bg-white shadow-lg shadow-stone-300/50 dark:border-stone-700 dark:shadow-black/30"
    >
      <div className="grid lg:grid-cols-2">
        <div className="space-y-6 bg-stone-50 p-6 text-stone-900 dark:text-stone-100 sm:p-8">
          <div className="flex items-center gap-2 text-sm font-semibold text-orange-800 dark:text-orange-300">
            <span className="rounded-lg bg-orange-100 p-2 dark:bg-orange-950/60">
              <Wallet className="size-4" />
            </span>
            Ví học phí EduFit
          </div>
          <div>
            <p className="text-sm text-stone-600 dark:text-stone-300">
              Số dư khả dụng
            </p>
            <p className="mt-2 text-4xl font-bold tracking-tight sm:text-5xl">
              {money(data.wallet.available)}
            </p>
          </div>
          <div className="flex items-center justify-between gap-4 border-t border-stone-200 pt-5 text-sm">
            <span className="text-stone-600 dark:text-stone-300">
              Đang giữ cho buổi học
            </span>
            <strong>{money(data.wallet.held)}</strong>
          </div>
          <p className="flex items-start gap-2 text-xs leading-relaxed text-stone-600 dark:text-stone-300">
            <ShieldCheck className="mt-0.5 size-4 shrink-0" />
            Tiền học được giữ riêng đến hết thời hạn phản hồi hoặc khi có quyết
            định giải quyết.
          </p>
        </div>
        <div className="p-6 sm:p-8">
          <h2 className="flex items-center gap-2 text-lg font-bold">
            <QrCode className="size-5 text-amber-600" />
            Nạp tiền vào ví
          </h2>
          <p className="mt-1 text-sm text-neutral-500">
            Chọn số tiền, quét QR và tiếp tục học.
          </p>
          {!data.topupEnabled && (
            <p
              role="status"
              className="mt-4 rounded-xl bg-amber-50 p-3 text-sm text-amber-900"
            >
              Kênh nạp tiền chưa được cấu hình. Vui lòng liên hệ hỗ trợ.
            </p>
          )}
          <form onSubmit={submit} className="mt-5 space-y-4">
            <label className="block text-sm font-medium">
              Số tiền nạp
              <div className="mt-2 flex items-center rounded-xl border border-stone-300 px-4 focus-within:border-amber-500 focus-within:ring-2 focus-within:ring-amber-100">
                <input
                  aria-label="Số tiền nạp"
                  type="number"
                  min={10000}
                  max={100000000}
                  step={1}
                  required
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  className="min-w-0 flex-1 bg-transparent py-3 text-xl font-semibold outline-none"
                />
                <span className="text-xs font-semibold text-neutral-500">
                  VND
                </span>
              </div>
            </label>
            <div className="grid grid-cols-3 gap-2">
              {[100000, 200000, 500000].map((value) => (
                <button
                  key={value}
                  type="button"
                  aria-pressed={Number(amount) === value}
                  onClick={() => setAmount(String(value))}
                  className={`rounded-lg border px-2 py-2 text-sm font-semibold ${
                    Number(amount) === value
                      ? "border-amber-500 bg-amber-50 text-amber-900"
                      : "border-stone-200 text-neutral-600 hover:bg-stone-50"
                  }`}
                >
                  {money(value)}
                </button>
              ))}
            </div>
            <button
              disabled={busy || !data.topupEnabled}
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-orange-700 px-4 py-3 text-sm font-bold text-white hover:bg-orange-800 disabled:cursor-not-allowed disabled:bg-stone-200 disabled:text-stone-500 dark:disabled:bg-stone-700 dark:disabled:text-stone-300"
            >
              {busy ? "Đang tạo mã…" : "Tạo mã QR"}
              <ArrowUpRight className="size-4" />
            </button>
            <p className="text-center text-xs text-neutral-500">
              Từ 10.000đ · Không cần rời trang
            </p>
          </form>
          {pending && (
            <div className="mt-4 flex flex-wrap items-center justify-between gap-2 border-t border-stone-100 pt-4 text-sm">
              <p className="text-neutral-500">
                Yêu cầu {money(pending.amount)} đang chờ
              </p>
              <button
                disabled={busy}
                type="button"
                onClick={() => setCheckout(checkoutFromTopup(pending))}
                className="font-semibold text-amber-800 underline underline-offset-4"
              >
                Tiếp tục nạp tiền
              </button>
            </div>
          )}
        </div>
      </div>
      {checkout && (
        <TopupCheckoutDialog
          key={checkout.id}
          checkout={checkout}
          status={data.topups.find((t) => t.id === checkout.id)?.status}
          execute={execute}
          onClose={() => setCheckout(null)}
        />
      )}
    </section>
  );
}
