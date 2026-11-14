import { Injectable, computed, inject, signal } from '@angular/core';

import { Api } from '../../core/api/api';
import { Account, Category, Entry, Home } from '../../core/api/models';
import { BudgetsStore } from '../../core/data/budgets-store';
import { showBadge } from '../../core/pwa/app-badge';
import { Toasts } from '../../shared/toast/toast';

/** What a missing category shows as (R-CAT). */
export const OTHER: Pick<Category, 'name' | 'emoji'> = { name: 'أخرى', emoji: '📦' };

/**
 * The Expenses section's state (M-SAL's X and viewModel): the open budget's accounts and
 * categories, the current account, the selected cycle and the home data. All figures come from
 * the server (SCREENS.md section 6).
 */
@Injectable({ providedIn: 'root' })
export class ExpensesStore {
  private readonly api = inject(Api);
  private readonly budgets = inject(BudgetsStore);
  private readonly toasts = inject(Toasts);

  readonly budgetId = computed(() => this.budgets.currentId());
  readonly accounts = signal<Account[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly accountId = signal<string | null>(null);
  /** "latest", "none" or a cycle id (API.md SRV-001) */
  readonly cycleKey = signal('latest');
  readonly home = signal<Home | null>(null);
  /** Bumped after any change, so open views reload */
  readonly changed = signal(0);

  readonly activeAccounts = computed(() => this.accounts().filter((a) => !a.isArchived));
  readonly account = computed(() => this.accounts().find((a) => a.id === this.accountId()) ?? null);
  /** The selected cycle's key as the server resolved it ("none" or an id) */
  readonly selectedKey = computed(() => this.home()?.selected.key ?? null);

  private loadedFor: string | null = null;
  private budgetVersion = 0;

  /** Loads accounts and categories of the open budget (again when it changed). */
  async ensureLoaded(force = false): Promise<void> {
    const budgetId = this.budgetId();
    if (!budgetId || (!force && this.loadedFor === budgetId)) return;
    const [accounts, categories, budget] = await Promise.all([
      this.api.accounts(budgetId, true),
      this.api.categories(budgetId),
      this.api.budget(budgetId),
    ]);
    this.accounts.set(accounts);
    this.categories.set(categories);
    this.budgetVersion = budget.version;
    if (this.loadedFor !== budgetId) {
      this.cycleKey.set('latest');
      this.day.set(null);
    }
    this.loadedFor = budgetId;
    // M-SAL's curAccount: the last used one if still active, else the first active one
    const active = accounts.filter((a) => !a.isArchived);
    this.accountId.set((active.find((a) => a.id === budget.lastAccountId) ?? active[0] ?? accounts[0])?.id ?? null);
  }

  async loadHome(): Promise<void> {
    const accountId = this.accountId();
    if (!accountId) return;
    const home = await this.api.home(accountId, this.cycleKey());
    this.home.set(home);
    // The messages waiting for review stay on the app icon until reviewed (B-22)
    showBadge(home.pendingTotal ?? 0);
  }

  catOf(id: string | null | undefined): Pick<Category, 'name' | 'emoji'> {
    return this.categories().find((c) => c.id === id) ?? OTHER;
  }

  /** The day chosen in the days strip (R-ENT-16), or null for today */
  readonly day = signal<string | null>(null);

  setCycle(key: string): void {
    this.cycleKey.set(key);
    this.day.set(null);
    this.changed.update((n) => n + 1);
  }

  /** Switches account and remembers it on the budget (M-SAL's switchAccount). */
  async switchAccount(id: string): Promise<void> {
    if (id === this.accountId()) return;
    this.accountId.set(id);
    this.cycleKey.set('latest');
    this.day.set(null);
    this.changed.update((n) => n + 1);
    const budgetId = this.budgetId();
    if (!budgetId) return;
    const saved = await this.api.patchBudget(budgetId, { lastAccountId: id, version: this.budgetVersion });
    this.budgetVersion = saved.version;
  }

  /** After a save: show the entry's account and cycle (M-SAL's jumpTo). */
  jumpTo(entry: Entry): void {
    const account = this.accounts().find((a) => a.id === entry.accountId);
    if (account && !account.isArchived && account.id !== this.accountId()) void this.switchAccount(account.id);
    if (entry.cycleKey) this.cycleKey.set(entry.cycleKey);
    this.changed.update((n) => n + 1);
  }

  /** The entry just added, whose row slides in at the top of the list (DESIGN.md D-10) */
  readonly fresh = signal<string | null>(null);
  private freshTimer: ReturnType<typeof setTimeout> | null = null;

  markFresh(entryId: string): void {
    this.fresh.set(entryId);
    if (this.freshTimer) clearTimeout(this.freshTimer);
    this.freshTimer = setTimeout(() => this.fresh.set(null), 1500);
  }

  refresh(): void {
    this.changed.update((n) => n + 1);
  }

  async reloadLists(): Promise<void> {
    await this.ensureLoaded(true);
    this.refresh();
  }

  /** A soft delete with "undo" (R-ENT-09). */
  async remove(entry: Entry): Promise<void> {
    await this.api.deleteEntry(entry.id);
    this.refresh();
    this.toasts.show(`حُذف «${entry.name || this.catOf(entry.categoryId).name}»`, {
      action: { label: 'تراجع', run: () => void this.api.restoreEntry(entry.id).then(() => this.refresh()) },
    });
  }

  /** A copy dated today, with "undo" (R-ENT-07). */
  async duplicate(entry: Entry): Promise<void> {
    const copy = await this.api.duplicateEntry(entry.id);
    this.jumpTo(copy);
    this.toasts.show('نُسخ بتاريخ اليوم: ', {
      money: copy.amount,
      action: { label: 'تراجع', run: () => void this.api.purgeEntry(copy.id).then(() => this.refresh()) },
    });
  }
}
