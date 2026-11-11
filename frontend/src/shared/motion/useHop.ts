"use client";

import { useLayoutEffect, useRef, type RefObject } from "react";
import { prefersReducedMotion } from "@/lib/motion/canvasLoop";

/** Chỉ báo bay theo đường vòng từ mục cũ sang mục mới, để người dùng thấy mình vừa chuyển từ đâu. */
export function useHop(
  containerRef: RefObject<HTMLElement | null>,
  indicatorRef: RefObject<HTMLElement | null>,
  activeKey: string | null,
  axis: "x" | "y" = "x",
) {
  const previous = useRef<{ x: number; y: number } | null>(null);

  useLayoutEffect(() => {
    const container = containerRef.current;
    const indicator = indicatorRef.current;
    if (!container || !indicator) return;

    const place = () => {
      const target = activeKey ? container.querySelector<HTMLElement>(`[data-hop="${activeKey}"]`) : null;
      if (!target) {
        indicator.style.opacity = "0";
        previous.current = null;
        return;
      }
      const next = { x: target.offsetLeft, y: target.offsetTop };
      const start = previous.current;
      const toTransform = (p: { x: number; y: number }) => `translate(${p.x}px, ${p.y}px)`;
      indicator.style.opacity = "1";
      indicator.style.width = `${target.offsetWidth}px`;
      indicator.style.height = `${target.offsetHeight}px`;
      indicator.style.transform = toTransform(next);
      if (start && (start.x !== next.x || start.y !== next.y) && typeof indicator.animate === "function" && !prefersReducedMotion()) {
        const mx = (start.x + next.x) / 2 + (axis === "y" ? 10 : 0);
        const my = (start.y + next.y) / 2 - (axis === "x" ? 12 : 0);
        indicator.animate(
          [
            { transform: toTransform(start) },
            { transform: `translate(${mx}px, ${my}px) scale(1.06, 0.9)`, offset: 0.5 },
            { transform: toTransform(next) },
          ],
          { duration: 380, easing: "cubic-bezier(0.34, 1.4, 0.64, 1)" },
        );
      }
      previous.current = next;
    };

    place();
    if (typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(place);
    observer.observe(container);
    return () => observer.disconnect();
  }, [containerRef, indicatorRef, activeKey, axis]);
}
