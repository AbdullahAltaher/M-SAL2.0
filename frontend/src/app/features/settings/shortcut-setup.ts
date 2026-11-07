import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { environment } from '../../../environments/environment';
import { FloatingWallet } from '../../shared/components/floating-wallet';
import { Icon } from '../../shared/components/icon';
import { NewSmsToken } from './new-token';

/**
 * How to make the iPhone shortcut send the bank's messages (BANK-SMS.md B-19 and 4.8), as drawn
 * steps like the install page (DESIGN.md D-31): the existing automation, "Get Contents of URL"
 * with the address and the token, the message as the body, and the answer as a notification.
 * The token itself shows only right after it was made, from memory ({@link NewSmsToken}).
 */
@Component({
  selector: 'app-shortcut-setup',
  imports: [FloatingWallet, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .setup { min-height: 100dvh; gap: 16px; max-width: 480px; margin-inline: auto;
             padding-top: calc(20px + env(safe-area-inset-top)); padding-bottom: calc(28px + env(safe-area-inset-bottom)); }
    .brand { display: flex; flex-direction: column; align-items: center; gap: 4px; text-align: center; }
    .brand app-floating-wallet { --w-opacity: 1; width: 84px; }
    .brand h1 { font: 700 22px/1.35 var(--ar); color: var(--x-text); }
    .brand p { margin: 0; color: var(--x-muted); font: 400 14px/1.6 var(--ar); }
    .steps { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 10px; }
    .step { display: flex; flex-direction: column; gap: 10px; padding: 14px; border-radius: 22px;
            background: var(--x-card); border: 1px solid var(--x-line); box-shadow: var(--x-hi); }
    .txt { font: 500 15.5px/1.6 var(--ar); color: var(--x-text); }
    .txt b { font-weight: 700; }
    .txt code, .en { font: 600 14px var(--num); direction: ltr; unicode-bidi: isolate; }
    .n { display: inline-grid; place-items: center; width: 24px; height: 24px; margin-inline-end: 6px; border-radius: 50%;
         background: linear-gradient(180deg, var(--x-btn-a), var(--x-btn-b)); color: #fff; font: 600 13px var(--num);
         vertical-align: 1px; }
    /* a Shortcuts action, drawn small */
    .act { display: flex; flex-direction: column; gap: 6px; padding: 10px 12px; border-radius: 14px;
           background: #1E2433; color: #E9EDF5; direction: ltr; text-align: left; font: 500 13px/1.45 var(--num); }
    .act .hd { display: flex; align-items: center; gap: 8px; font-weight: 600; }
    .act .ico { flex: none; width: 22px; height: 22px; border-radius: 6px; display: grid; place-items: center; color: #fff; }
    .act .row { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; color: #AEB7C9; }
    .act .tok { padding: 1px 8px; border-radius: 7px; background: #2F6BE0; color: #fff; }
    .act .var { padding: 1px 8px; border-radius: 7px; background: #3A4256; color: #fff; }
    .act .hot { animation: hot 1.8s ease-in-out infinite; }
    @keyframes hot { 50% { box-shadow: 0 0 0 3px rgba(47,107,224,.45); } }
    @media (prefers-reduced-motion: reduce) { .act .hot { animation: none; } }
    .copy { display: flex; align-items: center; gap: 8px; padding: 6px 6px 6px 12px; border-radius: 14px;
            background: var(--x-field); border: 1px solid var(--x-line); }
    .copy code { flex: 1; min-width: 0; overflow-wrap: anywhere; font: 500 13px/1.5 var(--num); color: var(--x-text); direction: ltr; text-align: left; }
    .copy button { flex: none; min-height: 36px; padding: 0 14px; border-radius: 999px; border: none; cursor: pointer;
                   background: linear-gradient(180deg, var(--x-btn-a), var(--x-btn-b)); color: #fff; font: 600 13px var(--ar); }
    .warn { margin: 0; color: var(--x-warn); font: 500 13px/1.6 var(--ar); }
    .note { margin: 0; color: var(--x-muted); font: 400 13.5px/1.7 var(--ar); text-align: center; }
    .go { width: 100%; min-height: 50px; border-radius: 999px; border: 1px solid var(--x-line); background: var(--x-field);
          color: var(--x-text); font: 600 16px var(--ar); cursor: pointer; }
  `,
  template: `
    <div class="xbg" aria-hidden="true"><span class="blob b1"></span><span class="blob b2"></span><span class="blob b3"></span></div>
    <main class="wrap setup" dir="rtl">
      <header class="brand">
        <app-floating-wallet />
        <h1>رسائل البنك من الآيفون</h1>
        <p>الآيفون لا يسمح لأي تطبيق بقراءة الرسائل، لذلك ترسلها أتمتة في تطبيق «الاختصارات» إلى التطبيق.</p>
      </header>

      <ol class="steps">
        <li class="step">
          <span class="txt"><span class="n">1</span>في تطبيق <b>الاختصارات</b> ← <b>الأتمتة</b>، افتح أتمتة <b>الرسالة</b> التي تحتوي على
            <code>a/c *</code> وآخر أرقام حسابك، واجعلها <span class="en">Run Immediately</span> (تشغيل فوراً).</span>
          <div class="act" aria-hidden="true">
            <span class="hd"><span class="ico" style="background:#34C759"><app-icon name="message" [size]="14" /></span>When I get a message containing</span>
            <span class="row"><span class="var hot">a/c *987</span><span>· Run Immediately</span></span>
          </div>
        </li>

        <li class="step">
          <span class="txt"><span class="n">2</span>أضف إجراء <span class="en">Get Contents of URL</span> بهذا العنوان:</span>
          <div class="copy">
            <code>{{ url }}</code>
            <button type="button" (click)="copy('url', url)">{{ copied() === 'url' ? 'نُسخ ✓' : 'نسخ' }}</button>
          </div>
          <div class="act" aria-hidden="true">
            <span class="hd"><span class="ico" style="background:#2F6BE0"><app-icon name="share" [size]="14" /></span>Get contents of <span class="tok">URL</span></span>
            <span class="row">Method <span class="var">POST</span></span>
          </div>
        </li>

        <li class="step">
          <span class="txt"><span class="n">3</span>افتح <span class="en">Show More</span>: الطريقة <code>POST</code>، وفي <span class="en">Headers</span> أضف
            <code>Authorization</code> وقيمته كلمة <code>Bearer</code> ثم مسافة ثم الرمز.</span>
          @if (token()) {
            <div class="copy">
              <code>Bearer {{ token() }}</code>
              <button type="button" (click)="copy('token', 'Bearer ' + token())">{{ copied() === 'token' ? 'نُسخ ✓' : 'نسخ' }}</button>
            </div>
            <p class="warn">انسخه الآن: الرمز لا يظهر مرة ثانية. إذا ضاع، أنشئ رمزاً جديداً من الإعدادات.</p>
          } @else {
            <p class="warn">الرمز يظهر مرة واحدة عند إنشائه من «الإعدادات ← رسائل البنك».</p>
          }
          <div class="act" aria-hidden="true">
            <span class="row">Headers</span>
            <span class="row"><span class="var">Authorization</span><span class="tok hot">Bearer msal_…</span></span>
          </div>
        </li>

        <li class="step">
          <span class="txt"><span class="n">4</span><span class="en">Request Body</span>: اختر <code>JSON</code>، وأضف حقلاً نصياً اسمه
            <code>text</code> وقيمته <span class="en">Shortcut Input</span> ثم <span class="en">Content</span> (نص الرسالة).</span>
          <div class="act" aria-hidden="true">
            <span class="row">Request Body <span class="var">JSON</span></span>
            <span class="row"><span class="var">text</span><span class="tok hot">Shortcut Input · Content</span></span>
          </div>
        </li>

        <li class="step">
          <span class="txt"><span class="n">5</span>أضف <span class="en">Get Dictionary Value</span> للمفتاح <code>message</code> من النتيجة، ثم
            <span class="en">Show Notification</span> بهذه القيمة. يصلك إشعار مثل «طلب جديد: 12.00 د.إ · أكل ومطاعم».</span>
          <div class="act" aria-hidden="true">
            <span class="hd"><span class="ico" style="background:#FF9500"><app-icon name="bell" [size]="14" /></span>Show notification</span>
            <span class="row"><span class="tok hot">message</span></span>
          </div>
        </li>
      </ol>

      <p class="note">جرّبها بعملية حقيقية: يظهر الطلب في «المصاريف» فوق بطاقة الحساب، ولا يُضاف أي مصروف إلا إذا اعتمدته.</p>
      <button class="go" type="button" (click)="back()">رجوع</button>
    </main>
  `,
})
export class ShortcutSetup {
  private readonly router = inject(Router);

  protected readonly url = environment.apiUrl + '/sms/ingest';
  /** Only right after "إنشاء رمز" in the settings */
  protected readonly token = inject(NewSmsToken).value;
  protected readonly copied = signal<'url' | 'token' | null>(null);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.token.set(null));
  }

  protected async copy(what: 'url' | 'token', text: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(text);
      this.copied.set(what);
      setTimeout(() => this.copied.set(null), 2000);
    } catch {
      this.copied.set(null);
    }
  }

  protected back(): void {
    void this.router.navigateByUrl('/expenses/home');
  }
}
