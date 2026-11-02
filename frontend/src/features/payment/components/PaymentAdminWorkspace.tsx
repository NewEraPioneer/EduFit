"use client";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  ArrowDownLeft,
  ArrowUpRight,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Settings2,
  ShieldAlert,
} from "lucide-react";
import {
  paymentApi,
  paymentError,
  money,
  type AdminOverview,
  type PaymentSession,
} from "../api/paymentApi";
import {
  AdminPaymentReviewDialog,
  type AdminReviewTarget,
} from "./AdminPaymentReviewDialog";
type Queue = "topups" | "withdrawals" | "cases";
export function PaymentAdminWorkspace({ initialTab }: { initialTab?: Queue }) {
  const [data, setData] = useState<AdminOverview | null>(null);
  const [disputes, setDisputes] = useState<PaymentSession[]>([]);
  const [offset, setOffset] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [target, setTarget] = useState<AdminReviewTarget | null>(null);
  const [chosen, setChosen] = useState<Queue | null>(initialTab ?? null);
  const [resolved, setResolved] = useState(false);
  const load = useCallback(async () => {
    const [wallets, cases] = await Promise.all([
      paymentApi.admin(offset),
      paymentApi.disputes(offset),
    ]);
    setData(wallets.data ?? null);
    setDisputes(cases.data ?? []);
  }, [offset]);
  useEffect(() => {
    let alive = true;
    async function run() {
      try {
        const [wallets, cases] = await Promise.all([
          paymentApi.admin(offset),
          paymentApi.disputes(offset),
        ]);
        if (alive) {
          setData(wallets.data ?? null);
          setDisputes(cases.data ?? []);
          setError(null);
        }
      } catch (e) {
        if (alive) setError(paymentError(e));
      }
    }
    void run();
    const refresh = () => {
      if (!document.hidden) void run();
    };
    const timer = setInterval(refresh, 15000);
    window.addEventListener("focus", refresh);
    return () => {
      alive = false;
      clearInterval(timer);
      window.removeEventListener("focus", refresh);
    };
  }, [offset]);
  const pendingTopups =
    data?.topups.filter((t) => t.status === "PENDING") ?? [];
  const pendingWithdrawals =
    data?.withdrawals.filter((w) =>
      ["PENDING", "PROCESSING"].includes(w.status),
    ) ?? [];
  const active =
    chosen ??
    (pendingWithdrawals.length
      ? "withdrawals"
      : pendingTopups.length
        ? "topups"
        : "cases");
  function select(value: AdminReviewTarget) {
    setError(null);
    setTarget(value);
  }
  async function submit(note: string, reference: string) {
    if (!target || busy) return;
    setBusy(true);
    setError(null);
    try {
      if (target.payout) await paymentApi.payout(target.id, note);
      else
        await paymentApi.review(
          target.kind,
          target.id,
          target.approve,
          note,
          reference,
        );
      setTarget(null);
      await load();
    } catch (e) {
      setError(paymentError(e));
    } finally {
      setBusy(false);
    }
  }
  async function refresh(kind: "topup" | "payout", id: string) {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      if (kind === "topup") await paymentApi.refreshTopup(id);
      else await paymentApi.refreshPayout(id);
      await load();
    } catch (e) {
      setError(paymentError(e));
    } finally {
      setBusy(false);
    }
  }
  const button =
    "rounded-lg border border-stone-200 bg-white px-3 py-2 text-xs font-semibold text-neutral-700 hover:bg-stone-50 disabled:opacity-40";
  const withdrawals = resolved ? data?.withdrawals ?? [] : pendingWithdrawals;
  const topups = resolved ? data?.topups ?? [] : pendingTopups;
  return (
    <div className="mx-auto max-w-6xl space-y-6 pb-8">
      <header>
        <p className="text-xs font-semibold uppercase tracking-widest text-amber-700">
          EduFit / Quản trị
        </p>
        <h1 className="mt-2 text-2xl font-bold tracking-tight sm:text-3xl">
          {initialTab === "cases" ? "Hồ sơ học phí" : "Quản lý thanh toán"}
        </h1>
        <p className="mt-2 text-sm text-neutral-500">
          Ưu tiên yêu cầu cần xử lý, đối chiếu trước khi xác nhận tiền.
        </p>
      </header>
      <div className="grid gap-3 sm:grid-cols-3">
        {(
          [
            {
              key: "withdrawals",
              label: "Chi trả gia sư",
              count: pendingWithdrawals.length,
              Icon: ArrowUpRight,
            },
            {
              key: "topups",
              label: "Nạp tiền",
              count: pendingTopups.length,
              Icon: ArrowDownLeft,
            },
            {
              key: "cases",
              label: "Hồ sơ vấn đề",
              count: disputes.length,
              Icon: ShieldAlert,
            },
          ] as const
        ).map(({ key, label, count, Icon }) => (
          <button
            key={key}
            aria-pressed={active === key}
            onClick={() => {
              setChosen(key);
              setOffset(0);
              setResolved(false);
            }}
            className={`flex items-center gap-4 rounded-2xl border p-5 text-left ${
              active === key
                ? "border-orange-300 bg-orange-50 text-stone-900 dark:border-orange-800 dark:bg-orange-950/40 dark:text-stone-100"
                : "border-stone-200 bg-white text-neutral-700 hover:border-orange-300 dark:hover:border-orange-800"
            }`}
          >
            <span
              className={`rounded-xl p-3 ${
                active === key
                  ? "bg-orange-100 text-orange-800 dark:bg-orange-900/60 dark:text-orange-200"
                  : "bg-stone-100"
              }`}
            >
              <Icon className="size-5" />
            </span>
            <span className="flex-1 text-sm font-semibold">{label}</span>
            <strong
              className={`text-2xl ${
                active === key ? "text-orange-800 dark:text-orange-200" : ""
              }`}
            >
              {count}
            </strong>
          </button>
        ))}
      </div>
      {error && !target && (
        <p
          role="alert"
          className="rounded-xl bg-red-50 p-4 text-sm text-red-700"
        >
          {error}
        </p>
      )}
      {!data ? (
        <p
          role="status"
          className="rounded-2xl border border-stone-200 bg-white p-8 text-center text-sm text-neutral-500"
        >
          {error
            ? "Chưa tải được danh sách. Hãy tải lại trang."
            : "Đang tải yêu cầu…"}
        </p>
      ) : (
        <section className="overflow-hidden rounded-2xl border border-stone-200 bg-white">
          <header className="flex flex-wrap items-center justify-between gap-3 border-b border-stone-100 p-5">
            <h2 className="font-bold">
              {active === "cases"
                ? "Hồ sơ cần Admin xem xét"
                : active === "withdrawals"
                  ? "Yêu cầu chi trả"
                  : "Yêu cầu nạp tiền"}
            </h2>
            {active !== "cases" && (
              <label className="flex items-center gap-2 text-xs text-neutral-500">
                <input
                  type="checkbox"
                  checked={resolved}
                  onChange={(e) => setResolved(e.target.checked)}
                />
                Gồm yêu cầu đã xử lý
              </label>
            )}
          </header>
          <div className="divide-y divide-stone-100">
            {active === "cases" &&
              disputes.map((s) => (
                <article
                  key={s.sessionId}
                  className="flex flex-wrap items-center justify-between gap-4 p-5"
                >
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-semibold">
                      {s.tutorName} · {money(s.amount)}
                    </p>
                    <p className="mt-1 text-xs text-amber-800">
                      {s.status === "ADMIN_REVIEW"
                        ? "Chưa thống nhất sau 48 giờ"
                        : "Hai bên đã thống nhất"}{" "}
                      · Tiền đang giữ
                    </p>
                    <p className="mt-2 line-clamp-2 text-sm text-neutral-500">
                      {s.disputeReason}
                    </p>
                  </div>
                  <Link
                    href={`/admin/disputes/${encodeURIComponent(s.sessionId)}`}
                    className={button}
                  >
                    Mở hồ sơ và xử lý
                  </Link>
                </article>
              ))}
            {active === "topups" &&
              topups.map((t) => (
                <article
                  key={t.id}
                  className="flex flex-wrap items-center justify-between gap-4 p-5"
                >
                  <div>
                    <p className="text-sm font-bold">
                      {money(t.amount)}{" "}
                      <span className="ml-2 text-xs font-normal text-neutral-500">
                        {t.status === "PAID"
                          ? "Đã cộng ví"
                          : t.status === "CANCELLED"
                            ? "Đã hủy"
                            : "Chờ xác minh"}
                      </span>
                    </p>
                    <p className="mt-1 text-xs text-neutral-500">
                      EF{t.order_code.toString(36).toUpperCase()} ·{" "}
                      {t.provider === "PAYOS"
                        ? "QR payOS"
                        : "Chuyển khoản thủ công"}
                    </p>
                    <details className="mt-2 text-xs text-neutral-500">
                      <summary className="cursor-pointer">
                        Thông tin đối soát
                      </summary>
                      <p className="mt-2 break-all">Ví: {t.user_id}</p>
                    </details>
                  </div>
                  {t.status === "PENDING" &&
                    (t.provider === "MANUAL" ? (
                      <button
                        disabled={busy}
                        className={button}
                        onClick={() =>
                          select({
                            kind: "topups",
                            id: t.id,
                            approve: true,
                            label: `Xác minh đã nhận ${money(
                              t.amount,
                            )} qua ngân hàng`,
                            summary: `Yêu cầu EF${t.order_code
                              .toString(36)
                              .toUpperCase()} · Ví ${
                              t.user_id
                            }. Đối chiếu giao dịch ngân hàng trước khi cộng tiền.`,
                          })
                        }
                      >
                        Xác minh giao dịch
                      </button>
                    ) : (
                      <button
                        disabled={busy}
                        className={button}
                        onClick={() => void refresh("topup", t.id)}
                      >
                        Kiểm tra giao dịch
                      </button>
                    ))}
                </article>
              ))}
            {active === "withdrawals" &&
              withdrawals.map((w) => (
                <article key={w.id} className="space-y-3 p-5">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <p className="text-sm font-bold">{money(w.amount)}</p>
                    <span className="rounded-full bg-stone-100 px-3 py-1 text-xs font-medium">
                      {w.status === "PENDING"
                        ? "Chờ duyệt"
                        : w.status === "PROCESSING"
                          ? "Đang chuyển"
                          : w.status === "PAID"
                            ? "Đã chuyển"
                            : "Đã hoàn ví"}
                    </span>
                  </div>
                  <p className="text-sm text-neutral-600">
                    {w.bank_name} · {w.bank_account} · {w.bank_holder}
                  </p>
                  <details className="text-xs text-neutral-500">
                    <summary className="cursor-pointer">
                      Thông tin đối soát
                    </summary>
                    <p className="mt-2 break-all">
                      Ví: {w.user_id} · BIN {w.bank_bin ?? "Chưa có"} · Mã:{" "}
                      {w.id}
                    </p>
                  </details>
                  {w.provider_message && (
                    <p role="status" className="text-xs text-amber-800">
                      {w.provider_message}
                    </p>
                  )}
                  {w.status === "PROCESSING" && (
                    <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-amber-50 p-3">
                      <p className="text-xs text-amber-900">
                        Chờ xác minh kết quả ngân hàng. Không chuyển lại thủ
                        công.
                      </p>
                      <button
                        disabled={busy}
                        className={button}
                        onClick={() => void refresh("payout", w.id)}
                      >
                        Đối soát kết quả chi
                      </button>
                    </div>
                  )}
                  {w.status === "PENDING" && (
                    <div className="flex flex-wrap gap-2">
                      {data.configuration?.payoutReady && w.bank_bin ? (
                        <button
                          disabled={busy}
                          className={button}
                          onClick={() =>
                            select({
                              kind: "withdrawals",
                              id: w.id,
                              approve: true,
                              payout: true,
                              label: `Duyệt chi hộ ${money(w.amount)} đến ${
                                w.bank_account
                              }`,
                              summary: `${w.bank_name} · ${w.bank_account} · ${w.bank_holder}. Xác nhận sẽ gửi lệnh chi qua payOS.`,
                            })
                          }
                        >
                          Duyệt và chuyển qua payOS
                        </button>
                      ) : (
                        <button
                          disabled={busy}
                          className={button}
                          onClick={() =>
                            select({
                              kind: "withdrawals",
                              id: w.id,
                              approve: true,
                              label: `Xác nhận đã chuyển ngân hàng ${money(
                                w.amount,
                              )}`,
                              summary: `${w.bank_name} · ${w.bank_account} · ${w.bank_holder}. Chỉ xác nhận khi giao dịch chuyển tiền thật đã thành công.`,
                            })
                          }
                        >
                          Đã chuyển ngân hàng
                        </button>
                      )}
                      <button
                        disabled={busy}
                        className={button}
                        onClick={() =>
                          select({
                            kind: "withdrawals",
                            id: w.id,
                            approve: false,
                            label:
                              "Từ chối chi trả và trả tiền về số dư gia sư",
                            summary: `Hoàn ${money(
                              w.amount,
                            )} về ví gia sư. Chỉ dùng khi chưa chuyển tiền; ghi rõ lý do.`,
                          })
                        }
                      >
                        Từ chối
                      </button>
                    </div>
                  )}
                </article>
              ))}
            {((active === "cases" && !disputes.length) ||
              (active === "topups" && !topups.length) ||
              (active === "withdrawals" && !withdrawals.length)) && (
              <div className="py-12 text-center">
                <CheckCircle2 className="mx-auto size-9 text-emerald-600" />
                <p className="mt-3 text-sm font-semibold">
                  Không có yêu cầu trong danh sách này
                </p>
                <p className="mt-1 text-xs text-neutral-500">
                  Chọn hàng đợi khác hoặc xem yêu cầu đã xử lý.
                </p>
              </div>
            )}
          </div>
          <footer className="flex items-center justify-between gap-3 border-t border-stone-100 p-4">
            <button
              disabled={offset === 0 || busy}
              className={button}
              onClick={() => setOffset((n) => Math.max(0, n - 50))}
            >
              <ChevronLeft className="inline size-4" />
              Trước
            </button>
            <span className="text-xs text-neutral-500">
              Trang {offset / 50 + 1} · Số liệu trong trang hiện tại
            </span>
            <button
              disabled={
                busy ||
                Math.max(
                  data.topups.length,
                  data.withdrawals.length,
                  disputes.length,
                ) < 50
              }
              className={button}
              onClick={() => setOffset((n) => n + 50)}
            >
              Sau
              <ChevronRight className="inline size-4" />
            </button>
          </footer>
        </section>
      )}
      {data?.configuration && (
        <details className="rounded-2xl border border-stone-200 bg-white px-5 py-4">
          <summary className="cursor-pointer text-sm font-semibold">
            <Settings2 className="mr-2 inline size-4 text-neutral-500" />
            Kết nối thanh toán
          </summary>
          <div className="mt-4 grid gap-4 border-t border-stone-100 pt-4 text-sm sm:grid-cols-3">
            <p>
              Nạp payOS:{" "}
              <strong>
                {data.configuration.topupReady
                  ? "Có cấu hình"
                  : "Chưa cấu hình"}
              </strong>
            </p>
            <p>
              QR thủ công:{" "}
              <strong>
                {data.configuration.manualReady
                  ? "Có cấu hình"
                  : "Chưa cấu hình"}
              </strong>
            </p>
            <p>
              Chi payOS:{" "}
              <strong>
                {data.configuration.payoutReady
                  ? "Đã bật"
                  : data.configuration.payoutEnabled
                    ? "Thiếu cấu hình"
                    : "Chưa bật"}
              </strong>
            </p>
          </div>
          <p className="mt-3 text-xs text-neutral-500">
            Cấu hình đầy đủ chưa xác nhận kênh và webhook đã hoạt động thực tế.
          </p>
        </details>
      )}
      {target && (
        <AdminPaymentReviewDialog
          key={target.id + target.approve + target.payout}
          target={target}
          busy={busy}
          error={error}
          onClose={() => {
            setTarget(null);
            setError(null);
          }}
          onConfirm={submit}
        />
      )}
    </div>
  );
}
