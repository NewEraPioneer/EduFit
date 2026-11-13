"use client";
import { useEffect, useId, useRef, useState, type KeyboardEvent } from "react";
import {
  ArrowDownLeft,
  ArrowUpRight,
  BookOpen,
  ChevronLeft,
  ChevronRight,
  RefreshCw,
} from "lucide-react";
import {
  entryLabels,
  money,
  paymentApi,
  paymentError,
  settlement,
  type SessionPaymentRecord,
  type TopupRecord,
  type Withdrawal,
} from "../api/paymentApi";
import { PaymentDialog } from "./PaymentDialog";
import { SettlementAmount } from "./SettlementAmount";

const PAGE_SIZE = 50;

type Section = "topups" | "sessions" | "withdrawals";
type Rows =
  | { section: "topups"; items: TopupRecord[] }
  | { section: "sessions"; items: SessionPaymentRecord[] }
  | { section: "withdrawals"; items: Withdrawal[] };

/** Người học xem tiền nạp vào ví; gia sư không nạp tiền nên xem tiền rút về ngân hàng. */
function sectionsFor(role: "STUDENT" | "PARENT" | "TUTOR") {
  return role === "TUTOR"
    ? ([
        ["sessions", "Thu nhập buổi dạy"],
        ["withdrawals", "Rút tiền"],
      ] as const)
    : ([
        ["topups", "Nạp tiền"],
        ["sessions", "Thanh toán buổi học"],
      ] as const);
}

async function load(section: Section, offset: number): Promise<Rows> {
  if (section === "withdrawals") {
    const response = await paymentApi.wallet(offset);
    return { section, items: response.data?.withdrawals ?? [] };
  }
  const response = await paymentApi.history(section, offset);
  return section === "topups"
    ? { section, items: (response.data ?? []) as TopupRecord[] }
    : { section, items: (response.data ?? []) as SessionPaymentRecord[] };
}

const dateTime = (value: string) => new Date(value).toLocaleString("vi-VN");
const signed = (amount: number) => `${amount > 0 ? "+" : ""}${money(amount)}`;

/** Học viên thấy khoản trả gia sư hoặc hoàn tiền; gia sư thấy thu nhập buổi dạy. */
function SessionAmount({ record: e }: { record: SessionPaymentRecord }) {
  if (e.kind === "SESSION_SETTLED")
    return (
      <SettlementAmount entry={e} className="pl-7 sm:pl-0 sm:text-right" />
    );
  return (
    <p
      className={`whitespace-nowrap pl-7 text-sm font-medium sm:pl-0 ${
        e.available_delta > 0 ? "text-success-strong" : ""
      }`}
    >
      {signed(e.available_delta)}
    </p>
  );
}

export function PaymentHistoryDialog({
  role,
  onClose,
}: {
  role: "STUDENT" | "PARENT" | "TUTOR";
  onClose: () => void;
}) {
  const sections = sectionsFor(role);
  const [section, setSection] = useState<Section>(sections[0][0]);
  const [offset, setOffset] = useState(0);
  const [rows, setRows] = useState<Rows | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  const panelId = useId();
  const tabs = useRef<(HTMLButtonElement | null)[]>([]);

  useEffect(() => {
    let alive = true;
    async function run() {
      setLoading(true);
      try {
        const next = await load(section, offset);
        if (alive) {
          setRows(next);
          setError(null);
        }
      } catch (e) {
        if (alive) setError(paymentError(e));
      } finally {
        if (alive) setLoading(false);
      }
    }
    void run();
    return () => {
      alive = false;
    };
  }, [section, offset, reload]);

  function select(next: Section) {
    setSection(next);
    setOffset(0);
    setRows(null);
  }

  function onTabKey(event: KeyboardEvent, index: number) {
    const step =
      event.key === "ArrowRight" ? 1 : event.key === "ArrowLeft" ? -1 : 0;
    if (!step) return;
    event.preventDefault();
    const next = (index + step + sections.length) % sections.length;
    select(sections[next][0]);
    tabs.current[next]?.focus();
  }

  const current = rows?.section === section ? rows : null;
  const count = current?.items.length ?? 0;

  return (
    <PaymentDialog title="Giao dịch của tôi" onClose={onClose} wide>
      <div className="space-y-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div
            role="tablist"
            aria-label="Mục lịch sử giao dịch"
            className="flex gap-1 rounded-xl bg-sunken p-1"
          >
            {sections.map(([key, label], index) => (
              <button
                key={key}
                ref={(el) => {
                  tabs.current[index] = el;
                }}
                type="button"
                role="tab"
                id={`${panelId}-${key}`}
                aria-selected={section === key}
                aria-controls={panelId}
                tabIndex={section === key ? 0 : -1}
                onClick={() => select(key)}
                onKeyDown={(event) => onTabKey(event, index)}
                className={`min-h-11 rounded-lg px-3 py-2 text-sm font-medium ${
                  section === key
                    ? "bg-action-soft text-action-strong shadow-sm"
                    : "text-muted hover:text-ink"
                }`}
              >
                {label}
              </button>
            ))}
          </div>
          <button
            type="button"
            aria-label="Làm mới giao dịch"
            disabled={loading}
            onClick={() => setReload((n) => n + 1)}
            className="rounded-lg border border-line p-2"
          >
            <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          </button>
        </div>
        {error && (
          <p
            role="alert"
            className="rounded-xl bg-danger-soft p-3 text-sm text-danger-strong"
          >
            {error}
          </p>
        )}
        <div
          role="tabpanel"
          id={panelId}
          aria-labelledby={`${panelId}-${section}`}
        >
          {loading && !current ? (
            <p role="status" className="py-8 text-center text-sm text-muted">
              Đang tải giao dịch…
            </p>
          ) : !count ? (
            <p className="py-8 text-center text-sm text-muted">
              {section === "topups"
                ? "Chưa có khoản nạp nào được cộng vào ví."
                : section === "sessions"
                  ? "Chưa có khoản tiền buổi học nào."
                  : "Chưa có yêu cầu rút tiền nào."}
            </p>
          ) : (
            <ul className="space-y-3">
              {current?.section === "topups" &&
                current.items.map((t) => (
                  <li
                    key={t.id}
                    className="flex items-start gap-3 rounded-xl border border-line p-4"
                  >
                    <ArrowDownLeft className="mt-1 size-4 shrink-0 text-success-strong" />
                    <div className="min-w-0">
                      <p className="text-sm font-medium">
                        Nạp {money(t.amount)}
                      </p>
                      <p className="mt-1 text-xs text-muted">
                        Đã cộng ví · {dateTime(t.paid_at)}
                      </p>
                      <p className="mt-1 break-all text-xs text-muted">
                        Mã đơn {t.order_code}
                        {t.bank_reference
                          ? ` · Mã giao dịch ${t.bank_reference}`
                          : ""}
                      </p>
                    </div>
                  </li>
                ))}
              {current?.section === "sessions" &&
                current.items.map((e) => (
                  <li
                    key={e.id}
                    className="flex flex-col gap-2 rounded-xl border border-line p-4 sm:flex-row sm:items-start sm:justify-between sm:gap-3"
                  >
                    <div className="flex min-w-0 items-start gap-3">
                      <BookOpen className="mt-1 size-4 shrink-0 text-action-strong" />
                      <div className="min-w-0">
                        <p className="text-sm font-medium">
                          {e.kind === "SESSION_SETTLED"
                            ? settlement(e).title
                            : (entryLabels[e.kind] ?? e.kind)}
                        </p>
                        <p className="mt-1 text-xs text-muted">
                          {e.session_end_at
                            ? `Buổi học kết thúc ${dateTime(e.session_end_at)} · `
                            : ""}
                          Ghi nhận {dateTime(e.created_at)}
                        </p>
                      </div>
                    </div>
                    <SessionAmount record={e} />
                  </li>
                ))}
              {current?.section === "withdrawals" &&
                current.items.map((w) => (
                  <li
                    key={w.id}
                    className="flex items-start gap-3 rounded-xl border border-line p-4"
                  >
                    <ArrowUpRight className="mt-1 size-4 shrink-0 text-muted" />
                    <div className="min-w-0">
                      <p className="text-sm font-medium">
                        Rút {money(w.amount)}
                      </p>
                      <p className="mt-1 text-xs text-muted">
                        {w.bank_name} · •••• {w.bank_account.slice(-4)} ·{" "}
                        {w.status === "PAID"
                          ? "Đã chuyển"
                          : w.status === "PROCESSING"
                            ? "Đang chuyển"
                            : w.status === "PENDING"
                              ? "Chờ duyệt"
                              : "Đã hoàn về ví"}
                      </p>
                      <p className="mt-1 text-xs text-muted">
                        {new Date(w.created_at).toLocaleDateString("vi-VN")}
                      </p>
                    </div>
                  </li>
                ))}
            </ul>
          )}
        </div>
        <footer className="flex items-center justify-between gap-3 border-t border-line pt-4 text-sm">
          <button
            type="button"
            disabled={offset === 0 || loading}
            onClick={() => setOffset((n) => Math.max(0, n - PAGE_SIZE))}
            className="flex items-center gap-1 rounded-lg border px-3 py-2 disabled:opacity-30"
          >
            <ChevronLeft className="size-4" />
            Trước
          </button>
          <span className="text-xs text-muted">
            Trang {offset / PAGE_SIZE + 1}
          </span>
          <button
            type="button"
            disabled={count < PAGE_SIZE || loading}
            onClick={() => setOffset((n) => n + PAGE_SIZE)}
            className="flex items-center gap-1 rounded-lg border px-3 py-2 disabled:opacity-30"
          >
            Sau
            <ChevronRight className="size-4" />
          </button>
        </footer>
      </div>
    </PaymentDialog>
  );
}
