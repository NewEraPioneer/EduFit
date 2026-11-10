"use client";

import { useTheme } from "@/context/ThemeContext";
import { cn } from "@/lib/utils";

/** Thanh ngày/đêm: núm mặt trời trượt sang và thành mặt trăng khi bật giao diện tối. */
export function DayNightToggle({ className }: { className?: string }) {
  const { resolvedTheme, setTheme } = useTheme();
  const night = resolvedTheme === "dark";
  const spring = "duration-500 ease-[var(--ease-jelly)]";

  return (
    <button
      type="button"
      role="switch"
      aria-checked={night}
      aria-label="Giao diện tối"
      data-compact
      onClick={() => setTheme(night ? "light" : "dark")}
      className={cn(
        "relative inline-flex h-9 w-[68px] shrink-0 items-center overflow-hidden rounded-full border border-line transition-colors",
        night ? "bg-night" : "bg-brand-tint",
        className,
      )}
    >
      <svg viewBox="0 0 68 36" aria-hidden="true" className="absolute inset-0 size-full">
        <g className={cn("transition-opacity", night ? "opacity-100" : "opacity-0")} fill="#fff">
          <circle cx="14" cy="11" r="1.2" className="loop-breathe" />
          <circle cx="24" cy="23" r="1" />
          <circle cx="32" cy="10" r="1.4" className="loop-breathe" />
        </g>
        <g className={cn("transition-[opacity,translate]", spring, night ? "translate-y-3 opacity-0" : "opacity-100")} fill="#fff">
          <ellipse cx="46" cy="24" rx="9" ry="4" />
          <ellipse cx="52" cy="20" rx="6" ry="4" />
        </g>
      </svg>
      <span
        aria-hidden="true"
        className={cn(
          "relative z-10 ml-[3px] size-7 overflow-hidden rounded-full transition-[translate,background-color]",
          spring,
          night ? "translate-x-[32px] bg-[#e8edf7]" : "translate-x-0 bg-action",
        )}
      >
        <span className={cn("absolute top-0.5 size-6 rounded-full bg-night transition-[left]", spring, night ? "left-2.5" : "left-8")} />
      </span>
    </button>
  );
}
