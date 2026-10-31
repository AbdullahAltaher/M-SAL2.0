import { ChangeDetectionStrategy, Component, ElementRef, OnInit, afterNextRender, computed, inject, signal, viewChild } from '@angular/core';

import { Api } from '../../core/api/api';
import { ApiError } from '../../core/api/api-error';
import { Entry } from '../../core/api/models';
import { IfService } from '../../core/permissions/if-service';
import { shortArabicDate } from '../../core/util/dates';
import { Icon } from '../../shared/components/icon';
import { MoneyView } from '../../shared/components/money';
import { Dialogs } from '../../shared/sheet/dialogs';
import { Sheets } from '../../shared/sheet/sheets';
import { Toasts } from '../../shared/toast/toast';
import { EntrySheets } from './entry-sheets';
import { ExpensesStore } from './expenses-store';

/** M-SAL's EMOJI_CHOICES (index.html line 3347). */
export const EMOJI_CHOICES = ['🍔', '🍕', '🍣', '🥗', '🍎', '🥖', '🍰', '🍫', '☕', '🧋', '🍺', '🛒', '🛍️', '👕', '👟', '👜', '💄', '💍', '⛽', '🚗', '🚌', '🚕', '✈️', '🏨', '🏖️', '🏠', '🔑', '🧾', '💡', '🔌', '💧', '📱', '💻', '🌐', '🎮', '🎬', '🎵', '📚', '🎓', '✏️', '🧸', '👶', '🍼', '🎁', '🎉', '🤝', '🕌', '❤️', '💊', '🩺', '🦷', '🧼', '🪥', '✂️', '🐕', '🐈', '🐟', '🌱', '🔧', '🧰', '💼', '💰', '📦', '🎯', '⚽', '🏋️', '🧳', '🚿', '🛏️'];

/** SH-03: the "more" menu (M-SAL's openMoreMenu), with the admin screen for admins only. */
@Component({
  selector: 'app-more-sheet',
  imports: [Icon, IfService],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (row of rows(); track row.text) {
      <button class="g-mrow" type="button" (click)="row.run()">
        <span class="ic" aria-hidden="true"><app-icon [name]="row.icon" [size]="22" /></span>
        <span class="tx" dir="rtl">{{ row.text }}</span>
        @if (row.sub) {
          <span class="sub"><bdi dir="ltr">{{ row.sub }}</bdi></span>
        }
        <span class="ch" aria-hidden="true"><app-icon name="chevL" [size]="20" /></span>
      </button>
    }
    <button *appIfService="'SRV-009'" class="g-mrow" type="button" (click)="open.admin()">
      <span class="ic" aria-hidden="true"><app-icon name="sliders" [size]="22" /></span>
      <span class="tx" dir="rtl">الإدارة</span>
      <span class="ch" aria-hidden="true"><app-icon name="chevL" [size]="20" /></span>
    </button>
  `,
})
export class MoreSheet implements OnInit {
  protected readonly open = inject(EntrySheets);
  private readonly api = inject(Api);
  private readonly store = inject(ExpensesStore);
  private readonly deleted = signal(0);

  protected readonly rows = computed(() => {
    const cycle = this.store.home()?.selected.cycle;
    return [
      { icon: 'wallet', text: 'الحسابات', sub: '', run: () => this.open.accounts() },
      { icon: 'grid', text: 'الفئات', sub: '', run: () => this.open.categories() },
      { icon: 'trash', text: 'المحذوفات', sub: this.deleted() ? String(this.deleted()) : '', run: () => this.open.deleted() },
      { icon: 'calendar', text: cycle ? 'تعديل الدورة' : 'إضافة دورة', sub: '', run: () => this.open.cycleOrNew() },
      { icon: 'receipt', text: 'الإيصال', sub: '', run: () => this.open.receipt() },
      { icon: 'sliders', text: 'البيانات والإعدادات', sub: '', run: () => this.open.settings() },
    ];
  });

  ngOnInit(): void {
    const budgetId = this.store.budgetId();
    if (budgetId) void this.api.deletedEntries(budgetId).then((list) => this.deleted.set(list.length));
  }
}

/** SH-04: accounts (M-SAL's openAccountsSheet): rename, archive and restore, add. */
@Component({
  selector: 'app-accounts-sheet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="m-list">
      @for (a of store.accounts(); track a.id) {
        <li class="m-row" [class.off]="a.isArchived">
          <span class="m-name" dir="auto">{{ a.name }}{{ a.isArchived ? ' (مؤرشف)' : '' }}</span>
          <button class="ab" type="button" [attr.aria-label]="'إعادة تسمية ' + a.name" (click)="rename(a.id, a.name, a.version)">✎</button>
          <button class="chip" type="button" (click)="toggle(a.id, a.name, a.isArchived, a.version)">{{ a.isArchived ? 'استعادة' : 'أرشفة' }}</button>
        </li>
      }
    </ul>
    <div class="toolbar"><button class="pill primary" type="button" (click)="open.addAccount()">+ حساب</button></div>
  `,
})
export class AccountsSheet {
  protected readonly store = inject(ExpensesStore);
  protected readonly open = inject(EntrySheets);
  private readonly api = inject(Api);
  private readonly dialogs = inject(Dialogs);
  private readonly toasts = inject(Toasts);

  protected async rename(id: string, name: string, version: number): Promise<void> {
    const n = await this.dialogs.prompt('الاسم الجديد:', name, 'إعادة تسمية');
    if (n) await this.save(() => this.api.patchAccount(id, { name: n, version }));
    void this.open.accounts();
  }

  protected async toggle(id: string, name: string, archived: boolean, version: number): Promise<void> {
    if (!archived) {
      if (this.store.activeAccounts().length <= 1) {
        this.toasts.show('لا يمكن أرشفة آخر حساب نشط.');
        return;
      }
      const ok = await this.dialogs.confirm(`أرشفة «${name}»؟ يُخفى من القوائم وتبقى بياناته محفوظة ويمكن استعادته.`, 'أرشفة', 'أرشفة');
      if (!ok) {
        void this.open.accounts();
        return;
      }
    }
    await this.save(() => this.api.patchAccount(id, { isArchived: !archived, version }));
    if (!archived && this.store.accountId() === id) {
      const next = this.store.activeAccounts().find((a) => a.id !== id);
      if (next) await this.store.switchAccount(next.id);
    }
    void this.open.accounts();
  }

  private async save(call: () => Promise<unknown>): Promise<void> {
    try {
      await call();
      await this.store.reloadLists();
    } catch (e) {
      this.toasts.show((e as ApiError).message);
    }
  }
}

/** SH-05: categories (M-SAL's openCategoriesSheet): order, rename, hide and show, add. Never delete. */
@Component({
  selector: 'app-categories-sheet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="hint" dir="rtl">الفئات لا تُحذف، فقط تُخفى، حتى تبقى مصاريفك القديمة مرتبطة بها.</p>
    <ul class="m-list">
      @for (c of store.categories(); track c.id; let i = $index, last = $last) {
        <li class="m-row" [class.off]="c.isHidden">
          <span class="m-emoji" aria-hidden="true">{{ c.emoji }}</span>
          <span class="m-name" dir="auto">{{ c.name }}{{ c.isHidden ? ' (مخفية)' : '' }}</span>
          <button class="ab" type="button" [attr.aria-label]="'تحريك ' + c.name + ' للأعلى'" [disabled]="i === 0" (click)="move(i, -1)">↑</button>
          <button class="ab" type="button" [attr.aria-label]="'تحريك ' + c.name + ' للأسفل'" [disabled]="last" (click)="move(i, 1)">↓</button>
          <button class="ab" type="button" [attr.aria-label]="'إعادة تسمية ' + c.name" (click)="rename(c.id, c.name, c.version)">✎</button>
          <button class="chip" type="button" (click)="toggle(c.id, c.isHidden, c.version)">{{ c.isHidden ? 'إظهار' : 'إخفاء' }}</button>
        </li>
      }
    </ul>
    <div class="toolbar"><button class="pill primary" type="button" (click)="open.newCategory()">+ فئة جديدة</button></div>
  `,
})
export class CategoriesSheet {
  protected readonly store = inject(ExpensesStore);
  protected readonly open = inject(EntrySheets);
  private readonly api = inject(Api);
  private readonly dialogs = inject(Dialogs);
  private readonly toasts = inject(Toasts);

  protected async move(i: number, d: number): Promise<void> {
    const ids = this.store.categories().map((c) => c.id);
    [ids[i], ids[i + d]] = [ids[i + d], ids[i]];
    await this.save(() => this.api.reorderCategories(this.store.budgetId() ?? '', ids));
  }

  protected async rename(id: string, name: string, version: number): Promise<void> {
    const n = await this.dialogs.prompt('الاسم الجديد:', name, 'إعادة تسمية');
    if (n) await this.save(() => this.api.patchCategory(id, { name: n, version }));
    void this.open.categories();
  }

  protected async toggle(id: string, hidden: boolean, version: number): Promise<void> {
    await this.save(() => this.api.patchCategory(id, { isHidden: !hidden, version }));
  }

  private async save(call: () => Promise<unknown>): Promise<void> {
    try {
      await call();
      await this.store.reloadLists();
    } catch (e) {
      this.toasts.show((e as ApiError).message);
    }
  }
}

/** SH-06: a new category: a name and an emoji from M-SAL's fixed list. */
@Component({
  selector: 'app-new-category-sheet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <input #nameField class="field" type="text" dir="auto" placeholder="اسم الفئة" aria-label="اسم الفئة"
           (input)="name.set(nameField.value)">
    <p class="hint" dir="rtl">اختر رمزًا:</p>
    <div class="emoji-grid" role="group" aria-label="الرمز">
      @for (e of choices; track e) {
        <button class="em" type="button" [attr.aria-label]="e" [attr.aria-pressed]="e === emoji()" (click)="emoji.set(e)">{{ e }}</button>
      }
    </div>
    @if (error()) {
      <div class="x-note bad" role="alert" dir="rtl">{{ error() }}</div>
    }
    <div class="toolbar"><button class="pill primary" type="button" (click)="add()">إضافة</button></div>
  `,
})
export class NewCategorySheet {
  private readonly api = inject(Api);
  private readonly store = inject(ExpensesStore);
  private readonly open = inject(EntrySheets);
  protected readonly choices = EMOJI_CHOICES;
  protected readonly name = signal('');
  protected readonly emoji = signal('');
  protected readonly error = signal('');
  private readonly nameField = viewChild<ElementRef<HTMLInputElement>>('nameField');

  constructor() {
    afterNextRender(() => setTimeout(() => this.nameField()?.nativeElement.focus({ preventScroll: true }), 50));
  }

  protected async add(): Promise<void> {
    const n = this.name().trim();
    if (!n) return this.error.set('أدخل اسم الفئة.');
    if (!this.emoji()) return this.error.set('اختر رمزًا.');
    try {
      await this.api.createCategory(this.store.budgetId() ?? '', n, this.emoji());
      await this.store.reloadLists();
      void this.open.categories();
    } catch (e) {
      this.error.set((e as ApiError).message);
    }
  }
}

/** SH-07: the trash, newest deleted first (M-SAL's openDeletedSheet, R-ENT-09). */
@Component({
  selector: 'app-deleted-sheet',
  imports: [MoneyView],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (list().length === 0) {
      <p class="x-none" dir="rtl">لا توجد عناصر محذوفة.</p>
    } @else {
      <ul class="m-list">
        @for (e of list(); track e.id) {
          <li class="m-row del">
            <span class="m-emoji" aria-hidden="true">{{ store.catOf(e.categoryId).emoji }}</span>
            <div class="m-main">
              <div class="m-name" dir="auto">{{ nameOf(e) }}</div>
              <div class="m-sub" dir="rtl">{{ sub(e) }}</div>
            </div>
            <app-money [value]="e.amount" cls="val" />
            <button class="chip" type="button" (click)="restore(e)">استعادة</button>
            <button class="chip danger" type="button" (click)="purge(e)">حذف نهائي</button>
          </li>
        }
      </ul>
      <div class="toolbar"><button class="pill" type="button" (click)="purgeAll()">حذف الكل نهائيًا</button></div>
    }
  `,
})
export class DeletedSheet implements OnInit {
  protected readonly store = inject(ExpensesStore);
  private readonly api = inject(Api);
  private readonly dialogs = inject(Dialogs);
  private readonly toasts = inject(Toasts);
  private readonly open = inject(EntrySheets);
  protected readonly list = signal<Entry[]>([]);

  ngOnInit(): void {
    void this.load();
  }

  protected nameOf(e: Entry): string {
    return e.name || this.store.catOf(e.categoryId).name;
  }

  protected sub(e: Entry): string {
    const multi = this.store.accounts().length > 1;
    const acc = this.store.accounts().find((a) => a.id === e.accountId)?.name ?? '';
    return shortArabicDate(e.entryDate, true) + (multi ? ' · ' + acc : '');
  }

  protected async restore(e: Entry): Promise<void> {
    await this.api.restoreEntry(e.id);
    this.store.refresh();
    await this.load();
    this.toasts.show('تمت الاستعادة');
  }

  protected async purge(e: Entry): Promise<void> {
    if (!(await this.dialogs.confirm(`حذف «${this.nameOf(e)}» نهائيًا؟ لا يمكن التراجع.`, 'حذف نهائي', 'حذف'))) {
      void this.open.deleted();
      return;
    }
    await this.api.purgeEntry(e.id);
    this.store.refresh();
    void this.open.deleted();
  }

  protected async purgeAll(): Promise<void> {
    const n = this.list().length;
    if (!(await this.dialogs.confirm(`حذف كل العناصر المحذوفة (${n}) نهائيًا؟ لا يمكن التراجع.`, 'حذف نهائي', 'حذف'))) {
      void this.open.deleted();
      return;
    }
    await this.api.purgeDeleted(this.store.budgetId() ?? '');
    this.store.refresh();
    void this.open.deleted();
  }

  private async load(): Promise<void> {
    const budgetId = this.store.budgetId();
    if (budgetId) this.list.set(await this.api.deletedEntries(budgetId));
  }
}
