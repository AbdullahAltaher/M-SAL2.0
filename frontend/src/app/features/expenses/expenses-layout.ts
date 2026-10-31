import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { ApiError } from '../../core/api/api-error';
import { BudgetsStore } from '../../core/data/budgets-store';
import { shortArabicDate } from '../../core/util/dates';
import { Icon } from '../../shared/components/icon';
import { Toasts } from '../../shared/toast/toast';
import { EntrySheets } from './entry-sheets';
import { ExpensesStore } from './expenses-store';

const TITLES: Record<string, string> = { home: 'مصاريفي', entries: 'المصاريف', stats: 'الإحصاءات' };

/**
 * The Expenses section (M-SAL's renderExp): the title, the cycle chip, the selected screen and
 * the floating bottom bar with the + button.
 */
@Component({
  selector: 'app-expenses-layout',
  imports: [RouterOutlet, RouterLink, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="g-head">
      <h1 class="g-h1" dir="rtl">{{ title() }}</h1>
      @if (subtitle()) {
        <div class="g-sub" dir="auto">{{ subtitle() }}</div>
      }
    </div>

    @if (keys().length) {
      <div class="g-cyc" (touchstart)="touchStart($event)" (touchend)="touchEnd($event)">
        <button class="g-circ" type="button" aria-label="الدورة السابقة" [disabled]="index() <= 0" (click)="go(-1)">
          <app-icon name="chevR" [size]="22" />
        </button>
        <button class="g-cyc-l" type="button" aria-label="تعديل الدورة" (click)="editCycle()">
          @if (range(); as r) {
            <span class="rng"><span dir="rtl">{{ r.start }}</span><span class="sep" aria-hidden="true">←</span><span dir="rtl">{{ r.end }}</span></span>
          } @else {
            <span dir="rtl">بدون دورة</span>
          }
          <span class="g-cyc-pos" dir="rtl">الدورة <bdi dir="ltr">{{ index() + 1 }}</bdi> من <bdi dir="ltr">{{ keys().length }}</bdi></span>
        </button>
        <button class="g-circ" type="button" aria-label="الدورة التالية" [disabled]="index() >= keys().length - 1" (click)="go(1)">
          <app-icon name="chevL" [size]="22" />
        </button>
      </div>
    }

    <router-outlet />

    <nav class="xbar" aria-label="التنقل في المصاريف">
      <a class="xi" routerLink="/expenses/home" aria-label="الرئيسية" [attr.aria-current]="view() === 'home' ? 'page' : null">
        <app-icon name="home" [size]="24" />
      </a>
      <a class="xi" routerLink="/expenses/entries" aria-label="المصاريف" [attr.aria-current]="view() === 'entries' ? 'page' : null">
        <app-icon name="list" [size]="24" />
      </a>
      <button class="fab" type="button" aria-label="إضافة مصروف" (click)="sheets.add()"><app-icon name="plus" [size]="30" [sw]="2.2" /></button>
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
  protected readonly subtitle = computed(() => {
    const name = this.store.account()?.name ?? '';
    return this.view() === 'home' && name === TITLES['home'] ? '' : name;
  });
  protected readonly keys = computed(() => this.store.home()?.cycleKeys ?? []);
  protected readonly index = computed(() => this.keys().indexOf(this.store.selectedKey() ?? ''));
  protected readonly range = computed(() => {
    const c = this.store.home()?.selected.cycle;
    return c ? { start: shortArabicDate(c.startDate, true), end: c.lastDay ? shortArabicDate(c.lastDay, true) : 'الآن' } : null;
  });

  private sx: number | null = null;
  private sy = 0;

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

  protected go(d: number): void {
    const key = this.keys()[this.index() + d];
    if (key) this.store.setCycle(key);
  }

  protected editCycle(): void {
    const cycle = this.store.home()?.selected.cycle;
    if (cycle) void this.sheets.editCycle(cycle.id);
  }

  protected touchStart(e: TouchEvent): void {
    this.sx = e.touches[0].clientX;
    this.sy = e.touches[0].clientY;
  }

  /** A clear sideways flick changes cycle (M-SAL's swipe). */
  protected touchEnd(e: TouchEvent): void {
    if (this.sx === null) return;
    const dx = e.changedTouches[0].clientX - this.sx;
    const dy = e.changedTouches[0].clientY - this.sy;
    this.sx = null;
    if (Math.abs(dx) > 50 && Math.abs(dy) < 40) this.go(dx > 0 ? -1 : 1);
  }

  private viewOf(url: string): string {
    return url.includes('/entries') ? 'entries' : url.includes('/stats') ? 'stats' : 'home';
  }
}
