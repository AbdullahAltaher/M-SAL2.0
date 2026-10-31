import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';

import { Api } from '../../core/api/api';
import { ApiError } from '../../core/api/api-error';
import { Day, Entry } from '../../core/api/models';
import { dayLabel } from '../../core/util/dates';
import { Icon } from '../../shared/components/icon';
import { MoneyView } from '../../shared/components/money';
import { Toasts } from '../../shared/toast/toast';
import { EntryRow } from './entry-row';
import { ExpensesStore } from './expenses-store';

/**
 * S-21: all entries of the cycle by day, with search, one-tap category chips and "repeat last"
 * (M-SAL's renderEntriesView, R-ENT-08, R-ENT-10, R-ENT-11). Search runs on the server.
 */
@Component({
  selector: 'app-entries-view',
  imports: [Icon, MoneyView, EntryRow],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="g-search">
      <span class="si" aria-hidden="true"><app-icon name="search" [size]="20" /></span>
      <input #q class="field" type="search" dir="rtl" enterkeyhint="search" autocomplete="off" placeholder="ابحث بالاسم أو الفئة"
             aria-label="بحث" [value]="query()" (input)="onQuery(q.value)">
    </div>

    <div class="g-chips" role="group" aria-label="تصفية بالفئة">
      <button class="g-fchip" type="button" [attr.aria-pressed]="!cat()" (click)="cat.set('')"><span dir="rtl">الكل</span></button>
      @for (c of chips(); track c.id) {
        <button class="g-fchip" type="button" [attr.aria-pressed]="cat() === c.id" (click)="cat.set(cat() === c.id ? '' : c.id)">
          <span class="e" aria-hidden="true">{{ c.emoji }}</span><span dir="rtl">{{ c.name }}</span>
        </button>
      }
    </div>

    @if (last(); as l) {
      <button class="g-repeat" type="button" [attr.aria-label]="'كرر آخر مصروف بتاريخ اليوم: ' + nameOf(l)" (click)="store.duplicate(l)">
        <app-icon name="repeat" [size]="20" />
        <span class="lb" dir="rtl">كرر:</span>
        <span class="nm" dir="auto">{{ nameOf(l) }}</span>
        <span class="dash" aria-hidden="true">—</span>
        <app-money [value]="l.amount" />
      </button>
    }

    <div class="g-daystack">
      @if (days().length) {
        @for (d of days(); track d.date) {
          <section class="g-card g-tight">
            <div class="g-dayhead">
              <h3 class="g-dayname" dir="rtl">{{ label(d.date) }}</h3>
              <span class="g-daytot"><app-money [value]="d.spent" />@if (+d.extra > 0) {<span class="plus">+<app-money [value]="d.extra" /></span>}</span>
            </div>
            <ul class="g-rows">
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
  protected readonly last = signal<Entry | null>(null);
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
    // The whole cycle and "repeat last", again after any change
    effect(() => {
      this.store.changed();
      const accountId = this.store.accountId();
      const key = this.store.selectedKey();
      if (!accountId || !key) return;
      void Promise.all([this.api.entries(accountId, key), this.api.lastExpense(accountId)])
        .then(([list, last]) => {
          this.all.set(list.days);
          this.last.set(last);
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

  protected nameOf(e: Entry): string {
    return e.name || this.store.catOf(e.categoryId).name;
  }

  protected label(date: string): string {
    return dayLabel(date, this.today());
  }
}
