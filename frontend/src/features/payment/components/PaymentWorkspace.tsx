"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { RefreshCw } from "lucide-react";
import {
  paymentApi,
  paymentError,
  type Overview,
  type PaymentSession,
} from "../api/paymentApi";
import { WalletPanel } from "./WalletPanel";
import { PaymentActivity } from "./PaymentActivity";
import { PaymentHistoryDialog } from "./PaymentHistoryDialog";
import { PaymentSessionsPanel } from "./PaymentSessionsPanel";
export function PaymentWorkspace({
  role,
}: {
  role: "STUDENT" | "PARENT" | "TUTOR";
}) {
  const [month, setMonth] = useState(() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
  });
  const [history, setHistory] = useState(false);
  const [data, setData] = useState<Overview | null>(null);
  const [sessions, setSessions] = useState<PaymentSession[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const balance = useRef<string | null>(null);
  const generation = useRef(0);
  const alive = useRef(true);
  const load = useCallback(async () => {
    const current = ++generation.current;
    try {
      const [year, m] = month.split("-").map(Number);
      if (!year || !m) return;
      const [wallet, list] = await Promise.all([
        paymentApi.wallet(0),
        paymentApi.sessions(
          new Date(year, m - 1, 1).toISOString(),
          new Date(year, m, 1).toISOString(),
        ),
      ]);
      if (alive.current && current === generation.current) {
        const currentBalance = wallet.data
          ? `${wallet.data.wallet.available}:${wallet.data.wallet.held}`
          : null;
        if (balance.current !== null && currentBalance !== balance.current)
          setNotice("Số dư ví đã được cập nhật.");
        balance.current = currentBalance;
        setData(wallet.data ?? null);
        setSessions(list.data ?? []);
        setError(null);
      }
    } catch (e) {
      if (alive.current && current === generation.current)
        setError(paymentError(e));
    }
  }, [month]);
  useEffect(() => {
    alive.current = true;
    async function initial() {
      await load();
    }
    void initial();
    const timer = setInterval(() => {
      if (!document.hidden) void load();
    }, 15000);
    const focused = () => void load();
    window.addEventListener("focus", focused);
    return () => {
      alive.current = false;
      clearInterval(timer);
      window.removeEventListener("focus", focused);
    };
  }, [load]);
  async function execute(work: () => Promise<unknown>) {
    setBusy(true);
    setNotice(null);
    setError(null);
    try {
      await work();
      setNotice("Đã ghi nhận yêu cầu.");
      await load();
      return true;
    } catch (e) {
      setError(paymentError(e));
      return false;
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="mx-auto max-w-6xl space-y-6 pb-8">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-widest text-amber-700">
            EduFit / Thanh toán
          </p>
          <h1 className="mt-2 text-2xl font-bold tracking-tight sm:text-3xl">
            {role === "TUTOR"
              ? "Thu nhập của bạn"
              : role === "PARENT"
                ? "Ví học phí gia đình"
                : "Ví học phí"}
          </h1>
          <p className="mt-2 text-sm text-neutral-500">
            {role === "TUTOR"
              ? "Theo dõi buổi dạy, quản lý thu nhập và rút về ngân hàng."
              : "Quản lý tiền học và phản hồi từng buổi tại một nơi."}
          </p>
        </div>
        <button
          disabled={busy}
          onClick={() => void load()}
          className="inline-flex items-center gap-2 rounded-xl border border-stone-200 bg-white px-3 py-2 text-sm font-medium text-neutral-600 hover:bg-stone-50"
        >
          <RefreshCw className={`size-4 ${busy ? "animate-spin" : ""}`} />
          Làm mới
        </button>
      </header>
      {error && (
        <p
          role="alert"
          className="rounded-xl bg-red-50 p-4 text-sm text-red-800"
        >
          {error}
        </p>
      )}
      {notice && (
        <p
          role="status"
          className="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-800"
        >
          {notice}
        </p>
      )}
      {!data ? (
        <div
          role="status"
          className="animate-pulse space-y-4 rounded-2xl border border-stone-200 bg-white p-8"
        >
          <p className="text-sm text-neutral-500">
            {error
              ? "Chưa tải được dữ liệu ví. Bấm làm mới để thử lại."
              : "Đang tải ví và học phí…"}
          </p>
          <div className="h-12 w-48 rounded-lg bg-stone-100" />
        </div>
      ) : (
        <>
          <WalletPanel
            data={data}
            tutor={role === "TUTOR"}
            busy={busy}
            execute={execute}
          />
          <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_18rem] xl:grid-cols-[minmax(0,1fr)_22rem]">
            <PaymentSessionsPanel
              key={month}
              sessions={sessions}
              role={role}
              month={month}
              onMonth={setMonth}
            />
            <PaymentActivity
              entries={data.entries}
              onHistory={() => setHistory(true)}
            />
          </div>
        </>
      )}
      {history && (
        <PaymentHistoryDialog
          onClose={() => setHistory(false)}
          execute={execute}
        />
      )}
    </div>
  );
}
