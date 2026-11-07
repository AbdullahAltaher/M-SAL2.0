import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { environment } from '../../../environments/environment';
import { Api } from '../../core/api/api';
import { ApiError } from '../../core/api/api-error';
import { ImportRecord } from '../../core/api/models';
import { SupabaseAuth } from '../../core/auth/supabase-auth';
import { AppLock } from '../../core/pwa/app-lock';
import { DailyReminder } from '../../core/pwa/daily-reminder';
import { canLock } from '../../core/pwa/device';
import { SessionStore } from '../../core/session/session-store';
import { BudgetsStore } from '../../core/data/budgets-store';
import { accountPhotoKey, budgetPhotoKey } from '../../core/photos/card-photos';
import { Theme, ThemeMode } from '../../core/theme/theme';
import { Dialogs } from '../../shared/sheet/dialogs';
import { Sheets } from '../../shared/sheet/sheets';
import { Toasts } from '../../shared/toast/toast';
import { CardPhotoSheet } from '../../shared/components/card-photo-sheet';
import { Icon } from '../../shared/components/icon';
import { IfService } from '../../core/permissions/if-service';
import { ExpensesStore } from '../expenses/expenses-store';
import { ImportExport } from './import-export';

/**
 * SH-08: data and settings (SCREENS.md 4.2), as iPhone-style groups with a coloured icon per row
 * (DESIGN.md D-34): appearance (light or dark, D-07, and the cards' photos, D-15), security and
 * the daily reminder, the data (export, import and its history, in place of M-SAL's storage
 * status and snapshots), and the account (email, name, time zone, version, install steps,
 * sign-out).
 */
@Component({
  selector: 'app-settings-sheet',
  imports: [Icon, IfService],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (me(); as m) {
      <h3 class="st-sec" dir="rtl">المظهر</h3>
      <div class="st-grp">
        <div class="st-row">
          <span class="st-ic" style="--c:#6B4FD8"><app-icon name="moon" [size]="16" /></span>
          <span class="st-l">الألوان</span>
          <div class="st-seg" role="group" aria-label="الألوان">
            @for (o of themes; track o.mode) {
              <button type="button" [attr.aria-pressed]="theme.mode() === o.mode" (click)="theme.set(o.mode)">{{ o.label }}</button>
            }
          </div>
        </div>
        @if (budgetName(); as name) {
          <button class="st-row" type="button" (click)="photo('budget')">
            <span class="st-ic" style="--c:#E07A4F"><app-icon name="photo" [size]="16" /></span>
            <span class="st-l">صورة بطاقة الميزانية</span><span class="st-v" dir="auto">{{ name }}</span><app-icon name="chevL" [size]="16" />
          </button>
        }
        @if (accountName(); as name) {
          <button class="st-row" type="button" (click)="photo('account')">
            <span class="st-ic" style="--c:#E07A4F"><app-icon name="photo" [size]="16" /></span>
            <span class="st-l">صورة بطاقة المصاريف</span><span class="st-v" dir="auto">{{ name }}</span><app-icon name="chevL" [size]="16" />
          </button>
        }
      </div>
      <p class="st-note" dir="rtl">«تلقائي» يتبع إعداد الجهاز (فاتح أو داكن).</p>

      <h3 class="st-sec" dir="rtl">الأمان والتذكير</h3>
      <div class="st-grp">
        @if (lockAvailable()) {
          <div class="st-row">
            <span class="st-ic" style="--c:#17A673"><app-icon name="faceId" [size]="16" /></span>
            <span class="st-l">قفل Face ID</span>
            <button class="st-switch" type="button" role="switch" [attr.aria-checked]="lock.enabled()" aria-label="قفل Face ID" (click)="toggleLock()"></button>
          </div>
        }
        <div class="st-row">
          <span class="st-ic" style="--c:#D85A30"><app-icon name="bell" [size]="16" /></span>
          <span class="st-l">التذكير اليومي</span>
          @if (reminder.time(); as t) {
            <input #rt class="st-time" type="time" dir="ltr" aria-label="وقت التذكير" [value]="t" (change)="setReminderTime(rt.value)">
          }
          <button class="st-switch" type="button" role="switch" [attr.aria-checked]="!!reminder.time()" aria-label="التذكير اليومي"
                  (click)="reminder.time() ? reminderOff() : reminderOn()"></button>
        </div>
      </div>
      <p class="st-note" dir="rtl">
        @if (lockAvailable()) {القفل يُطلب عند فتح التطبيق وبعد 5 دقائق في الخلفية، ولا يغني عن تسجيل الدخول. }
        والتذكير «سجّلت مصاريف اليوم؟» يصل فقط إذا لم تسجّل أي مصروف ذلك اليوم.
      </p>

      <ng-container *appIfService="'SRV-011'">
        <h3 class="st-sec" dir="rtl">رسائل البنك</h3>
        <div class="st-grp">
          <button class="st-row" type="button" (click)="bankSms()">
            <span class="st-ic" style="--c:#2BB673"><app-icon name="message" [size]="16" /></span>
            <span class="st-l">رسائل البنك</span><span class="st-v" dir="rtl">الحساب، والرمز، والكلمات</span><app-icon name="chevL" [size]="16" />
          </button>
        </div>
        <p class="st-note" dir="rtl">اختصار في الآيفون يرسل رسائل البنك، فتصير طلبات تراجعها قبل أن تُضاف.</p>
      </ng-container>

      <h3 class="st-sec" dir="rtl">البيانات</h3>
      <div class="st-grp">
        <button class="st-row" type="button" (click)="exportAll()">
          <span class="st-ic" style="--c:#2F6BE0"><app-icon name="share" [size]="16" /></span>
          <span class="st-l">تصدير كل البيانات</span><span class="st-v">آخر مرة: {{ lastExport() }}</span>
        </button>
        <button class="st-row" type="button" (click)="importFile()">
          <span class="st-ic" style="--c:#2F6BE0"><app-icon name="download" [size]="16" /></span>
          <span class="st-l">استيراد</span><app-icon name="chevL" [size]="16" />
        </button>
        @if (io.canShare()) {
          <button class="st-row" type="button" (click)="io.share()">
            <span class="st-ic" style="--c:#2F6BE0"><app-icon name="share" [size]="16" /></span>
            <span class="st-l">مشاركة الملف</span><app-icon name="chevL" [size]="16" />
          </button>
        }
      </div>
      <p class="st-note" dir="rtl">التصدير يشمل كل الأشخاص (الميزانية والمصاريف) في ملف واحد باسم التاريخ.</p>
      @if (imports().length) {
        <h3 class="st-sec" dir="rtl">سجل الاستيراد</h3>
        <div class="st-grp">
          @for (i of imports(); track i.id) {
            <div class="st-row">
              <span class="st-l" dir="auto">{{ i.fileName || 'ملف' }}</span>
              <span class="st-v" dir="rtl">{{ i.mode === 'MERGE' ? 'دمج' : 'استبدال' }} · <bdi dir="ltr">{{ when(i.createdAt) }}</bdi></span>
            </div>
          }
        </div>
      }

      <h3 class="st-sec" dir="rtl">الحساب</h3>
      <div class="st-grp">
        <div class="st-row">
          <span class="st-ic" style="--c:#566A8E"><app-icon name="mail" [size]="16" /></span>
          <span class="st-l" dir="rtl">البريد</span><bdi class="st-v" dir="ltr">{{ m.email }}</bdi>
        </div>
        <button class="st-row" type="button" (click)="rename()">
          <span class="st-ic" style="--c:#566A8E"><app-icon name="edit" [size]="16" /></span>
          <span class="st-l">الاسم الظاهر</span><span class="st-v" dir="auto">{{ m.displayName || '—' }}</span><app-icon name="chevL" [size]="16" />
        </button>
        <div class="st-row">
          <span class="st-ic" style="--c:#566A8E"><app-icon name="clock" [size]="16" /></span>
          <span class="st-l">المنطقة الزمنية</span>
          <select #tz class="st-select" dir="ltr" aria-label="المنطقة الزمنية" (change)="setZone(tz.value)">
            @for (z of zones(); track z) {
              <option [value]="z" [selected]="z === m.timeZone">{{ z }}</option>
            }
          </select>
        </div>
        <div class="st-row">
          <span class="st-ic" style="--c:#566A8E"><app-icon name="info" [size]="16" /></span>
          <span class="st-l">الإصدار</span><span class="st-v" dir="ltr">{{ version }}</span>
        </div>
        <button class="st-row" type="button" (click)="installHelp()">
          <span class="st-ic" style="--c:#566A8E"><app-icon name="phone" [size]="16" /></span>
          <span class="st-l">تعليمات التثبيت</span><app-icon name="chevL" [size]="16" />
        </button>
        <button class="st-row danger" type="button" (click)="signOut()">
          <span class="st-ic" style="--c:#C73A2E"><app-icon name="logout" [size]="16" /></span>
          <span class="st-l">تسجيل الخروج</span>
        </button>
      </div>
    }
  `,
})
export class SettingsSheet {
  private readonly api = inject(Api);
  private readonly session = inject(SessionStore);
  private readonly auth = inject(SupabaseAuth);
  private readonly dialogs = inject(Dialogs);
  private readonly sheets = inject(Sheets);
  private readonly toasts = inject(Toasts);
  private readonly router = inject(Router);
  protected readonly io = inject(ImportExport);
  protected readonly lock = inject(AppLock);
  protected readonly reminder = inject(DailyReminder);
  protected readonly theme = inject(Theme);
  protected readonly themes: { mode: ThemeMode; label: string }[] = [
    { mode: 'auto', label: 'تلقائي' }, { mode: 'light', label: 'فاتح' }, { mode: 'dark', label: 'داكن' },
  ];
  protected readonly version = environment.version;
  protected readonly lockAvailable = signal(false);
  private readonly budgets = inject(BudgetsStore);
  private readonly expenses = inject(ExpensesStore);
  /** The open person and account, whose cards' photos these rows change */
  protected readonly budgetName = computed(() => this.budgets.current()?.name ?? '');
  protected readonly accountName = computed(() => this.expenses.account()?.name ?? '');

  protected readonly me = this.session.user;
  protected readonly imports = signal<ImportRecord[]>([]);
  protected readonly lastExport = computed(() => {
    const r = this.me()?.exportReminder;
    if (!r || r.never) return 'لم يتم';
    return r.days === 0 ? 'اليوم' : r.days === 1 ? 'أمس' : `قبل ${r.days} يوم`;
  });
  protected readonly zones = computed(() => {
    const all = typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone') : [];
    const current = this.me()?.timeZone;
    return current && !all.includes(current) ? [current, ...all] : all;
  });

  constructor() {
    this.api.imports().then((list) => this.imports.set(list), () => undefined);
    void canLock().then((ok) => this.lockAvailable.set(ok));
  }

  /** Face ID asks right away, so this runs inside the tap. */
  protected async toggleLock(): Promise<void> {
    if (this.lock.enabled()) {
      this.lock.disable();
      this.toasts.show('أُوقف القفل');
      return;
    }
    try {
      await this.lock.enable(this.me()?.email ?? '');
      this.toasts.show('فُعّل قفل Face ID');
    } catch {
      this.toasts.show('لم يُفعَّل القفل.');
    }
  }

  /** The browser asks for notifications here, inside the tap; 21:00 to start, as a default. */
  protected async reminderOn(): Promise<void> {
    const why = this.reminder.unavailable();
    if (why) {
      this.toasts.show(why);
      return;
    }
    try {
      await this.reminder.enable('21:00');
      this.toasts.show('فُعّل التذكير، الساعة 21:00');
    } catch (e) {
      const error = e as Partial<ApiError>;
      this.toasts.show(error?.code && error.message ? error.message : 'لم يُفعَّل التذكير.');
    }
  }

  protected async setReminderTime(time: string): Promise<void> {
    if (!/^\d{2}:\d{2}$/.test(time)) return;
    try {
      await this.reminder.changeTime(time);
      this.toasts.show('حُفظ');
    } catch (e) {
      this.toasts.show((e as ApiError).message);
    }
  }

  protected async reminderOff(): Promise<void> {
    try {
      await this.reminder.disable();
      this.toasts.show('أُوقف التذكير');
    } catch (e) {
      this.toasts.show((e as ApiError).message);
    }
  }

  protected photo(card: 'budget' | 'account'): void {
    const id = card === 'budget' ? this.budgets.currentId() : this.expenses.accountId();
    if (!id) return;
    const photoKey = card === 'budget' ? budgetPhotoKey(id) : accountPhotoKey(id);
    void this.sheets.open(CardPhotoSheet, 'صورة البطاقة', { photoKey });
  }

  /** BANK-SMS.md B-19 */
  protected async bankSms(): Promise<void> {
    const { BankSmsSheet } = await import('./bank-sms-sheet');
    await this.sheets.open(BankSmsSheet, 'رسائل البنك');
  }

  protected installHelp(): void {
    this.sheets.close();
    void this.router.navigateByUrl('/install');
  }

  /** M-SAL's fmtDateTime: YYYY-MM-DD HH:MM on this device. */
  protected when(iso: string): string {
    const d = new Date(iso);
    const p = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
  }

  protected exportAll(): void {
    void this.io.export();
    this.sheets.close();
  }

  /** The picker opens first, while the click still counts. */
  protected importFile(): void {
    this.io.pick();
    this.sheets.close();
  }

  protected async rename(): Promise<void> {
    const name = await this.dialogs.prompt('الاسم الظاهر:', this.me()?.displayName ?? '', 'الاسم الظاهر');
    if (name !== null) await this.save({ displayName: name });
    void this.reopen();
  }

  protected setZone(timeZone: string): Promise<void> {
    return this.save({ timeZone });
  }

  protected async signOut(): Promise<void> {
    this.sheets.close();
    this.session.clear();
    await this.auth.signOut();
    await this.router.navigateByUrl('/login');
  }

  private async save(changes: { displayName?: string; timeZone?: string }): Promise<void> {
    const me = this.me();
    if (!me) return;
    try {
      const saved = await this.api.patchMe({ ...changes, version: me.version });
      this.session.update(saved);
      this.toasts.show('حُفظ');
    } catch (e) {
      this.toasts.show((e as ApiError).message);
    }
  }

  private async reopen(): Promise<void> {
    await this.sheets.open(SettingsSheet, 'البيانات والإعدادات');
  }
}
