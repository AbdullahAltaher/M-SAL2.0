import { ChangeDetectionStrategy, Component, computed, effect, inject, input, linkedSignal, output, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { Api } from '../../core/api/api';
import { ApiError } from '../../core/api/api-error';
import { Category, Entry, PendingEntry } from '../../core/api/models';
import { SessionStore } from '../../core/session/session-store';
import { sliceVar } from '../../core/util/chart';
import { addDays, dayLabel, parseDate, weekdayDate } from '../../core/util/dates';
import { Icon } from '../../shared/components/icon';
import { MoneyView } from '../../shared/components/money';
import { Toasts } from '../../shared/toast/toast';
import { Gratitude } from '../gratitude/gratitude';
import { flyCoin } from './coin-flight';
import { ExpensesStore } from './expenses-store';
import { cleanMerchant } from './pending-label';

/** What a card asks its page to do. */
export interface CardDone {
  id: string;
  /** The entry made by "اعتماد"; absent for "تجاهل" */
  entry?: Entry;
  /** The category the merchant was remembered for (B-16) */
  remembered?: string;
  /** The approve button, where the coin starts */
  from?: Element;
}

/**
 * One bank message waiting for review, as a card (BANK-SMS.md B-18, "الشكل أ"): the category's
 * tile, the merchant, the time and the amount; the category chip (changed with a tap, then
 * "تذكّر هذه الجهة"); the description with the category's suggestions; "نص الرسالة" to see the
 * bank's message as it came; and اعتماد or تجاهل. Approving needs a category and a description.
 */
@Component({
  selector: 'app-pending-card',
  imports: [Icon, MoneyView],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'pc', '[class.leaving]': 'leaving()', role: 'listitem' },
  template: `
    <div class="pc-head">
      <span class="x-tile pc-tile" aria-hidden="true" [style.--c]="color()">{{ cat()?.emoji ?? '؟' }}</span>
      <span class="pc-main">
        <span class="pc-merchant" dir="ltr">{{ entry().merchant }}</span>
        <span class="pc-when" dir="rtl">{{ when() }}</span>
      </span>
      <span class="pc-amt"><app-money [value]="entry().amount" /></span>
    </div>

    <div class="pc-catrow">
      <button class="pc-chip" type="button" [class.empty]="!cat()" [attr.aria-expanded]="picking()" (click)="picking.set(!picking()); dating.set(false)">
        @if (cat(); as c) {
          <span class="e" aria-hidden="true">{{ c.emoji }}</span><span dir="rtl">{{ c.name }}</span>
        } @else {
          <app-icon name="tag" [size]="16" /><span dir="rtl">اختر الفئة</span>
        }
        <app-icon name="chevD" [size]="14" [sw]="2.4" />
      </button>
      <!-- the day it counts on: the day it arrived, or another (B-21) -->
      <button class="pc-chip pc-date" type="button" [class.moved]="date() !== entry().entryDate" [attr.aria-expanded]="dating()"
              [attr.aria-label]="'اليوم: ' + dateLabel()" (click)="dating.set(!dating()); picking.set(false)">
        <app-icon name="calendar" [size]="15" /><span dir="rtl">{{ dateLabel() }}</span><app-icon name="chevD" [size]="14" [sw]="2.4" />
      </button>
      @if (entry().askEachTime && !cat()) {
        <span class="pc-hint" dir="rtl">جهة تختار فئتها كل مرة</span>
      } @else if (suggested() && cat()?.id === entry().categoryId) {
        <span class="pc-hint" dir="rtl">مقترحة</span>
      }
    </div>
    @if (dating()) {
      <div class="g-dchips pc-dates" role="group" aria-label="اليوم">
        <button class="g-dchip" type="button" [attr.aria-pressed]="date() === today()" (click)="setDate(today())">اليوم</button>
        <button class="g-dchip" type="button" [attr.aria-pressed]="date() === yesterday()" (click)="setDate(yesterday())">أمس</button>
        <label class="g-dchip" [attr.data-on]="otherDate() ? '1' : '0'">
          <span dir="rtl">{{ otherDate() ? shortDate(date()) : 'تاريخ آخر' }}</span>
          <input #dateField class="g-date-in" type="date" dir="ltr" aria-label="تاريخ آخر" [value]="date()"
                 (click)="openPicker(dateField)" (change)="setDate(dateField.value)">
        </label>
      </div>
    }
    @if (picking()) {
      <div class="g-chips pc-cats" role="group" aria-label="الفئة">
        @for (c of categories(); track c.id) {
          <button class="g-fchip" type="button" [attr.aria-pressed]="c.id === categoryId()" (click)="pick(c.id)">
            <span class="e" aria-hidden="true">{{ c.emoji }}</span><span dir="rtl">{{ c.name }}</span>
          </button>
        }
      </div>
    }
    @if (canRemember()) {
      <div class="pc-remember">
        <span class="pc-rl" dir="rtl">تذكّر هذه الجهة<small>المرة القادمة تُقترح {{ cat()?.name }}</small></span>
        <button class="st-switch" type="button" role="switch" [attr.aria-checked]="remember()" aria-label="تذكّر هذه الجهة"
                (click)="remember.set(!remember())"></button>
      </div>
    }

    <input #nameField class="field g-field pc-name" type="text" [attr.dir]="name() ? 'auto' : 'rtl'" enterkeyhint="done" autocomplete="off"
           placeholder="الوصف: ريوق، غداء…" aria-label="الوصف" maxlength="200" [class.bad]="bad()"
           [value]="name()" (input)="setName(nameField.value)" (keydown.enter)="$event.preventDefault(); nameField.blur()">
    @if (suggestions().length) {
      <div class="g-chips pc-sugg" role="group" aria-label="اقتراحات الوصف">
        @for (s of suggestions(); track s) {
          <button class="g-fchip" type="button" [attr.aria-pressed]="name() === s" (click)="setName(s)"><span dir="auto">{{ s }}</span></button>
        }
      </div>
    }

    <button class="pc-smstog" type="button" [attr.aria-expanded]="showText()" (click)="showText.set(!showText())">
      <app-icon name="message" [size]="16" /><span dir="rtl">نص الرسالة</span><app-icon name="chevD" [size]="14" [sw]="2.4" />
    </button>
    @if (showText()) {
      <p class="pc-sms" dir="ltr">{{ entry().smsText }}</p>
    }

    @if (hint()) {
      <p class="pc-err" role="alert" dir="rtl">{{ hint() }}</p>
    }
    <div class="pc-acts">
      <button #ok class="g-btn primary" type="button" [attr.aria-disabled]="!canApprove() || busy()" (click)="approve(ok)">اعتماد</button>
      <button class="g-btn" type="button" [disabled]="busy()" (click)="ignore()">تجاهل</button>
    </div>
  `,
})
export class PendingCard {
  private readonly api = inject(Api);
  private readonly store = inject(ExpensesStore);
  private readonly session = inject(SessionStore);

  readonly entry = input.required<PendingEntry>();
  /** The categories to pick from, most used first */
  readonly categories = input.required<Category[]>();
  readonly today = input('');
  readonly showAccount = input(false);
  readonly done = output<CardDone>();

  /** The suggestion until the user picks another (B-18) */
  protected readonly categoryId = linkedSignal(() => this.entry().categoryId);
  protected readonly name = signal('');
  protected readonly remember = signal(false);
  protected readonly picking = signal(false);
  /** The day the entry counts on: the day it arrived, unless picked (B-21) */
  protected readonly date = linkedSignal(() => this.entry().entryDate);
  protected readonly dating = signal(false);
  protected readonly yesterday = computed(() => (this.today() ? addDays(this.today(), -1) : ''));
  protected readonly otherDate = computed(() => this.date() !== this.today() && this.date() !== this.yesterday());
  protected readonly dateLabel = computed(() => {
    const d = this.date();
    return d === this.today() ? 'اليوم' : d === this.yesterday() ? 'أمس' : this.shortDate(d);
  });
  protected readonly showText = signal(false);
  protected readonly suggestions = signal<string[]>([]);
  protected readonly busy = signal(false);
  protected readonly bad = signal(false);
  protected readonly hint = signal('');
  protected readonly leaving = signal(false);

  protected readonly cat = computed(() => this.store.categories().find((c) => c.id === this.categoryId()) ?? null);
  protected readonly suggested = computed(() => !!this.entry().categoryId);
  protected readonly color = computed(() => {
    const i = this.store.categories().findIndex((c) => c.id === this.categoryId());
    return i < 0 ? 'var(--x-muted)' : sliceVar(i);
  });
  /** "Remember" only after a change from the suggestion, and never for "pick every time" (B-15, B-16) */
  protected readonly canRemember = computed(() =>
    !!this.categoryId() && this.categoryId() !== this.entry().categoryId && !this.entry().askEachTime);
  protected readonly canApprove = computed(() => !!this.categoryId() && this.name().trim() !== '');
  protected readonly when = computed(() => {
    const e = this.entry();
    const parts = [dayLabel(e.entryDate, this.today()) + ' ' + this.time(e.receivedAt)];
    if (this.showAccount()) {
      const account = this.store.accounts().find((a) => a.id === e.accountId);
      if (account) parts.push(account.name);
    }
    return parts.join(' · ');
  });

  constructor() {
    // The description's suggestions follow the category; one suggestion alone is taken (D-20)
    effect(() => {
      const categoryId = this.categoryId();
      const budgetId = this.store.budgetId();
      if (!categoryId || !budgetId) {
        this.suggestions.set([]);
        return;
      }
      void this.api.suggestions(budgetId, categoryId).then((r) => {
        if (categoryId !== this.categoryId()) return;
        this.suggestions.set(r.suggestions);
        if (r.suggestions.length === 1 && !this.name().trim()) this.name.set(r.suggestions[0]);
      }, () => this.suggestions.set([]));
    });
  }

  protected pick(id: string): void {
    this.categoryId.set(id);
    this.picking.set(false);
    this.hint.set('');
    if (id === this.entry().categoryId) this.remember.set(false);
  }

  protected setDate(d: string): void {
    if (!parseDate(d)) return;
    this.date.set(d);
    this.dating.set(false);
    this.hint.set('');
  }

  protected openPicker(input: HTMLInputElement): void {
    try {
      input.showPicker?.();
    } catch {
      // The native tap already opens it
    }
  }

  protected shortDate(d: string): string {
    return parseDate(d) ? weekdayDate(d) : 'تاريخ آخر';
  }

  protected setName(value: string): void {
    this.name.set(value);
    this.bad.set(false);
    this.hint.set('');
  }

  protected async approve(button: Element): Promise<void> {
    if (this.busy()) return;
    const categoryId = this.categoryId();
    if (!categoryId) {
      this.hint.set('اختر فئة قبل الاعتماد.');
      this.picking.set(true);
      return;
    }
    if (!this.name().trim()) {
      this.hint.set('اكتب وصف');
      this.bad.set(false);
      requestAnimationFrame(() => this.bad.set(true));
      return;
    }
    this.busy.set(true);
    try {
      const remember = this.canRemember() && this.remember();
      const entry = await this.api.approvePending(this.entry().id, { categoryId, name: this.name().trim(), remember, entryDate: this.date() });
      this.leave({ id: this.entry().id, entry, remembered: remember ? categoryId : undefined, from: button });
    } catch (e) {
      this.hint.set((e as ApiError).message);
      this.busy.set(false);
    }
  }

  protected async ignore(): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true);
    try {
      await this.api.ignorePending(this.entry().id);
      this.leave({ id: this.entry().id });
    } catch (e) {
      this.hint.set((e as ApiError).message);
      this.busy.set(false);
    }
  }

  /** The card fades and slides away, then the page drops it. */
  private leave(result: CardDone): void {
    this.leaving.set(true);
    const reduce = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
    setTimeout(() => this.done.emit(result), reduce ? 0 : 260);
  }

  /** The time it arrived, in the user's time zone (rule 14). */
  private time(iso: string): string {
    try {
      return new Intl.DateTimeFormat('en-GB', {
        hour: '2-digit', minute: '2-digit', hour12: false, timeZone: this.session.user()?.timeZone || undefined,
      }).format(new Date(iso));
    } catch {
      return '';
    }
  }
}

/**
 * "بانتظار المراجعة" (BANK-SMS.md B-18): the bank messages read by the iPhone shortcut, newest
 * first, each approved alone (B-09) or ignored (B-05). They are not entries until approved
 * (B-06). Approving plays the usual save motion when the entry lands in the cycle on screen
 * (D-10), and with the last card gone the page goes back home.
 */
@Component({
  selector: 'app-pending-view',
  imports: [PendingCard, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (list(); as l) {
      @if (l.length) {
        <p class="pc-intro" dir="rtl">اعتمد العملية لتُضاف لمصاريفك، أو تجاهلها إذا ما تبي تسجّلها.</p>
        <div class="pc-list" role="list" aria-label="بانتظار المراجعة">
          @for (p of l; track p.id) {
            <app-pending-card [entry]="p" [categories]="categories()" [today]="today()" [showAccount]="multi()" (done)="onDone($event)" />
          }
        </div>
      } @else {
        <div class="pc-empty">
          <p class="g-none" dir="rtl">لا توجد طلبات بانتظار المراجعة.</p>
          <a class="g-btn" routerLink="/expenses/home">رجوع للرئيسية</a>
        </div>
      }
    } @else if (failed()) {
      <p class="g-none" dir="rtl">{{ failed() }}</p>
    }
  `,
})
export class PendingView {
  private readonly api = inject(Api);
  private readonly store = inject(ExpensesStore);
  private readonly toasts = inject(Toasts);
  private readonly router = inject(Router);
  private readonly session = inject(SessionStore);
  private readonly gratitude = inject(Gratitude);
  /** Approved on this visit: the thanks card comes once, after the last request (D-42) */
  private approved: Entry[] = [];

  protected readonly list = signal<PendingEntry[] | null>(null);
  protected readonly failed = signal('');
  private readonly order = signal<string[]>([]);

  protected readonly today = computed(() => this.store.home()?.today ?? this.session.today() ?? '');
  protected readonly multi = computed(() => this.store.activeAccounts().length > 1);
  /** The visible categories, most used first, as in the entry sheet (R-ENT-15) */
  protected readonly categories = computed(() => {
    const order = this.order();
    const rank = (id: string) => (order.indexOf(id) < 0 ? order.length : order.indexOf(id));
    return this.store.categories().filter((c) => !c.isHidden).sort((a, b) => rank(a.id) - rank(b.id) || a.sortOrder - b.sortOrder);
  });

  constructor() {
    effect(() => {
      const budgetId = this.store.budgetId();
      if (!budgetId) return;
      void this.load(budgetId);
      void this.api.quick(budgetId).then((q) => this.order.set(q.categoryOrder), () => undefined);
    });
  }

  private async load(budgetId: string): Promise<void> {
    try {
      this.list.set(await this.api.pendingEntries(budgetId));
      this.failed.set('');
    } catch (e) {
      this.failed.set((e as ApiError).message);
    }
  }

  protected onDone(done: CardDone): void {
    const entry = done.entry;
    const rest = (this.list() ?? []).filter((p) => p.id !== done.id);
    // A merchant remembered on one card is suggested on its other cards at once (B-16)
    const card = this.list()?.find((p) => p.id === done.id);
    if (done.remembered && card) {
      const name = cleanMerchant(card.merchant);
      this.list.set(rest.map((p) => (cleanMerchant(p.merchant) === name
        ? { ...p, categoryId: done.remembered ?? null, askEachTime: false } : p)));
    } else {
      this.list.set(rest);
    }

    if (entry) {
      // The coin flies to the home tab, where the account card is (D-10), if the entry is on screen there
      const onScreen = entry.accountId === this.store.accountId() && entry.cycleKey === this.store.selectedKey();
      if (onScreen) flyCoin(done.from ?? null, document.querySelector('.xbar a.xi'));
      this.store.markFresh(entry.id);
      this.store.jumpTo(entry);
      this.toasts.show('أُضيف: ', { money: entry.amount });
    } else {
      this.store.refresh();
      this.toasts.show('تم التجاهل');
    }
    if (entry) this.approved.push(entry);
    if (!rest.length && this.approved.length) {
      const one = this.approved.length === 1 ? this.approved[0] : null;
      this.gratitude.added(one ? 'أُضيف' : 'انتهت المراجعة', one?.amount ?? null);
      this.approved = [];
    }
    if (!rest.length) setTimeout(() => void this.router.navigateByUrl('/expenses/home'), entry ? 900 : 300);
  }
}
