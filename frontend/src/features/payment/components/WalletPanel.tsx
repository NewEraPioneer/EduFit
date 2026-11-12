"use client";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { ArrowUpRight, QrCode, ShieldCheck, Wallet } from "lucide-react";
import { TutorWithdrawalPanel } from "./TutorWithdrawalPanel";
import { TopupCheckoutDialog } from "./TopupCheckoutDialog";
import { TuitionDuePanel } from "./TuitionDuePanel";
import {
  paymentApi,
  money,
  type Overview,
  type Checkout,
  type Topup,
  type PaymentSession,
} from "../api/paymentApi";
interface Props {
  data: Overview;
  tutor: boolean;
  busy: boolean;
  execute: (work: () => Promise<unknown>) => Promise<boolean>;
  /** Buổi học đến hạn mà hệ thống chưa thu được học phí (chỉ người học). */
  due?: PaymentSession[];
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
function TopupPanel({ data, busy, execute, due = [] }: Props) {
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
  async function startTopup(value: number) {
    if (busy || request.current || !data.topupEnabled) return;
    request.current = true;
    try {
      await execute(async () => {
        const response = await paymentApi.topup(value);
        if (response.data)
          setCheckout({
            ...response.data,
            amount: response.data.amount ?? value,
          });
      });
    } finally {
      request.current = false;
    }
  }
  function submit(e: FormEvent) {
    e.preventDefault();
    void startTopup(Number(amount));
  }
  return (
    <section
      aria-label="Ví học phí"
      className="overflow-hidden rounded-2xl border border-line-strong/80 bg-surface shadow-lg shadow-ink/10"
    >
      <div className="grid lg:grid-cols-2">
        <div className="space-y-6 bg-paper p-6 text-ink sm:p-8">
          <div className="flex items-center gap-2 text-sm font-medium text-action-strong">
            <span className="rounded-lg bg-action-soft p-2">
              <Wallet className="size-4" />
            </span>
            Ví học phí EduFit
          </div>
          <div>
            <p className="text-sm text-ink-2">Số dư khả dụng</p>
            <p className="mt-2 text-4xl font-bold tracking-tight sm:text-5xl">
              {money(data.wallet.available)}
            </p>
          </div>
          <div className="flex items-center justify-between gap-4 border-t border-line pt-5 text-sm">
            <span className="text-ink-2">Đang giữ cho buổi học</span>
            <strong>{money(data.wallet.held)}</strong>
          </div>
          <p className="flex items-start gap-2 text-xs leading-relaxed text-ink-2">
            <ShieldCheck className="mt-0.5 size-4 shrink-0" />
            Tiền học được giữ riêng đến hết thời hạn phản hồi hoặc khi có quyết
            định giải quyết.
          </p>
        </div>
        <div className="p-6 sm:p-8">
          <h2 className="flex items-center gap-2 text-lg font-bold">
            <QrCode className="size-5 text-action-strong" />
            Nạp tiền vào ví
          </h2>
          <p className="mt-1 text-sm text-muted">
            Chọn số tiền, quét QR và tiếp tục học.
          </p>
          <div className="mt-4">
            <TuitionDuePanel
              due={due}
              available={data.wallet.available}
              busy={busy}
              canTopup={data.topupEnabled}
              onTopup={(value) => {
                setAmount(String(value));
                void startTopup(value);
              }}
            />
          </div>
          {!data.topupEnabled && (
            <p
              role="status"
              className="mt-4 rounded-xl bg-action-soft p-3 text-sm text-action-strong"
            >
              Kênh nạp tiền chưa được cấu hình. Vui lòng liên hệ hỗ trợ.
            </p>
          )}
          <form onSubmit={submit} className="mt-5 space-y-4">
            <label className="block text-sm font-medium">
              Số tiền nạp
              <div className="mt-2 flex items-center rounded-xl border border-line-strong px-4 focus-within:border-action focus-within:ring-2 focus-within:ring-action-line">
                <input
                  aria-label="Số tiền nạp"
                  type="number"
                  min={10000}
                  max={100000000}
                  step={1}
                  required
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  className="min-w-0 flex-1 bg-transparent py-3 text-xl font-bold outline-none"
                />
                <span className="text-xs font-medium text-muted">VND</span>
              </div>
            </label>
            <div className="grid grid-cols-3 gap-2">
              {[100000, 200000, 500000].map((value) => (
                <button
                  key={value}
                  type="button"
                  aria-pressed={Number(amount) === value}
                  onClick={() => setAmount(String(value))}
                  className={`rounded-lg border px-2 py-2 text-sm font-medium ${
                    Number(amount) === value
                      ? "border-action bg-action-soft text-action-strong"
                      : "border-line text-ink-2 hover:bg-paper"
                  }`}
                >
                  {money(value)}
                </button>
              ))}
            </div>
            <button
              disabled={busy || !data.topupEnabled}
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-action px-4 py-3 text-sm font-medium text-on-action hover:bg-action-deep disabled:cursor-not-allowed disabled:bg-line disabled:text-muted"
            >
              {busy ? "Đang tạo mã…" : "Tạo mã QR"}
              <ArrowUpRight className="size-4" />
            </button>
            <p className="text-center text-xs text-muted">
              Từ 10.000đ · Không cần rời trang
            </p>
          </form>
          {pending && (
            <div className="mt-4 flex flex-wrap items-center justify-between gap-2 border-t border-line pt-4 text-sm">
              <p className="text-muted">
                Yêu cầu {money(pending.amount)} đang chờ
              </p>
              <button
                disabled={busy}
                type="button"
                onClick={() => setCheckout(checkoutFromTopup(pending))}
                className="font-medium text-action-strong underline underline-offset-4"
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
