import { ChangeDetectionStrategy, Component, ElementRef, OnInit, computed, effect, inject, input, signal, viewChild } from '@angular/core';

import { Api } from '../../core/api/api';
import { ApiError } from '../../core/api/api-error';
import { Cycle, Entry, EntryType, QuickPicks } from '../../core/api/models';
import { SessionStore } from '../../core/session/session-store';
import { parseEntryAmount, toLatinDigits } from '../../core/util/amounts';
import { addDays, dayLabel, fullArabicDate, parseDate, shortArabicDate } from '../../core/util/dates';
import { AED } from '../../core/util/money';
import { Icon } from '../../shared/components/icon';
import { Collapse } from '../../shared/directives/collapse';
import { Sheets } from '../../shared/sheet/sheets';
import { Toasts } from '../../shared/toast/toast';
import { flyCoin } from './coin-flight';
import { ExpensesStore } from './expenses-store';

/**
 * SH-01: a new entry or an edit (SCREENS.md 4.1, M-SAL's openEntrySheet), with fewer steps
 * (DESIGN.md D-20): one-tap shortcuts that save at once (R-ENT-14), the amount typed on a keypad
 * inside the sheet so the iPhone keyboard never covers it, the categories in one row by use
 * (R-ENT-15), and the description picked from suggestions. The keyboard opens only for "+ اكتب
 * وصف". The description stays required (R-ENT-02).
 */
@Component({
  selector: 'app-entry-sheet',
  imports: [Icon, Collapse],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './entry-sheet.html',
})
export class EntrySheet implements OnInit {
  private readonly api = inject(Api);
  private readonly store = inject(ExpensesStore);
  private readonly sheets = inject(Sheets);
  private readonly toasts = inject(Toasts);

  readonly editing = input<Entry | null>(null);
  readonly presetType = input<EntryType>('EXPENSE');

  protected readonly unit = AED;
  protected readonly today = inject(SessionStore).today() ?? '';
  protected readonly yesterday = this.today ? addDays(this.today, -1) : '';

  protected readonly type = signal<EntryType>('EXPENSE');
  protected readonly amount = signal('');
  protected readonly categoryId = signal<string | null>(null);
  protected readonly name = signal('');
  protected readonly date = signal(this.today);
  protected readonly accountId = signal<string | null>(null);
  protected readonly hint = signal('');
  protected readonly bad = signal(false);
  /** The description field has the keyboard: the keypad steps aside */
  protected readonly typing = signal(false);
  protected readonly datesOpen = signal(false);
  private readonly quick = signal<QuickPicks | null>(null);
  protected readonly keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '0', 'back'];
  protected readonly suggestions = signal<string[]>([]);
  protected readonly cycles = signal<Cycle[]>([]);
  protected readonly busy = signal(false);

  protected readonly multi = computed(() => this.store.activeAccounts().length > 1);
  protected readonly accountChoices = computed(() =>
    this.store.accounts().filter((a) => !a.isArchived || a.id === this.accountId()));
  /** By use, then the person's order (R-ENT-15); a hidden one only when the edited entry has it (4.1 point 8) */
  protected readonly orderedCategories = computed(() => {
    const shown = this.store.categories().filter((c) => !c.isHidden || c.id === this.editing()?.categoryId);
    const order = this.quick()?.categoryOrder ?? [];
    const rank = (id: string) => (order.indexOf(id) < 0 ? order.length : order.indexOf(id));
    return [...shown].sort((a, b) => rank(a.id) - rank(b.id) || a.sortOrder - b.sortOrder);
  });
  /** Only when adding an expense (R-ENT-14) */
  protected readonly shortcuts = computed(() =>
    this.editing() || this.type() !== 'EXPENSE' ? [] : this.quick()?.shortcuts ?? []);
  protected readonly dateLabel = computed(() => {
    const d = this.date();
    return d === this.today ? 'اليوم' : d === this.yesterday ? 'أمس' : this.shortDate(d);
  });
  protected readonly canSave = computed(() =>
    parseEntryAmount(this.amount()) !== null && !!this.categoryId() && this.name().trim() !== '');
  protected readonly amountWidth = computed(() => Math.max(2, this.amount().length + 0.6) + 'ch');
  protected readonly otherDate = computed(() => this.date() !== this.today && this.date() !== this.yesterday);
  protected readonly note = computed(() => this.dateNote());

  private readonly amountField = viewChild<ElementRef<HTMLInputElement>>('amountField');
  private readonly nameField = viewChild<ElementRef<HTMLInputElement>>('nameField');

  constructor() {
    effect(() => {
      const id = this.accountId();
      if (id) void this.api.cycles(id).then((r) => this.cycles.set(r.cycles));
    });
  }

  ngOnInit(): void {
    const e = this.editing();
    if (e) {
      this.type.set(e.type);
      this.amount.set(String(Number(e.amount)));
      this.categoryId.set(e.categoryId);
      this.name.set(e.name || this.store.catOf(e.categoryId).name);
      this.date.set(e.entryDate);
      this.accountId.set(e.accountId);
      void this.loadSuggestions();
    } else {
      this.type.set(this.presetType());
      this.accountId.set(this.store.accountId());
    }
    this.setTitle();
    const budgetId = this.store.budgetId();
    if (budgetId) void this.api.quick(budgetId).then((q) => this.quick.set(q)).catch(() => undefined);
  }

  protected setType(t: EntryType): void {
    this.type.set(t);
    this.setTitle();
  }

  /** The keypad: digits, one point, two decimals at most, and delete. */
  protected press(key: string): void {
    const now = this.amount();
    let next: string;
    if (key === 'back') next = now.slice(0, -1);
    else if (key === '.') next = now.includes('.') ? now : (now || '0') + '.';
    else if (now.includes('.') && now.split('.')[1].length >= 2) return;
    else if (!now.includes('.') && now.replace(/^0+/, '').length >= 9) return;
    else next = now === '0' ? key : now + key;
    this.amount.set(next);
    this.hint.set('');
  }

  /** Arabic digits become Latin while typing (4.1 point 2), from a hardware keyboard. */
  protected onAmount(value: string, field: HTMLInputElement): void {
    const n = toLatinDigits(value).replace(/٫/g, '.').replace(/،/g, ',');
    if (n !== value) field.value = n;
    this.amount.set(n);
    this.hint.set('');
  }

  /** Enter in the amount moves to the description once a category is chosen (4.1 point 3). */
  protected amountEnter(event: Event): void {
    event.preventDefault();
    if (this.categoryId()) this.nameField()?.nativeElement.focus();
    else (event.target as HTMLInputElement).blur();
  }

  protected pick(id: string): void {
    if (id === this.categoryId()) return;
    this.categoryId.set(id);
    this.hint.set('');
    void this.loadSuggestions(true);
  }

  protected onName(value: string): void {
    this.name.set(value);
    this.bad.set(false);
    this.hint.set('');
  }

  protected useSuggestion(n: string): void {
    this.onName(n);
  }

  /** "18.00" shows as "18" on a shortcut */
  protected plain(amount: string): string {
    return String(Number(amount));
  }

  /** One tap saves it, then "تراجع" removes it for good (R-ENT-14). */
  protected async saveShortcut(s: QuickPicks['shortcuts'][number]): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true);
    try {
      const saved = await this.api.createEntry(this.store.budgetId() ?? '', {
        type: 'EXPENSE', amount: s.amount, name: s.name, categoryId: s.categoryId,
        entryDate: this.date(), accountId: this.accountId() ?? '',
      });
      this.afterSave(saved, null, true);
    } catch (error) {
      this.hint.set((error as ApiError).message);
    } finally {
      this.busy.set(false);
    }
  }

  protected setDate(d: string): void {
    if (d) this.date.set(d);
  }

  protected openPicker(input: HTMLInputElement): void {
    try {
      input.showPicker?.();
    } catch {
      // The native tap already opens it
    }
  }

  protected shortDate(d: string): string {
    return parseDate(d) ? shortArabicDate(d) : 'تاريخ آخر';
  }

  /** Checked in the order of R-ENT-02, with M-SAL's messages. */
  protected async commit(): Promise<void> {
    if (this.busy()) return;
    const amount = parseEntryAmount(this.amount());
    if (amount === null) {
      this.hint.set('أدخل مبلغًا صحيحًا أكبر من صفر.');
      this.amountField()?.nativeElement.focus();
      return;
    }
    const categoryId = this.categoryId();
    if (!categoryId) {
      this.hint.set('اختر تصنيفًا.');
      return;
    }
    const name = this.name().trim();
    if (!name) {
      this.hint.set('اكتب وصف');
      // Restart the shake each time (4.1 point 5)
      this.bad.set(false);
      requestAnimationFrame(() => this.bad.set(true));
      this.nameField()?.nativeElement.focus();
      return;
    }
    if (!parseDate(this.date())) {
      this.hint.set('تاريخ غير صالح.');
      return;
    }
    const body = { type: this.type(), amount, name, categoryId, entryDate: this.date(), accountId: this.accountId() ?? '' };
    const editing = this.editing();
    this.busy.set(true);
    try {
      const saved = editing
        ? await this.api.patchEntry(editing.id, { ...body, version: editing.version })
        : await this.api.createEntry(this.store.budgetId() ?? '', body);
      this.afterSave(saved, editing, false);
    } catch (error) {
      this.hint.set((error as ApiError).message);
    } finally {
      this.busy.set(false);
    }
  }

  private afterSave(saved: Entry, editing: Entry | null, undoable: boolean): void {
    this.store.jumpTo(saved);
    this.sheets.close(saved);
    if (!editing) {
      this.store.markFresh(saved.id);
      // a coin from + to the account card, then its numbers count down (DESIGN.md D-10, D-24)
      if (saved.type === 'EXPENSE') flyCoin(document.querySelector('.xbar .fab'), document.querySelector('app-home-view .g-hero'));
    }
    const accName = this.store.accounts().find((a) => a.id === saved.accountId)?.name;
    const label = editing ? 'حُفظ التعديل: ' : saved.type === 'INCOME' ? 'أُضيف دخل: ' : 'أُضيف: ';
    this.toasts.show(label, {
      money: saved.amount,
      suffix: this.multi() && accName ? ' · ' + accName : undefined,
      action: undoable ? { label: 'تراجع', run: () => void this.api.purgeEntry(saved.id).then(() => this.store.refresh()) } : undefined,
    });
  }

  private setTitle(): void {
    this.sheets.setTitle(this.editing() ? 'تعديل' : this.type() === 'INCOME' ? 'دخل جديد' : 'مصروف جديد');
  }

  /** With a new category and no description yet, a single suggestion is taken at once (D-20). */
  private async loadSuggestions(pickSingle = false): Promise<void> {
    const categoryId = this.categoryId();
    const budgetId = this.store.budgetId();
    if (!categoryId || !budgetId) return;
    try {
      const list = (await this.api.suggestions(budgetId, categoryId)).suggestions;
      if (categoryId !== this.categoryId()) return;
      this.suggestions.set(list);
      if (pickSingle && list.length === 1 && !this.name().trim()) this.onName(list[0]);
    } catch {
      this.suggestions.set([]);
    }
  }

  /** Where the entry will land (R-ENT-04). */
  private dateNote(): { text: string; cls: string } {
    const d = this.date();
    if (!parseDate(d)) return { text: 'تاريخ غير صالح، اختر تاريخًا صحيحًا.', cls: 'bad' };
    const cycles = [...this.cycles()].sort((a, b) => (a.startDate < b.startDate ? -1 : 1));
    const owner = [...cycles].reverse().find((c) => c.startDate <= d);
    let where: string;
    if (owner) where = 'ضمن الدورة: ' + shortArabicDate(owner.startDate) + ' ← ' + (owner.lastDay ? shortArabicDate(owner.lastDay) : 'الآن');
    else where = cycles.length ? 'قبل أول دورة، ستظهر في «بدون دورة»' : 'لا توجد دورات بعد، ستظهر في «بدون دورة»';
    if (d === this.today) return { text: 'التاريخ: اليوم · ' + where, cls: '' };
    const day = dayLabel(d, this.today);
    const full = fullArabicDate(d);
    return { text: 'سيُسجَّل بتاريخ: ' + day + (day === full ? '' : ' (' + full + ')') + ' · ' + where, cls: 'warn' };
  }
}
