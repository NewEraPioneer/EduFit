"use client";

import { useEffect, useRef } from "react";
import { prefersReducedMotion } from "@/lib/motion/canvasLoop";

/** Vệt phấn theo con trỏ trong section cha. Không chạy trên màn hình cảm ứng. */
export function CursorTrail() {
  const ref = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = ref.current;
    const host = canvas?.parentElement;
    const ctx = canvas?.getContext?.("2d");
    if (!canvas || !host || !ctx || prefersReducedMotion() || !window.matchMedia("(pointer: fine)").matches) return;

    const dust: { x: number; y: number; life: number }[] = [];
    const color = getComputedStyle(document.documentElement).getPropertyValue("--action").trim();
    let frame = 0;

    const tick = () => {
      const ratio = Math.min(window.devicePixelRatio || 1, 2);
      const { clientWidth: w, clientHeight: h } = canvas;
      canvas.width = w * ratio;
      canvas.height = h * ratio;
      ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
      ctx.fillStyle = color;
      for (let i = dust.length - 1; i >= 0; i--) {
        const d = dust[i];
        d.life -= 0.035;
        d.y += 0.35;
        if (d.life <= 0) {
          dust.splice(i, 1);
          continue;
        }
        ctx.globalAlpha = d.life * 0.55;
        ctx.beginPath();
        ctx.arc(d.x, d.y, 1.2 + d.life * 2.4, 0, Math.PI * 2);
        ctx.fill();
      }
      frame = dust.length ? requestAnimationFrame(tick) : 0;
    };

    const onMove = (event: PointerEvent) => {
      const box = canvas.getBoundingClientRect();
      dust.push({ x: event.clientX - box.left, y: event.clientY - box.top, life: 1 });
      if (dust.length > 60) dust.shift();
      if (!frame) frame = requestAnimationFrame(tick);
    };

    host.addEventListener("pointermove", onMove);
    return () => {
      host.removeEventListener("pointermove", onMove);
      cancelAnimationFrame(frame);
    };
  }, []);

  return <canvas ref={ref} aria-hidden="true" className="pointer-events-none absolute inset-0 z-0 size-full" />;
}
