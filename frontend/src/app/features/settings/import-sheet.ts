import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';

import { ImportCounts, ImportPreview } from '../../core/api/models';
import { Sheets } from '../../shared/sheet/sheets';

const ROWS: [string, keyof ImportCounts][] = [
  ['الأشخاص', 'profiles'], ['مجموعات الميزانية', 'blocks'], ['بنود الميزانية', 'rows'], ['الحسابات', 'accounts'],
  ['الدورات', 'cycles'], ['المصاريف', 'entries'], ['المحذوفات', 'deleted'],
];

/**
 * SH-09: M-SAL's openImportSheet. "In the file" next to "what you have now", then merge or
 * replace; closes with the chosen mode ({@code MERGE} or {@code REPLACE}).
 */
@Component({
  selector: 'app-import-sheet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let p = preview();
    <p dir="rtl">{{ p.kind === 'STATE' ? 'ملف تصدير كامل.' : 'ملف بصيغة قديمة لشخص واحد (ميزانية فقط).' }}</p>
    @if (p.newer) {
      <p class="x-note warn" dir="rtl">هذا الملف من نسخة أحدث من التطبيق. سيُحتفظ بكل حقوله كما هي.</p>
    }
    <table class="cmp" dir="rtl">
      <thead><tr><th></th><th>في الملف</th><th>عندك الآن</th></tr></thead>
      <tbody>
        @for (r of rows; track r[1]) {
          <tr><td>{{ r[0] }}</td><td dir="ltr">{{ p.counts[r[1]] }}</td><td dir="ltr">{{ p.current[r[1]] }}</td></tr>
        }
      </tbody>
    </table>
    @for (w of p.warnings; track w.code) {
      <p class="x-note warn" dir="rtl">{{ w.message }}</p>
    }
    <p class="hint" dir="rtl">الدمج لا يغيّر ولا يحذف أي شيء موجود، ويضيف فقط ما هو ناقص. قبل أي تطبيق تُحفظ نسخة احتياطية.</p>
    <div class="toolbar">
      @if (p.kind === 'STATE') {
        <button class="pill primary" type="button" (click)="sheets.close('MERGE')">دمج مع بياناتي</button>
        <button class="pill" type="button" (click)="sheets.close('REPLACE')">استبدال الكل</button>
      } @else {
        <button class="pill primary" type="button" (click)="sheets.close('MERGE')">دمج في «{{ p.target?.name }}»</button>
        <button class="pill" type="button" (click)="sheets.close('REPLACE')">استبدال ميزانية «{{ p.target?.name }}»</button>
      }
      <button class="pill" type="button" (click)="sheets.close()">إلغاء</button>
    </div>
  `,
})
export class ImportSheet {
  protected readonly sheets = inject(Sheets);
  readonly preview = input.required<ImportPreview>();
  protected readonly rows = ROWS;
}
