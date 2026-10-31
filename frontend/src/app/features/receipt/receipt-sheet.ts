import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject, input, signal } from '@angular/core';

import { Api } from '../../core/api/api';
import { ApiError } from '../../core/api/api-error';
import { SessionStore } from '../../core/session/session-store';
import { drawBudgetReceipt, drawExpensesReceipt } from './receipt-drawing';

/**
 * The receipt image (SCREENS.md section 4, "الإيصال"): the paper picture, "مشاركة" where the
 * device can share files (iPhone), and "تنزيل الصورة" everywhere.
 */
@Component({
  selector: 'app-receipt-sheet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (error()) {
      <p class="x-note bad" role="alert" dir="rtl">{{ error() }}</p>
    } @else if (url()) {
      <p class="hint" dir="rtl">اضغط مطولاً على الصورة ثم اختر «حفظ الصورة»</p>
      <img class="receipt-img" [src]="url()" alt="الإيصال" style="width:100%;height:auto;display:block;margin:10px 0">
      <div class="toolbar">
        @if (canShare()) {
          <button class="pill primary" type="button" (click)="share()">مشاركة</button>
        }
        <a class="pill" [href]="url()" [download]="fileName()">تنزيل الصورة</a>
      </div>
    } @else {
      <p class="hint" dir="rtl">جارٍ تجهيز الإيصال…</p>
    }
  `,
})
export class ReceiptSheet implements OnInit, OnDestroy {
  private readonly api = inject(Api);
  private readonly session = inject(SessionStore);

  readonly kind = input<'expenses' | 'budget'>('expenses');
  readonly accountId = input('');
  readonly cycle = input('latest');
  readonly budgetId = input('');

  protected readonly url = signal('');
  protected readonly fileName = signal('receipt.png');
  protected readonly error = signal('');
  protected readonly canShare = signal(false);
  private file: File | null = null;
  private title = '';

  async ngOnInit(): Promise<void> {
    try {
      await document.fonts?.ready;
      const today = this.session.today() ?? '';
      const printedOn = new Date().toLocaleDateString('en-GB');
      let canvas: HTMLCanvasElement;
      if (this.kind() === 'budget') {
        const budget = await this.api.budget(this.budgetId());
        canvas = drawBudgetReceipt(budget, printedOn);
        this.fileName.set(`receipt-${today}.png`);
        this.title = `${budget.name} — ${budget.monthLabel || 'ميزانية'}`;
      } else {
        const m = await this.api.receipt(this.accountId(), this.cycle());
        canvas = drawExpensesReceipt(m, today, printedOn);
        this.fileName.set(`expenses-${m.start ?? 'none'}.png`);
        this.title = `${m.accountName} — إيصال المصاريف`;
      }
      const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/png'));
      if (!blob) {
        this.error.set('تعذّر إنشاء صورة الإيصال (الصورة كبيرة جدًا).');
        return;
      }
      this.file = new File([blob], this.fileName(), { type: 'image/png' });
      this.canShare.set(!!navigator.canShare?.({ files: [this.file] }));
      this.url.set(URL.createObjectURL(blob));
    } catch (e) {
      this.error.set((e as ApiError).message ?? 'تعذّر إنشاء الإيصال.');
    }
  }

  protected async share(): Promise<void> {
    if (!this.file) return;
    try {
      await navigator.share({ files: [this.file], title: this.title });
    } catch {
      // Cancelled by the user: nothing to do
    }
  }

  ngOnDestroy(): void {
    if (this.url()) URL.revokeObjectURL(this.url());
  }
}
