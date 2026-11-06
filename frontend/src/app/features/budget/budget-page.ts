import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, inject, signal } from '@angular/core';

import { ApiError } from '../../core/api/api-error';
import { BudgetsStore } from '../../core/data/budgets-store';
import { parseLedgerAmount, toLatinDigits } from '../../core/util/amounts';
import { Icon } from '../../shared/components/icon';
import { Collapse } from '../../shared/directives/collapse';
import { Dialogs } from '../../shared/sheet/dialogs';
import { Sheets } from '../../shared/sheet/sheets';
import { Toasts } from '../../shared/toast/toast';
import { ImportExport } from '../settings/import-export';
import { LedgerGroup, LedgerStore, displayRaw } from './ledger-store';

const NF = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 });
/** Folded groups, per device (a list of group ids), never under a "mizaniya" key (CLAUDE.md rule 15) */
const FOLDED = 'msal2.budget.folded';

type Tool = 'newMonth' | 'receipt' | 'export' | 'import';

/**
 * S-10: the monthly budget (M-SAL's renderAll, renderBlock and renderRow, R-BUD-01 to 06), in the
 * one glass identity (DESIGN.md D-05, D-06, D-14): the remaining card on top with the month,
 * the tools menu and the salary, then the groups as cards that fold. Fields are edited in place
 * and save on their own. Amount fields show what was typed while editing and the formatted
 * value after (R-BUD-03).
 */
@Component({
  selector: 'app-budget-page',
  imports: [Icon, Collapse],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './budget-page.html',
  host: { '(document:pointerdown)': 'outside($event)' },
})
export class BudgetPage {
  protected readonly ledger = inject(LedgerStore);
  protected readonly io = inject(ImportExport);
  private readonly budgets = inject(BudgetsStore);
  private readonly dialogs = inject(Dialogs);
  private readonly sheets = inject(Sheets);
  private readonly toasts = inject(Toasts);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  /** The amount field being edited ("salary" or an item id) shows the raw text */
  protected readonly focused = signal<string | null>(null);
  protected readonly toolsOpen = signal(false);
  private readonly folded = signal<ReadonlySet<string>>(readFolded());

  /** "صُرف X من Y بنود" and the bar under it: the items marked as paid */
  protected readonly progress = computed(() => {
    const items = this.ledger.state()?.groups.flatMap((g) => g.items) ?? [];
    return { done: items.filter((i) => i.isDone).length, total: items.length };
  });

  constructor() {
    effect(() => {
      this.budgets.currentId();
      void this.ledger.load().catch((e: ApiError) => this.toasts.show(e.message));
    });
  }

  protected shown(id: string, raw: string): string {
    return this.focused() === id ? raw : displayRaw(raw);
  }

  /** M-SAL's bindAmount: on focus the canonical text, selected. */
  protected focusAmount(id: string, raw: string, field: HTMLInputElement): void {
    this.focused.set(id);
    const p = parseLedgerAmount(raw);
    field.value = p.amount === null ? toLatinDigits(raw).trim() : (p.isApprox ? '~' : '') + p.amount;
    requestAnimationFrame(() => field.select());
  }

  /** Arabic digits become Latin while typing (R-GEN-04). */
  protected typed(field: HTMLInputElement): string {
    const v = toLatinDigits(field.value).replace(/٫/g, '.').replace(/٬/g, '');
    if (v !== field.value) field.value = v;
    return v;
  }

  protected blurAmount(id: string, field: HTMLInputElement): void {
    const p = parseLedgerAmount(field.value);
    const canonical = p.amount === null ? '' : (p.isApprox ? '~' : '') + p.amount;
    this.focused.set(null);
    if (id === 'salary') this.ledger.editSalary(canonical, true);
    else this.ledger.editAmount(id, canonical, true);
    field.value = displayRaw(canonical);
  }

  protected value(n: number | undefined): string {
    return NF.format(n ?? 0);
  }

  /** The fields inside the card are as wide as their text, so they read as text, not boxes. */
  protected fieldWidth(text: string, min: number): number {
    return Math.max(min, [...text].length + 1);
  }

  protected isOpen(groupId: string): boolean {
    return !this.folded().has(groupId);
  }

  protected toggle(groupId: string): void {
    const next = new Set(this.folded());
    if (!next.delete(groupId)) next.add(groupId);
    this.folded.set(next);
    try {
      localStorage.setItem(FOLDED, JSON.stringify([...next]));
    } catch {
      // private browsing: folded for this visit only
    }
  }

  /** A tap anywhere outside the tools button and its menu closes the menu. */
  protected outside(event: Event): void {
    if (this.toolsOpen() && !(event.target as Element | null)?.closest?.('.b-tools, .b-tools-menu')) this.toolsOpen.set(false);
  }

  protected async tool(name: Tool): Promise<void> {
    this.toolsOpen.set(false);
    switch (name) {
      case 'newMonth': return this.newMonth();
      case 'receipt': return this.receipt();
      case 'export': return this.io.export();
      case 'import': return this.io.pick();
    }
  }

  protected async addItem(groupId: string): Promise<void> {
    if (!this.isOpen(groupId)) this.toggle(groupId);
    const id = await this.ledger.addItem(groupId);
    this.focusLater(`[data-row="${id}"] .name-input`);
  }

  protected async addGroup(): Promise<void> {
    const id = await this.ledger.addGroup();
    if (id) this.focusLater(`[data-block="${id}"] .title-input`);
  }

  /** A group with content asks first (R-BUD-04). */
  protected async deleteGroup(g: LedgerGroup): Promise<void> {
    const hasContent = g.items.some((i) => i.name || i.raw);
    if (hasContent && !(await this.dialogs.confirm(`حذف المجموعة «${g.title || 'بدون اسم'}» وكل بنودها؟`, 'حذف المجموعة', 'حذف'))) return;
    await this.ledger.deleteGroup(g.id);
  }

  private async newMonth(): Promise<void> {
    const name = this.ledger.state()?.name ?? '';
    const ok = await this.dialogs.confirm(`شهر جديد لـ«${name}»؟ بتنمسح علامات الصرف ويتحدّث اسم الشهر، والبنود تبقى مثل ما هي.`, 'شهر جديد', 'شهر جديد');
    if (ok) await this.ledger.newMonth();
  }

  private async receipt(): Promise<void> {
    await this.ledger.flushAll();
    const { ReceiptSheet } = await import('../receipt/receipt-sheet');
    await this.sheets.open(ReceiptSheet, 'الإيصال', { kind: 'budget', budgetId: this.ledger.state()?.id ?? '' });
  }

  private focusLater(selector: string): void {
    setTimeout(() => this.host.querySelector<HTMLInputElement>(selector)?.focus(), 30);
  }
}

function readFolded(): ReadonlySet<string> {
  try {
    const ids = JSON.parse(localStorage.getItem(FOLDED) ?? '[]') as unknown;
    return new Set(Array.isArray(ids) ? ids.filter((x): x is string => typeof x === 'string') : []);
  } catch {
    return new Set();
  }
}
