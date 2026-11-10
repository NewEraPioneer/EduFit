"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams, usePathname } from "next/navigation";

/**
 * Hook đọc và tiêu thụ đồng thời nhiều tham số deep link từ URL:
 * - Đọc toàn bộ các tham số trong `keys` từ `searchParams`.
 * - Lưu snapshot giá trị tham số vào React State để các component con / consumer
 *   bất đồng bộ (async fetchers) không bị mất giá trị khi URL được làm sạch.
 * - Tự động xóa tất cả các tham số đó trong đúng một lần gọi `router.replace` duy nhất,
 *   loại bỏ hoàn toàn race condition khi có nhiều tham số deep link xuất hiện cùng lúc (vd. sessionId + action).
 * - Nếu người dùng điều hướng hoặc click vào thông báo mới có query param mới, hook tự động nhận diện
 *   phiên điều hướng mới và cập nhật toàn bộ snapshot tương ứng (các param không còn xuất hiện sẽ về null).
 *
 * @param keys Mảng tên tham số query cần đọc (vd. ["sessionId", "action"])
 * @returns Object chứa cặp key-value tương ứng từ snapshot
 */
export function useDeepLinkParams<T extends string>(keys: T[]): Record<T, string | null> {
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();

  const keysKey = keys.join(",");

  // Snapshot giá trị đã đọc: khởi tạo từ searchParams hiện tại
  const [snapshot, setSnapshot] = useState<Record<T, string | null>>(() => {
    const result = {} as Record<T, string | null>;
    for (const key of keys) {
      result[key] = searchParams.get(key);
    }
    return result;
  });

  // Track query param đã được dọn khỏi URL để tránh replace lặp
  const cleanedUrlRef = useRef<string | null>(null);

  // Có param deep link trên URL ở lần render trước hay không (để bắt chuyển trạng thái vắng -> có)
  const livePresent = keys.some((key) => searchParams.get(key) !== null);
  const [wasPresent, setWasPresent] = useState<boolean>(livePresent);

  // Nhận diện phiên điều hướng mới ngay trong render (React 19 pattern) để tránh cascading render.
  // Điều hướng mới = param xuất hiện lại sau khi đã được dọn (kể cả cùng giá trị) hoặc giá trị khác snapshot.
  // Mỗi lần điều hướng mới tạo snapshot mới (định danh khác) để consumer chạy lại effect.
  if (livePresent) {
    const valueChanged = keys.some((key) => {
      const liveVal = searchParams.get(key);
      return liveVal !== null && liveVal !== snapshot[key];
    });
    if (!wasPresent || valueChanged) {
      const newSnapshot = {} as Record<T, string | null>;
      for (const key of keys) {
        newSnapshot[key] = searchParams.get(key);
      }
      setSnapshot(newSnapshot);
    }
  }
  if (livePresent !== wasPresent) {
    setWasPresent(livePresent);
  }

  // Xóa các param khỏi URL trong 1 lần router.replace duy nhất
  useEffect(() => {
    const presentKeys = keys.filter((key) => searchParams.get(key) !== null);
    if (presentKeys.length === 0) {
      // URL đã sạch: cho phép dọn lại khi cùng một query xuất hiện lần nữa
      cleanedUrlRef.current = null;
      return;
    }

    const currentFullSearch = searchParams.toString();
    if (cleanedUrlRef.current === currentFullSearch) return;
    cleanedUrlRef.current = currentFullSearch;

    const params = new URLSearchParams(searchParams.toString());
    for (const key of keys) {
      params.delete(key);
    }
    const newQuery = params.toString();
    const newUrl = newQuery ? `${pathname}?${newQuery}` : pathname;

    router.replace(newUrl, { scroll: false });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams, keysKey, pathname, router]);

  return snapshot;
}

/**
 * Hook đọc và tiêu thụ một tham số deep link từ URL:
 * Tái sử dụng `useDeepLinkParams` với mảng 1 phần tử.
 *
 * @param name Tên tham số query (vd. "sessionId", "tab", "action")
 * @returns Giá trị tham số tại thời điểm đọc, hoặc null nếu không có
 */
export function useDeepLinkParam(name: string): string | null {
  const params = useDeepLinkParams([name]);
  return params[name];
}
