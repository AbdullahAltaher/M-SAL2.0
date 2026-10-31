import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';

import { skipInstall } from '../../core/pwa/install-guard';

/**
 * S-02: how to add the app to the home screen, in three steps, as M-SAL's friends and family
 * will need it (PLAN.md section 10). "متابعة في المتصفح" goes on to sign-in without it.
 */
@Component({
  selector: 'app-install-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .install { min-height: 100dvh; justify-content: center; padding-top: calc(24px + env(safe-area-inset-top)); }
    .brand { text-align: center; }
    .brand img { width: 84px; height: 84px; border-radius: 19px; }
    .brand .eyebrow { text-align: center; font: 500 11px var(--lat); letter-spacing: .3em; color: var(--brass); }
    .brand h1 { font: 700 24px var(--ar); color: var(--ink); }
    .steps { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 14px; }
    .steps li { display: flex; align-items: center; gap: 12px; font: 500 16px/1.5 var(--ar); color: var(--ink); }
    .n { flex: none; width: 30px; height: 30px; border-radius: 50%; display: grid; place-items: center;
         background: var(--green); color: var(--on-green); font: 600 15px var(--num); }
    .glyph { flex: none; width: 34px; height: 34px; display: grid; place-items: center; color: var(--green);
             border: 1px solid var(--border); border-radius: 9px; background: var(--bg); }
    .why { color: var(--sec); font: 400 14px/1.6 var(--ar); margin: 4px 0 0; }
    .go { width: 100%; margin-top: 12px; min-height: 44px; border: 1px solid var(--border); border-radius: 10px; background: var(--surface);
          color: var(--ink); font: 600 16px var(--ar); cursor: pointer; }
  `,
  template: `
    <main class="wrap install" dir="rtl">
      <header class="brand">
        <img src="icons/icon-180.png" alt="">
        <span class="eyebrow">M-SAL</span>
        <h1>ثبّت التطبيق على الشاشة الرئيسية</h1>
      </header>
      <section class="panel">
        <ol class="steps">
          <li>
            <span class="n">1</span>
            <span class="glyph" aria-hidden="true">
              <svg width="20" height="22" viewBox="0 0 20 22" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M10 14V2M6 6l4-4 4 4"/><path d="M6 9H4a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8a2 2 0 0 0-2-2h-2"/>
              </svg>
            </span>
            <span>اضغط زر المشاركة أسفل Safari</span>
          </li>
          <li>
            <span class="n">2</span>
            <span class="glyph" aria-hidden="true">
              <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
                <rect x="2" y="2" width="16" height="16" rx="4"/><path d="M10 6v8M6 10h8"/>
              </svg>
            </span>
            <span>اختر «إضافة إلى الشاشة الرئيسية»</span>
          </li>
          <li>
            <span class="n">3</span>
            <span class="glyph" aria-hidden="true" dir="ltr" style="font: 600 13px var(--lat)">Add</span>
            <span>اضغط «إضافة»، وافتح «ميزانية» من الشاشة الرئيسية</span>
          </li>
        </ol>
        <p class="why">هكذا يفتح التطبيق بدون أشرطة Safari، ويصلك التذكير اليومي بالمصاريف، ويبقى دخولك محفوظًا.</p>
        <button class="go" type="button" (click)="skip()">متابعة في المتصفح</button>
      </section>
    </main>
  `,
})
export class InstallPage {
  private readonly router = inject(Router);

  protected skip(): void {
    skipInstall();
    void this.router.navigateByUrl('/');
  }
}
