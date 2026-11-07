import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';

import { Api } from '../../core/api/api';
import { ApiError } from '../../core/api/api-error';
import { Day } from '../../core/api/models';
import { dayHeading } from '../../core/util/dates';
import { Icon } from '../../shared/components/icon';
import { MoneyView } from '../../shared/components/money';
import { Collapse } from '../../shared/directives/collapse';
import { Toasts } from '../../shared/toast/toast';
import { EntryRow } from './entry-row';
import { ExpensesStore } from './expenses-store';

/**
 * S-21: all entries of the cycle by day, with search and the category chips behind a filter
 * button (M-SAL's renderEntriesView, R-ENT-10, R-ENT-11), as clean rows (DESIGN.md D-21).
 * "Repeat last" is gone: the one-tap shortcuts in the entry sheet replace it (D-25). Search
 * runs on the server.
 */
@Component({
  selector: 'app-entries-view',
  imports: [Icon, MoneyView, EntryRow, Collapse],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="x-search">
      <div class="g-search">
        <span class="si" aria-hidden="true"><app-icon name="search" [size]="20" /></span>
        <input #q class="field" type="search" dir="rtl" enterkeyhint="search" autocomplete="off" placeholder="ابحث بالاسم أو الفئة"
               aria-label="بحث" [value]="query()" (input)="onQuery(q.value)">
      </div>
      <button class="x-filter" type="button" [class.on]="!!cat()" [attr.aria-expanded]="filterOpen()"
              [attr.aria-label]="cat() ? 'التصفية: ' + catName() : 'تصفية بالفئة'" (click)="filterOpen.set(!filterOpen())">
        <app-icon name="sliders" [size]="20" />
      </button>
    </div>

    <div class="g-col" [appCollapse]="filterOpen()">
      <div class="g-chips" role="group" aria-label="تصفية بالفئة">
        <button class="g-fchip" type="button" [attr.aria-pressed]="!cat()" (click)="cat.set('')"><span dir="rtl">الكل</span></button>
        @for (c of chips(); track c.id) {
          <button class="g-fchip" type="button" [attr.aria-pressed]="cat() === c.id" (click)="cat.set(cat() === c.id ? '' : c.id)">
            <span class="e" aria-hidden="true">{{ c.emoji }}</span><span dir="rtl">{{ c.name }}</span>
          </button>
        }
      </div>
    </div>

    <div class="g-daystack">
      @if (days().length) {
        @for (d of days(); track d.date) {
          <section class="x-day" [attr.aria-label]="label(d.date)">
            <div class="x-dayhead">
              <h3 class="x-dayname" dir="rtl">{{ label(d.date) }}</h3>
              <span class="g-daytot"><app-money [value]="d.spent" />@if (+d.extra > 0) {<span class="plus">+<app-money [value]="d.extra" /></span>}</span>
            </div>
            <ul class="x-rows">
              @for (e of d.entries; track e.id) {
                <li app-entry-row [entry]="e" [today]="today()"></li>
              }
            </ul>
          </section>
        }
      } @else if (loaded()) {
        <p class="g-none" dir="rtl">{{ all().length ? 'لا توجد نتائج مطابقة.' : 'لا توجد مصاريف في هذه الدورة بعد. اضغط + لإضافة مصروف.' }}</p>
      }
    </div>
  `,
})
export class EntriesView {
  protected readonly store = inject(ExpensesStore);
  private readonly api = inject(Api);
  private readonly toasts = inject(Toasts);

  protected readonly query = signal('');
  private readonly debounced = signal('');
  protected readonly cat = signal('');
  /** The cycle's days without any filter */
  protected readonly all = signal<Day[]>([]);
  private readonly filtered = signal<Day[] | null>(null);
  protected readonly filterOpen = signal(false);
  protected readonly loaded = signal(false);
  protected readonly today = computed(() => this.store.home()?.today ?? '');

  protected readonly days = computed(() => this.filtered() ?? this.all());
  /** Hidden categories show only when used in this cycle or selected (S-21) */
  protected readonly chips = computed(() => {
    const used = new Set(this.all().flatMap((d) => d.entries.map((e) => e.categoryId)));
    return this.store.categories().filter((c) => !c.isHidden || used.has(c.id) || c.id === this.cat());
  });

  private timer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    // The whole cycle, again after any change
    effect(() => {
      this.store.changed();
      const accountId = this.store.accountId();
      const key = this.store.selectedKey();
      if (!accountId || !key) return;
      void this.api.entries(accountId, key)
        .then((list) => {
          this.all.set(list.days);
          this.loaded.set(true);
        })
        .catch((e: ApiError) => this.toasts.show(e.message));
    });
    // The filtered list when there is a search or a category
    effect(() => {
      this.store.changed();
      const q = this.debounced().trim();
      const cat = this.cat();
      const accountId = this.store.accountId();
      const key = this.store.selectedKey();
      if (!accountId || !key || (!q && !cat)) {
        this.filtered.set(null);
        return;
      }
      void this.api.entries(accountId, key, q, cat || undefined)
        .then((list) => this.filtered.set(list.days))
        .catch((e: ApiError) => this.toasts.show(e.message));
    });
  }

  protected onQuery(value: string): void {
    this.query.set(value);
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => this.debounced.set(value), 300);
  }

  protected catName(): string {
    return this.store.catOf(this.cat()).name;
  }

  /** "اليوم · الخميس 6 نوفمبر", "أمس · …", or "الثلاثاء 4 نوفمبر" (D-21) */
  protected label(date: string): string {
    return dayHeading(date, this.today());
  }
}
