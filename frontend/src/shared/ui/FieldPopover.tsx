"use client";

import { useLayoutEffect, useRef, useState, type ReactNode, type RefObject } from "react";
import { createPortal } from "react-dom";
import { cn } from "@/lib/utils";

const GAP_PX = 6;
const EDGE_PX = 8;

interface FieldPopoverProps {
  anchorRef: RefObject<HTMLElement | null>;
  className?: string;
  children: ReactNode;
}

/** Bảng bung của ô nhập. Gắn vào body để không bị dialog cắt, giữ tiêu điểm ở ô nhập khi bấm vào bảng. */
export function FieldPopover({ anchorRef, className, children }: FieldPopoverProps) {
  const panelRef = useRef<HTMLDivElement>(null);
  const [position, setPosition] = useState<{ top: number; left: number } | null>(null);

  useLayoutEffect(() => {
    const place = () => {
      const anchor = anchorRef.current;
      const panel = panelRef.current;
      if (!anchor || !panel) return;
      const box = anchor.getBoundingClientRect();
      const below = window.innerHeight - box.bottom;
      const flip = below < panel.offsetHeight + GAP_PX + EDGE_PX && box.top > below;
      setPosition({
        top: Math.max(EDGE_PX, flip ? box.top - panel.offsetHeight - GAP_PX : box.bottom + GAP_PX),
        left: Math.max(EDGE_PX, Math.min(box.left, window.innerWidth - panel.offsetWidth - EDGE_PX)),
      });
    };
    place();
    window.addEventListener("resize", place);
    window.addEventListener("scroll", place, true);
    return () => {
      window.removeEventListener("resize", place);
      window.removeEventListener("scroll", place, true);
    };
  }, [anchorRef]);

  return createPortal(
    <div
      ref={panelRef}
      onMouseDown={(event) => event.preventDefault()}
      style={{ top: position?.top ?? 0, left: position?.left ?? 0, visibility: position ? "visible" : "hidden" }}
      className={cn(
        "fixed z-[90] rounded-2xl border border-line bg-surface shadow-xl animate-in fade-in zoom-in-95 duration-150",
        className
      )}
    >
      {children}
    </div>,
    document.body
  );
}

export const FIELD_INPUT_CLASS =
  "h-9 w-full rounded-xl border border-line bg-surface pl-3 pr-8 text-xs font-medium tabular-nums text-ink outline-hidden transition-colors placeholder:font-normal placeholder:text-muted hover:border-line-strong focus:border-action focus:ring-2 focus:ring-action/15 disabled:cursor-not-allowed disabled:opacity-60";
