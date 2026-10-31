import { ChangeDetectionStrategy, Component, ElementRef, Injectable, afterNextRender, inject, input, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Sheets } from './sheets';

/**
 * SH-11: replaces M-SAL's confirm() with the same text and the same steps (SCREENS.md section 1).
 */
@Component({
  selector: 'app-confirm-sheet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="hint" dir="rtl">{{ message() }}</p>
    <div class="toolbar">
      <button class="pill primary" type="button" (click)="sheets.close(true)">{{ ok() }}</button>
      <button class="pill" type="button" (click)="sheets.close(false)">إلغاء</button>
    </div>
  `,
})
export class ConfirmSheet {
  protected readonly sheets = inject(Sheets);
  readonly message = input('');
  readonly ok = input('تأكيد');
}

/**
 * SH-10: replaces M-SAL's prompt() with the same question, such as "اسم الشخص الجديد:".
 */
@Component({
  selector: 'app-prompt-sheet',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form (ngSubmit)="submit()">
      <label class="flabel" dir="rtl">
        <span>{{ question() }}</span>
        <input #field class="field" name="value" type="text" dir="auto" autocomplete="off"
               [ngModel]="value()" (ngModelChange)="value.set($event)">
      </label>
      <div class="toolbar">
        <button class="pill primary" type="submit">حفظ</button>
      </div>
    </form>
  `,
})
export class PromptSheet {
  protected readonly sheets = inject(Sheets);
  readonly question = input('');
  readonly initial = input('');
  protected readonly value = signal('');
  private readonly field = viewChild<ElementRef<HTMLInputElement>>('field');

  constructor() {
    afterNextRender(() => {
      this.value.set(this.initial());
      setTimeout(() => this.field()?.nativeElement.focus({ preventScroll: true }), 50);
    });
  }

  protected submit(): void {
    const v = this.value().trim();
    this.sheets.close(v || undefined);
  }
}

/** Confirm and prompt as promises, like confirm() and prompt() in M-SAL. */
@Injectable({ providedIn: 'root' })
export class Dialogs {
  private readonly sheets = inject(Sheets);

  async confirm(message: string, title = 'تأكيد', ok = 'تأكيد'): Promise<boolean> {
    return (await this.sheets.open<boolean>(ConfirmSheet, title, { message, ok })) === true;
  }

  /** The trimmed answer, or null when it is empty or cancelled. */
  async prompt(question: string, initial = '', title = ''): Promise<string | null> {
    const answer = await this.sheets.open<string>(PromptSheet, title || question.replace(/:$/, ''), { question, initial });
    return answer ?? null;
  }
}
