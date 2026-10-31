/**
 * The monthly budget totals while the user types (R-BUD-01, R-BUD-02), the same rule as the
 * server's BudgetTotals. The server's values are the reference after saving (API.md SRV-002).
 * Sums are in cents, so 0.1 + 0.2 is exactly 0.30.
 */

export interface Amount {
  /** null for an empty field, which counts as 0 */
  amount: string | null;
  isApprox: boolean;
}

export interface GroupTotal {
  subtotal: number;
  subtotalApprox: boolean;
  balanceAfter: number;
  balanceApprox: boolean;
}

export interface Totals {
  groups: GroupTotal[];
  final: number;
  finalApprox: boolean;
}

const cents = (a: Amount) => Math.round((Number(a.amount) || 0) * 100);

/** Remaining = salary − group 1 − group 2 − …; "~" spreads down the page, never up. */
export function computeTotals(salary: Amount, groups: Amount[][]): Totals {
  let balance = cents(salary);
  let anyApprox = salary.isApprox;
  const result: GroupTotal[] = [];
  for (const items of groups) {
    const sub = items.reduce((n, i) => n + cents(i), 0);
    const approx = items.some((i) => i.isApprox);
    balance -= sub;
    anyApprox = anyApprox || approx;
    result.push({ subtotal: sub / 100, subtotalApprox: approx, balanceAfter: balance / 100, balanceApprox: anyApprox });
  }
  return { groups: result, final: balance / 100, finalApprox: anyApprox };
}
