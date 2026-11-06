import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { ApiError } from '../../core/api/api-error';
import { BudgetsStore } from '../../core/data/budgets-store';
import { weekdayDate } from '../../core/util/dates';
import { Icon } from '../../shared/components/icon';
import { Toasts } from '../../shared/toast/toast';
import { EntrySheets } from './entry-sheets';
import { ExpensesStore } from './expenses-store';

const TITLES: Record<string, string> = { entries: 'المصاريف', stats: 'الإحصاءات' };

/**
 * The Expenses section (M-SAL's renderExp): the selected screen and the floating bottom bar
 * with the + button. The cycle moved to the header (cycle-switch.ts, DESIGN.md D-18); the home
 * screen has no title, its account card says where you are, and the other two keep theirs.
 */
@Component({
  selector: 'app-expenses-layout',
  imports: [RouterOutlet, RouterLink, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (title(); as t) {
      <div class="g-head">
        <h1 class="g-h1" dir="rtl">{{ t }}</h1>
        @if (subtitle()) {
          <div class="g-sub" dir="auto">{{ subtitle() }}</div>
        }
      </div>
    }

    <router-outlet />
    <!-- room under the last item ("وصل الراتب" on the home screen) for the floating bar -->
    <div class="xbar-space" aria-hidden="true"></div>

    <nav class="xbar" aria-label="التنقل في المصاريف">
      <a class="xi" routerLink="/expenses/home" aria-label="الرئيسية" [attr.aria-current]="view() === 'home' ? 'page' : null">
        <app-icon name="home" [size]="24" />
      </a>
      <a class="xi" routerLink="/expenses/entries" aria-label="المصاريف" [attr.aria-current]="view() === 'entries' ? 'page' : null">
        <app-icon name="list" [size]="24" />
      </a>
      @if (addsTo(); as day) {
        <span class="fab-hint" dir="rtl" aria-hidden="true">+ يضيف إلى {{ day }}</span>
      }
      <button class="fab" type="button" [attr.aria-label]="addsTo() ? 'إضافة مصروف إلى ' + addsTo() : 'إضافة مصروف'" (click)="sheets.add()"><app-icon name="plus" [size]="30" [sw]="2.2" /></button>
      <span class="xgap" aria-hidden="true"></span>
      <a class="xi" routerLink="/expenses/stats" aria-label="الإحصاءات" [attr.aria-current]="view() === 'stats' ? 'page' : null">
        <app-icon name="chart" [size]="24" />
      </a>
      <button class="xi" type="button" aria-label="المزيد" (click)="sheets.more()"><app-icon name="more" [size]="24" [sw]="3" /></button>
    </nav>
  `,
})
export class ExpensesLayout {
  protected readonly store = inject(ExpensesStore);
  protected readonly sheets = inject(EntrySheets);
  private readonly budgets = inject(BudgetsStore);
  private readonly toasts = inject(Toasts);
  private readonly router = inject(Router);

  protected readonly view = signal(this.viewOf(this.router.url));
  protected readonly title = computed(() => TITLES[this.view()] ?? '');
  /** "+ يضيف إلى X" while another day is chosen (R-ENT-16) */
  protected readonly addsTo = computed(() => {
    const day = this.store.day();
    return day && day !== this.store.home()?.today ? weekdayDate(day) : null;
  });
  /** The account, when there is more than one */
  protected readonly subtitle = computed(() => (this.store.activeAccounts().length > 1 ? this.store.account()?.name ?? '' : ''));

  constructor() {
    const sub = this.router.events.pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => this.view.set(this.viewOf(e.urlAfterRedirects)));
    inject(DestroyRef).onDestroy(() => sub.unsubscribe());

    // Load the open budget's accounts and categories, then the home data on every change
    effect(() => {
      this.budgets.currentId();
      void this.store.ensureLoaded().then(() => this.store.refresh()).catch((e: ApiError) => this.toasts.show(e.message));
    });
    effect(() => {
      this.store.changed();
      this.store.accountId();
      void this.store.loadHome().catch((e: ApiError) => this.toasts.show(e.message));
    });
  }

  private viewOf(url: string): string {
    return url.includes('/entries') ? 'entries' : url.includes('/stats') ? 'stats' : 'home';
  }
}
