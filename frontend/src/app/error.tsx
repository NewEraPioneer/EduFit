"use client";

import { useEffect } from "react";
import { Mascot } from "@/shared/motion/Mascot";
import { Button } from "@/shared/ui/Button";

export default function Error({ error, retry }: { error: Error & { digest?: string }; retry: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <div className="mx-auto flex min-h-dvh max-w-md flex-col items-center justify-center gap-3 p-6 text-center">
      <Mascot mood="shy" className="size-24" />
      <h1 className="font-heading text-2xl font-bold text-ink">Trang này chưa mở được</h1>
      <p className="text-sm text-muted">Bấm Thử lại. Nếu lỗi vẫn còn, hãy quay về trang chủ.</p>
      <div className="flex gap-2">
        <Button onClick={() => retry()}>Thử lại</Button>
        <Button href="/" variant="secondary">
          Về trang chủ
        </Button>
      </div>
    </div>
  );
}
