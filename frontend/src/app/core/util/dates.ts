/**
 * Calendar dates as "YYYY-MM-DD" strings (R-GEN-02, R-GEN-06), ported from M-SAL. All
 * arithmetic is on whole days in UTC, so the device's time zone never shifts a date. "Today"
 * comes from the server, in the user's time zone (API.md section 1).
 */

export const AR_MONTHS = ['يناير', 'فبراير', 'مارس', 'أبريل', 'مايو', 'يونيو', 'يوليو', 'أغسطس', 'سبتمبر', 'أكتوبر', 'نوفمبر', 'ديسمبر'];
export const AR_WEEKDAYS = ['الأحد', 'الاثنين', 'الثلاثاء', 'الأربعاء', 'الخميس', 'الجمعة', 'السبت'];

const DAY_MS = 86_400_000;

interface Ymd {
  y: number;
  m: number;
  d: number;
}

/** A real date, or null ("2026-02-30" is not one). */
export function parseDate(s: unknown): Ymd | null {
  if (typeof s !== 'string') return null;
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s);
  if (!m) return null;
  const y = +m[1], mo = +m[2], d = +m[3];
  const t = new Date(Date.UTC(y, mo - 1, d));
  if (t.getUTCFullYear() !== y || t.getUTCMonth() !== mo - 1 || t.getUTCDate() !== d) return null;
  return { y, m: mo, d };
}

function pad2(n: number): string {
  return (n < 10 ? '0' : '') + n;
}

function dayNumber(s: string): number {
  const p = parseDate(s)!;
  return Math.round(Date.UTC(p.y, p.m - 1, p.d) / DAY_MS);
}

export function addDays(s: string, n: number): string {
  const t = new Date((dayNumber(s) + n) * DAY_MS);
  return t.getUTCFullYear() + '-' + pad2(t.getUTCMonth() + 1) + '-' + pad2(t.getUTCDate());
}

/** 0 = Sunday */
export function weekdayIndex(date: string): number {
  return (((dayNumber(date) + 4) % 7) + 7) % 7;
}

/** "2 سبتمبر", or "2 سبتمبر 2026" with the year (R-GEN-06). */
export function shortArabicDate(date: string, withYear = false): string {
  const p = parseDate(date)!;
  return p.d + ' ' + AR_MONTHS[p.m - 1] + (withYear ? ' ' + p.y : '');
}

/** "الجمعة 2 أكتوبر 2026" (R-GEN-06). */
export function fullArabicDate(date: string): string {
  const p = parseDate(date)!;
  return AR_WEEKDAYS[weekdayIndex(date)] + ' ' + p.d + ' ' + AR_MONTHS[p.m - 1] + ' ' + p.y;
}

/** "اليوم", "أمس", "غدًا", or the full date (R-GEN-06). */
export function dayLabel(date: string, today: string): string {
  if (date === today) return 'اليوم';
  if (date === addDays(today, -1)) return 'أمس';
  if (date === addDays(today, 1)) return 'غدًا';
  return fullArabicDate(date);
}
