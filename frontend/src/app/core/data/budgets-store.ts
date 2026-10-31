import { Injectable, computed, inject, signal } from '@angular/core';

import { Api } from '../api/api';
import { BudgetListItem } from '../api/models';
import { SessionStore } from '../session/session-store';

/**
 * The user's budgets ("people" in M-SAL) and the open one (SCREENS.md "الرأس المشترك").
 * The open budget is remembered on the server ({@code lastBudgetId}), as M-SAL's currentId.
 */
@Injectable({ providedIn: 'root' })
export class BudgetsStore {
  private readonly api = inject(Api);
  private readonly session = inject(SessionStore);

  readonly list = signal<BudgetListItem[]>([]);
  readonly currentId = signal<string | null>(null);
  readonly current = computed(() => this.list().find((b) => b.id === this.currentId()) ?? null);
  /** Bumped after every successful save, for the "محفوظ ✓" mark */
  readonly savedAt = signal(0);

  async load(): Promise<void> {
    const [me, list] = await Promise.all([this.session.load(), this.api.budgets()]);
    this.list.set(list);
    const open = list.find((b) => b.id === me.lastBudgetId) ?? list[0] ?? null;
    this.currentId.set(open?.id ?? null);
  }

  /** Opens another budget and remembers it. */
  async select(id: string): Promise<void> {
    this.currentId.set(id);
    const me = this.session.user();
    if (!me) return;
    const saved = await this.api.patchMe({ lastBudgetId: id, version: me.version });
    this.session.update({ lastBudgetId: saved.lastBudgetId, version: saved.version });
    this.markSaved();
  }

  async create(name: string): Promise<void> {
    const budget = await this.api.createBudget(name);
    const me = await this.reloadMe();
    await this.load();
    this.currentId.set(me?.lastBudgetId ?? budget.id);
    this.markSaved();
  }

  async rename(id: string, name: string): Promise<void> {
    const b = this.list().find((x) => x.id === id);
    if (!b) return;
    const saved = await this.api.patchBudget(id, { name, version: b.version });
    this.list.update((all) => all.map((x) => (x.id === id ? { ...x, name: saved.name, version: saved.version } : x)));
    this.markSaved();
  }

  /** Updates the list entry after the ledger saved the budget (name, month, version). */
  patched(id: string, changes: Partial<BudgetListItem>): void {
    this.list.update((all) => all.map((x) => (x.id === id ? { ...x, ...changes } : x)));
  }

  async remove(id: string): Promise<void> {
    await this.api.deleteBudget(id);
    await this.reloadMe();
    await this.load();
    this.markSaved();
  }

  markSaved(): void {
    this.savedAt.set(Date.now());
  }

  private async reloadMe() {
    this.session.clear();
    return this.session.load();
  }
}
