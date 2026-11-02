"use client";
import { useEffect, useId, useRef, type ReactNode } from "react";
import { X } from "lucide-react";

export function PaymentDialog({
  title,
  children,
  onClose,
  busy = false,
  wide = false,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
  busy?: boolean;
  wide?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => {
    const dialog = ref.current;
    const previous =
      document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    dialog?.showModal();
    return () => {
      dialog?.close();
      document.body.style.overflow = overflow;
      previous?.focus();
    };
  }, []);
  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      aria-modal="true"
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onClose();
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget && !busy) {
          const box = event.currentTarget.getBoundingClientRect();
          if (
            event.clientX < box.left ||
            event.clientX > box.right ||
            event.clientY < box.top ||
            event.clientY > box.bottom
          )
            onClose();
        }
      }}
      className={`m-auto max-h-[90dvh] w-[calc(100%-2rem)] overflow-y-auto rounded-2xl border border-stone-200 bg-white p-0 text-neutral-900 shadow-xl backdrop:bg-stone-950/50 ${
        wide ? "max-w-4xl" : "max-w-xl"
      }`}
    >
      <header className="sticky top-0 z-10 flex items-center justify-between gap-4 border-b border-stone-100 bg-white px-5 py-4">
        <h2 id={titleId} className="text-lg font-bold">
          {title}
        </h2>
        <button
          type="button"
          aria-label="Đóng popup"
          disabled={busy}
          onClick={onClose}
          className="rounded-full p-2 text-neutral-500 hover:bg-stone-100 focus-visible:outline-2 focus-visible:outline-amber-600 disabled:opacity-40"
        >
          <X className="size-5" />
        </button>
      </header>
      <div className="p-5 sm:p-6">{children}</div>
    </dialog>
  );
}
