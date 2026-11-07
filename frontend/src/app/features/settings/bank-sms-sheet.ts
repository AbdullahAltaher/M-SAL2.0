import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { Router } from '@angular/router';

import { Api } from '../../core/api/api';
import { ApiError } from '../../core/api/api-error';
import { Category, MerchantKeyword, SmsSettings } from '../../core/api/models';
import { SessionStore } from '../../core/session/session-store';
import { toLatinDigits } from '../../core/util/amounts';
import { dayLabel } from '../../core/util/dates';
import { Icon } from '../../shared/components/icon';
import { Dialogs } from '../../shared/sheet/dialogs';
import { Sheets } from '../../shared/sheet/sheets';
import { Toasts } from '../../shared/toast/toast';
import { ExpensesStore } from '../expenses/expenses-store';
import { NewSmsToken } from './new-token';

/**
 * "الإعدادات ← رسائل البنك" (BANK-SMS.md B-19), in the settings' iPhone-style groups (D-34):
 * the account (the digits after "a/c *" linked to an account; messages of other digits are
 * refused), the link with the iPhone (a token shown once, when it was last used, revoke, and the
 * shortcut's steps), the words by category (B-10, chips with ×), and the merchants whose
 * category is picked every time (B-15). Words belong to the open person's budget.
 */
@Component({
  selector: 'app-bank-sms-sheet',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (settings(); as s) {
      <h3 class="st-sec" dir="rtl">الحساب</h3>
      <div class="st-grp">
        @for (l of s.links; track l.suffix) {
          <div class="st-row">
            <span class="st-ic" style="--c:#2F6BE0"><app-icon name="wallet" [size]="16" /></span>
            <span class="st-l"><bdi class="bs-suffix" dir="ltr">*{{ l.suffix }}</bdi></span>
            <span class="st-v" dir="auto">{{ l.accountName }}{{ l.budgetId !== budgetId() ? ' · ' + l.budgetName : '' }}{{ l.archived ? ' (مؤرشف)' : '' }}</span>
            <button class="bs-x" type="button" [attr.aria-label]="'فك ربط ' + l.suffix" (click)="unlink(l.suffix)"><app-icon name="close" [size]="16" [sw]="2.2" /></button>
          </div>
        }
        @if (linking()) {
          <div class="st-row bs-form">
            <input #suffixField class="st-time bs-digits" type="text" inputmode="numeric" dir="ltr" maxlength="6" placeholder="987"
                   aria-label="آخر أرقام الحساب في رسالة البنك" autocomplete="off">
            <select #accountField class="st-select bs-acct" dir="rtl" aria-label="الحساب في التطبيق">
              @for (a of accounts(); track a.id) {
                <option [value]="a.id" [selected]="a.id === store.accountId()">{{ a.name }}</option>
              }
            </select>
            <button class="bs-ok" type="button" [disabled]="busy()" (click)="link(suffixField.value, accountField.value)">ربط</button>
          </div>
        } @else {
          <button class="st-row" type="button" (click)="startLink()">
            <span class="st-ic" style="--c:#2F6BE0"><app-icon name="plus" [size]="16" /></span>
            <span class="st-l">{{ s.links.length ? 'ربط حساب آخر' : 'ربط حساب' }}</span><app-icon name="chevL" [size]="16" />
          </button>
        }
      </div>
      <p class="st-note" dir="rtl">الأرقام التي بعد <bdi dir="ltr">a/c *</bdi> في رسالة البنك. رسائل أي حساب غير مربوط تُرفض.</p>

      <h3 class="st-sec" dir="rtl">الربط مع الآيفون</h3>
      <div class="st-grp">
        <div class="st-row">
          <span class="st-ic" style="--c:#17A673"><app-icon name="key" [size]="16" /></span>
          <span class="st-l">الرمز</span>
          <span class="st-v" dir="rtl">@if (s.token; as t) {ينتهي بـ <bdi dir="ltr">{{ t.hint }}</bdi>} @else {لا يوجد}</span>
        </div>
        @if (s.token; as t) {
          <div class="st-row">
            <span class="st-ic" style="--c:#17A673"><app-icon name="clock" [size]="16" /></span>
            <span class="st-l">آخر استخدام</span>
            <span class="st-v" dir="rtl">{{ t.lastUsedAt ? when(t.lastUsedAt) : 'لم يُستخدم بعد' }}</span>
          </div>
        }
        @if (fresh(); as token) {
          <div class="bs-token">
            <code dir="ltr">{{ token }}</code>
            <button class="bs-ok" type="button" (click)="copy(token)">{{ copied() ? 'نُسخ ✓' : 'نسخ' }}</button>
            <p dir="rtl">انسخه الآن وضعه في الاختصار: لا يظهر مرة ثانية.</p>
          </div>
        }
        <button class="st-row" type="button" [disabled]="busy()" (click)="newToken()">
          <span class="st-ic" style="--c:#17A673"><app-icon name="plus" [size]="16" /></span>
          <span class="st-l">{{ s.token ? 'إنشاء رمز جديد' : 'إنشاء رمز' }}</span>
        </button>
        <button class="st-row" type="button" (click)="setup()">
          <span class="st-ic" style="--c:#566A8E"><app-icon name="phone" [size]="16" /></span>
          <span class="st-l">خطوات إعداد الاختصار</span><app-icon name="chevL" [size]="16" />
        </button>
        @if (s.token) {
          <button class="st-row danger" type="button" [disabled]="busy()" (click)="revoke()">
            <span class="st-ic" style="--c:#C73A2E"><app-icon name="close" [size]="16" [sw]="2.2" /></span>
            <span class="st-l">إلغاء الرمز</span>
          </button>
        }
      </div>
      <p class="st-note" dir="rtl">الرمز يظهر مرة واحدة فقط. إنشاء رمز جديد يوقف القديم، فتحدّث الاختصار به.</p>

      <h3 class="st-sec" dir="rtl">الكلمات حسب الفئة</h3>
      <div class="st-grp bs-words">
        @for (g of groups(); track g.category.id) {
          <div class="bs-group">
            <div class="bs-cat" dir="rtl"><span class="e" aria-hidden="true">{{ g.category.emoji }}</span>{{ g.category.name }}</div>
            <div class="bs-chips">
              @for (k of g.words; track k.id) {
                <span class="bs-kw"><bdi dir="ltr">{{ k.keyword }}</bdi>
                  <button type="button" [attr.aria-label]="'حذف ' + k.keyword" (click)="removeWord(k)"><app-icon name="close" [size]="13" [sw]="2.4" /></button></span>
              }
              <button class="bs-add" type="button" (click)="startWord(g.category.id)">+ كلمة</button>
            </div>
          </div>
        }
        @if (adding() !== undefined && adding() !== null && !hasWords(adding()!)) {
          <div class="bs-group">
            <div class="bs-cat" dir="rtl"><span class="e" aria-hidden="true">{{ store.catOf(adding()).emoji }}</span>{{ store.catOf(adding()).name }}</div>
          </div>
        }
        @if (adding() !== undefined) {
          <div class="st-row bs-form">
            <input #wordField class="st-time bs-word" type="text" dir="ltr" maxlength="40" autocapitalize="characters" autocomplete="off"
                   [attr.aria-label]="adding() ? 'كلمة لفئة ' + store.catOf(adding()).name : 'جهة تختار فئتها كل مرة'"
                   [placeholder]="adding() ? 'CAFE' : 'FASTBITE'" (keydown.enter)="addWord(wordField.value)">
            <button class="bs-ok" type="button" [disabled]="busy()" (click)="addWord(wordField.value)">إضافة</button>
            <button class="bs-x" type="button" aria-label="إلغاء" (click)="adding.set(undefined)"><app-icon name="close" [size]="16" [sw]="2.2" /></button>
          </div>
        }
        @if (others().length) {
          <div class="bs-group">
            <div class="bs-cat muted" dir="rtl">أضف كلمة لفئة:</div>
            <div class="g-chips bs-others" role="group" aria-label="فئات بدون كلمات">
              @for (c of others(); track c.id) {
                <button class="g-fchip" type="button" [attr.aria-pressed]="adding() === c.id" (click)="startWord(c.id)">
                  <span class="e" aria-hidden="true">{{ c.emoji }}</span><span dir="rtl">{{ c.name }}</span>
                </button>
              }
            </div>
          </div>
        }
      </div>
      <p class="st-note" dir="rtl">الكلمة تطابق كلمات كاملة في اسم الجهة من أي مكان فيه (<bdi dir="ltr">CAFE</bdi> لا تطابق <bdi dir="ltr">CAFETERIA</bdi>). إذا طابقت جهةٌ كلمتين، فالتي تأتي أولاً في الاسم تفوز.</p>

      <h3 class="st-sec" dir="rtl">اختر الفئة كل مرة</h3>
      <div class="st-grp bs-words">
        <div class="bs-group">
          <div class="bs-chips">
            @for (k of askWords(); track k.id) {
              <span class="bs-kw"><bdi dir="ltr">{{ k.keyword }}</bdi>
                <button type="button" [attr.aria-label]="'حذف ' + k.keyword" (click)="removeWord(k)"><app-icon name="close" [size]="13" [sw]="2.4" /></button></span>
            }
            <button class="bs-add" type="button" (click)="startWord(null)">+ جهة</button>
          </div>
        </div>
      </div>
      <p class="st-note" dir="rtl">جهة تحتمل عدة فئات (مثل تطبيقات التوصيل) تصل بدون اقتراح، وتختار فئتها في كل طلب.</p>
    } @else if (failed()) {
      <p class="g-none" dir="rtl">{{ failed() }}</p>
    }
  `,
})
export class BankSmsSheet {
  private readonly api = inject(Api);
  protected readonly store = inject(ExpensesStore);
  private readonly session = inject(SessionStore);
  private readonly sheets = inject(Sheets);
  private readonly dialogs = inject(Dialogs);
  private readonly toasts = inject(Toasts);
  private readonly router = inject(Router);
  private readonly newSmsToken = inject(NewSmsToken);

  protected readonly settings = signal<SmsSettings | null>(null);
  protected readonly failed = signal('');
  protected readonly busy = signal(false);
  protected readonly linking = signal(false);
  /** The category a word is being added to: an id, null for "pick every time", undefined for none */
  protected readonly adding = signal<string | null | undefined>(undefined);
  /** The token just made, shown this once (B-19) */
  protected readonly fresh = this.newSmsToken.value;
  protected readonly copied = signal(false);

  private readonly suffixField = viewChild<ElementRef<HTMLInputElement>>('suffixField');
  private readonly wordField = viewChild<ElementRef<HTMLInputElement>>('wordField');

  protected readonly budgetId = computed(() => this.store.budgetId());
  protected readonly accounts = computed(() => this.store.accounts().filter((a) => !a.isArchived));
  private readonly words = computed(() => (this.settings()?.keywords ?? []).filter((k) => k.budgetId === this.budgetId()));
  protected readonly groups = computed(() => {
    const words = this.words();
    return this.store.categories()
      .map((category) => ({ category, words: words.filter((k) => k.categoryId === category.id) }))
      .filter((g) => g.words.length);
  });
  protected readonly askWords = computed(() => this.words().filter((k) => !k.categoryId));
  /** The visible categories with no word yet */
  protected readonly others = computed<Category[]>(() => {
    const used = new Set(this.words().map((k) => k.categoryId));
    return this.store.categories().filter((c) => !c.isHidden && !used.has(c.id));
  });

  constructor() {
    void this.load();
    // The new token shows while this sheet is open again or the steps page is, then it is forgotten
    inject(DestroyRef).onDestroy(() => setTimeout(() => {
      if (this.sheets.current()?.component !== BankSmsSheet && !this.router.url.startsWith('/bank-sms/setup')) {
        this.newSmsToken.value.set(null);
      }
    }, 400));
  }

  private async load(): Promise<void> {
    try {
      this.settings.set(await this.api.smsSettings());
      this.failed.set('');
    } catch (e) {
      this.failed.set((e as ApiError).message);
    }
  }

  protected hasWords(categoryId: string): boolean {
    return this.words().some((k) => k.categoryId === categoryId);
  }

  // ── The account ─────────────────────────────────────────────

  protected startLink(): void {
    this.linking.set(true);
    setTimeout(() => this.suffixField()?.nativeElement.focus({ preventScroll: true }), 30);
  }

  protected async link(rawSuffix: string, accountId: string): Promise<void> {
    const suffix = toLatinDigits(rawSuffix).replace(/\D/g, '');
    await this.run(async () => {
      await this.api.linkSms(suffix, accountId);
      this.linking.set(false);
      this.toasts.show('رُبط الحساب');
    });
  }

  protected unlink(suffix: string): Promise<void> {
    return this.run(async () => {
      await this.api.unlinkSms(suffix);
      this.toasts.show('فُكّ الربط');
    });
  }

  // ── The token ───────────────────────────────────────────────

  /** A new token stops the old one, so that is asked first; this sheet then opens again. */
  protected async newToken(): Promise<void> {
    if (this.settings()?.token) {
      const ok = await this.dialogs.confirm('الرمز الحالي سيتوقف، ويلزم وضع الرمز الجديد في الاختصار.', 'رمز جديد', 'إنشاء');
      if (!ok) {
        void this.reopen();
        return;
      }
    }
    try {
      const made = await this.api.newSmsToken();
      this.newSmsToken.value.set(made.token);
    } catch (e) {
      this.toasts.show((e as ApiError).message);
    }
    void this.reopen();
  }

  protected async revoke(): Promise<void> {
    const ok = await this.dialogs.confirm('الاختصار سيتوقف عن إرسال الرسائل حتى تنشئ رمزاً جديداً.', 'إلغاء الرمز', 'إلغاء الرمز');
    if (ok) {
      try {
        await this.api.revokeSmsToken();
        this.newSmsToken.value.set(null);
        this.toasts.show('أُلغي الرمز');
      } catch (e) {
        this.toasts.show((e as ApiError).message);
      }
    }
    void this.reopen();
  }

  protected async copy(token: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(token);
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    } catch {
      this.toasts.show('لم يُنسخ، انسخه يدوياً.');
    }
  }

  protected setup(): void {
    this.sheets.close();
    void this.router.navigateByUrl('/bank-sms/setup');
  }

  // ── Words ───────────────────────────────────────────────────

  protected startWord(categoryId: string | null): void {
    this.adding.set(categoryId);
    setTimeout(() => this.wordField()?.nativeElement.focus({ preventScroll: true }), 30);
  }

  protected async addWord(word: string): Promise<void> {
    const budgetId = this.budgetId();
    const categoryId = this.adding();
    if (!budgetId || categoryId === undefined) return;
    await this.run(async () => {
      await this.api.addKeyword(budgetId, word, categoryId);
      this.adding.set(undefined);
    });
  }

  protected removeWord(k: MerchantKeyword): Promise<void> {
    return this.run(async () => {
      await this.api.deleteKeyword(k.id);
      this.toasts.show(`حُذفت «${k.keyword}»`);
    });
  }

  /** Runs a change, then loads the settings again; errors show as they come. */
  private async run(change: () => Promise<void>): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true);
    try {
      await change();
      await this.load();
    } catch (e) {
      this.toasts.show((e as ApiError).message);
    } finally {
      this.busy.set(false);
    }
  }

  private reopen(): Promise<unknown> {
    return this.sheets.open(BankSmsSheet, 'رسائل البنك');
  }

  /** "اليوم 14:05", "أمس 09:30", or the date and time, in the user's time zone. */
  protected when(iso: string): string {
    const zone = this.session.user()?.timeZone || undefined;
    try {
      const date = new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(iso));
      const time = new Intl.DateTimeFormat('en-GB', { timeZone: zone, hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(iso));
      const today = this.session.today();
      return (today ? dayLabel(date, today) : date) + ' ' + time;
    } catch {
      return '';
    }
  }
}

