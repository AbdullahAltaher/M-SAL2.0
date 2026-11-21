import { ChangeDetectionStrategy, Component, inject, input, linkedSignal, signal } from '@angular/core';
import { Router } from '@angular/router';

import { Api } from '../../core/api/api';
import { ApiError } from '../../core/api/api-error';
import { Sheets } from '../../shared/sheet/sheets';
import { Toasts } from '../../shared/toast/toast';
import { ExpensesStore } from './expenses-store';
import { readBankMessage } from './paste-sms';

/**
 * A bank message the shortcut missed (BANK-SMS.md B-23), pasted here: the server reads it as
 * it reads the shortcut's (a debit, a linked account, not seen before, the category from the
 * user's words), and it waits for review like the others. Then the review page opens.
 */
@Component({
  selector: 'app-paste-sms-sheet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="ps-note" dir="rtl">رسالة فاتت الاختصار (مثلاً بدون إنترنت)؟ انسخها من تطبيق الرسائل والصقها هنا، فتصير طلباً بانتظار المراجعة بفئتها من كلماتك.</p>
    <textarea #box class="ps-text" dir="ltr" rows="5" autocomplete="off" autocapitalize="off" spellcheck="false"
              aria-label="نص رسالة البنك" placeholder="Transaction of AED … debited from your a/c *…"
              [value]="text()" (input)="text.set(box.value); hint.set('')"></textarea>
    <div class="ps-row">
      <button class="g-link" type="button" (click)="paste()">لصق</button>
      @if (text()) {
        <button class="g-link" type="button" (click)="text.set(''); hint.set('')">مسح</button>
      }
    </div>
    @if (hint()) {
      <p class="x-note bad" dir="rtl" role="alert">{{ hint() }}</p>
    }
    <button class="g-btn primary ps-go" type="button" [disabled]="busy()" (click)="send()">أضفها للمراجعة</button>
  `,
})
export class PasteSmsSheet {
  /** What was on the clipboard when the sheet was opened, if it looked like a bank message */
  readonly initial = input('', { alias: 'text' });

  private readonly api = inject(Api);
  private readonly sheets = inject(Sheets);
  private readonly store = inject(ExpensesStore);
  private readonly toasts = inject(Toasts);
  private readonly router = inject(Router);

  protected readonly text = linkedSignal(() => this.initial());
  protected readonly hint = signal('');
  protected readonly busy = signal(false);

  /** In the tap, so the iPhone shows its "لصق" button */
  protected async paste(): Promise<void> {
    const text = await readBankMessage(true);
    if (text) {
      this.text.set(text);
      this.hint.set('');
    } else {
      this.hint.set('ما قدرت أقرأ النص. اضغط مطوّلاً في الخانة واختر لصق.');
    }
  }

  protected async send(): Promise<void> {
    const text = this.text().trim();
    if (!text) {
      this.hint.set('الصق نص الرسالة أولاً.');
      return;
    }
    this.busy.set(true);
    try {
      const res = await this.api.pasteSms(text);
      if (res.status !== 'PENDING') {
        this.hint.set(res.message);
        return;
      }
      this.sheets.close();
      this.store.pasted.update((n) => n + 1);
      this.store.refresh();
      this.toasts.show(res.message);
      void this.router.navigateByUrl('/expenses/pending');
    } catch (e) {
      this.hint.set((e as ApiError).message ?? 'لم تُرسل الرسالة.');
    } finally {
      this.busy.set(false);
    }
  }
}
