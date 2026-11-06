import { ChangeDetectionStrategy, Component, inject, output, signal } from '@angular/core';

import { AppLock } from '../../core/pwa/app-lock';
import { FloatingWallet } from '../../shared/components/floating-wallet';
import { Icon } from '../../shared/components/icon';

/**
 * S-03: the Face ID lock over the app, the content hidden behind it. It is a privacy lock for
 * someone holding the phone, not a sign-in; if Face ID fails, sign out and sign in by email.
 * In the glass identity: the wallet with a small lock on its corner (DESIGN.md D-30).
 */
@Component({
  selector: 'app-lock-screen',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { role: 'dialog', 'aria-modal': 'true', 'aria-label': 'التطبيق مقفل' },
  imports: [FloatingWallet, Icon],
  styles: `
    :host { position: fixed; inset: 0; z-index: 1000; display: flex; flex-direction: column; align-items: center;
            justify-content: center; gap: 16px; padding: 24px; text-align: center;
            background: linear-gradient(180deg, var(--x-bg0), var(--x-bg1)); }
    .mark { position: relative; width: 140px; }
    .mark app-floating-wallet { --w-opacity: 1; }
    .badge { position: absolute; right: -4px; bottom: 6px; width: 40px; height: 40px; border-radius: 50%; display: grid;
             place-items: center; color: #fff; background: linear-gradient(180deg, var(--x-btn-a), var(--x-btn-b));
             box-shadow: 0 8px 18px -6px rgba(40, 100, 230, .8), 0 0 0 4px var(--x-bg0); }
    h1 { font: 700 22px var(--ar); color: var(--x-text); margin: 0; }
    .open { display: inline-flex; align-items: center; justify-content: center; gap: 8px; min-height: 54px; min-width: 240px;
            margin-top: 10px; border: none; border-radius: 999px; color: #fff; font: 600 17px var(--ar); cursor: pointer;
            background: linear-gradient(180deg, var(--x-btn-a), var(--x-btn-b));
            box-shadow: 0 14px 26px -12px rgba(40, 100, 230, .85), inset 0 1px 0 rgba(255, 255, 255, .3); }
    .open:disabled { opacity: .6; }
    .out { min-height: 44px; background: none; border: none; color: var(--x-accent-ink); font: 500 15px var(--ar); cursor: pointer; }
    .err { color: var(--x-exp); font: 500 14px var(--ar); margin: 0; }
  `,
  template: `
    <div class="mark" aria-hidden="true">
      <app-floating-wallet />
      <span class="badge"><app-icon name="lock" [size]="20" [sw]="2.2" /></span>
    </div>
    <h1 dir="rtl">التطبيق مقفل</h1>
    <button class="open" type="button" (click)="open()" [disabled]="busy()"><app-icon name="faceId" [size]="22" />افتح بـ Face ID</button>
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
