import { ChangeDetectionStrategy, Component, ElementRef, afterRenderEffect, computed, inject, input, output } from '@angular/core';

import { Day, Home } from '../../core/api/models';
import { AR_WEEKDAYS, addDays, parseDate, weekdayIndex } from '../../core/util/dates';

export interface StripDay {
  date: string;
  /** "أحد", the first word of the weekday, or "اليوم" */
  label: string;
  num: number;
  future: boolean;
  spent: boolean;
}

/**
 * The days of the selected cycle (R-ENT-16): from its start to its last day, an open cycle up to
 * the day before the next salary (else today). Days after today show but cannot be chosen; a dot
 * marks the days with an expense.
 */
export function stripDays(cycle: NonNullable<Home['selected']['cycle']>, today: string, days: Day[]): StripDay[] {
  const end = cycle.lastDay
    ?? (cycle.expectedNextSalaryDate && cycle.expectedNextSalaryDate > today ? addDays(cycle.expectedNextSalaryDate, -1) : today);
  const spent = new Set(days.filter((d) => Number(d.spent) > 0).map((d) => d.date));
  const list: StripDay[] = [];
  for (let d = cycle.startDate; d <= end && list.length < 62; d = addDays(d, 1)) {
    list.push({
      date: d,
      label: d === today ? 'اليوم' : AR_WEEKDAYS[weekdayIndex(d)].replace(/^ال/, ''),
      num: parseDate(d)!.d,
      future: d > today,
      spent: spent.has(d),
    });
  }
  return list;
}

/** The days strip (DESIGN.md D-19): one row that scrolls sideways, the chosen day in the middle. */
@Component({
  selector: 'app-day-strip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="ds" role="listbox" aria-label="أيام الدورة" dir="rtl">
      @for (d of list(); track d.date) {
        <button class="ds-d" type="button" role="option" [attr.aria-selected]="d.date === selected()"
                [class.today]="d.date === today()" [class.future]="d.future" [disabled]="d.future"
                [attr.aria-label]="d.label + ' ' + d.num + (d.spent ? '، فيه مصاريف' : '')" (click)="pick.emit(d.date)">
          <span class="ds-w">{{ d.label }}</span>
          <span class="ds-n">{{ d.num }}</span>
          <i class="ds-dot" [class.on]="d.spent" aria-hidden="true"></i>
        </button>
      }
    </div>
  `,
})
export class DayStrip {
  readonly cycle = input.required<NonNullable<Home['selected']['cycle']>>();
  readonly today = input.required<string>();
  readonly days = input<Day[]>([]);
  readonly selected = input.required<string>();
  readonly pick = output<string>();

  protected readonly list = computed(() => stripDays(this.cycle(), this.today(), this.days()));
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  constructor() {
    // Keep the chosen day in view, moving only the strip, never the page
    afterRenderEffect(() => {
      this.selected();
      const row = this.host.querySelector<HTMLElement>('.ds');
      const on = this.host.querySelector<HTMLElement>('.ds-d[aria-selected="true"]');
      if (!row || !on) return;
      row.scrollTo({ left: on.offsetLeft - row.clientWidth / 2 + on.offsetWidth / 2, behavior: 'smooth' });
    });
  }
}
