import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, input, signal } from '@angular/core';

import { ApiError } from '../../core/api/api-error';
import { Entry } from '../../core/api/models';
import { sliceVar } from '../../core/util/chart';
import { dayLabel } from '../../core/util/dates';
import { Icon } from '../../shared/components/icon';
import { MoneyView } from '../../shared/components/money';
import { Toasts } from '../../shared/toast/toast';
import { EntrySheets } from './entry-sheets';
import { ExpensesStore } from './expenses-store';

/** How far a row is dragged before letting go deletes or copies it */
const ARM = 96;

/**
 * One entry as a clean row (DESIGN.md D-21): the category's emoji on its colour, the description
 * with the category under it, and the amount. A tap edits it. Dragged toward the left edge it is
 * deleted (with "تراجع"), toward the right edge it is copied to today: the app is right-to-left,
 * so delete sits at the trailing edge as on the iPhone. Screen readers and keyboards get the
 * same two actions as buttons.
 */
@Component({
  selector: 'li[app-entry-row]',
  imports: [Icon, MoneyView],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'x-row',
    '[class.fresh]': 'store.fresh() === entry().id',
    '[class.dragging]': 'dragging()',
    '[attr.data-swipe]': 'side()',
    '[class.armed]': 'armed()',
    '(pointerdown)': 'down($event)',
    '(pointermove)': 'move($event)',
    '(pointerup)': 'up()',
    '(pointercancel)': 'cancel()',
  },
  template: `
    <div class="x-act del" aria-hidden="true"><app-icon name="trash" [size]="20" /><span>حذف</span></div>
    <div class="x-act copy" aria-hidden="true"><app-icon name="copy" [size]="20" /><span>نسخ لليوم</span></div>
    <button class="x-front" type="button" [style.transform]="offset() ? 'translateX(' + offset() + 'px)' : null"
            [attr.aria-label]="'تعديل ' + name()" (click)="open()">
      <span class="x-tile" aria-hidden="true" [style.--c]="color()">{{ cat().emoji }}</span>
      <span class="x-main">
        <span class="x-name" dir="auto">{{ name() }}</span>
        <span class="x-sub" dir="rtl">{{ sub() }}</span>
      </span>
      <span class="x-amt" [class.inc]="income()">@if (income()) {+}<app-money [value]="entry().amount" /></span>
    </button>
    <span class="x-vh">
      <button type="button" (click)="store.duplicate(entry())">{{ 'نسخ ' + name() + ' بتاريخ اليوم' }}</button>
      <button type="button" (click)="store.remove(entry())">{{ 'حذف ' + name() }}</button>
    </span>
  `,
})
export class EntryRow {
  protected readonly store = inject(ExpensesStore);
  protected readonly sheets = inject(EntrySheets);
  private readonly toasts = inject(Toasts);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  readonly entry = input.required<Entry>();
  readonly showDate = input(false);
  readonly today = input('');

  protected readonly cat = computed(() => this.store.catOf(this.entry().categoryId));
  protected readonly name = computed(() => this.entry().name || this.cat().name);
  protected readonly income = computed(() => this.entry().type === 'INCOME');
  /** Under the description: the category, and the day when the list is not by day */
  protected readonly sub = computed(() => {
    const parts = [this.income() ? 'دخل' : this.cat().name];
    if (this.showDate()) parts.push(dayLabel(this.entry().entryDate, this.today()));
    return parts.join(' · ');
  });
  /** Each category keeps one colour, in the order of the categories list */
  protected readonly color = computed(() => {
    const i = this.store.categories().findIndex((c) => c.id === this.entry().categoryId);
    return sliceVar(Math.max(0, i));
  });

  protected readonly offset = signal(0);
  protected readonly dragging = signal(false);
  protected readonly side = computed(() => (this.offset() > 0 ? 'del' : this.offset() < 0 ? 'copy' : null));
  protected readonly armed = computed(() => Math.abs(this.offset()) >= ARM);

  private start: { x: number; y: number; id: number } | null = null;
  private axis: 'x' | 'y' | null = null;
  private swiped = false;

  protected open(): void {
    if (this.swiped) return;
    void this.sheets.edit(this.entry());
  }

  protected down(e: PointerEvent): void {
    if (e.pointerType === 'mouse' && e.button !== 0) return;
    this.start = { x: e.clientX, y: e.clientY, id: e.pointerId };
    this.axis = null;
    this.swiped = false;
  }

  protected move(e: PointerEvent): void {
    if (!this.start || e.pointerId !== this.start.id) return;
    const dx = e.clientX - this.start.x;
    const dy = e.clientY - this.start.y;
    if (!this.axis) {
      if (Math.abs(dx) > 8 && Math.abs(dx) > Math.abs(dy)) {
        this.axis = 'x';
        this.dragging.set(true);
        this.host.setPointerCapture(e.pointerId);
      } else if (Math.abs(dy) > 8) {
        this.axis = 'y';
        this.start = null;
        return;
      }
    }
    if (this.axis !== 'x') return;
    // past the arming point it follows the finger more slowly
    const far = Math.abs(dx) > ARM ? Math.sign(dx) * (ARM + (Math.abs(dx) - ARM) * 0.35) : dx;
    this.offset.set(Math.round(far));
  }

  protected up(): void {
    if (this.axis !== 'x') {
      this.start = null;
      return;
    }
    this.swiped = true;
    const side = this.side();
    const armed = this.armed();
    this.dragging.set(false);
    this.start = null;
    this.axis = null;
    if (armed && side === 'del') {
      this.offset.set(this.host.offsetWidth);
      setTimeout(() => this.store.remove(this.entry()).catch((err: ApiError) => {
        this.offset.set(0);
        this.toasts.show(err.message);
      }), 180);
    } else {
      this.offset.set(0);
      if (armed && side === 'copy') this.store.duplicate(this.entry()).catch((err: ApiError) => this.toasts.show(err.message));
    }
    // the click that follows a drag is not a tap
    setTimeout(() => (this.swiped = false), 50);
  }

  protected cancel(): void {
    this.start = null;
    this.axis = null;
    this.dragging.set(false);
    this.offset.set(0);
  }
}
