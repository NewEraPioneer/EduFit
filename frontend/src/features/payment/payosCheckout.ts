// Official frontend SDK. It receives only a checkout URL, never merchant credentials.
export interface PayosEmbedConfig {
  RETURN_URL: string;
  ELEMENT_ID: string;
  CHECKOUT_URL: string;
  embedded: boolean;
  onSuccess: () => void;
  onCancel: () => void;
  onExit: () => void;
}
interface PayosCheckoutSdk {
  usePayOS: (config: PayosEmbedConfig) => {
    open: () => void;
    exit: () => void;
  };
}
declare global {
  interface Window {
    PayOSCheckout?: PayosCheckoutSdk;
  }
}
let loading: Promise<PayosCheckoutSdk> | null = null;
export function validCheckoutUrl(value?: string) {
  try {
    const u = new URL(value ?? "");
    return (
      u.protocol === "https:" &&
      u.hostname === "pay.payos.vn" &&
      !u.username &&
      !u.password &&
      (!u.port || u.port === "443")
    );
  } catch {
    return false;
  }
}
export function validManualQr(value?: string) {
  try {
    const u = new URL(value ?? "");
    return (
      u.protocol === "https:" &&
      u.hostname === "img.vietqr.io" &&
      !u.username &&
      !u.password &&
      !u.port
    );
  } catch {
    return false;
  }
}
export function loadPayosCheckout(): Promise<PayosCheckoutSdk> {
  if (window.PayOSCheckout) return Promise.resolve(window.PayOSCheckout);
  if (loading) return loading;
  loading = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src =
      "https://cdn.payos.vn/payos-checkout/v1/stable/payos-initialize.js";
    script.async = true;
    const fail = () => {
      clearTimeout(timer);
      script.remove();
      loading = null;
      reject(
        new Error("Không tải được mã QR payOS. Kiểm tra kết nối rồi thử lại."),
      );
    };
    const timer = window.setTimeout(fail, 15000);
    script.onerror = fail;
    script.onload = () => {
      clearTimeout(timer);
      if (window.PayOSCheckout) resolve(window.PayOSCheckout);
      else fail();
    };
    document.head.appendChild(script);
  });
  return loading;
}
