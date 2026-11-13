"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { DayNightToggle } from "@/shared/motion/DayNightToggle";
import { useHop } from "@/shared/motion/useHop";
import { Button } from "@/shared/ui/Button";
import { EduFitLogo } from "@/shared/ui/EduFitLogo";
import { cn } from "@/lib/utils";

const LINKS = [
  { id: "cach-hoat-dong", href: "#cach-hoat-dong", label: "Cách hoạt động" },
  { id: "gia-su", href: "/tutors", label: "Danh bạ gia sư" },
  { id: "tien-bo", href: "#tien-bo", label: "Tiến bộ" },
];

const CLOSED_CLIP = "inset(0 0 calc(100% - 44px) calc(100% - 44px) round 22px)";

export function LandingHeader() {
  const [open, setOpen] = useState(false);
  const [hovered, setHovered] = useState<string | null>(null);
  const [section, setSection] = useState<string | null>(null);
  const navRef = useRef<HTMLElement>(null);
  const dotRef = useRef<HTMLSpanElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  useHop(navRef, dotRef, hovered ?? section);

  useEffect(() => {
    if (typeof IntersectionObserver === "undefined") return;
    const observer = new IntersectionObserver(
      (entries) => entries.forEach((entry) => entry.isIntersecting && setSection(entry.target.id)),
      { rootMargin: "-45% 0px -50% 0px" },
    );
    LINKS.forEach((link) => {
      const target = document.getElementById(link.id);
      if (target) observer.observe(target);
    });
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => event.key === "Escape" && setOpen(false);
    const onPointer = (event: PointerEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener("keydown", onKey);
    document.addEventListener("pointerdown", onPointer);
    return () => {
      document.removeEventListener("keydown", onKey);
      document.removeEventListener("pointerdown", onPointer);
    };
  }, [open]);

  const bar = "absolute left-1/2 top-1/2 h-0.5 w-5 -ml-2.5 rounded-full bg-ink transition-transform duration-500 ease-[var(--ease-jelly)]";

  return (
    <header className="sticky top-0 z-50 border-b border-line/60 bg-paper/90 backdrop-blur-md">
      <div className="mx-auto flex h-18 max-w-6xl items-center justify-between gap-3 px-4 sm:px-6">
        <EduFitLogo href="/" size="lg" />

        <nav ref={navRef} className="relative hidden items-center gap-1 md:flex" aria-label="Trang chủ" onPointerLeave={() => setHovered(null)}>
          <span ref={dotRef} aria-hidden="true" className="absolute top-0 left-0 opacity-0">
            <span className="absolute bottom-1 left-1/2 size-1.5 -translate-x-1/2 rounded-full bg-action" />
          </span>
          {LINKS.map((link) => {
            const className = "relative block px-4 py-3 text-sm text-ink-2 transition-colors hover:text-ink";
            const props = { "data-hop": link.id, onPointerEnter: () => setHovered(link.id), className };
            return link.href.startsWith("#") ? (
              <a key={link.id} href={link.href} {...props}>
                {link.label}
              </a>
            ) : (
              <Link key={link.id} href={link.href} {...props}>
                {link.label}
              </Link>
            );
          })}
        </nav>

        <div className="flex items-center gap-2">
          <DayNightToggle className="hidden md:inline-flex" />
          <Button href="/login" variant="secondary" size="sm" className="hidden sm:inline-flex">
            Đăng nhập
          </Button>
          <Button href="/register" size="sm">
            Đăng ký
          </Button>

          <div ref={menuRef} className="relative size-11 md:hidden">
            <div
              id="landing-menu"
              inert={!open}
              style={{ clipPath: open ? "inset(0 round 24px)" : CLOSED_CLIP }}
              className="absolute top-0 right-0 w-[min(19rem,calc(100vw-2rem))] bg-surface shadow-lg shadow-ink/10 transition-[clip-path] duration-500 ease-[var(--ease-out-soft)]"
            >
              <div className="flex flex-col gap-1 p-3 pt-14">
                {LINKS.map((link) => (
                  <a key={link.id} href={link.href} onClick={() => setOpen(false)} className="rounded-xl px-3 py-3 text-base text-ink hover:bg-sunken">
                    {link.label}
                  </a>
                ))}
                <Link href="/login" onClick={() => setOpen(false)} className="rounded-xl px-3 py-3 text-base text-ink hover:bg-sunken">
                  Đăng nhập
                </Link>
                <div className="mt-1 flex items-center justify-between border-t border-line px-3 pt-3">
                  <span className="text-sm text-muted">Giao diện tối</span>
                  <DayNightToggle />
                </div>
              </div>
            </div>
            <button
              type="button"
              aria-expanded={open}
              aria-controls="landing-menu"
              aria-label={open ? "Đóng menu" : "Mở menu"}
              onClick={() => setOpen((value) => !value)}
              className="absolute inset-0 rounded-full"
            >
              <span className={cn(bar, open ? "rotate-45" : "-translate-y-1")} />
              <span className={cn(bar, open ? "-rotate-45" : "translate-y-1")} />
            </button>
          </div>
        </div>
      </div>
    </header>
  );
}
