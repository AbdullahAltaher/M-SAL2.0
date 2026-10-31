import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';

import { Api } from '../../core/api/api';
import { ApiError } from '../../core/api/api-error';
import { Entry, EntryType } from '../../core/api/models';
import { Dialogs } from '../../shared/sheet/dialogs';
import { Sheets } from '../../shared/sheet/sheets';
import { Toasts } from '../../shared/toast/toast';
import { ExpensesStore } from './expenses-store';

/**
 * Opens the Expenses sheets (SCREENS.md section 4). The sheet components are loaded on demand,
 * which also keeps the imports one-way (sheets can open other sheets through this service).
 */
@Injectable({ providedIn: 'root' })
export class EntrySheets {
  private readonly sheets = inject(Sheets);
  private readonly store = inject(ExpensesStore);
  private readonly api = inject(Api);
  private readonly dialogs = inject(Dialogs);
  private readonly toasts = inject(Toasts);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);

  /**
   * SH-01. iOS opens the keyboard only for a focus() inside the tap, so a hidden input takes
   * the focus now and hands it to the amount when the slide ends (SCREENS.md 4.1 point 1).
   */
  async add(type: EntryType = 'EXPENSE'): Promise<void> {
    this.primeKeyboard();
    const { EntrySheet } = await import('./entry-sheet');
    await this.sheets.open(EntrySheet, type === 'INCOME' ? 'دخل جديد' : 'مصروف جديد', { presetType: type }, { keepFocus: true });
  }

  async edit(entry: Entry): Promise<void> {
    this.primeKeyboard();
    const { EntrySheet } = await import('./entry-sheet');
    await this.sheets.open(EntrySheet, 'تعديل', { editing: entry }, { keepFocus: true });
  }

  /** SH-02 for "salary arrived", with today and the latest allowance (R-CYC-02). */
  async newCycle(date?: string, allowance?: string): Promise<void> {
    const accountId = this.store.accountId();
    if (!accountId) return;
    const prefill = date ? { startDate: date, allowance: allowance ?? '0' } : await this.api.prefill(accountId);
    const { CycleSheet } = await import('./cycle-sheet');
    await this.sheets.open(CycleSheet, 'وصل الراتب', { date: prefill.startDate, allowance: prefill.allowance });
  }

  async editCycle(cycleId: string): Promise<void> {
    const accountId = this.store.accountId();
    if (!accountId) return;
    const cycle = (await this.api.cycles(accountId)).cycles.find((c) => c.id === cycleId);
    if (!cycle) return;
    const { CycleSheet } = await import('./cycle-sheet');
    await this.sheets.open(CycleSheet, 'تعديل الدورة', { cycle });
  }

  cycleOrNew(): Promise<void> {
    const cycle = this.store.home()?.selected.cycle;
    return cycle ? this.editCycle(cycle.id) : this.newCycle();
  }

  async more(): Promise<void> {
    const { MoreSheet } = await import('./menu-sheets');
    await this.sheets.open(MoreSheet, 'المزيد');
  }

  async accounts(): Promise<void> {
    const { AccountsSheet } = await import('./menu-sheets');
    await this.sheets.open(AccountsSheet, 'الحسابات');
  }

  /** M-SAL's addAccount: a name, then the new account becomes the current one (R-ACC). */
  async addAccount(): Promise<void> {
    const name = await this.dialogs.prompt('اسم الحساب الجديد:', '', 'حساب جديد');
    if (!name) return;
    try {
      const account = await this.api.createAccount(this.store.budgetId() ?? '', name);
      await this.store.reloadLists();
      await this.store.switchAccount(account.id);
    } catch (e) {
      this.toasts.show((e as ApiError).message);
    }
  }

  async categories(): Promise<void> {
    const { CategoriesSheet } = await import('./menu-sheets');
    await this.sheets.open(CategoriesSheet, 'الفئات');
  }

  async newCategory(): Promise<void> {
    const { NewCategorySheet } = await import('./menu-sheets');
    await this.sheets.open(NewCategorySheet, 'فئة جديدة');
  }

  async deleted(): Promise<void> {
    const { DeletedSheet } = await import('./menu-sheets');
    await this.sheets.open(DeletedSheet, 'المحذوفات');
  }

  async receipt(): Promise<void> {
    const accountId = this.store.accountId();
    if (!accountId) return;
    const { ReceiptSheet } = await import('../receipt/receipt-sheet');
    await this.sheets.open(ReceiptSheet, 'الإيصال', { kind: 'expenses', accountId, cycle: this.store.selectedKey() ?? 'latest' });
  }

  async settings(): Promise<void> {
    const { SettingsSheet } = await import('../settings/settings-sheet');
    await this.sheets.open(SettingsSheet, 'البيانات والإعدادات');
  }

  admin(): void {
    this.sheets.close();
    void this.router.navigateByUrl('/admin');
  }

  private primeKeyboard(): void {
    const proxy = this.document.getElementById('kbProxy') as HTMLInputElement | null;
    if (!proxy) return;
    proxy.value = '';
    try {
      proxy.focus({ preventScroll: true });
    } catch {
      // No keyboard to prime
    }
  }
}
