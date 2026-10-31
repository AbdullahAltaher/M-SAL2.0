import { ChangeDetectionStrategy, Component, Injectable, inject, signal } from '@angular/core';

import { MoneyView } from '../components/money';

export interface ToastAction {
  label: string;
  run: () => void;
}

interface ToastState {
  text: string;
  /** Shown after the text as money, such as "أُضيف: 46.25 د.إ" */
  money?: string | number;
  /** Shown after the money, such as " · البيت" */
  suffix?: string;
  action?: ToastAction;
}

/** M-SAL's showToast: one message at a time, 3 seconds, or 7 with an "undo" button. */
@Injectable({ providedIn: 'root' })
export class Toasts {
  readonly current = signal<ToastState | null>(null);
  private timer: ReturnType<typeof setTimeout> | null = null;

  show(text: string, options: Omit<ToastState, 'text'> = {}, ms?: number): void {
    this.current.set({ text, ...options });
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => this.current.set(null), ms ?? (options.action ? 7000 : 3000));
  }

  act(): void {
    const action = this.current()?.action;
    if (this.timer) clearTimeout(this.timer);
    this.current.set(null);
    action?.run();
  }
}

@Component({
  selector: 'app-toast-host',
  imports: [MoneyView],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="toast-host" aria-live="polite">
      @if (toasts.current(); as t) {
        <div class="toast">
          <span class="t-text" dir="rtl">{{ t.text }}@if (t.money !== undefined) {<app-money [value]="t.money" />}@if (t.suffix) {<span dir="auto">{{ t.suffix }}</span>}</span>
          @if (t.action) {
            <button class="t-act" type="button" (click)="toasts.act()">{{ t.action.label }}</button>
          }
        </div>
      }
    </div>
  `,
})
export class ToastHost {
  protected readonly toasts = inject(Toasts);
}
