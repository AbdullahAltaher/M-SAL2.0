import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, effect, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { ApiError } from '../../core/api/api-error';
import { SupabaseAuth } from '../../core/auth/supabase-auth';
import { BudgetsStore } from '../../core/data/budgets-store';
import { AppLock } from '../../core/pwa/app-lock';
import { AppStatus } from '../../core/pwa/app-status';
import { SessionStore } from '../../core/session/session-store';
import { FloatingWallet } from '../../shared/components/floating-wallet';
import { Icon } from '../../shared/components/icon';
import { Dialogs } from '../../shared/sheet/dialogs';
import { SheetHost } from '../../shared/sheet/sheet-host';
import { ToastHost, Toasts } from '../../shared/toast/toast';
import { LedgerStore } from '../budget/ledger-store';
import { LockScreen } from '../lock/lock-screen';
import { ImportExport } from '../settings/import-export';
import { LastTab } from './last-tab';

const NF = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 });

/**
 * The signed-in frame: the glass background, the shared header, the sheet host, the toast host
 * and the keyboard proxy. The header (DESIGN.md D-08, D-14) shows only the person's name, which
 * opens the people menu, the saved mark in the budget, and the section switch (D-03), whose pill
 * slides while the page slides with it (D-32, app.config.ts).
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, SheetHost, ToastHost, LockScreen, Icon, FloatingWallet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shell.html',
  host: { '(document:pointerdown)': 'outside($event)' },
})
export class Shell {
  protected readonly budgets = inject(BudgetsStore);
  protected readonly ledger = inject(LedgerStore);
  private readonly session = inject(SessionStore);
  private readonly auth = inject(SupabaseAuth);
  protected readonly status = inject(AppStatus);
  protected readonly lock = inject(AppLock);
  private readonly dialogs = inject(Dialogs);
  private readonly toasts = inject(Toasts);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly expenses = signal(false);
  /** The admin screen belongs to neither tab, and hides the budget-only header parts */
  protected readonly admin = signal(false);
  /** The Expenses home screen, the only one with the floating wallet (D-09) */
  protected readonly home = signal(false);
  protected readonly menuOpen = signal(false);
  protected readonly shrunk = signal(false);
  protected readonly savedOn = signal(false);
  protected readonly final = computed(() => this.ledger.totals());
  protected readonly io = inject(ImportExport);
  /** M-SAL's export banner (R-IO-01): until an export, or closed for this visit */
  protected readonly reminderClosed = signal(false);
  protected readonly exportReminder = computed(() => {
    const r = this.session.user()?.exportReminder;
    return r?.due && !this.reminderClosed() ? r : null;
  });

  constructor() {
    void this.budgets.load().catch((e: ApiError) => this.toasts.show(e.message));
    this.applySection(this.router.url);
    const sub = this.router.events.pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => this.applySection(e.urlAfterRedirects));

    // The header shrinks after a little scrolling (M-SAL's topHeader.shrunk)
    const onScroll = () => this.shrunk.set(window.scrollY > 12);
    window.addEventListener('scroll', onScroll, { passive: true });

    // Nothing in the background moves while the app is not on screen
    const onVisibility = () => this.document.documentElement.classList.toggle('x-paused', this.document.hidden);
    this.document.addEventListener('visibilitychange', onVisibility);

    // "محفوظ" lights up briefly after each save
    let timer: ReturnType<typeof setTimeout> | null = null;
    effect(() => {
      if (!this.budgets.savedAt()) return;
      this.savedOn.set(true);
      if (timer) clearTimeout(timer);
      timer = setTimeout(() => this.savedOn.set(false), 1200);
    });

    inject(DestroyRef).onDestroy(() => {
      sub.unsubscribe();
      window.removeEventListener('scroll', onScroll);
      this.document.removeEventListener('visibilitychange', onVisibility);
      this.document.documentElement.classList.remove('x-paused');
    });
  }

  /** Face ID failed: the lock goes, and the email sign-in decides (SCREENS.md S-03). */
  protected async signOutFromLock(): Promise<void> {
    this.lock.disable();
    this.session.clear();
    await this.auth.signOut();
    await this.router.navigateByUrl('/login');
  }

  protected format(n: number): string {
    return NF.format(n);
  }

  protected show(tab: 'budget' | 'expenses'): void {
    void this.router.navigateByUrl(tab === 'budget' ? '/budget' : '/expenses/home');
  }

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  protected closeMenu(): void {
    this.menuOpen.set(false);
  }

  /** A tap anywhere outside the name and its menu closes the menu. */
  protected outside(event: Event): void {
    if (!this.menuOpen()) return;
    const row = this.host.nativeElement.querySelector('.top-row');
    if (!row?.contains(event.target as Node)) this.closeMenu();
  }

  protected async pick(id: string): Promise<void> {
    this.closeMenu();
    if (id === this.budgets.currentId()) return;
    await this.ledger.flushAll();
    await this.run(() => this.budgets.select(id));
  }

  protected async add(): Promise<void> {
    this.closeMenu();
    const name = await this.dialogs.prompt('اسم الشخص الجديد:', '', 'شخص جديد');
    if (name) await this.run(() => this.ledger.flushAll().then(() => this.budgets.create(name)));
  }

  protected async rename(): Promise<void> {
    this.closeMenu();
    const current = this.budgets.current();
    if (!current) return;
    const name = await this.dialogs.prompt('الاسم الجديد:', current.name, 'إعادة تسمية');
    if (name) await this.run(async () => {
      await this.ledger.flushAll();
      await this.budgets.rename(current.id, name);
      await this.ledger.load();
    });
  }

  /** The last budget cannot be deleted (R-BUD-06). */
  protected async remove(): Promise<void> {
    this.closeMenu();
    const current = this.budgets.current();
    if (!current) return;
    if (this.budgets.list().length <= 1) {
      this.toasts.show('ما يمكن حذف آخر شخص. أضف شخص ثاني أول.');
      return;
    }
    if (!(await this.dialogs.confirm(`حذف «${current.name}» وكل بياناته نهائيًا؟ الأفضل تسوي تصدير قبل.`, 'حذف الشخص', 'حذف'))) return;
    await this.run(() => this.budgets.remove(current.id));
  }

  private async run(action: () => Promise<unknown>): Promise<void> {
    try {
      await action();
    } catch (e) {
      this.toasts.show((e as ApiError).message);
    }
  }

  private applySection(url: string): void {
    const expenses = url.startsWith('/expenses');
    this.expenses.set(expenses);
    this.admin.set(url.startsWith('/admin'));
    this.home.set(url.startsWith('/expenses/home'));
    this.menuOpen.set(false);
    if (url.startsWith('/budget') || expenses) LastTab.remember(expenses ? 'expenses' : 'budget');
  }
}
