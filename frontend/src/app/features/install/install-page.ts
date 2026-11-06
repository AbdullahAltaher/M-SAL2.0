import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';

import { skipInstall } from '../../core/pwa/install-guard';
import { FloatingWallet } from '../../shared/components/floating-wallet';

/**
 * S-02: how to add the app to the home screen, as M-SAL's friends and family will need it
 * (PLAN.md section 10), in three drawn steps (DESIGN.md D-31): Safari's share button, "Add to
 * Home Screen", then the icon on the home screen. "متابعة في المتصفح" goes on without it.
 */
@Component({
  selector: 'app-install-page',
  imports: [FloatingWallet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .install { min-height: 100dvh; justify-content: center; gap: 18px; max-width: 460px;
               padding-top: calc(24px + env(safe-area-inset-top)); }
    .brand { display: flex; flex-direction: column; align-items: center; gap: 4px; text-align: center; }
    .brand app-floating-wallet { --w-opacity: 1; width: 104px; }
    .brand h1 { font: 700 23px/1.35 var(--ar); color: var(--x-text); }
    .steps { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 10px; }
    .step { display: flex; align-items: center; gap: 14px; padding: 12px; border-radius: 22px;
            background: var(--x-card); border: 1px solid var(--x-line); box-shadow: var(--x-hi); }
    .txt { flex: 1; min-width: 0; font: 500 16px/1.5 var(--ar); color: var(--x-text); }
    .n { display: inline-grid; place-items: center; width: 24px; height: 24px; margin-inline-end: 6px; border-radius: 50%;
         background: linear-gradient(180deg, var(--x-btn-a), var(--x-btn-b)); color: #fff; font: 600 13px var(--num);
         vertical-align: 1px; }
    /* the small phones */
    .ph { position: relative; flex: none; width: 84px; height: 112px; border-radius: 16px; overflow: hidden;
          background: #F2F4F8; border: 3px solid #1C2233; }
    .ph .bar { position: absolute; left: 0; right: 0; bottom: 0; height: 24px; background: #E4E8F0;
               display: flex; justify-content: space-around; align-items: center; color: #7A8499; }
    .ph .bar svg { width: 13px; height: 13px; }
    .ph .bar .hot { color: #2F6BE0; animation: pulse 1.6s ease-in-out infinite; }
    .ph .page { position: absolute; left: 8px; right: 8px; top: 10px; display: flex; flex-direction: column; gap: 5px; }
    .ph .page i { height: 6px; border-radius: 3px; background: #D6DCE8; }
    .ph .share { position: absolute; left: 0; right: 0; bottom: 0; height: 74px; padding: 6px; background: #fff;
                 border-radius: 10px 10px 0 0; display: flex; flex-direction: column; gap: 4px; }
    .ph .share i { height: 12px; border-radius: 4px; background: #EEF1F6; }
    .ph .share .hot { background: #2F6BE0; animation: pulse 1.6s ease-in-out infinite; }
    .ph.home { background: linear-gradient(160deg, #6E8BD8, #3C4F9E); }
    .ph .apps { position: absolute; inset: 12px 8px; display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
    .ph .apps i { aspect-ratio: 1; border-radius: 6px; background: rgba(255, 255, 255, .35); }
    .ph .apps img { width: 100%; border-radius: 6px; animation: pulse 1.6s ease-in-out infinite; }
    @keyframes pulse { 50% { transform: scale(1.12); } }
    .why { color: var(--x-muted); font: 400 14px/1.6 var(--ar); text-align: center; margin: 0; }
    .go { width: 100%; min-height: 50px; border-radius: 999px; border: 1px solid var(--x-line); background: var(--x-field);
          color: var(--x-text); font: 600 16px var(--ar); cursor: pointer; }
  `,
  template: `
    <div class="xbg" aria-hidden="true"><span class="blob b1"></span><span class="blob b2"></span><span class="blob b3"></span></div>
    <main class="wrap install" dir="rtl">
      <header class="brand">
        <app-floating-wallet />
        <h1>ثبّت التطبيق على الشاشة الرئيسية</h1>
      </header>
      <ol class="steps">
        <li class="step">
          <span class="ph" aria-hidden="true">
            <span class="page"><i></i><i style="width:70%"></i><i></i><i style="width:55%"></i></span>
            <span class="bar">
              <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M13 4l-6 6 6 6"/></svg>
              <svg class="hot" viewBox="0 0 20 22" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M10 14V2M6 6l4-4 4 4"/><path d="M6 9H4v11h12V9h-2"/></svg>
              <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2.2"><rect x="3" y="3" width="14" height="14" rx="3"/></svg>
            </span>
          </span>
          <span class="txt"><span class="n">1</span>اضغط زر المشاركة أسفل Safari</span>
        </li>
        <li class="step">
          <span class="ph" aria-hidden="true">
            <span class="page"><i></i><i style="width:70%"></i></span>
            <span class="share"><i></i><i class="hot"></i><i></i><i style="width:60%"></i></span>
          </span>
          <span class="txt"><span class="n">2</span>اختر «إضافة إلى الشاشة الرئيسية»</span>
        </li>
        <li class="step">
          <span class="ph home" aria-hidden="true">
            <span class="apps"><i></i><i></i><i></i><i></i><img src="icons/icon-180.png" alt=""><i></i></span>
          </span>
          <span class="txt"><span class="n">3</span>اضغط «إضافة»، وافتح «ميزانية» من الشاشة الرئيسية</span>
        </li>
      </ol>
      <p class="why">هكذا يفتح التطبيق بدون أشرطة Safari، ويصلك التذكير اليومي بالمصاريف، ويبقى دخولك محفوظًا.</p>
      <button class="go" type="button" (click)="skip()">متابعة في المتصفح</button>
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
