import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';

import { Api } from '../../core/api/api';
import { ApiError } from '../../core/api/api-error';
import { ImportMode, ImportPreview } from '../../core/api/models';
import { BudgetsStore } from '../../core/data/budgets-store';
import { SessionStore } from '../../core/session/session-store';
import { Dialogs } from '../../shared/sheet/dialogs';
import { Sheets } from '../../shared/sheet/sheets';
import { Toasts } from '../../shared/toast/toast';
import { LedgerStore } from '../budget/ledger-store';
import { ExpensesStore } from '../expenses/expenses-store';
import { ImportSheet } from './import-sheet';

/**
 * SRV-007 in the app: M-SAL's doExport, shareExport and the import flow (pick a file, preview,
 * then merge or replace). The server checks the file; nothing is saved before the user chooses.
 */
@Injectable({ providedIn: 'root' })
export class ImportExport {
  private readonly api = inject(Api);
  private readonly session = inject(SessionStore);
  private readonly budgets = inject(BudgetsStore);
  private readonly ledger = inject(LedgerStore);
  private readonly expenses = inject(ExpensesStore);
  private readonly sheets = inject(Sheets);
  private readonly dialogs = inject(Dialogs);
  private readonly toasts = inject(Toasts);
  private readonly document = inject(DOCUMENT);

  /** M-SAL's exportFileName: {@code ميزانية-YYYY-MM-DD.json} by the user's "today" (R-IO-01). */
  fileName(): string {
    return `ميزانية-${this.session.today() ?? new Date().toLocaleDateString('en-CA')}.json`;
  }

  async export(): Promise<void> {
    try {
      await this.ledger.flushAll();
      const blob = await this.api.exportFile();
      const a = this.document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = this.fileName();
      this.document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(a.href), 4000);
      this.exported();
    } catch {
      this.toasts.show('تعذّر إنشاء ملف التصدير.');
    }
  }

  /** The share sheet with the file (the iPhone's "Save to Files"), else a download. */
  canShare(): boolean {
    return typeof navigator !== 'undefined' && typeof navigator.canShare === 'function';
  }

  async share(): Promise<void> {
    try {
      await this.ledger.flushAll();
      const file = new File([await this.api.exportFile()], this.fileName(), { type: 'application/json' });
      if (navigator.canShare?.({ files: [file] })) {
        await navigator.share({ files: [file], title: 'ميزانية' });
        this.exported();
        return;
      }
    } catch (e) {
      if ((e as Error)?.name === 'AbortError') return;
    }
    await this.export();
  }

  /** Opens the file picker; must run inside the click, so the browser allows it. */
  pick(): void {
    const input = this.document.createElement('input');
    input.type = 'file';
    input.accept = 'application/json,.json';
    input.addEventListener('change', () => {
      const file = input.files?.[0];
      if (file) void this.open(file);
    });
    input.click();
  }

  /** SH-09: the preview, then the chosen way (R-IO-02, R-IO-03). */
  async open(file: File): Promise<void> {
    let preview: ImportPreview;
    try {
      preview = await this.api.previewImport(file);
    } catch (e) {
      this.toasts.show(`${(e as ApiError).message} لم يتغيّر شيء.`);
      return;
    }
    for (;;) {
      const mode = await this.sheets.open<ImportMode>(ImportSheet, 'استيراد', { preview });
      if (!mode) return;
      if (mode === 'REPLACE' && !(await this.dialogs.confirm(replaceQuestion(preview), 'استبدال', 'استبدال'))) continue;
      await this.apply(file, mode);
      return;
    }
  }

  private async apply(file: File, mode: ImportMode): Promise<void> {
    try {
      await this.ledger.flushAll();
      await this.api.importFile(file, mode);
    } catch (e) {
      this.toasts.show(`${(e as ApiError).message} لم يتغيّر شيء.`);
      return;
    }
    await this.reload();
    this.toasts.show('تم الاستيراد');
  }

  /** Every screen shows the imported data, as M-SAL's renderAll. */
  private async reload(): Promise<void> {
    this.session.clear();
    await this.budgets.load();
    await this.ledger.load();
    await this.expenses.ensureLoaded(true);
    this.expenses.changed.update((n) => n + 1);
    this.budgets.markSaved();
  }

  private exported(): void {
    this.session.update({ exportReminder: { due: false, days: 0, never: false } });
  }
}

function replaceQuestion(preview: ImportPreview): string {
  return preview.kind === 'STATE'
    ? 'استبدال كل البيانات الحالية بمحتوى الملف؟ تُحفظ نسخة منها أولًا.'
    : `استبدال ميزانية «${preview.target?.name ?? ''}» بمحتوى الملف؟ المصاريف لا تتأثر، وتُحفظ نسخة أولًا.`;
}
