"use client";

import { useEffect, type RefObject } from "react";

/** phase chạy trong [0, 1). Hàm render phải tuần hoàn: render(0) và render(1) vẽ cùng một khung. */
export type LoopRender = (ctx: CanvasRenderingContext2D, phase: number, width: number, height: number) => void;

export const TAU = Math.PI * 2;

export function wrap(value: number) {
  return value - Math.floor(value);
}

export function prefersReducedMotion() {
  return typeof window.matchMedia !== "function" || window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

/** Chạy hàm render của một section. Dừng khi section ra khỏi màn hình hoặc tab bị ẩn. */
export function useCanvasLoop(ref: RefObject<HTMLCanvasElement | null>, render: LoopRender, durationMs: number) {
  useEffect(() => {
    const canvas = ref.current;
    const ctx = canvas?.getContext?.("2d");
    if (!canvas || !ctx) return;

    let frame = 0;
    let visible = false;
    const draw = (phase: number) => {
      const ratio = Math.min(window.devicePixelRatio || 1, 2);
      const { clientWidth: w, clientHeight: h } = canvas;
      if (canvas.width !== Math.round(w * ratio)) canvas.width = Math.round(w * ratio);
      if (canvas.height !== Math.round(h * ratio)) canvas.height = Math.round(h * ratio);
      ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
      ctx.clearRect(0, 0, w, h);
      render(ctx, phase, w, h);
    };

    if (prefersReducedMotion()) {
      draw(0);
      return;
    }

    const tick = (now: number) => {
      draw((now % durationMs) / durationMs);
      frame = requestAnimationFrame(tick);
    };
    const sync = () => {
      cancelAnimationFrame(frame);
      if (visible && !document.hidden) frame = requestAnimationFrame(tick);
    };
    const observer = new IntersectionObserver(([entry]) => {
      visible = entry.isIntersecting;
      sync();
    });
    observer.observe(canvas);
    document.addEventListener("visibilitychange", sync);
    return () => {
      cancelAnimationFrame(frame);
      observer.disconnect();
      document.removeEventListener("visibilitychange", sync);
    };
  }, [ref, render, durationMs]);
}
