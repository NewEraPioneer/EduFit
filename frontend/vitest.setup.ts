import "@testing-library/jest-dom/vitest";
import { vi } from "vitest";
import { useSyncExternalStore } from "react";

// Mock điều hướng thực tế cho next/navigation trong môi trường kiểm thử vitest/jsdom:
// - Đảm bảo router.replace / router.push đồng bộ URL trên window.history.
// - Tự động kích hoạt re-render đồng bộ cho các component sử dụng useSearchParams / usePathname.
type NavigationListener = () => void;
const navigationListeners = new Set<NavigationListener>();

function notifyNavigation() {
  navigationListeners.forEach((listener) => {
    try {
      listener();
    } catch {
      // Ignore listener error during teardown
    }
  });
}

const subscribe = (callback: () => void) => {
  navigationListeners.add(callback);
  return () => {
    navigationListeners.delete(callback);
  };
};

export const mockRouter = {
  push: vi.fn((url: string) => {
    if (typeof window !== "undefined") {
      window.history.pushState(null, "", url);
      notifyNavigation();
    }
  }),
  replace: vi.fn((url: string) => {
    if (typeof window !== "undefined") {
      window.history.replaceState(null, "", url);
      notifyNavigation();
    }
  }),
  prefetch: vi.fn(),
  // back/forward trong jsdom chạy bất đồng bộ: để sự kiện popstate thông báo sau khi URL đã đổi
  back: vi.fn(() => {
    if (typeof window !== "undefined") window.history.back();
  }),
  forward: vi.fn(() => {
    if (typeof window !== "undefined") window.history.forward();
  }),
};

if (typeof window !== "undefined") {
  window.addEventListener("popstate", notifyNavigation);
}

let lastSearchString = "";
let cachedSearchParams = new URLSearchParams();

vi.mock("next/navigation", () => {
  return {
    useRouter: () => mockRouter,
    useSearchParams: () => {
      const searchStr = useSyncExternalStore(
        subscribe,
        () => (typeof window !== "undefined" ? window.location.search : ""),
        () => ""
      );

      if (searchStr !== lastSearchString) {
        lastSearchString = searchStr;
        cachedSearchParams = new URLSearchParams(searchStr);
      }
      return cachedSearchParams;
    },
    usePathname: () => {
      return useSyncExternalStore(
        subscribe,
        () => (typeof window !== "undefined" ? window.location.pathname : "/"),
        () => "/"
      );
    },
    useParams: () => ({}),
  };
});
