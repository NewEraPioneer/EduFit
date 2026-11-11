"use client";

import { useEffect, useRef } from "react";
import { cn } from "@/lib/utils";

export type MascotMood = "idle" | "happy" | "shy";

/** Mầm, nhân vật của EduFit. Mắt nhìn theo con trỏ, che mắt khi người dùng nhập mật khẩu. */
export function Mascot({ mood = "idle", className }: { mood?: MascotMood; className?: string }) {
  const ref = useRef<SVGSVGElement>(null);

  useEffect(() => {
    const svg = ref.current;
    if (!svg || typeof window.matchMedia !== "function" || !window.matchMedia("(pointer: fine)").matches) return;
    let frame = 0;
    const onMove = (event: PointerEvent) => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        const box = svg.getBoundingClientRect();
        const dx = event.clientX - (box.left + box.width / 2);
        const dy = event.clientY - (box.top + box.height / 2);
        const length = Math.hypot(dx, dy) || 1;
        const reach = Math.min(length / 60, 1) * 3;
        svg.style.setProperty("--ex", `${(dx / length) * reach}px`);
        svg.style.setProperty("--ey", `${(dy / length) * reach}px`);
      });
    };
    window.addEventListener("pointermove", onMove);
    return () => {
      window.removeEventListener("pointermove", onMove);
      cancelAnimationFrame(frame);
    };
  }, []);

  const shy = mood === "shy";
  const happy = mood === "happy";
  const spring = "transition-[translate,rotate,opacity] duration-500 ease-[var(--ease-jelly)] [transform-box:fill-box]";

  return (
    <svg ref={ref} viewBox="0 0 96 96" aria-hidden="true" className={cn("overflow-visible", className)}>
      <g className={cn(spring, happy && "-translate-y-1.5")}>
        <path d="M48 42V24" stroke="var(--success)" strokeWidth="4" strokeLinecap="round" />
        <path d="M48 26C38 26 31 20 29 10c11-1 19 5 19 16Z" fill="var(--success)" className="loop-breathe" />
        <path d="M48 26c10 0 17-6 19-16-11-1-19 5-19 16Z" fill="#a7ce8c" className="loop-breathe" />
        <rect x="16" y="40" width="64" height="50" rx="24" fill="var(--action)" />
        {happy ? (
          <g stroke="var(--on-action)" strokeWidth="3.5" strokeLinecap="round" fill="none">
            <path d="M31 62c2-5 8-5 10 0" />
            <path d="M55 62c2-5 8-5 10 0" />
          </g>
        ) : (
          <g style={{ transform: "translate(var(--ex, 0px), var(--ey, 0px))" }}>
            <circle cx="36" cy="60" r="4.5" fill="var(--on-action)" />
            <circle cx="60" cy="60" r="4.5" fill="var(--on-action)" />
          </g>
        )}
        <path d={happy ? "M40 72c4 6 12 6 16 0" : "M42 74c4 2 8 2 12 0"} stroke="var(--on-action)" strokeWidth="3" strokeLinecap="round" fill="none" />
        <path d="M14 74c6-14 16-22 26-16 2 6-8 16-20 22Z" fill="#a7ce8c" className={cn(spring, shy ? "opacity-100" : "translate-y-5 -rotate-12 opacity-0")} />
        <path d="M82 74c-6-14-16-22-26-16-2 6 8 16 20 22Z" fill="#a7ce8c" className={cn(spring, shy ? "opacity-100" : "translate-y-5 rotate-12 opacity-0")} />
      </g>
    </svg>
  );
}
