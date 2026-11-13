/** Đọc giá trị người dùng gõ nhanh vào ô giờ và ô ngày. Giá trị chuẩn: "HH:mm" và "YYYY-MM-DD". */

const pad = (value: number) => String(value).padStart(2, "0");

/** Nhận "8", "08", "8:30", "8h30", "8h", "830", "14.30". */
export function parseTimeInput(raw: string): string | null {
  const match = raw.trim().toLowerCase().match(/^(\d{1,2})(?:[:h.\s]?(\d{2}))?h?$/);
  if (!match) return null;
  const hour = Number(match[1]);
  const minute = Number(match[2] ?? 0);
  if (hour > 23 || minute > 59) return null;
  return `${pad(hour)}:${pad(minute)}`;
}

export function buildTimeOptions(stepMinutes: number): string[] {
  const options: string[] = [];
  for (let total = 0; total < 24 * 60; total += stepMinutes) {
    options.push(`${pad(Math.floor(total / 60))}:${pad(total % 60)}`);
  }
  return options;
}

/** Lọc theo số đã gõ: "8" khớp 08:00 và 08:30, "14" khớp 14:00 và 14:30. */
export function matchTimeOptions(options: string[], query: string): string[] {
  const digits = query.replace(/\D/g, "");
  if (!digits) return options;
  return options.filter((option) => {
    const compact = option.replace(":", "");
    return compact.startsWith(digits) || compact.replace(/^0/, "").startsWith(digits);
  });
}

export function formatDuration(from: string, to: string): string {
  const toMinutes = (value: string) => Number(value.slice(0, 2)) * 60 + Number(value.slice(3, 5));
  const minutes = toMinutes(to) - toMinutes(from);
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) return `${rest} phút`;
  return rest === 0 ? `${hours} giờ` : `${hours} giờ ${rest} phút`;
}

export function toIsoDate(year: number, month: number, day: number): string | null {
  const date = new Date(year, month - 1, day);
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) return null;
  return `${year}-${pad(month)}-${pad(day)}`;
}

export function todayIso(now: Date = new Date()): string {
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

export function addDays(iso: string, days: number): string {
  const [year, month, day] = iso.split("-").map(Number);
  return todayIso(new Date(year, month - 1, day + days));
}

export function formatDateDisplay(iso: string): string {
  const [year, month, day] = iso.split("-");
  return year && month && day ? `${day}/${month}/${year}` : "";
}

/** Có đủ năm nên có thể lưu ngay khi đang gõ. */
export function isCompleteDateInput(raw: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(raw) || /^\d{1,2}[/.-]\d{1,2}[/.-]\d{4}$/.test(raw);
}

/** Nhận "15/11", "15-11-2026", "15112026", "1511", "2026-11-15". Thiếu năm thì lấy lần gần nhất chưa qua. */
export function parseDateInput(raw: string, now: Date = new Date()): string | null {
  const text = raw.trim();
  const iso = text.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (iso) return toIsoDate(Number(iso[1]), Number(iso[2]), Number(iso[3]));

  let parts = text.match(/^(\d{1,2})[/.\-\s](\d{1,2})(?:[/.\-\s](\d{2}|\d{4}))?$/)?.slice(1);
  if (!parts && /^\d+$/.test(text) && [4, 6, 8].includes(text.length)) {
    parts = [text.slice(0, 2), text.slice(2, 4), text.slice(4) || undefined] as string[];
  }
  if (!parts) return null;

  const day = Number(parts[0]);
  const month = Number(parts[1]);
  if (parts[2]) {
    const year = parts[2].length === 2 ? 2000 + Number(parts[2]) : Number(parts[2]);
    return toIsoDate(year, month, day);
  }
  const thisYear = toIsoDate(now.getFullYear(), month, day);
  if (thisYear && thisYear >= todayIso(now)) return thisYear;
  return toIsoDate(now.getFullYear() + 1, month, day);
}

/** 42 ô của một tháng, tuần bắt đầu từ thứ Hai. */
export function buildMonthGrid(year: number, month: number): string[] {
  const first = new Date(year, month - 1, 1);
  const offset = (first.getDay() + 6) % 7;
  return Array.from({ length: 42 }, (_, index) => todayIso(new Date(year, month - 1, 1 - offset + index)));
}
