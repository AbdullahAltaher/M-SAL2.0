import { ChangeDetectionStrategy, Component, inject, output, signal } from '@angular/core';

import { AppLock } from '../../core/pwa/app-lock';

/**
 * S-03: the Face ID lock over the app, the content hidden behind it. It is a privacy lock for
 * someone holding the phone, not a sign-in; if Face ID fails, sign out and sign in by email.
 */
@Component({
  selector: 'app-lock-screen',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { role: 'dialog', 'aria-modal': 'true', 'aria-label': 'التطبيق مقفل' },
  styles: `
    :host { position: fixed; inset: 0; z-index: 1000; background: var(--bg); display: flex; flex-direction: column;
            align-items: center; justify-content: center; gap: 18px; padding: 24px; text-align: center; }
    img { width: 96px; height: 96px; border-radius: 22px; }
    .eyebrow { font: 500 11px var(--lat); letter-spacing: .3em; color: var(--brass); }
    h1 { font: 700 24px var(--ar); color: var(--ink); margin: 0; }
    .open { min-height: 48px; min-width: 220px; border: none; border-radius: 12px; background: var(--green);
            color: var(--on-green); font: 600 17px var(--ar); cursor: pointer; }
    .out { background: none; border: none; color: var(--sec); font: 500 15px var(--ar); text-decoration: underline;
           cursor: pointer; }
    .err { color: var(--neg); font: 500 14px var(--ar); margin: 0; }
  `,
  template: `
    <img src="icons/icon-180.png" alt="">
    <span class="eyebrow">M-SAL</span>
    <h1 dir="rtl">ميزانية الشهر</h1>
    <button class="open" type="button" (click)="open()" [disabled]="busy()">افتح بـ Face ID</button>
    @if (failed()) {
      <p class="err" dir="rtl">لم يتم التحقق. حاول مرة أخرى.</p>
      <button class="out" type="button" (click)="signOut.emit()">تسجيل الخروج والدخول بالإيميل</button>
    }
  `,
})
export class LockScreen {
  private readonly lock = inject(AppLock);

  readonly signOut = output<void>();
  protected readonly busy = signal(false);
  protected readonly failed = signal(false);

  protected async open(): Promise<void> {
    this.busy.set(true);
    const ok = await this.lock.unlock();
    this.busy.set(false);
    this.failed.set(!ok);
  }
}
