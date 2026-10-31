import { ChangeDetectionStrategy, Component, ElementRef, effect, inject, signal } from '@angular/core';

import { ApiError } from '../../core/api/api-error';
import { BudgetsStore } from '../../core/data/budgets-store';
import { parseLedgerAmount, toLatinDigits } from '../../core/util/amounts';
import { Dialogs } from '../../shared/sheet/dialogs';
import { Sheets } from '../../shared/sheet/sheets';
import { Toasts } from '../../shared/toast/toast';
import { ImportExport } from '../settings/import-export';
import { LedgerGroup, LedgerStore, displayRaw } from './ledger-store';

const NF = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 });

/**
 * S-10: the monthly budget (M-SAL's renderAll, renderBlock and renderRow, R-BUD-01 to 06).
 * Amount fields show what was typed while editing and the formatted value after (R-BUD-03).
 */
@Component({
  selector: 'app-budget-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './budget-page.html',
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

  protected plate(i: number): string {
    return String(i + 1).padStart(2, '0');
  }

  protected async addItem(groupId: string): Promise<void> {
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

  protected async newMonth(): Promise<void> {
    const name = this.ledger.state()?.name ?? '';
    const ok = await this.dialogs.confirm(`شهر جديد لـ«${name}»؟ بتنمسح علامات الصرف ويتحدّث اسم الشهر، والبنود تبقى مثل ما هي.`, 'شهر جديد', 'شهر جديد');
    if (ok) await this.ledger.newMonth();
  }

  protected async addBudget(): Promise<void> {
    const name = await this.dialogs.prompt('اسم الشخص الجديد:', '', 'شخص جديد');
    if (!name) return;
    await this.ledger.flushAll();
    try {
      await this.budgets.create(name);
    } catch (e) {
      this.toasts.show((e as ApiError).message);
    }
  }

  protected async receipt(): Promise<void> {
    await this.ledger.flushAll();
    const { ReceiptSheet } = await import('../receipt/receipt-sheet');
    await this.sheets.open(ReceiptSheet, 'الإيصال', { kind: 'budget', budgetId: this.ledger.state()?.id ?? '' });
  }

  private focusLater(selector: string): void {
    setTimeout(() => this.host.querySelector<HTMLInputElement>(selector)?.focus(), 30);
  }
}
