import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { Api } from '../../core/api/api';
import { ApiError } from '../../core/api/api-error';
import { AdminUser, AuditPage, Invitation, ServiceInfo } from '../../core/api/models';
import { SessionStore } from '../../core/session/session-store';
import { Dialogs } from '../../shared/sheet/dialogs';
import { Toasts } from '../../shared/toast/toast';

type Tab = 'users' | 'invitations' | 'services' | 'audit';

const STATUS: Record<string, string> = {
  PENDING: 'معلّقة', ACCEPTED: 'مقبولة', REVOKED: 'ملغاة', EXPIRED: 'منتهية', ACTIVE: 'نشط', DISABLED: 'موقوف',
};

/**
 * S-30: administration, for SRV-009 only (SCREENS.md section 3). Paper style and simple: users,
 * invitations, services and the activity log. No financial data is ever shown (API.md SRV-009).
 */
@Component({
  selector: 'app-admin-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-page.html',
})
export class AdminPage {
  private readonly api = inject(Api);
  private readonly dialogs = inject(Dialogs);
  private readonly toasts = inject(Toasts);
  private readonly router = inject(Router);
  protected readonly me = inject(SessionStore).user;

  protected readonly tab = signal<Tab>((inject(ActivatedRoute).snapshot.queryParamMap.get('tab') as Tab) || 'users');
  protected readonly users = signal<AdminUser[]>([]);
  protected readonly invitations = signal<Invitation[]>([]);
  protected readonly services = signal<ServiceInfo[]>([]);
  protected readonly audit = signal<AuditPage | null>(null);
  protected readonly auditService = signal('');
  protected readonly page = signal(0);
  protected readonly email = signal('');

  constructor() {
    effect(() => {
      const tab = this.tab();
      void this.router.navigate([], { queryParams: { tab }, replaceUrl: true });
      void this.load(tab);
    });
  }

  protected status(s: string): string {
    return STATUS[s] ?? s;
  }

  protected pages(a: AuditPage): number {
    return Math.max(1, Math.ceil(a.total / a.size));
  }

  protected when(iso: string | null): string {
    return iso ? iso.replace('T', ' ').slice(0, 16) : '—';
  }

  protected async toggleUser(u: AdminUser): Promise<void> {
    await this.run(() => this.api.patchAdminUser(u.id, { status: u.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE', version: u.version }));
  }

  protected async toggleAdmin(u: AdminUser): Promise<void> {
    const roles = u.roles.includes('ADMIN') ? u.roles.filter((r) => r !== 'ADMIN') : [...u.roles, 'ADMIN'];
    await this.run(() => this.api.patchAdminUser(u.id, { roles, version: u.version }));
  }

  protected async invite(): Promise<void> {
    const email = this.email().trim();
    if (!email) return;
    await this.run(async () => {
      await this.api.invite(email);
      this.email.set('');
      // No email is sent (API.md 2.3): the admin tells the friend
      this.toasts.show('تمت الدعوة. أخبر صديقك يفتح التطبيق ويدخل بإيميله.');
    });
  }

  protected async revoke(i: Invitation): Promise<void> {
    if (!(await this.dialogs.confirm(`إلغاء دعوة «${i.email}»؟`, 'إلغاء الدعوة', 'إلغاء الدعوة'))) return;
    await this.run(() => this.api.revokeInvitation(i.id));
  }

  protected async toggleService(s: ServiceInfo): Promise<void> {
    await this.run(() => this.api.patchService(s.code, !s.enabled));
  }

  protected go(page: number): void {
    this.page.set(page);
    void this.load('audit');
  }

  protected filter(service: string): void {
    this.auditService.set(service);
    this.go(0);
  }

  private async run(action: () => Promise<unknown>): Promise<void> {
    try {
      await action();
    } catch (e) {
      this.toasts.show((e as ApiError).message);
    }
    await this.load(this.tab());
  }

  private async load(tab: Tab): Promise<void> {
    try {
      if (tab === 'users') this.users.set(await this.api.adminUsers());
      if (tab === 'invitations') this.invitations.set(await this.api.invitations());
      if (tab === 'services' || tab === 'audit') this.services.set(await this.api.services());
      if (tab === 'audit') this.audit.set(await this.api.auditLog({ service: this.auditService() || undefined, page: this.page(), size: 50 }));
    } catch (e) {
      this.toasts.show((e as ApiError).message);
    }
  }
}
