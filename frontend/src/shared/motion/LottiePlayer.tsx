"use client";

import { useEffect, useRef } from "react";
import { prefersReducedMotion } from "@/lib/motion/canvasLoop";
import { cn } from "@/lib/utils";

/** Phát file Lottie JSON trong public/lottie. Chỉ tải thư viện khi component xuất hiện. */
export function LottiePlayer({ src, loop = true, className }: { src: string; loop?: boolean; className?: string }) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const container = ref.current;
    if (!container || typeof window.matchMedia !== "function") return;
    let cancelled = false;
    let destroy = () => {};
    import("lottie-web/build/player/lottie_light")
      .then(({ default: lottie }) => {
        if (cancelled) return;
        const reduced = prefersReducedMotion();
        const animation = lottie.loadAnimation({ container, renderer: "svg", path: src, loop: loop && !reduced, autoplay: !reduced });
        destroy = () => animation.destroy();
      })
      .catch(() => {});
    return () => {
      cancelled = true;
      destroy();
    };
  }, [src, loop]);

  return <div ref={ref} aria-hidden="true" className={cn("size-20", className)} />;
}
