import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { shortArabicDate } from '../../core/util/dates';
import { Icon } from '../../shared/components/icon';
import { EntrySheets } from './entry-sheets';
import { ExpensesStore } from './expenses-store';

/**
 * The cycle, small, in the header beside the person's name (DESIGN.md D-18, D-19): the arrows
 * go to the cycle before or after, and the dates open the cycle's editing (SH-02), as the
 * old cycle strip did.
 */
@Component({
  selector: 'app-cycle-switch',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (keys().length) {
      <div class="cyc" role="group" aria-label="الدورة">
        <button class="cyc-b" type="button" aria-label="الدورة السابقة" [disabled]="index() <= 0" (click)="go(-1)">
          <app-icon name="chevR" [size]="18" [sw]="2.2" />
        </button>
        <button class="cyc-l" type="button" [attr.aria-label]="'الدورة ' + label() + '، تعديل'" (click)="edit()">
          <span dir="rtl">{{ label() }}</span>
        </button>
        <button class="cyc-b" type="button" aria-label="الدورة التالية" [disabled]="index() >= keys().length - 1" (click)="go(1)">
          <app-icon name="chevL" [size]="18" [sw]="2.2" />
        </button>
      </div>
    }
  `,
})
export class CycleSwitch {
  private readonly store = inject(ExpensesStore);
  private readonly sheets = inject(EntrySheets);

  protected readonly keys = computed(() => this.store.home()?.cycleKeys ?? []);
  protected readonly index = computed(() => this.keys().indexOf(this.store.selectedKey() ?? ''));
  protected readonly label = computed(() => {
    const c = this.store.home()?.selected.cycle;
    if (!c) return 'بدون دورة';
    return shortArabicDate(c.startDate) + ' ← ' + (c.lastDay ? shortArabicDate(c.lastDay) : 'الآن');
  });

  protected go(d: number): void {
    const key = this.keys()[this.index() + d];
    if (key) this.store.setCycle(key);
  }

  protected edit(): void {
    const cycle = this.store.home()?.selected.cycle;
    if (cycle) void this.sheets.editCycle(cycle.id);
  }
}
