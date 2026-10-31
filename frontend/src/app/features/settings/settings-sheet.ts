import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';

import { Api } from '../../core/api/api';
import { ApiError } from '../../core/api/api-error';
import { SupabaseAuth } from '../../core/auth/supabase-auth';
import { SessionStore } from '../../core/session/session-store';
import { Dialogs } from '../../shared/sheet/dialogs';
import { Sheets } from '../../shared/sheet/sheets';
import { Toasts } from '../../shared/toast/toast';

/**
 * SH-08: data and settings (SCREENS.md 4.2). The account part and sign-out are here now;
 * export and import come with SRV-007 (phase 6), Face ID and the reminder with phase 7.
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

  protected readonly me = this.session.user;
  protected readonly zones = computed(() => {
    const all = typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone') : [];
    const current = this.me()?.timeZone;
    return current && !all.includes(current) ? [current, ...all] : all;
  });

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
