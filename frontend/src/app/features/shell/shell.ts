import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { ApiError } from '../../core/api/api-error';
import { BudgetsStore } from '../../core/data/budgets-store';
import { SessionStore } from '../../core/session/session-store';
import { Dialogs } from '../../shared/sheet/dialogs';
import { SheetHost } from '../../shared/sheet/sheet-host';
import { ToastHost, Toasts } from '../../shared/toast/toast';
import { LedgerStore } from '../budget/ledger-store';
import { ImportExport } from '../settings/import-export';
import { LastTab } from './last-tab';

const ADD_VALUE = '__add__';
const NF = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 });

/**
 * The signed-in frame, with M-SAL's markup (index.html lines 715-790): the glass background,
 * the shared header (people menu, saved mark, month, remaining, tabs), the sheet host, the
 * toast host and the keyboard proxy. The Expenses section adds body.x-on, as in M-SAL.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, SheetHost, ToastHost],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shell.html',
})
export class Shell {
  protected readonly budgets = inject(BudgetsStore);
  protected readonly ledger = inject(LedgerStore);
  private readonly session = inject(SessionStore);
  private readonly dialogs = inject(Dialogs);
  private readonly toasts = inject(Toasts);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);

  protected readonly addValue = ADD_VALUE;
  protected readonly expenses = signal(false);
  /** The admin screen belongs to neither tab, and hides the budget-only header parts */
  protected readonly admin = signal(false);
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

  private readonly themeMeta = [...this.document.querySelectorAll<HTMLMetaElement>('meta[name="theme-color"]')];
  private readonly themeOriginal = this.themeMeta.map((m) => m.content);

  constructor() {
    void this.budgets.load().catch((e: ApiError) => this.toasts.show(e.message));
    this.applySection(this.router.url);
    const sub = this.router.events.pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => this.applySection(e.urlAfterRedirects));

    // The header shrinks after a little scrolling (M-SAL's topHeader.shrunk)
    const onScroll = () => this.shrunk.set(window.scrollY > 12);
    window.addEventListener('scroll', onScroll, { passive: true });

    // "محفوظ ✓" lights up briefly after each save
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
      this.document.body.classList.remove('x-on');
      this.syncThemeColor(false);
    });
  }

  protected format(n: number): string {
    return NF.format(n);
  }

  protected show(tab: 'budget' | 'expenses'): void {
    void this.router.navigateByUrl(tab === 'budget' ? '/budget' : '/expenses/home');
  }

  protected async onSelect(select: HTMLSelectElement): Promise<void> {
    if (select.value === ADD_VALUE) {
      select.value = this.budgets.currentId() ?? '';
      const name = await this.dialogs.prompt('اسم الشخص الجديد:', '', 'شخص جديد');
      if (name) await this.run(() => this.ledger.flushAll().then(() => this.budgets.create(name)));
      return;
    }
    await this.ledger.flushAll();
    await this.run(() => this.budgets.select(select.value));
  }

  protected async rename(): Promise<void> {
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
    this.document.body.classList.toggle('x-on', expenses);
    this.syncThemeColor(expenses);
    if (url.startsWith('/budget') || expenses) LastTab.remember(expenses ? 'expenses' : 'budget');
  }

  /** The status bar follows the Expenses background (M-SAL's syncThemeColor). */
  private syncThemeColor(on: boolean): void {
    const light = matchMedia?.('(prefers-color-scheme: light)').matches;
    this.themeMeta.forEach((m, i) => (m.content = on ? (light ? '#E3EBF8' : '#0B1426') : this.themeOriginal[i]));
  }
}
