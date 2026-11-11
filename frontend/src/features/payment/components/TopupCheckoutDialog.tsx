"use client";
import {
  useCallback,
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
} from "react";
import {
  Check,
  CheckCircle2,
  Copy,
  LoaderCircle,
  RefreshCw,
  ShieldCheck,
} from "lucide-react";
import {
  money,
  paymentApi,
  paymentError,
  type Checkout,
} from "../api/paymentApi";
import {
  loadPayosCheckout,
  validCheckoutUrl,
  validManualQr,
} from "../payosCheckout";
import { PaymentDialog } from "./PaymentDialog";

function PayosFrame({ url, verify }: { url: string; verify: () => void }) {
  const elementId = `payos-${useId().replace(/[^a-zA-Z0-9]/g, "")}`;
  const [error, setError] = useState<string | null>(null);
  const [ready, setReady] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const latestVerify = useRef(verify);
  useEffect(() => {
    latestVerify.current = verify;
  }, [verify]);
  useLayoutEffect(() => {
    let alive = true;
    let exit: (() => void) | undefined;
    async function mount() {
      try {
        if (!validCheckoutUrl(url))
          throw new Error(
            "Đường dẫn thanh toán không hợp lệ. Vui lòng liên hệ hỗ trợ.",
          );
        const sdk = await loadPayosCheckout();
        if (!alive) return;
        const checkout = sdk.usePayOS({
          CHECKOUT_URL: url,
          ELEMENT_ID: elementId,
          RETURN_URL: window.location.origin + window.location.pathname,
          embedded: true,
          onSuccess: () => {
            exit = undefined;
            if (alive) latestVerify.current();
          },
          onCancel: () => {
            exit = undefined;
            if (alive)
              setError(
                "Yêu cầu trên payOS đã đóng. Nếu đã chuyển tiền, hãy kiểm tra giao dịch trước khi tạo mã mới.",
              );
          },
          onExit: () => {
            exit = undefined;
            if (alive)
              setError(
                "Mã QR đã đóng. Bạn có thể hiển thị lại mã hoặc kiểm tra tiền đã chuyển.",
              );
          },
        });
        exit = checkout.exit;
        checkout.open();
        document
          .getElementById(elementId)
          ?.querySelector("iframe")
          ?.setAttribute("title", "QR thanh toán payOS");
        setReady(true);
      } catch (e) {
        if (alive) setError(paymentError(e));
      }
    }
    void mount();
    return () => {
      alive = false;
      exit?.();
    };
  }, [url, elementId, attempt]);
  return (
    <div>
      {!ready && !error && (
        <p
          role="status"
          className="flex items-center justify-center gap-2 py-10 text-sm text-muted"
        >
          <LoaderCircle className="size-4 animate-spin" />
          Đang tải mã QR…
        </p>
      )}
      <div
        id={elementId}
        aria-label="QR thanh toán payOS"
        className={
          error
            ? "hidden"
            : "h-[32rem] max-h-[60dvh] w-full overflow-hidden rounded-xl"
        }
      />
      {error && (
        <div className="space-y-3 rounded-xl bg-action-soft p-4 text-sm">
          <p role="alert">{error}</p>
          {validCheckoutUrl(url) && (
            <button
              type="button"
              onClick={() => {
                setError(null);
                setReady(false);
                setAttempt((a) => a + 1);
              }}
              className="font-medium underline"
            >
              Hiển thị lại mã QR
            </button>
          )}
        </div>
      )}
    </div>
  );
}
export function TopupCheckoutDialog({
  checkout,
  status,
  execute,
  onClose,
}: {
  checkout: Checkout;
  status?: string;
  execute: (work: () => Promise<unknown>) => Promise<boolean>;
  onClose: () => void;
}) {
  const [verified, setVerified] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const inFlight = useRef(false);
  const paid = status === "PAID" || verified === "PAID";
  const verify = useCallback(async () => {
    if (inFlight.current || paid) return;
    inFlight.current = true;
    setChecking(true);
    setError(null);
    await execute(async () => {
      try {
        const result = await paymentApi.refreshTopup(checkout.id);
        setVerified(result.data?.status ?? null);
      } catch (e) {
        setError(paymentError(e));
        throw e;
      }
    });
    inFlight.current = false;
    setChecking(false);
  }, [execute, checkout.id, paid]);
  return (
    <PaymentDialog
      title="Nạp tiền vào ví"
      onClose={onClose}
      wide={checkout.provider === "PAYOS" && !paid}
    >
      {paid ? (
        <div role="status" className="space-y-4 py-6 text-center">
          <CheckCircle2 className="mx-auto size-14 text-success-strong" />
          <h3 className="text-xl font-bold">Nạp tiền thành công</h3>
          <p className="text-sm text-ink-2">
            Giao dịch đã được xác minh. Số dư ví được cập nhật từ hệ thống.
          </p>
          <button
            onClick={onClose}
            className="rounded-xl bg-night px-6 py-3 text-sm font-medium text-white"
          >
            Về ví của tôi
          </button>
        </div>
      ) : (
        <div
          className={
            checkout.provider === "PAYOS"
              ? "grid gap-5 md:grid-cols-2"
              : "space-y-4"
          }
        >
          <div className="text-center md:col-start-1">
            <p className="text-sm text-muted">Số tiền cần chuyển</p>
            <p className="mt-1 text-3xl font-bold tracking-tight">
              {checkout.amount ? money(checkout.amount) : "Theo mã QR bên dưới"}
            </p>
            <p className="mt-2 text-sm text-muted">
              Mở ứng dụng ngân hàng và quét mã để nạp vào ví.
            </p>
          </div>
          {checkout.provider === "PAYOS" ? (
            <div className="md:col-start-2 md:row-start-1 md:row-span-6">
              <PayosFrame
                url={checkout.checkoutUrl ?? ""}
                verify={() => void verify()}
              />
            </div>
          ) : (
            <div className="space-y-4">
              {validManualQr(checkout.qrUrl) ? (
                <div className="mx-auto max-w-xs rounded-2xl border border-line bg-surface p-3">
                  {/* A payment QR must be shown directly, without Next image optimization/caching. */}
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src={checkout.qrUrl}
                    alt="Mã QR chuyển khoản"
                    className="h-auto w-full"
                    referrerPolicy="no-referrer"
                  />
                </div>
              ) : (
                <p role="alert" className="rounded-lg bg-action-soft p-3 text-sm">
                  Chưa tải được ảnh QR. Kiểm tra thông tin chuyển khoản hoặc
                  liên hệ hỗ trợ.
                </p>
              )}
              <div className="rounded-xl bg-paper p-4 text-sm">
                <p>{checkout.bankHolder}</p>
                <p className="font-medium">{checkout.bankAccount}</p>
                {checkout.description && (
                  <div className="mt-2 flex items-center justify-between gap-2">
                    <span>
                      Nội dung: <strong>{checkout.description}</strong>
                    </span>
                    <button
                      aria-label="Sao chép nội dung chuyển khoản"
                      onClick={async () => {
                        try {
                          await navigator.clipboard.writeText(
                            checkout.description!,
                          );
                          setCopied(true);
                        } catch {
                          setError(
                            "Không thể sao chép. Bạn có thể nhập nội dung hiển thị bên cạnh.",
                          );
                        }
                      }}
                      className="rounded-lg border bg-surface p-2"
                    >
                      {copied ? (
                        <Check className="size-4" />
                      ) : (
                        <Copy className="size-4" />
                      )}
                    </button>
                  </div>
                )}
              </div>
              <p className="text-sm text-action-strong">
                Admin xác minh giao dịch thực tế trước khi cộng ví.
              </p>
            </div>
          )}
          {error && (
            <p
              role="alert"
              className="rounded-lg bg-danger-soft p-3 text-sm text-danger-strong md:col-start-1"
            >
              {error}
            </p>
          )}
          {checkout.provider === "PAYOS" && (
            <button
              disabled={checking}
              onClick={() => void verify()}
              className="flex w-full items-center justify-center gap-2 rounded-xl border border-line-strong md:col-start-1 px-4 py-3 text-sm font-medium disabled:opacity-50"
            >
              <RefreshCw
                className={`size-4 ${checking ? "animate-spin" : ""}`}
              />
              {checking ? "Đang xác minh…" : "Tôi đã chuyển tiền"}
            </button>
          )}
          {verified === "PENDING" && (
            <p
              role="status"
              className="text-center text-sm text-action-strong md:col-start-1"
            >
              Chưa nhận được xác nhận thanh toán. Ví sẽ tự cập nhật khi giao
              dịch được xác minh.
            </p>
          )}
          <p className="flex items-start gap-2 border-t border-line pt-4 text-xs leading-relaxed text-muted md:col-start-1">
            <ShieldCheck className="mt-0.5 size-4 shrink-0" />
            Đóng popup không hủy yêu cầu nạp. Nếu đã chuyển tiền, hãy chờ xác
            minh trước khi tạo yêu cầu khác.
          </p>
        </div>
      )}
    </PaymentDialog>
  );
}
