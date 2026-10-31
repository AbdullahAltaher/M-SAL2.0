import { ChangeDetectionStrategy, Component, ElementRef, OnInit, afterNextRender, inject, input, signal, viewChild } from '@angular/core';

import { Api } from '../../core/api/api';
import { ApiError } from '../../core/api/api-error';
import { Cycle } from '../../core/api/models';
import { parseAllowance } from '../../core/util/amounts';
import { parseDate, shortArabicDate } from '../../core/util/dates';
import { Dialogs } from '../../shared/sheet/dialogs';
import { Sheets } from '../../shared/sheet/sheets';
import { Toasts } from '../../shared/toast/toast';
import { ExpensesStore } from './expenses-store';

/**
 * SH-02: "salary arrived" or editing a cycle (M-SAL's openCycleSheet, R-CYC-02, R-CYC-03,
 * R-CYC-07, R-CYC-08). Changing the date shows how many entries will move.
 */
@Component({
  selector: 'app-cycle-sheet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <label class="flabel" dir="rtl">
      <span>تاريخ وصول الراتب (بداية الدورة)</span>
      <input #dateField class="field" type="date" dir="ltr" aria-label="تاريخ وصول الراتب (بداية الدورة)"
             [value]="start()" (change)="onStart(dateField.value)">
    </label>
    <label class="flabel" dir="rtl">
      <span>المبلغ المخصَّص للمصاريف</span>
      <input #allowField class="field" type="text" inputmode="decimal" dir="ltr" autocomplete="off" placeholder="0"
             aria-label="المبلغ المخصَّص للمصاريف" [value]="allowanceText()" (input)="allowanceText.set(allowField.value)">
    </label>
    <label class="flabel" dir="rtl">
      <span>تاريخ الراتب القادم المتوقع (اختياري)</span>
      <input #nextField class="field" type="date" dir="ltr" aria-label="تاريخ الراتب القادم المتوقع (اختياري)"
             [value]="next()" (change)="next.set(nextField.value)">
    </label>
    <button class="chip" type="button" (click)="next.set('')">مسح الراتب القادم</button>
    <p class="hint" dir="rtl">الدورة تنتهي عند بداية الدورة التي بعدها. المبلغ المخصَّص منفصل تمامًا عن دفتر الميزانية.</p>
    @if (impact()) {
      <div class="x-note" dir="rtl">{{ impact() }}</div>
    }
    @if (error()) {
      <div class="x-note bad" role="alert" dir="rtl">{{ error() }}</div>
    }
    <div class="toolbar">
      <button class="pill primary" type="button" (click)="save()">حفظ</button>
      @if (cycle()) {
        <button class="pill" type="button" (click)="remove()">حذف الدورة</button>
      }
    </div>
  `,
})
export class CycleSheet implements OnInit {
  private readonly api = inject(Api);
  private readonly store = inject(ExpensesStore);
  private readonly sheets = inject(Sheets);
  private readonly dialogs = inject(Dialogs);
  private readonly toasts = inject(Toasts);

  readonly cycle = input<Cycle | null>(null);
  readonly date = input('');
  readonly allowance = input('');

  protected readonly start = signal('');
  protected readonly allowanceText = signal('');
  protected readonly next = signal('');
  protected readonly impact = signal('');
  protected readonly error = signal('');
  private readonly dateField = viewChild<ElementRef<HTMLInputElement>>('dateField');

  constructor() {
    afterNextRender(() => this.dateField()?.nativeElement.focus({ preventScroll: true }));
  }

  ngOnInit(): void {
    const c = this.cycle();
    this.start.set(c ? c.startDate : this.date());
    const a = c ? c.allowance : this.allowance();
    this.allowanceText.set(a && Number(a) ? String(Number(a)) : c ? '0' : '');
    this.next.set(c?.expectedNextSalaryDate ?? '');
  }

  /** R-CYC-07: tell before saving how many entries will change cycle. */
  protected async onStart(value: string): Promise<void> {
    this.start.set(value);
    this.impact.set('');
    const c = this.cycle();
    if (!c || !parseDate(value) || value === c.startDate) return;
    try {
      const n = (await this.api.moveImpact(c.id, value)).movedEntries;
      this.impact.set(n ? `بهذا التغيير ستنتقل ${n} من المصاريف بين الدورات تلقائيًا.` : 'لن تنتقل أي مصاريف بين الدورات.');
    } catch {
      // The save itself reports any problem
    }
  }

  protected async save(): Promise<void> {
    this.error.set('');
    const start = this.start();
    if (!parseDate(start)) return this.error.set('اختر تاريخًا صحيحًا.');
    const allowance = parseAllowance(this.allowanceText());
    if (allowance === null) return this.error.set('أدخل مبلغًا صحيحًا للمخصَّص (يمكن أن يكون 0).');
    const next = this.next();
    if (next && (!parseDate(next) || next <= start)) return this.error.set('تاريخ الراتب القادم يجب أن يكون بعد بداية الدورة.');
    const body = { startDate: start, allowance, expectedNextSalaryDate: next || null };
    const editing = this.cycle();
    try {
      const saved = editing
        ? await this.api.patchCycle(editing.id, { ...body, version: editing.version })
        : await this.api.createCycle(this.store.accountId() ?? '', body);
      this.store.setCycle(saved.id);
      this.sheets.close(saved);
      this.toasts.show(editing ? 'حُفظت الدورة' : 'بدأت دورة جديدة من ' + shortArabicDate(start, true));
    } catch (e) {
      this.error.set((e as ApiError).message);
    }
  }

  /** R-CYC-08: the entries move to the previous cycle or to "no cycle". */
  protected async remove(): Promise<void> {
    const c = this.cycle();
    if (!c) return;
    const ok = await this.dialogs.confirm(
      'حذف هذه الدورة؟ مصاريفها لن تُحذف، بل ستنتقل تلقائيًا إلى الدورة السابقة (أو «بدون دورة»).', 'حذف الدورة', 'حذف');
    if (!ok) return;
    try {
      await this.api.deleteCycle(c.id);
      this.store.setCycle('latest');
      this.toasts.show('حُذفت الدورة، والمصاريف محفوظة');
    } catch (e) {
      this.toasts.show((e as ApiError).message);
    }
  }
}
