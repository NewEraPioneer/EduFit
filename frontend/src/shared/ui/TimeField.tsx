"use client";

import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from "react";
import { Clock } from "lucide-react";
import { cn } from "@/lib/utils";
import { buildTimeOptions, formatDuration, matchTimeOptions, parseTimeInput } from "@/shared/lib/fieldParsers";
import { FIELD_INPUT_CLASS, FieldPopover } from "./FieldPopover";

const DEFAULT_SCROLL_TIME = "08:00";

interface TimeFieldProps {
  id?: string;
  /** "HH:mm" hoặc "HH:mm:ss". */
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
  disabled?: boolean;
  stepMinutes?: number;
  /** Giờ bắt đầu: danh sách chỉ hiện giờ sau mốc này, kèm thời lượng. */
  startFrom?: string;
  className?: string;
  "aria-label"?: string;
}

/** Ô giờ: gõ nhanh (8, 830, 8h30) hoặc cuộn danh sách để chọn. */
export function TimeField({
  id,
  value,
  onChange,
  required,
  disabled,
  stepMinutes = 30,
  startFrom,
  className,
  "aria-label": ariaLabel,
}: TimeFieldProps) {
  const generatedId = useId();
  const fieldId = id ?? generatedId;
  const listId = `${fieldId}-list`;
  const anchorRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<string | null>(null);
  const [active, setActive] = useState(0);

  const current = value.slice(0, 5);
  const from = startFrom?.slice(0, 5) || undefined;
  const options = useMemo(() => {
    const all = buildTimeOptions(stepMinutes);
    return from ? all.filter((option) => option > from) : all;
  }, [stepMinutes, from]);
  const visible = draft ? matchTimeOptions(options, draft) : options;
  const activeIndex = Math.min(active, visible.length - 1);
  const typedTime = draft ? parseTimeInput(draft) : null;

  useEffect(() => {
    if (!open || !listRef.current || activeIndex < 0) return;
    const list = listRef.current;
    const item = list.children[activeIndex] as HTMLElement | undefined;
    if (!item) return;
    if (item.offsetTop < list.scrollTop || item.offsetTop + item.offsetHeight > list.scrollTop + list.clientHeight) {
      list.scrollTop = item.offsetTop - (list.clientHeight - item.offsetHeight) / 2;
    }
  }, [open, activeIndex]);

  const openList = () => {
    const target = current || DEFAULT_SCROLL_TIME;
    const index = options.findIndex((option) => option >= target);
    setActive(index === -1 ? 0 : index);
    setOpen(true);
  };

  const pick = (time: string) => {
    onChange(time);
    setDraft(null);
    setOpen(false);
  };

  const commitDraft = () => {
    if (typedTime) onChange(typedTime);
    setDraft(null);
    setOpen(false);
  };

  const handleChange = (text: string) => {
    setOpen(true);
    setActive(0);
    if (/^\d{2}:\d{2}$/.test(text) && parseTimeInput(text)) {
      onChange(text);
      setDraft(null);
      return;
    }
    setDraft(text);
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      if (!open) return openList();
      const delta = event.key === "ArrowDown" ? 1 : -1;
      setActive(Math.max(0, Math.min(visible.length - 1, activeIndex + delta)));
    } else if (event.key === "Enter" && open) {
      event.preventDefault();
      if (activeIndex >= 0) pick(visible[activeIndex]);
      else commitDraft();
    } else if (event.key === "Escape" && open) {
      event.stopPropagation();
      event.nativeEvent.stopImmediatePropagation();
      setDraft(null);
      setOpen(false);
    }
  };

  return (
    <div ref={anchorRef} className={cn("relative min-w-0", className)}>
      <input
        id={fieldId}
        type="text"
        role="combobox"
        inputMode="numeric"
        autoComplete="off"
        aria-label={ariaLabel}
        aria-expanded={open}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={open && activeIndex >= 0 ? `${listId}-${activeIndex}` : undefined}
        placeholder="08:30"
        required={required}
        disabled={disabled}
        value={draft ?? current}
        onChange={(event) => handleChange(event.target.value)}
        onFocus={(event) => event.target.select()}
        onClick={() => {
          if (!open) openList();
        }}
        onBlur={commitDraft}
        onKeyDown={handleKeyDown}
        className={FIELD_INPUT_CLASS}
      />
      <Clock className="pointer-events-none absolute top-1/2 right-2.5 size-3.5 -translate-y-1/2 text-muted" aria-hidden="true" />

      {open && (
        <FieldPopover anchorRef={anchorRef} className={cn("p-1.5", from ? "w-48" : "w-32")}>
          {visible.length === 0 ? (
            <p id={listId} className="px-2 py-1.5 text-xs text-muted">
              {typedTime ? `Nhấn Enter để chọn ${typedTime}` : "Chưa đúng giờ. Ví dụ: 8:30 hoặc 1430"}
            </p>
          ) : (
            <ul ref={listRef} id={listId} role="listbox" aria-label="Chọn giờ" className="relative max-h-56 overflow-y-auto overscroll-contain">
              {visible.map((time, index) => {
                const selected = time === current;
                return (
                  <li
                    key={time}
                    id={`${listId}-${index}`}
                    role="option"
                    aria-selected={selected}
                    onClick={() => pick(time)}
                    onMouseEnter={() => setActive(index)}
                    className={cn(
                      "flex cursor-pointer items-center justify-between gap-2 rounded-lg px-2 py-1.5 text-xs tabular-nums transition-colors",
                      selected ? "bg-action font-medium text-on-action" : index === activeIndex ? "bg-action-soft text-action-strong" : "text-ink-2"
                    )}
                  >
                    <span>{time}</span>
                    {from && <span className={selected ? "text-on-action/80" : "text-muted"}>{formatDuration(from, time)}</span>}
                  </li>
                );
              })}
            </ul>
          )}
        </FieldPopover>
      )}
    </div>
  );
}
