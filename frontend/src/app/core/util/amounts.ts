/**
 * Amount input (BUSINESS_RULES.md R-GEN-04, R-ENT-03, R-BUD-03), ported from M-SAL's
 * toLatinDigits, parseEntryAmount and parseAmount. Angular turns what the user types into the
 * unified format the API accepts ("46.25"); the server only checks it (API.md section 1).
 */

/** Arabic-Indic ٠-٩ and Persian ۰-۹ digits to 0-9 (R-GEN-04). */
export function toLatinDigits(s: string): string {
  return String(s)
    .replace(/[٠-٩]/g, (c) => String(c.charCodeAt(0) - 0x0660))
    .replace(/[۰-۹]/g, (c) => String(c.charCodeAt(0) - 0x06f0));
}

/** M-SAL's roundMoney: 2 decimals, halves up. */
export function roundMoney(n: number): number {
  return Math.round((n + Number.EPSILON) * 100) / 100;
}

/**
 * An expense amount (R-ENT-03) as the API wants it ("46.25"), or null when it is not a
 * positive amount up to 999,999,999.
 * "٤٦٫٢٥", "46,25", "1,234.50", " 12 " are all accepted.
 */
export function parseEntryAmount(raw: unknown): string | null {
  if (raw == null) return null;
  let s = toLatinDigits(String(raw))
    .replace(/٫/g, '.')
    .replace(/٬/g, '')
    .replace(/،/g, ',')
    .replace(/[\s‎‏]/g, '');
  if (s === '') return null;
  if (s.indexOf('.') >= 0) s = s.replace(/,/g, '');
  else {
    const parts = s.split(',');
    s = parts.length === 2 && parts[1].length > 0 && parts[1].length <= 2 ? parts[0] + '.' + parts[1] : parts.join('');
  }
  if (!/^(\d+\.?\d*|\.\d+)$/.test(s)) return null;
  const v = parseFloat(s);
  if (!isFinite(v) || v <= 0 || v > 999999999) return null;
  const r = roundMoney(v);
  return r > 0 ? r.toFixed(2) : null;
}

/** A monthly budget amount (R-BUD-03): {@code amount} is null for an empty field. */
export interface LedgerAmount {
  amount: string | null;
  isApprox: boolean;
}

/**
 * A monthly budget amount (R-BUD-03), from M-SAL's parseAmount: a leading "~" means
 * approximate, separators and spaces are dropped, negatives are allowed, and text that is not
 * a number counts as 0. An empty field stays empty (null).
 */
export function parseLedgerAmount(raw: unknown): LedgerAmount {
  if (raw == null) return { amount: null, isApprox: false };
  let s = toLatinDigits(String(raw)).replace(/٫/g, '.').replace(/٬/g, '').trim();
  if (s === '') return { amount: null, isApprox: false };
  const isApprox = /^[~～∼]/.test(s);
  s = s.replace(/^[~～∼]\s*/, '').replace(/[,\s]/g, '');
  const v = parseFloat(s);
  const value = isFinite(v) ? roundMoney(v) : 0;
  return { amount: String(value === 0 ? 0 : value), isApprox };
}
