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
import { Dialogs } from '../../shared/sheet/dialogs';
import { Sheets } from '../../shared/sheet/sheets';
import { Toasts } from '../../shared/toast/toast';
import { ImportExport } from './import-export';

/**
 * SH-08: data and settings (SCREENS.md 4.2): the account, the data (export, import and its
 * history, in place of M-SAL's storage status and snapshots), the Face ID lock, the daily
 * reminder, and the app (version, install steps, sign-out).
 */
@Component({
  selector: 'app-settings-sheet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (me(); as m) {
      <h3 class="sub-h" dir="rtl">الحساب</h3>
      <ul class="m-list">
        <li class="m-row"><span class="m-name" dir="rtl">البريد</span><bdi class="m-sub" dir="ltr">{{ m.email }}</bdi></li>
        <li class="m-row">
          <span class="m-name" dir="rtl">الاسم الظاهر</span>
          <span class="m-sub" dir="auto">{{ m.displayName || '—' }}</span>
          <button class="ab" type="button" aria-label="تعديل الاسم الظاهر" (click)="rename()">✎</button>
        </li>
        <li class="m-row">
          <span class="m-name" dir="rtl">المنطقة الزمنية</span>
          <select #tz class="field" dir="ltr" aria-label="المنطقة الزمنية" (change)="setZone(tz.value)">
            @for (z of zones(); track z) {
              <option [value]="z" [selected]="z === m.timeZone">{{ z }}</option>
            }
          </select>
        </li>
      </ul>
      <h3 class="sub-h" dir="rtl">البيانات</h3>
      <div class="totals">
        <div class="tline"><span class="lbl" dir="rtl">آخر تصدير</span><span class="val" dir="auto">{{ lastExport() }}</span></div>
      </div>
      <div class="toolbar">
        <button class="pill primary" type="button" (click)="exportAll()">تصدير كل البيانات</button>
        <button class="pill" type="button" (click)="importFile()">استيراد</button>
        @if (io.canShare()) {
          <button class="pill" type="button" (click)="io.share()">مشاركة الملف</button>
        }
      </div>
      <p class="hint" dir="rtl">التصدير يشمل كل الأشخاص (الميزانية والمصاريف) في ملف واحد باسم التاريخ.</p>
      <h3 class="sub-h" dir="rtl">سجل الاستيراد</h3>
      @if (imports().length) {
        <ul class="m-list">
          @for (i of imports(); track i.id) {
            <li class="m-row">
              <div class="m-main">
                <div class="m-name" dir="auto">{{ i.fileName || 'ملف' }}</div>
                <div class="m-sub" dir="rtl">{{ i.mode === 'MERGE' ? 'دمج' : 'استبدال' }} · <bdi dir="ltr">{{ when(i.createdAt) }}</bdi></div>
              </div>
            </li>
          }
        </ul>
      } @else {
        <p class="x-none" dir="rtl">لا يوجد استيراد بعد.</p>
      }
      <h3 class="sub-h" dir="rtl">الأمان</h3>
      @if (lockAvailable()) {
        <ul class="m-list">
          <li class="m-row">
            <span class="m-name" dir="rtl">قفل Face ID</span>
            <span class="m-sub" dir="rtl">{{ lock.enabled() ? 'مفعّل' : 'متوقف' }}</span>
            <button class="pill sm" type="button" (click)="toggleLock()">{{ lock.enabled() ? 'إيقاف' : 'تفعيل' }}</button>
          </li>
        </ul>
        <p class="hint" dir="rtl">قفل خصوصية على هذا الجهاز: يُطلب عند فتح التطبيق وبعد 5 دقائق في الخلفية. لا يغني عن تسجيل الدخول.</p>
      } @else {
        <p class="x-none" dir="rtl">قفل Face ID غير متاح على هذا الجهاز.</p>
      }
      <h3 class="sub-h" dir="rtl">التذكير</h3>
      <ul class="m-list">
        <li class="m-row">
          <span class="m-name" dir="rtl">تذكير يومي بتسجيل المصاريف</span>
          @if (reminder.time(); as t) {
            <input #rt class="field" type="time" dir="ltr" aria-label="وقت التذكير" [value]="t" (change)="setReminderTime(rt.value)">
            <button class="pill sm" type="button" (click)="reminderOff()">إيقاف</button>
          } @else {
            <button class="pill sm" type="button" (click)="reminderOn()">تفعيل</button>
          }
        </li>
      </ul>
      <p class="hint" dir="rtl">إشعار «سجّلت مصاريف اليوم؟» في الوقت الذي تختاره، فقط إذا لم تسجّل أي مصروف ذلك اليوم.</p>
      <h3 class="sub-h" dir="rtl">التطبيق</h3>
      <div class="totals">
        <div class="tline"><span class="lbl" dir="rtl">الإصدار</span><span class="val" dir="ltr">{{ version }}</span></div>
      </div>
      <div class="toolbar">
        <button class="pill" type="button" (click)="installHelp()">تعليمات التثبيت</button>
        <button class="pill" type="button" (click)="signOut()">تسجيل الخروج</button>
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
  protected readonly version = environment.version;
  protected readonly lockAvailable = signal(false);

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
