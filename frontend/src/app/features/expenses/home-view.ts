import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Api } from '../../core/api/api';
import { CycleSummary } from '../../core/api/models';
import { shortArabicDate } from '../../core/util/dates';
import { AED, formatMoney } from '../../core/util/money';
import { Icon } from '../../shared/components/icon';
import { MoneyView } from '../../shared/components/money';
import { EntryRow } from './entry-row';
import { EntrySheets } from './entry-sheets';
import { ExpensesStore } from './expenses-store';

/**
 * S-20: the Expenses home (M-SAL's renderHome): the account card, "can spend today", the
 * spent/allowance pair, "salary arrived" and the last 5 entries (R-CYC-04 to R-CYC-06).
 */
@Component({
  selector: 'app-home-view',
  imports: [Icon, MoneyView, EntryRow, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './home-view.html',
})
export class HomeView {
  protected readonly store = inject(ExpensesStore);
  protected readonly sheets = inject(EntrySheets);
  private readonly api = inject(Api);
  protected readonly unit = AED;

  protected readonly home = this.store.home;
  protected readonly summary = computed(() => this.home()?.selected.summary ?? null);
  protected readonly noCycle = computed(() => this.home()?.selected.key === 'none');
  protected readonly range = computed(() => {
    const s = this.summary();
    return s ? { start: shortArabicDate(s.start, true), end: s.lastDay ? shortArabicDate(s.lastDay, true) : 'الآن' } : null;
  });
  protected readonly daily = computed(() => this.dailyCard(this.summary()));

  private sx: number | null = null;
  private sy = 0;

  protected fmt(v: string | number | null | undefined): string {
    return formatMoney(v);
  }

  protected pct(frac: number): number {
    return Math.max(0, Math.min(100, Math.round(frac * 100)));
  }

  /** "Add an earlier cycle" suggests the oldest no-cycle entry's date and 0 (R-CYC-06). */
  protected async addEarlierCycle(): Promise<void> {
    const accountId = this.store.accountId();
    if (!accountId) return;
    const days = (await this.api.entries(accountId, 'none')).days;
    // Days come newest first, so the last one is the oldest
    void this.sheets.newCycle(days.at(-1)?.date ?? this.home()?.today, '0');
  }

  protected touchStart(e: TouchEvent): void {
    this.sx = e.touches[0].clientX;
    this.sy = e.touches[0].clientY;
  }

  /** Swiping the card changes account (S-20). */
  protected touchEnd(e: TouchEvent): void {
    if (this.sx === null) return;
    const dx = e.changedTouches[0].clientX - this.sx;
    const dy = e.changedTouches[0].clientY - this.sy;
    this.sx = null;
    if (Math.abs(dx) <= 60 || Math.abs(dy) >= 40) return;
    const accounts = this.store.activeAccounts();
    const i = accounts.findIndex((a) => a.id === this.store.accountId());
    if (dx > 0 && i > 0) void this.store.switchAccount(accounts[i - 1].id);
    else if (dx < 0 && i >= 0 && i < accounts.length - 1) void this.store.switchAccount(accounts[i + 1].id);
  }

  /** M-SAL's renderDaily, by cycle state (R-CYC-06). */
  private dailyCard(s: CycleSummary | null) {
    if (!s) return null;
    if (s.open && s.canSpendToday === null) return { kind: 'prompt' as const };
    if (!s.open) return { kind: 'closed' as const };
    const dayBudget = Math.max(0, Number(s.todayBudget));
    const spentToday = Number(s.spentToday);
    const over = Number(s.canSpendToday) < 0;
    const frac = over ? 1 : dayBudget > 0 ? spentToday / dayBudget : spentToday > 0 ? 1 : 0;
    return { kind: 'open' as const, over, frac, dayBudget, value: over ? s.overToday : s.canSpendToday };
  }
}
