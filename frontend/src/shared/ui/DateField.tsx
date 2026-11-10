"use client";

import { useId, useRef, useState, type KeyboardEvent } from "react";
import { CalendarDays, ChevronLeft, ChevronRight } from "lucide-react";
import { cn } from "@/lib/utils";
import {
  addDays,
  buildMonthGrid,
  formatDateDisplay,
  isCompleteDateInput,
  parseDateInput,
  todayIso,
} from "@/shared/lib/fieldParsers";
import { FIELD_INPUT_CLASS, FieldPopover } from "./FieldPopover";

const WEEKDAYS = ["T2", "T3", "T4", "T5", "T6", "T7", "CN"];
const ARROW_DAYS: Record<string, number> = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 };

interface DateFieldProps {
  id?: string;
  /** "YYYY-MM-DD" hoặc rỗng. */
  value: string;
  onChange: (value: string) => void;
  min?: string;
  max?: string;
  required?: boolean;
  disabled?: boolean;
  className?: string;
  "aria-label"?: string;
}

const monthOf = (iso: string) => ({ year: Number(iso.slice(0, 4)), month: Number(iso.slice(5, 7)) });

/** Ô ngày: gõ nhanh (15/11, 15112026) hoặc chọn trên lịch tháng. */
export function DateField({
  id,
  value,
  onChange,
  min,
  max,
  required,
  disabled,
  className,
  "aria-label": ariaLabel,
}: DateFieldProps) {
  const generatedId = useId();
  const fieldId = id ?? generatedId;
  const panelId = `${fieldId}-calendar`;
  const anchorRef = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<string | null>(null);
  const [view, setView] = useState(() => monthOf(value || todayIso()));
  const [cursor, setCursor] = useState(value || todayIso());

  const today = todayIso();
  const inRange = (iso: string) => (!min || iso >= min) && (!max || iso <= max);
  const typedDate = draft ? parseDateInput(draft) : null;
  const shown = typedDate ? monthOf(typedDate) : view;
  const days = buildMonthGrid(shown.year, shown.month);
  const activeDay = typedDate ?? cursor;

  const openCalendar = () => {
    const base = value || today;
    setView(monthOf(base));
    setCursor(base);
    setOpen(true);
  };

  const pick = (iso: string) => {
    if (!inRange(iso)) return;
    onChange(iso);
    setDraft(null);
    setOpen(false);
  };

  const commitDraft = () => {
    if (typedDate && inRange(typedDate)) onChange(typedDate);
    setDraft(null);
    setOpen(false);
  };

  const handleChange = (text: string) => {
    setOpen(true);
    if (text === "") {
      onChange("");
      setDraft(null);
      return;
    }
    const parsed = isCompleteDateInput(text) ? parseDateInput(text) : null;
    if (parsed && inRange(parsed)) {
      onChange(parsed);
      setView(monthOf(parsed));
      setCursor(parsed);
      setDraft(null);
      return;
    }
    setDraft(text);
  };

  const shiftMonth = (delta: number) => {
    setDraft(null);
    const next = new Date(shown.year, shown.month - 1 + delta, 1);
    setView({ year: next.getFullYear(), month: next.getMonth() + 1 });
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    const step = ARROW_DAYS[event.key];
    if (step !== undefined && (open || event.key === "ArrowDown") && !draft) {
      event.preventDefault();
      if (!open) return openCalendar();
      const next = addDays(cursor, step);
      setCursor(next);
      setView(monthOf(next));
    } else if (event.key === "Enter" && open) {
      event.preventDefault();
      if (draft !== null) commitDraft();
      else pick(cursor);
    } else if (event.key === "Escape" && open) {
      event.stopPropagation();
      event.nativeEvent.stopImmediatePropagation();
      setDraft(null);
      setOpen(false);
    }
  };

  const shortcuts = [
    { label: "Hôm nay", iso: today },
    { label: "Ngày mai", iso: addDays(today, 1) },
    { label: "Tuần sau", iso: addDays(today, 7) },
  ];

  return (
    <div ref={anchorRef} className={cn("relative min-w-0", className)}>
      <input
        id={fieldId}
        type="text"
        role="combobox"
        inputMode="numeric"
        autoComplete="off"
        aria-label={ariaLabel}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls={panelId}
        placeholder="dd/mm/yyyy"
        required={required}
        disabled={disabled}
        value={draft ?? formatDateDisplay(value)}
        onChange={(event) => handleChange(event.target.value)}
        onFocus={(event) => event.target.select()}
        onClick={() => {
          if (!open) openCalendar();
        }}
        onBlur={commitDraft}
        onKeyDown={handleKeyDown}
        className={FIELD_INPUT_CLASS}
      />
      <CalendarDays className="pointer-events-none absolute top-1/2 right-2.5 size-3.5 -translate-y-1/2 text-muted" aria-hidden="true" />

      {open && (
        <FieldPopover anchorRef={anchorRef} className="w-72 p-3">
          <div id={panelId} role="dialog" aria-label="Chọn ngày">
            <div className="flex items-center justify-between">
              <button type="button" data-compact tabIndex={-1} aria-label="Tháng trước" onClick={() => shiftMonth(-1)} className="grid size-8 place-items-center rounded-lg text-ink-2 hover:bg-action-soft hover:text-action-strong">
                <ChevronLeft className="size-4" aria-hidden="true" />
              </button>
              <span className="text-xs font-medium text-ink" aria-live="polite">
                Tháng {shown.month}, {shown.year}
              </span>
              <button type="button" data-compact tabIndex={-1} aria-label="Tháng sau" onClick={() => shiftMonth(1)} className="grid size-8 place-items-center rounded-lg text-ink-2 hover:bg-action-soft hover:text-action-strong">
                <ChevronRight className="size-4" aria-hidden="true" />
              </button>
            </div>

            <div className="mt-2 grid grid-cols-7 text-center text-xs text-muted" aria-hidden="true">
              {WEEKDAYS.map((weekday) => (
                <span key={weekday} className="py-1">{weekday}</span>
              ))}
            </div>

            <div className="grid grid-cols-7 gap-y-0.5">
              {days.map((iso) => {
                const selected = iso === value;
                const outside = monthOf(iso).month !== shown.month;
                return (
                  <button
                    key={iso}
                    type="button"
                    data-compact
                    data-still
                    tabIndex={-1}
                    disabled={!inRange(iso)}
                    aria-label={formatDateDisplay(iso)}
                    aria-pressed={selected}
                    onClick={() => pick(iso)}
                    className={cn(
                      "mx-auto grid size-9 place-items-center rounded-lg text-xs tabular-nums transition-colors disabled:cursor-not-allowed disabled:opacity-35",
                      selected
                        ? "bg-action font-medium text-on-action"
                        : iso === activeDay
                          ? "bg-action-soft text-action-strong"
                          : outside
                            ? "text-muted hover:bg-sunken"
                            : "text-ink hover:bg-action-soft hover:text-action-strong",
                      iso === today && !selected && "ring-1 ring-action-line"
                    )}
                  >
                    {Number(iso.slice(8))}
                  </button>
                );
              })}
            </div>

            <div className="mt-2 flex gap-1.5 border-t border-line pt-2">
              {shortcuts.map((shortcut) => (
                <button
                  key={shortcut.label}
                  type="button"
                  data-compact
                  tabIndex={-1}
                  disabled={!inRange(shortcut.iso)}
                  onClick={() => pick(shortcut.iso)}
                  className="flex-1 rounded-lg border border-line px-1.5 py-1 text-xs text-ink-2 hover:border-action-line hover:bg-action-soft hover:text-action-strong disabled:cursor-not-allowed disabled:opacity-40"
                >
                  {shortcut.label}
                </button>
              ))}
            </div>
          </div>
        </FieldPopover>
      )}
    </div>
  );
}
