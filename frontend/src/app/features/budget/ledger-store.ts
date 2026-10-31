import { Injectable, computed, inject, signal } from '@angular/core';

import { Api } from '../../core/api/api';
import { ApiError } from '../../core/api/api-error';
import { Budget, BudgetGroup, BudgetItem } from '../../core/api/models';
import { BudgetsStore } from '../../core/data/budgets-store';
import { parseLedgerAmount } from '../../core/util/amounts';
import { computeTotals } from '../../core/util/budget-totals';

/** An amount field as M-SAL shows it: "~1,234.5", or "" when empty (displayRaw). */
const NF = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 });
export function displayRaw(raw: string): string {
  const p = parseLedgerAmount(raw);
  return p.amount === null ? '' : (p.isApprox ? '~' : '') + NF.format(Number(p.amount));
}
/** The canonical text kept in the state ("~1234.5"); displayRaw formats it for showing. */
function fromServer(amount: string | null, approx: boolean): string {
  return amount === null ? '' : (approx ? '~' : '') + String(Number(amount));
}

export interface LedgerItem {
  id: string;
  name: string;
  /** What the field holds, such as "~400" */
  raw: string;
  isDone: boolean;
}

export interface LedgerGroup {
  id: string;
  title: string;
  items: LedgerItem[];
}

export interface LedgerState {
  id: string;
  name: string;
  monthLabel: string;
  salaryLabel: string;
  salaryRaw: string;
  groups: LedgerGroup[];
}

type Kind = 'budget' | 'group' | 'item';

/**
 * The open budget's ledger (S-10) and its auto-save (API.md section 5): every field saves
 * 600ms after typing stops, or at once on leaving it; one request at a time per record, sent
 * with the latest version, so the user never conflicts with themselves. Totals are computed
 * here while typing (R-BUD-01); the server's are the reference after saving.
 */
@Injectable({ providedIn: 'root' })
export class LedgerStore {
  private readonly api = inject(Api);
  private readonly budgets = inject(BudgetsStore);

  readonly state = signal<LedgerState | null>(null);
  /** Set when a save failed ("لم يُحفظ"), cleared by the next success */
  readonly failed = signal(false);

  readonly totals = computed(() => {
    const s = this.state();
    if (!s) return null;
    return computeTotals(parseLedgerAmount(s.salaryRaw),
      s.groups.map((g) => g.items.map((i) => parseLedgerAmount(i.raw))));
  });

  private readonly versions = new Map<string, number>();
  private readonly pending = new Map<string, { kind: Kind; changes: Record<string, unknown>; timer: ReturnType<typeof setTimeout> | null }>();
  private readonly inflight = new Map<string, Promise<void>>();

  async load(): Promise<void> {
    const id = this.budgets.currentId();
    if (!id) return;
    await this.flushAll();
    this.apply(await this.api.budget(id));
  }

  // ── Editing (local first, then saved) ───────────────────────

  editBudget(field: 'monthLabel' | 'salaryLabel', value: string, now = false): void {
    this.patchState((s) => ({ ...s, [field]: value }));
    this.schedule('budget', this.state()!.id, { [field]: value }, now);
  }

  editSalary(raw: string, now = false): void {
    this.patchState((s) => ({ ...s, salaryRaw: raw }));
    const p = parseLedgerAmount(raw);
    this.schedule('budget', this.state()!.id, { salary: p.amount === null ? null : { amount: p.amount, isApprox: p.isApprox } }, now);
  }

  editTitle(groupId: string, title: string, now = false): void {
    this.patchGroup(groupId, (g) => ({ ...g, title }));
    this.schedule('group', groupId, { title }, now);
  }

  editName(itemId: string, name: string, now = false): void {
    this.patchItem(itemId, (i) => ({ ...i, name }));
    this.schedule('item', itemId, { name }, now);
  }

  editAmount(itemId: string, raw: string, now = false): void {
    this.patchItem(itemId, (i) => ({ ...i, raw }));
    const p = parseLedgerAmount(raw);
    this.schedule('item', itemId, { amount: p.amount, isApprox: p.isApprox }, now);
  }

  /** "تم الصرف" saves at once and never changes a total (R-BUD-01). */
  toggleDone(itemId: string): void {
    let done = false;
    this.patchItem(itemId, (i) => ((done = !i.isDone), { ...i, isDone: done }));
    this.schedule('item', itemId, { isDone: done }, true);
  }

  // ── Structure ───────────────────────────────────────────────

  async addGroup(): Promise<string | null> {
    const s = this.state();
    if (!s) return null;
    const group = await this.api.addGroup(s.id);
    this.versions.set(group.id, group.version);
    group.items.forEach((i) => this.versions.set(i.id, i.version));
    this.patchState((st) => ({ ...st, groups: [...st.groups, this.toGroup(group)] }));
    return group.id;
  }

  async addItem(groupId: string): Promise<string> {
    const item = await this.api.addItem(groupId);
    this.versions.set(item.id, item.version);
    this.patchGroup(groupId, (g) => ({ ...g, items: [...g.items, this.toItem(item)] }));
    return item.id;
  }

  async deleteItem(itemId: string): Promise<void> {
    this.cancel(itemId);
    this.patchState((s) => ({ ...s, groups: s.groups.map((g) => ({ ...g, items: g.items.filter((i) => i.id !== itemId) })) }));
    await this.api.deleteItem(itemId);
    this.budgets.markSaved();
  }

  async deleteGroup(groupId: string): Promise<void> {
    const group = this.state()?.groups.find((g) => g.id === groupId);
    group?.items.forEach((i) => this.cancel(i.id));
    this.cancel(groupId);
    this.patchState((s) => ({ ...s, groups: s.groups.filter((g) => g.id !== groupId) }));
    await this.api.deleteGroup(groupId);
    this.budgets.markSaved();
  }

  /** R-BUD-05: clears "done" and sets this month's name. */
  async newMonth(): Promise<void> {
    const s = this.state();
    if (!s) return;
    await this.flushAll();
    const budget = await this.api.newMonth(s.id);
    this.apply(budget);
    this.budgets.patched(budget.id, { monthLabel: budget.monthLabel, version: budget.version });
    this.budgets.markSaved();
  }

  /** Waits for every pending save (before switching budget or leaving). */
  async flushAll(): Promise<void> {
    for (const id of [...this.pending.keys()]) void this.flush(id);
    await Promise.allSettled([...this.inflight.values()]);
  }

  // ── Saving ──────────────────────────────────────────────────

  private schedule(kind: Kind, id: string, changes: Record<string, unknown>, now: boolean): void {
    const p = this.pending.get(id);
    if (p?.timer) clearTimeout(p.timer);
    const merged = { kind, changes: { ...(p?.changes ?? {}), ...changes }, timer: null as ReturnType<typeof setTimeout> | null };
    this.pending.set(id, merged);
    if (now) void this.flush(id);
    else merged.timer = setTimeout(() => void this.flush(id), 600);
  }

  private async flush(id: string): Promise<void> {
    // One request at a time per record: wait for the one in flight, then send the latest
    const before = this.inflight.get(id);
    if (before) await before;
    const p = this.pending.get(id);
    if (!p) return;
    if (p.timer) clearTimeout(p.timer);
    this.pending.delete(id);
    const run = this.send(p.kind, id, p.changes).finally(() => {
      if (this.inflight.get(id) === run) this.inflight.delete(id);
    });
    this.inflight.set(id, run);
    await run;
  }

  private async send(kind: Kind, id: string, changes: Record<string, unknown>): Promise<void> {
    const body = { ...changes, version: this.versions.get(id) ?? 0 };
    try {
      if (kind === 'budget') {
        const saved = await this.api.patchBudget(id, body);
        this.versions.set(id, saved.version);
        this.budgets.patched(id, { name: saved.name, monthLabel: saved.monthLabel, version: saved.version });
      } else if (kind === 'group') {
        this.versions.set(id, (await this.api.patchGroup(id, body as { title?: string; version: number })).version);
      } else {
        this.versions.set(id, (await this.api.patchItem(id, body)).version);
      }
      this.failed.set(false);
      this.budgets.markSaved();
    } catch (e) {
      this.failed.set(true);
      // Changed on another device: reload so the user sees the latest (SYS-E005)
      if ((e as ApiError).code === 'SYS-E005') await this.load();
    }
  }

  private cancel(id: string): void {
    const p = this.pending.get(id);
    if (p?.timer) clearTimeout(p.timer);
    this.pending.delete(id);
  }

  // ── State helpers ───────────────────────────────────────────

  private apply(budget: Budget): void {
    this.versions.set(budget.id, budget.version);
    budget.groups.forEach((g) => {
      this.versions.set(g.id, g.version);
      g.items.forEach((i) => this.versions.set(i.id, i.version));
    });
    this.state.set({
      id: budget.id, name: budget.name, monthLabel: budget.monthLabel, salaryLabel: budget.salaryLabel,
      salaryRaw: fromServer(budget.salary.amount, budget.salary.isApprox), groups: budget.groups.map((g) => this.toGroup(g)),
    });
  }

  private toGroup(g: BudgetGroup): LedgerGroup {
    return { id: g.id, title: g.title, items: g.items.map((i) => this.toItem(i)) };
  }

  private toItem(i: BudgetItem): LedgerItem {
    return { id: i.id, name: i.name, raw: fromServer(i.amount, i.isApprox), isDone: i.isDone };
  }

  private patchState(fn: (s: LedgerState) => LedgerState): void {
    const s = this.state();
    if (s) this.state.set(fn(s));
  }

  private patchGroup(groupId: string, fn: (g: LedgerGroup) => LedgerGroup): void {
    this.patchState((s) => ({ ...s, groups: s.groups.map((g) => (g.id === groupId ? fn(g) : g)) }));
  }

  private patchItem(itemId: string, fn: (i: LedgerItem) => LedgerItem): void {
    this.patchState((s) => ({ ...s, groups: s.groups.map((g) => ({ ...g, items: g.items.map((i) => (i.id === itemId ? fn(i) : i)) })) }));
  }
}
