import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Api } from '../../core/api/api';
import { CycleSummary, Day } from '../../core/api/models';
import { CardPhoto, CardPhotos, accountPhotoKey } from '../../core/photos/card-photos';
import { shortArabicDate, weekdayDate } from '../../core/util/dates';
import { AED, formatMoney } from '../../core/util/money';
import { Icon } from '../../shared/components/icon';
import { CardPhotoSheet } from '../../shared/components/card-photo-sheet';
import { CountTo } from '../../shared/components/count-to';
import { MoneyView } from '../../shared/components/money';
import { Sheets } from '../../shared/sheet/sheets';
import { DayStrip } from './day-strip';
import { EntryRow } from './entry-row';
import { EntrySheets } from './entry-sheets';
import { ExpensesStore } from './expenses-store';
import { pendingLabel } from './pending-label';

/**
 * S-20: the Expenses home (M-SAL's renderHome, R-CYC-04 to R-CYC-06), as the owner uses it
 * (DESIGN.md D-18): the account card with what was spent of what is available, "can spend today",
 * the last 5 entries, then "salary arrived". The two big numbers count to their new value.
 * Tapping "اليوم" opens the days strip (R-ENT-16, D-19, D-23): a chosen day shows what was spent
 * that day and its entries, and new entries go to it.
 */
@Component({
  selector: 'app-home-view',
  imports: [Icon, MoneyView, EntryRow, RouterLink, CountTo, DayStrip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './home-view.html',
})
export class HomeView {
  protected readonly store = inject(ExpensesStore);
  protected readonly sheets = inject(EntrySheets);
  private readonly api = inject(Api);
  protected readonly unit = AED;
  private readonly photos = inject(CardPhotos);
  private readonly sheetHost = inject(Sheets);

  protected readonly home = this.store.home;
  protected readonly summary = computed(() => this.home()?.selected.summary ?? null);
  protected readonly noCycle = computed(() => this.home()?.selected.key === 'none');
  protected readonly range = computed(() => {
    const s = this.summary();
    return s ? { start: shortArabicDate(s.start, true), end: s.lastDay ? shortArabicDate(s.lastDay, true) : 'الآن' } : null;
  });
  protected readonly daily = computed(() => this.dailyCard(this.summary()));

  /** The days strip is open (hidden by default, D-19) */
  protected readonly daysOpen = signal(false);
  /** The cycle's days, loaded only when the strip or a chosen day needs them */
  protected readonly dayList = signal<Day[]>([]);
  protected readonly selectedDay = computed(() => this.store.day() ?? this.home()?.today ?? '');
  /** A day other than today: what was spent that day and its entries (R-ENT-16) */
  protected readonly dayView = computed(() => {
    const date = this.store.day();
    const today = this.home()?.today;
    if (!date || date === today) return null;
    const day = this.dayList().find((d) => d.date === date);
    const entries = day?.entries ?? [];
    return {
      date,
      label: weekdayDate(date),
      spent: Number(day?.spent ?? 0),
      extra: Number(day?.extra ?? 0),
      entries,
      count: entries.filter((e) => e.type === 'EXPENSE').length,
    };
  });

  constructor() {
    effect(() => {
      this.store.changed();
      const want = this.daysOpen() || !!this.store.day();
      const accountId = this.store.accountId();
      const key = this.store.selectedKey();
      if (!want || !accountId || !key || key === 'none') return;
      void this.api.entries(accountId, key).then((r) => this.dayList.set(r.days)).catch(() => this.dayList.set([]));
    });
  }

  /** This account's photo behind the card, kept on this device (D-15, D-26) */
  protected readonly photo = computed(() => {
    const id = this.home()?.account.id;
    return id ? this.photos.get(accountPhotoKey(id)) : null;
  });

  protected photoClass(p: CardPhoto | null): string {
    return p ? 'g-hero hero-photo ph-' + p.style : 'g-hero';
  }

  protected openPhoto(): void {
    const id = this.home()?.account.id;
    if (id) void this.sheetHost.open(CardPhotoSheet, 'صورة البطاقة', { photoKey: accountPhotoKey(id) });
  }

  protected toggleDays(): void {
    this.daysOpen.update((open) => !open);
  }

  /** Today is the normal view; another day is shown on its own (R-ENT-16). */
  protected pickDay(date: string): void {
    this.store.day.set(date === this.home()?.today ? null : date);
  }

  private sx: number | null = null;
  private sy = 0;

  /** "طلب واحد بانتظار المراجعة", "طلبان…", "3 طلبات…", "11 طلبًا…" */
  protected pendingText(n: number): string {
    return pendingLabel(n) + ' بانتظار المراجعة';
  }

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
