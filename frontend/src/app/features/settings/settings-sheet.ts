import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { Api } from '../../core/api/api';
import { ApiError } from '../../core/api/api-error';
import { ImportRecord } from '../../core/api/models';
import { SupabaseAuth } from '../../core/auth/supabase-auth';
import { SessionStore } from '../../core/session/session-store';
import { Dialogs } from '../../shared/sheet/dialogs';
import { Sheets } from '../../shared/sheet/sheets';
import { Toasts } from '../../shared/toast/toast';
import { ImportExport } from './import-export';

/**
 * SH-08: data and settings (SCREENS.md 4.2): the account, the data (export, import and its
 * history, in place of M-SAL's storage status and snapshots) and sign-out. Face ID and the
 * reminder come with phase 7.
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
      <h3 class="sub-h" dir="rtl">التطبيق</h3>
      <div class="toolbar">
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
