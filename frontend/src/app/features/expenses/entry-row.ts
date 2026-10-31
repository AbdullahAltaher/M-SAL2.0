import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';

import { Entry } from '../../core/api/models';
import { dayLabel } from '../../core/util/dates';
import { Icon } from '../../shared/components/icon';
import { MoneyView } from '../../shared/components/money';
import { EntrySheets } from './entry-sheets';
import { ExpensesStore } from './expenses-store';

/**
 * One entry (M-SAL's gRow): emoji tile, the description with the amount under it, then edit,
 * copy-to-today and delete. Every target is 44px.
 */
@Component({
  selector: 'li[app-entry-row]',
  imports: [Icon, MoneyView],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'g-row' },
  template: `
    <span class="g-tile" aria-hidden="true">{{ cat().emoji }}</span>
    <div class="g-rmain">
      <div class="g-rname" dir="auto">{{ name() }}</div>
      <div class="g-rsub">
        <span class="g-ramt" [class.inc]="income()">@if (income()) {+}<app-money [value]="entry().amount" /></span>
        @if (showDate()) {
          <span class="g-rdate" dir="rtl">{{ date() }}</span>
        } @else if (entry().name && entry().name !== cat().name) {
          <span class="g-rcat" dir="rtl">{{ cat().name }}</span>
        }
      </div>
    </div>
    <div class="g-racts">
      <button class="g-ib" type="button" [attr.aria-label]="'تعديل ' + name()" (click)="sheets.edit(entry())">
        <app-icon name="edit" [size]="18" />
      </button>
      <button class="g-ib" type="button" [attr.aria-label]="'نسخ ' + name() + ' بتاريخ اليوم'" (click)="store.duplicate(entry())">
        <app-icon name="copy" [size]="18" />
      </button>
      <button class="g-ib del" type="button" [attr.aria-label]="'حذف ' + name()" (click)="store.remove(entry())">
        <app-icon name="trash" [size]="18" />
      </button>
    </div>
  `,
})
export class EntryRow {
  protected readonly store = inject(ExpensesStore);
  protected readonly sheets = inject(EntrySheets);

  readonly entry = input.required<Entry>();
  readonly showDate = input(false);
  readonly today = input('');

  protected readonly cat = computed(() => this.store.catOf(this.entry().categoryId));
  protected readonly name = computed(() => this.entry().name || this.cat().name);
  protected readonly income = computed(() => this.entry().type === 'INCOME');
  protected readonly date = computed(() => dayLabel(this.entry().entryDate, this.today()));
}
